package org.chaiware.acommander.commands;

import org.assertj.core.api.Assertions;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.mockito.Mockito.*;

class CommandsAdvancedImplTest {

    @TempDir
    Path tempDir;

    @Test
    void moveBatchMovesMultipleFilesToTargetFolder() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));

        Path first = sourceDir.resolve("one.txt");
        Path second = sourceDir.resolve("two.txt");
        Files.writeString(first, "first");
        Files.writeString(second, "second");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        VFileSystem localFs = new LocalFileSystem("");
        when(panesHelper.getFocusedFileSystem()).thenReturn(localFs);
        when(panesHelper.getUnfocusedFileSystem()).thenReturn(localFs);

        AppConfig appConfig = new AppConfig();
        appConfig.setActions(List.of());
        AppRegistry appRegistry = new AppRegistry(appConfig);
        CommandsAdvancedImpl commands = new CommandsAdvancedImpl(panesHelper, appRegistry);

        commands.moveBatch(
                List.of(new FileItem(first.toFile()), new FileItem(second.toFile())),
                targetDir.toString()
        );

        Assertions.assertThat(first).doesNotExist();
        Assertions.assertThat(second).doesNotExist();
        Assertions.assertThat(targetDir.resolve("one.txt")).exists();
        Assertions.assertThat(targetDir.resolve("two.txt")).exists();
        verify(panesHelper, atLeastOnce()).refreshFileListViews();
    }

    @Test
    void mergePdfsKeepsAsciiInputsUntilExternalProcessCompletes() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));

        Path first = sourceDir.resolve("one.pdf");
        Path second = sourceDir.resolve("two.pdf");
        Path targetMerged = targetDir.resolve("merged.pdf");
        Files.writeString(first, "pdf-a");
        Files.writeString(second, "pdf-b");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        VFileSystem localFs = new LocalFileSystem("");
        when(panesHelper.getFocusedFileSystem()).thenReturn(localFs);
        when(panesHelper.getUnfocusedFileSystem()).thenReturn(localFs);

        ActionDefinition mergeAction = new ActionDefinition();
        mergeAction.setId("mergePdf");
        mergeAction.setPath("apps/pdf/pdftk.exe");
        mergeAction.setArgs(List.of("${selectedFiles}", "cat", "output", "${outputPdf}"));

        AppConfig appConfig = new AppConfig();
        appConfig.setActions(List.of(mergeAction));
        AppRegistry appRegistry = new AppRegistry(appConfig);

        TestMergeCommands commands = new TestMergeCommands(panesHelper, appRegistry);

        commands.mergePDFs(
                List.of(new FileItem(first.toFile()), new FileItem(second.toFile())),
                targetMerged.toString()
        );

        waitForCondition(() -> commands.asciiInputPaths.size() == 2, 2_000);
        for (Path input : commands.asciiInputPaths) {
            Assertions.assertThat(input).exists();
        }

        commands.completeExternalMerge();

        waitForCondition(() -> Files.exists(targetMerged), 2_000);
        Assertions.assertThat(targetMerged).exists();

        waitForCondition(() -> commands.asciiWorkDir != null && !Files.exists(commands.asciiWorkDir), 2_000);
        Assertions.assertThat(commands.asciiWorkDir).doesNotExist();
    }

    private static void waitForCondition(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        Assertions.fail("Timed out waiting for condition.");
    }

    private static final class TestMergeCommands extends CommandsAdvancedImpl {
        private final CompletableFuture<List<String>> processFuture = new CompletableFuture<>();
        private final List<Path> asciiInputPaths = new ArrayList<>();
        private Path asciiWorkDir;
        private Path asciiOutputPath;

        private TestMergeCommands(FilesPanesHelper fileListsLoader, AppRegistry appRegistry) {
            super(fileListsLoader, appRegistry);
        }

        @Override
        protected CompletableFuture<List<String>> runExecutable(List<String> params, boolean shouldUpdateUI) {
            asciiInputPaths.clear();
            asciiWorkDir = null;
            asciiOutputPath = null;

            for (int i = 0; i < params.size(); i++) {
                String value = stripQuotes(params.get(i));
                if (value.endsWith("input_0.pdf") || value.endsWith("input_1.pdf")) {
                    Path input = Path.of(value);
                    asciiInputPaths.add(input);
                    asciiWorkDir = input.getParent();
                }
                if ("output".equalsIgnoreCase(value) && i + 1 < params.size()) {
                    asciiOutputPath = Path.of(stripQuotes(params.get(i + 1)));
                }
            }
            return processFuture;
        }

        private void completeExternalMerge() {
            if (asciiOutputPath == null) {
                throw new IllegalStateException("Missing output path in test command.");
            }
            try {
                Files.createDirectories(asciiOutputPath.getParent());
                Files.writeString(asciiOutputPath, "merged-pdf-placeholder");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            processFuture.complete(List.of("ok"));
        }

        private static String stripQuotes(String value) {
            if (value == null) {
                return "";
            }
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
