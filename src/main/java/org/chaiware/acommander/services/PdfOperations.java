package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * PDF merge, page extraction and page count with the apps.json pdftk actions. Sources come from the focused pane and
 * results go to the other pane, local, archive or FTP. pdftk here is not Unicode-safe, so it only sees ASCII copies.
 */
public class PdfOperations {
    private static final Logger log = LoggerFactory.getLogger(PdfOperations.class);

    private final FilesPanesHelper panes;
    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public PdfOperations(FilesPanesHelper panes, AppRegistry registry, ExternalToolRunner runner) {
        this.panes = panes;
        this.registry = registry;
        this.runner = runner;
    }

    /** Merges {@code items} into {@code fileName} in {@code targetFolder}; runs in the background, a failure is shown. */
    public void merge(List<FileItem> items, String targetFolder, String fileName) throws IOException {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_merge_work_");
        try {
            List<String> inputs = new ArrayList<>();
            for (FileItem item : items) {
                if (isParentEntry(item)) {
                    continue;
                }
                Path input = workDir.resolve("input_" + inputs.size() + ".pdf");
                sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), input.toString());
                inputs.add(input.toString());
            }
            Path output = workDir.resolve("output.pdf");
            ActionDefinition action = registry.requireAction("mergePdf");
            List<String> command = ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), panes,
                    Map.of("${outputPdf}", output.toString()), inputs);
            String target = ClipboardTransfer.targetInternalPath(targetFs, targetFolder, fileName, false);
            runner.reportFailure(runner.runExecutable(command, false)
                    .thenRun(() -> {
                        save(output, targetFs, target);
                        panes.refreshFileListViews();
                    })
                    .whenComplete((ignored, error) -> FileHelper.deleteQuietly(workDir)), "Merge PDFs");
        } catch (IOException | RuntimeException e) {
            FileHelper.deleteQuietly(workDir);
            throw e;
        }
    }

    /** Page files of {@code pdf} into {@code destinationPath}, as {@code options} say; the tool runs in the background. */
    public void extractPages(FileItem pdf, String destinationPath, PdfExtractOptions options) throws IOException {
        if (isParentEntry(pdf)) {
            return;
        }
        requirePdf(pdf);
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_extract_work_");
        try {
            Path input = workDir.resolve("input.pdf");
            sourceFs.copy(sourceFs.getInternalPath(pdf), new LocalFileSystem(""), input.toString());
            ActionDefinition action = registry.requireAction("extractPdfPages");
            int totalPages = options.knownTotalPages() != null && options.knownTotalPages() > 0
                    ? options.knownTotalPages() : readPageCount(action, input);
            validateExtractRequest(pdf.getName(), totalPages, options);
            List<Integer> selectedPages = options.mode() == PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE
                    ? parsePageExpression(options.pageExpression(), totalPages) : List.of();
            List<String> command = ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), panes,
                    Map.of("${outputPattern}", workDir.resolve("page_%04d.pdf").toString()), List.of(input.toString()));
            String prefix = pdf.getName().replaceFirst("(?i)\\.pdf$", "");
            runner.reportFailure(runner.runExecutable(command, false)
                    .handle((ignored, error) -> {
                        if (error != null) {
                            log.warn("pdftk burst failed for '{}', extracting page by page", pdf.getName(), error);
                            extractPageByPage(action, input, workDir, totalPages);
                        }
                        return null;
                    })
                    .thenRun(() -> {
                        try {
                            savePages(workDir, prefix, options, selectedPages, targetFs, destinationPath);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                        panes.refreshFileListViews();
                    })
                    .whenComplete((ignored, error) -> FileHelper.deleteQuietly(workDir)), "Extract PDF Pages");
        } catch (IOException | RuntimeException e) {
            FileHelper.deleteQuietly(workDir);
            throw e;
        }
    }

    public int pageCount(FileItem pdf) throws IOException {
        if (isParentEntry(pdf)) {
            throw new IllegalArgumentException("No valid PDF selected.");
        }
        requirePdf(pdf);
        Path workDir = AppTempDir.createTempDirectory("acommander_pdf_count_work_");
        try {
            Path input = workDir.resolve("input.pdf");
            VFileSystem fs = panes.getFocusedFileSystem();
            fs.copy(fs.getInternalPath(pdf), new LocalFileSystem(""), input.toString());
            return readPageCount(registry.requireAction("extractPdfPages"), input);
        } finally {
            FileHelper.deleteQuietly(workDir);
        }
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

    private static boolean isParentEntry(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }

    private static void requirePdf(FileItem item) {
        if (item.isDirectory() || !"pdf".equals(item.extension())) {
            throw new IllegalArgumentException("The selected file is not a PDF: " + item.getName());
        }
    }

    private static void save(Path local, VFileSystem targetFs, String target) {
        try {
            new LocalFileSystem("").copy(local.toString(), targetFs, target);
        } catch (IOException e) {
            throw new UncheckedIOException("The PDF was made but could not be saved to " + target, e);
        }
    }

    /** Names the burst pages as the options ask, in a local folder, then saves them to the target pane. */
    private void savePages(Path workDir, String prefix, PdfExtractOptions options, List<Integer> selectedPages,
                           VFileSystem targetFs, String destinationPath) throws Exception {
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
                    runner.runExecutable(ToolCommandBuilder.buildCommand(mergeAction.getPath(), mergeAction.getArgs(),
                            panes, Map.of("${outputPdf}", chunkFile.toString()), chunk), false).join();
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
            runner.runExecutable(ToolCommandBuilder.buildCommand(action.getPath(),
                    List.of("${selectedFile}", "cat", String.valueOf(page), "output", "${outputPdf}"), panes,
                    Map.of("${outputPdf}", output.toString()), List.of(input.toString())), false).join();
        }
    }

    private int readPageCount(ActionDefinition action, Path input) {
        List<String> output;
        try {
            output = runner.runExecutable(ToolCommandBuilder.buildCommand(action.getPath(),
                    List.of("${selectedFile}", "dump_data"), panes, Map.of(), List.of(input.toString())), false).join();
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
