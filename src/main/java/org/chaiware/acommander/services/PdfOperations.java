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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * PDF merge, page extraction and page count with the apps.json qpdf actions, on the file systems and paths captured
 * when the user started ({@link ClipboardTransfer#capture}); results go to any pane type. qpdf works on local copies,
 * since a source may be on FTP or in an archive. Blocking: each method returns once its output is saved.
 */
public class PdfOperations {
    private static final String WARNINGS_OK = "--warning-exit-0";

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
            String prefix = pdf.name().replaceFirst("(?i)\\.pdf$", "");
            Path outDir = Files.createDirectories(workDir.resolve("out"));
            if (options.mode() == PdfExtractOptions.Mode.PAGES_PER_PDF) {
                for (int start = 1; start <= totalPages; start += options.pagesPerPdf()) {
                    Operation.checkNotStopped();
                    int end = Math.min(start + options.pagesPerPdf() - 1, totalPages);
                    Path chunk = outDir.resolve(String.format("%s_%04d-%04d.pdf", prefix, start, end));
                    run(action, List.of(WARNINGS_OK, "${selectedFile}", "--pages", ".", start + "-" + end, "--", "${outputPdf}"),
                            Map.of("${outputPdf}", chunk.toString()), List.of(input.toString()));
                }
            } else {
                List<Integer> selectedPages = options.mode() == PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE
                        ? parsePageExpression(options.pageExpression(), totalPages) : List.of();
                run(action, null, Map.of("${outputPattern}", workDir.resolve("page_%d.pdf").toString()), List.of(input.toString()));
                namePages(workDir, outDir, prefix, selectedPages);
            }
            saveAll(outDir, targetFs, destinationFolder);
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

    /** Runs qpdf and waits; {@code args} replaces the action's arguments when given. Fails with the tool's error. */
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

    /** Moves the split pages into {@code outDir} as {@code prefix_0001.pdf}; only {@code selectedPages} when given. */
    private static void namePages(Path workDir, Path outDir, String prefix, List<Integer> selectedPages) throws IOException {
        List<Path> pages;
        try (Stream<Path> files = Files.list(workDir)) {
            pages = files.filter(path -> path.getFileName().toString().matches("^page_\\d+\\.pdf$"))
                    .sorted(Comparator.comparingInt(path -> Integer.parseInt(path.getFileName().toString().replaceAll("\\D", ""))))
                    .toList();
        }
        if (pages.isEmpty()) {
            throw new IOException("PDF extraction produced no pages.");
        }
        if (selectedPages.isEmpty()) {
            for (int i = 0; i < pages.size(); i++) {
                Files.move(pages.get(i), outDir.resolve(String.format("%s_%04d.pdf", prefix, i + 1)));
            }
            return;
        }
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

    /** Saves every file in {@code outDir} to the target pane's folder. */
    private static void saveAll(Path outDir, VFileSystem targetFs, String destinationPath) throws IOException {
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

    private int readPageCount(ActionDefinition action, Path input) {
        List<String> output;
        try {
            output = run(action, List.of(WARNINGS_OK, "--show-npages", "${selectedFile}"), Map.of(), List.of(input.toString()));
        } catch (CompletionException ex) {
            throw new IllegalArgumentException("Failed to read PDF page count.", ex.getCause() == null ? ex : ex.getCause());
        }
        // stderr is merged in, so a damaged file's warnings come before the count
        return output.stream().map(String::trim).filter(line -> line.matches("\\d{1,9}")).map(Integer::parseInt)
                .reduce((first, last) -> last)
                .orElseThrow(() -> new IllegalArgumentException("Could not determine PDF page count."));
    }
}
