package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.commands.Operation;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * PDF merge, page extraction and page count with the apps.json pdftk actions, on the file systems and paths captured
 * when the user started ({@link ClipboardTransfer#capture}); results go to any pane type. pdftk here is not
 * Unicode-safe, so it only sees ASCII copies. Blocking: each method returns once its output is saved.
 */
public class PdfOperations {
    private static final Logger log = LoggerFactory.getLogger(PdfOperations.class);

    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public PdfOperations(AppRegistry registry, ExternalToolRunner runner) {
        this.registry = registry;
        this.runner = runner;
    }

    /** Merges {@code pdfs} into {@code fileName} in {@code targetFolder}. */
    public void merge(VFileSystem sourceFs, List<Entry> pdfs, VFileSystem targetFs, String targetFolder, String fileName)
            throws IOException {
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_merge_work_");
        try {
            List<String> inputs = new ArrayList<>();
            for (Entry pdf : pdfs) {
                Path input = workDir.resolve("input_" + inputs.size() + ".pdf");
                sourceFs.copy(pdf.sourceInternalPath(), new LocalFileSystem(""), input.toString());
                inputs.add(input.toString());
            }
            Path output = workDir.resolve("output.pdf");
            run(registry.requireAction("mergePdf"), null, Map.of("${outputPdf}", output.toString()), inputs);
            save(output, targetFs, ClipboardTransfer.targetInternalPath(targetFs, targetFolder, fileName, false));
        } finally {
            FileHelper.deleteQuietly(workDir);
        }
    }

    /** Page files of {@code pdf} into {@code destinationFolder}, as {@code options} say. */
    public void extractPages(VFileSystem sourceFs, Entry pdf, VFileSystem targetFs, String destinationFolder,
                             PdfExtractOptions options) throws IOException {
        requirePdf(pdf);
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_extract_work_");
        try {
            Path input = workDir.resolve("input.pdf");
            sourceFs.copy(pdf.sourceInternalPath(), new LocalFileSystem(""), input.toString());
            ActionDefinition action = registry.requireAction("extractPdfPages");
            int totalPages = options.knownTotalPages() != null && options.knownTotalPages() > 0
                    ? options.knownTotalPages() : readPageCount(action, input);
            validateExtractRequest(pdf.name(), totalPages, options);
            List<Integer> selectedPages = options.mode() == PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE
                    ? parsePageExpression(options.pageExpression(), totalPages) : List.of();
            try {
                run(action, null, Map.of("${outputPattern}", workDir.resolve("page_%04d.pdf").toString()), List.of(input.toString()));
            } catch (CompletionException e) {
                Operation.rethrowIfStopped(e);
                log.warn("pdftk burst failed for '{}', extracting page by page", pdf.name(), e.getCause());
                extractPageByPage(action, input, workDir, totalPages);
            }
            savePages(workDir, pdf.name().replaceFirst("(?i)\\.pdf$", ""), options, selectedPages, targetFs, destinationFolder);
        } finally {
            FileHelper.deleteQuietly(workDir);
        }
    }

    public int pageCount(VFileSystem fs, Entry pdf) throws IOException {
        requirePdf(pdf);
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_count_work_");
        try {
            Path input = workDir.resolve("input.pdf");
            fs.copy(pdf.sourceInternalPath(), new LocalFileSystem(""), input.toString());
            return readPageCount(registry.requireAction("extractPdfPages"), input);
        } finally {
            FileHelper.deleteQuietly(workDir);
        }
    }

    /** Runs pdftk and waits; {@code args} replaces the action's arguments when given. Fails with the tool's error. */
    private List<String> run(ActionDefinition action, List<String> args, Map<String, String> values, List<String> files) {
        return runner.runExecutable(ToolCommandBuilder.buildCommand(action.getPath(),
                args == null ? action.getArgs() : args, null, values, files), false).join();
    }

    /**
     * Pages like "1, 3-5, p7, 9:8" in ascending order. Every page is checked against {@code totalPages} before a range
     * is expanded, so a typo like 1-2000000000 fails at once instead of filling memory.
     */
    static List<Integer> parsePageExpression(String expression, int totalPages) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Page expression is empty.");
        }
        BitSet pages = new BitSet(Math.max(totalPages, 0) + 1);
        for (String rawToken : expression.split(",")) {
            String token = rawToken.trim().replaceFirst("(?i)^pages?\\s*", "").replaceFirst("(?i)^p\\s*", "");
            if (token.isEmpty()) {
                continue;
            }
            try {
                String[] bounds = token.replace(':', '-').split("-", -1);
                if (bounds.length > 2) {
                    throw new IllegalArgumentException("Invalid page range: " + token);
                }
                int start = Integer.parseInt(bounds[0].trim());
                int end = bounds.length == 2 ? Integer.parseInt(bounds[1].trim()) : start;
                if (start <= 0 || end <= 0) {
                    throw new IllegalArgumentException("Page numbers must be positive: " + token);
                }
                int last = Math.max(start, end);
                if (last > totalPages) {
                    throw new IllegalArgumentException("Requested page is out of bounds. PDF has " + totalPages
                            + " pages, requested up to page " + last + ".");
                }
                pages.set(Math.min(start, end), last);
                pages.set(last);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid page token: " + token, ex);
            }
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No pages were parsed from page expression.");
        }
        return pages.stream().boxed().toList();
    }

    static void validateExtractRequest(String fileName, int totalPages, PdfExtractOptions options) {
        if (totalPages <= 1) {
            throw new IllegalArgumentException(
                    "Cannot extract pages from '" + fileName + "': the PDF contains only " + totalPages + " page.");
        }
        if (options.mode() == PdfExtractOptions.Mode.PAGES_PER_PDF) {
            int pagesPerPdf = options.pagesPerPdf() == null ? 0 : options.pagesPerPdf();
            if (pagesPerPdf <= 0) {
                throw new IllegalArgumentException("Pages per PDF must be greater than zero.");
            }
            if (pagesPerPdf > totalPages) {
                throw new IllegalArgumentException(
                        "Pages per PDF (" + pagesPerPdf + ") exceeds PDF length (" + totalPages + " pages).");
            }
        } else if (options.mode() == PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE) {
            parsePageExpression(options.pageExpression(), totalPages);
        }
    }

    private static void requirePdf(Entry entry) {
        if (entry.directory() || !"pdf".equals(FileItem.extension(entry.name()))) {
            throw new IllegalArgumentException("The selected file is not a PDF: " + entry.name());
        }
    }

    private static void save(Path local, VFileSystem targetFs, String target) throws IOException {
        try {
            new LocalFileSystem("").copy(local.toString(), targetFs, target);
        } catch (IOException e) {
            throw new IOException("The PDF was made but could not be saved to " + target, e);
        }
    }

    /** Names the burst pages as the options ask, in a local folder, then saves them to the target pane. */
    private void savePages(Path workDir, String prefix, PdfExtractOptions options, List<Integer> selectedPages,
                           VFileSystem targetFs, String destinationPath) throws IOException {
        List<Path> pages;
        try (Stream<Path> files = Files.list(workDir)) {
            pages = files.filter(path -> path.getFileName().toString().matches("^page_\\d{4}\\.pdf$")).sorted().toList();
        }
        if (pages.isEmpty()) {
            throw new IOException("PDF extraction produced no pages.");
        }
        Path outDir = Files.createDirectories(workDir.resolve("out"));
        switch (options.mode()) {
            case SPECIFIC_PAGES_SINGLE -> {
                int saved = 0;
                for (int page : selectedPages) {
                    if (page <= pages.size()) {
                        Files.move(pages.get(page - 1), outDir.resolve(String.format("%s_%04d.pdf", prefix, page)));
                        saved++;
                    }
                }
                if (saved == 0) {
                    throw new IllegalArgumentException("No selected pages matched the source PDF page count.");
                }
            }
            case PAGES_PER_PDF -> {
                ActionDefinition mergeAction = registry.requireAction("mergePdf");
                for (int start = 1; start <= pages.size(); start += options.pagesPerPdf()) {
                    int end = Math.min(start + options.pagesPerPdf() - 1, pages.size());
                    List<String> chunk = pages.subList(start - 1, end).stream().map(Path::toString).toList();
                    Path chunkFile = outDir.resolve(String.format("%s_%04d-%04d.pdf", prefix, start, end));
                    run(mergeAction, null, Map.of("${outputPdf}", chunkFile.toString()), chunk);
                }
            }
            default -> {
                for (int i = 0; i < pages.size(); i++) {
                    Files.move(pages.get(i), outDir.resolve(String.format("%s_%04d.pdf", prefix, i + 1)));
                }
            }
        }
        try (Stream<Path> files = Files.list(outDir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (targetFs instanceof LocalFileSystem) {
                    Files.move(file, Paths.get(destinationPath, name), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    save(file, targetFs, ClipboardTransfer.targetInternalPath(targetFs, destinationPath, name, false));
                }
            }
        }
    }

    private void extractPageByPage(ActionDefinition action, Path input, Path workDir, int totalPages) {
        for (int page = 1; page <= totalPages; page++) {
            Path output = workDir.resolve(String.format("page_%04d.pdf", page));
            run(action, List.of("${selectedFile}", "cat", String.valueOf(page), "output", "${outputPdf}"),
                    Map.of("${outputPdf}", output.toString()), List.of(input.toString()));
        }
    }

    private int readPageCount(ActionDefinition action, Path input) {
        List<String> output;
        try {
            output = run(action, List.of("${selectedFile}", "dump_data"), Map.of(), List.of(input.toString()));
        } catch (CompletionException ex) {
            throw new IllegalArgumentException("Failed to read PDF page count.", ex.getCause() == null ? ex : ex.getCause());
        }
        for (String line : output) {
            String trimmed = line.trim();
            if (trimmed.startsWith("NumberOfPages:")) {
                try {
                    return Integer.parseInt(trimmed.substring("NumberOfPages:".length()).trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Failed to parse PDF page count.", ex);
                }
            }
        }
        throw new IllegalArgumentException("Could not determine PDF page count.");
    }
}
