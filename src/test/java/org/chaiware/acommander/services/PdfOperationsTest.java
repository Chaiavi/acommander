package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfOperationsTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesPageListsAndRanges() {
        assertThat(PdfOperations.parsePageExpression("1, 3-5, p7, 9:8", 10)).containsExactly(1, 3, 4, 5, 7, 8, 9);
        assertThat(PdfOperations.parsePageExpression("pages 2", 10)).containsExactly(2);
        assertThat(PdfOperations.parsePageExpression("5-3, 4, 3", 5)).containsExactly(3, 4, 5);
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("0", 10)).hasMessageContaining("positive");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("1-2-3", 10)).hasMessageContaining("Invalid page range");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("x", 10)).hasMessageContaining("Invalid page token");
    }

    @Test
    void aHugeRangeIsRejectedBeforeItIsExpanded() {
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("1-2147483647", 3)).hasMessageContaining("out of bounds");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("2147483647-1", 3)).hasMessageContaining("out of bounds");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("1-99999999999", 3)).hasMessageContaining("Invalid page token");
        assertThat(PdfOperations.parsePageExpression("1-3", 3)).containsExactly(1, 2, 3);
    }

    @Test
    void extractRequestIsCheckedAgainstThePageCount() {
        PdfExtractOptions tooMany = new PdfExtractOptions(PdfExtractOptions.Mode.PAGES_PER_PDF, null, 5, null);
        PdfExtractOptions outOfRange = new PdfExtractOptions(PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE, "2, 9", null, null);

        assertThatThrownBy(() -> PdfOperations.validateExtractRequest("a.pdf", 1, PdfExtractOptions.extractAll()))
                .hasMessageContaining("only 1 page");
        assertThatThrownBy(() -> PdfOperations.validateExtractRequest("a.pdf", 3, tooMany)).hasMessageContaining("exceeds");
        assertThatThrownBy(() -> PdfOperations.validateExtractRequest("a.pdf", 3, outOfRange)).hasMessageContaining("out of bounds");
    }

    @Test
    void mergeUsesAsciiInputCopiesAndReturnsOnlyAfterSavingAndCleaningUp() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path first = Files.writeString(sourceDir.resolve("one.pdf"), "pdf-a");
        Path second = Files.writeString(sourceDir.resolve("two.pdf"), "pdf-b");

        ActionDefinition mergeAction = new ActionDefinition();
        mergeAction.setId("mergePdf");
        mergeAction.setPath("apps/pdf/pdftk.exe");
        mergeAction.setArgs(List.of("${selectedFiles}", "cat", "output", "${outputPdf}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(mergeAction));
        FakeRunner runner = new FakeRunner();
        LocalFileSystem local = new LocalFileSystem("");

        new PdfOperations(new AppRegistry(config), runner).merge(local,
                List.of(new Entry("one.pdf", false, first.toString()), new Entry("two.pdf", false, second.toString())),
                local, targetDir.toString(), "merged.pdf");

        assertThat(runner.inputNames).containsExactly("input_0.pdf", "input_1.pdf");
        assertThat(targetDir.resolve("merged.pdf")).hasContent("merged");
        assertThat(runner.output.getParent()).as("the work folder is cleaned up").doesNotExist();
    }

    @Test
    void pagesPerPdfCutsEachChunkStraightFromTheSource() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path book = Files.writeString(sourceDir.resolve("book.pdf"), "pdf");
        ActionDefinition extractAction = new ActionDefinition();
        extractAction.setId("extractPdfPages");
        extractAction.setPath("apps/pdf/pdftk.exe");
        extractAction.setArgs(List.of("${selectedFile}", "burst", "output", "${outputPattern}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(extractAction));
        FakeRunner runner = new FakeRunner();
        LocalFileSystem local = new LocalFileSystem("");

        new PdfOperations(new AppRegistry(config), runner).extractPages(local, new Entry("book.pdf", false, book.toString()),
                local, targetDir.toString(), new PdfExtractOptions(PdfExtractOptions.Mode.PAGES_PER_PDF, null, 100, 250));

        assertThat(runner.commands).as("no burst, no merge").allMatch(command -> command.contains("cat"));
        assertThat(runner.commands).extracting(command -> command.get(command.indexOf("cat") + 1))
                .containsExactly("1-100", "101-200", "201-250");
        try (Stream<Path> files = Files.list(targetDir)) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted())
                    .containsExactly("book_0001-0100.pdf", "book_0101-0200.pdf", "book_0201-0250.pdf");
        }
    }

    /** Plays pdftk: records the inputs and writes the output file. */
    private static final class FakeRunner extends ExternalToolRunner {
        private final List<List<String>> commands = new ArrayList<>();
        private List<String> inputNames = List.of();
        private Path output;

        FakeRunner() {
            super(() -> {});
        }

        @Override
        public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles,
                                                             Set<Integer> acceptedNonZeroExitCodes) {
            commands.add(command);
            inputNames = command.stream().filter(arg -> arg.matches(".*input_\\d\\.pdf"))
                    .map(arg -> Path.of(arg).getFileName().toString()).toList();
            output = Path.of(command.get(command.indexOf("output") + 1));
            try {
                Files.writeString(output, "merged");
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(List.of("ok"));
        }
    }
}
