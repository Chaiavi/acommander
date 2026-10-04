package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.*;

class PdfOperationsTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesPageListsAndRanges() {
        assertThat(PdfOperations.parsePageExpression("1, 3-5, p7, 9:8")).containsExactly(1, 3, 4, 5, 7, 8, 9);
        assertThat(PdfOperations.parsePageExpression("pages 2")).containsExactly(2);
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("0")).hasMessageContaining("positive");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("1-2-3")).hasMessageContaining("Invalid page range");
        assertThatThrownBy(() -> PdfOperations.parsePageExpression("x")).hasMessageContaining("Invalid page token");
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
    void mergeKeepsAsciiInputsUntilTheToolEndsThenSavesAndCleansUp() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path first = Files.writeString(sourceDir.resolve("one.pdf"), "pdf-a");
        Path second = Files.writeString(sourceDir.resolve("two.pdf"), "pdf-b");

        FilesPanesHelper panes = mock(FilesPanesHelper.class);
        VFileSystem localFs = new LocalFileSystem("");
        when(panes.getFocusedFileSystem()).thenReturn(localFs);
        when(panes.getUnfocusedFileSystem()).thenReturn(localFs);
        ActionDefinition mergeAction = new ActionDefinition();
        mergeAction.setId("mergePdf");
        mergeAction.setPath("apps/pdf/pdftk.exe");
        mergeAction.setArgs(List.of("${selectedFiles}", "cat", "output", "${outputPdf}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(mergeAction));
        FakeRunner runner = new FakeRunner();

        new PdfOperations(panes, new AppRegistry(config), runner)
                .merge(List.of(new FileItem(first.toFile()), new FileItem(second.toFile())), targetDir.toString(), "merged.pdf");

        assertThat(runner.inputs).hasSize(2).allSatisfy(input -> assertThat(input).exists());
        runner.finishMerge();

        Path merged = targetDir.resolve("merged.pdf");
        waitFor(() -> Files.exists(merged) && !Files.exists(runner.output.getParent()));
        assertThat(merged).hasContent("merged");
        verify(panes).refreshFileListViews();
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for the merge to finish.");
            }
            Thread.sleep(20);
        }
    }

    /** Records the pdftk command instead of running it; {@link #finishMerge} plays the tool. */
    private static final class FakeRunner extends ExternalToolRunner {
        private final CompletableFuture<List<String>> result = new CompletableFuture<>();
        private List<Path> inputs = List.of();
        private Path output;

        FakeRunner() {
            super(() -> {});
        }

        @Override
        public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles,
                                                             Set<Integer> acceptedNonZeroExitCodes) {
            inputs = command.stream().filter(arg -> arg.matches(".*input_\\d\\.pdf")).map(Path::of).toList();
            output = Path.of(command.get(command.indexOf("output") + 1));
            return result;
        }

        void finishMerge() throws IOException {
            Files.writeString(output, "merged");
            result.complete(List.of("ok"));
        }
    }
}
