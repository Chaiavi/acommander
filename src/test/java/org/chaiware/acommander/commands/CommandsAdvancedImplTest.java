package org.chaiware.acommander.commands;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class CommandsAdvancedImplTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRemovesFilesRecursively() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path nested = Files.createDirectory(root.resolve("nested"));
        Files.writeString(nested.resolve("data.txt"), "delete");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        org.chaiware.acommander.vfs.VFileSystem fs = mock(org.chaiware.acommander.vfs.VFileSystem.class);
        when(panesHelper.getFocusedFileSystem()).thenReturn(fs);
        when(fs.getInternalPath(any())).thenAnswer(invocation -> {
            FileItem item = invocation.getArgument(0);
            return item.getFile().getAbsolutePath();
        });
        doAnswer(invocation -> {
            String pathStr = invocation.getArgument(0);
            Path path = Path.of(pathStr);
            if (Files.isDirectory(path)) {
                try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
            } else {
                Files.deleteIfExists(path);
            }
            return null;
        }).when(fs).delete(any());

        AppRegistry registry = new AppRegistry(new AppConfig());
        CommandsAdvancedImpl commands = new CommandsAdvancedImpl(panesHelper, registry);

        commands.delete(List.of(new FileItem(root.toFile())));

        Assertions.assertThat(root).doesNotExist();
        verify(panesHelper).refreshFileListViews();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void copyUsesExternalToolAndCopiesFile() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path sourceFile = sourceDir.resolve("data.txt");
        Files.writeString(sourceFile, "external");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getFocusedPath()).thenReturn(sourceDir.toString());
        when(panesHelper.getUnfocusedPath()).thenReturn(targetDir.toString());

        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "copy",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "Copy-Item -Path \"${selectedFile}\" -Destination \"${targetFolder}\" -Force"
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.copy(new FileItem(sourceFile.toFile()), targetDir.toString());
        commands.awaitLast();

        Assertions.assertThat(targetDir.resolve("data.txt")).exists();
        Assertions.assertThat(Files.readString(targetDir.resolve("data.txt"))).isEqualTo("external");
    }

    @Test
    void copyBatchBuildsSingleCommandWithAllSelectedFiles() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path first = sourceDir.resolve("first.txt");
        Path second = sourceDir.resolve("second.txt");
        Files.writeString(first, "one");
        Files.writeString(second, "two");
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "copy",
                        "copy-tool.exe",
                        List.of("${selectedFiles}", "/to=${targetFolder}")
                )
        ));

        RecordingCommandsAdvanced commands = new RecordingCommandsAdvanced(panesHelper, registry);

        commands.copyBatch(
                List.of(new FileItem(first.toFile()), new FileItem(second.toFile())),
                targetDir.toString()
        );

        Assertions.assertThat(commands.lastCommand)
                .containsExactly(
                        Path.of(System.getProperty("user.dir"), "copy-tool.exe").toString(),
                        first.toString(),
                        second.toString(),
                        "/to=" + targetDir
                );
        Assertions.assertThat(commands.lastShouldUpdateUI).isTrue();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void moveUsesExternalToolWhenDifferentDrive() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path sourceFile = sourceDir.resolve("data.txt");
        Files.writeString(sourceFile, "move");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        when(panesHelper.getFocusedPath()).thenReturn(sourceDir.toString());
        when(panesHelper.getUnfocusedPath()).thenReturn("D:\\dest");

        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "move",
                        "cmd",
                        List.of("/c", "exit", "0")
                )
        ));

        RecordingCommandsAdvanced commands = new RecordingCommandsAdvanced(panesHelper, registry);

        commands.move(new FileItem(sourceFile.toFile()), "D:\\dest");

        Assertions.assertThat(commands.lastCommand)
                .containsExactly("cmd", "/c", "exit", "0");
        Assertions.assertThat(commands.lastShouldUpdateUI).isTrue();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void packCreatesZipViaExternalTool() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path file = sourceDir.resolve("data.txt");
        Files.writeString(file, "zip");

        Path outputZip = tempDir.resolve("out.zip");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "pack",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "Compress-Archive -Path ${selectedFilesJoined} -DestinationPath \"${archiveFile}\" -Force"
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.pack(List.of(new FileItem(file.toFile())), outputZip.toString());
        commands.awaitLast();

        Assertions.assertThat(outputZip).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void unpackExtractsArchiveViaExternalTool() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path file = sourceDir.resolve("data.txt");
        Files.writeString(file, "zip");
        Path zip = tempDir.resolve("out.zip");

        ProcessBuilder pack = new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                "Compress-Archive -Path \"" + file + "\" -DestinationPath \"" + zip + "\" -Force"
        );
        pack.start().waitFor();

        Path destination = tempDir.resolve("dest");
        Files.createDirectories(destination);

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "unpack",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "Expand-Archive -Path \"${selectedFile}\" -DestinationPath \"${destinationPath}\" -Force"
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.unpack(new FileItem(zip.toFile()), destination.toString());
        commands.awaitLast();

        Assertions.assertThat(destination.resolve("data.txt")).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void extractAllCreatesOutputViaExternalTool() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path file = sourceDir.resolve("data.txt");
        Files.writeString(file, "extract");
        Path destination = Files.createDirectory(tempDir.resolve("dest"));

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "extractAll",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "Copy-Item -Path \"${selectedFile}\" -Destination \"${destinationPath}\" -Force"
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.extractAll(new FileItem(file.toFile()), destination.toString());
        commands.awaitLast();

        Assertions.assertThat(destination.resolve("data.txt")).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void mergePdfCreatesOutputViaExternalTool() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path a = sourceDir.resolve("a.txt");
        Path b = sourceDir.resolve("b.txt");
        Files.writeString(a, "one");
        Files.writeString(b, "two");
        Path output = tempDir.resolve("merged.pdf");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "mergePdf",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "Get-Content ${selectedFilesJoined} | Set-Content -Path \"${outputPdf}\""
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.mergePDFs(List.of(new FileItem(a.toFile()), new FileItem(b.toFile())), output.toString());
        commands.awaitLast();

        Assertions.assertThat(output).exists();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void extractPdfPagesCreatesOutputPatternViaExternalTool() throws Exception {
        Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
        Path pdf = sourceDir.resolve("doc.pdf");
        Files.writeString(pdf, "pdf");
        Path destination = Files.createDirectory(tempDir.resolve("dest"));

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        AppRegistry registry = new AppRegistry(configWithAction(
                action(
                        "extractPdfPages",
                        "powershell",
                        List.of(
                                "-NoProfile",
                                "-Command",
                                "$pattern='${outputPattern}'; $out=$pattern -replace '%04d','0001'; Set-Content -Path $out -Value 'page'"
                        )
                )
        ));

        ExecutingCommandsAdvanced commands = new ExecutingCommandsAdvanced(panesHelper, registry);

        commands.extractPDFPages(new FileItem(pdf.toFile()), destination.toString());
        commands.awaitLast();

        Assertions.assertThat(destination.resolve("doc_0001.pdf")).exists();
    }

    private static AppConfig configWithAction(ActionDefinition action) {
        AppConfig config = new AppConfig();
        config.setActions(List.of(action));
        return config;
    }

    private static ActionDefinition action(String id, String path, List<String> args) {
        ActionDefinition def = new ActionDefinition();
        def.setId(id);
        def.setPath(path);
        def.setArgs(args);
        def.setContexts(List.of("global"));
        return def;
    }

    private static final class RecordingCommandsAdvanced extends CommandsAdvancedImpl {
        private List<String> lastCommand;
        private boolean lastShouldUpdateUI;

        private RecordingCommandsAdvanced(FilesPanesHelper fileListsLoader, AppRegistry appRegistry) {
            super(fileListsLoader, appRegistry);
        }

        @Override
        protected CompletableFuture<List<String>> runExecutable(List<String> params, boolean shouldUpdateUI) {
            this.lastCommand = new ArrayList<>(params);
            this.lastShouldUpdateUI = shouldUpdateUI;
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class ExecutingCommandsAdvanced extends CommandsAdvancedImpl {
        private CompletableFuture<List<String>> lastFuture;

        private ExecutingCommandsAdvanced(FilesPanesHelper fileListsLoader, AppRegistry appRegistry) {
            super(fileListsLoader, appRegistry);
        }

        @Override
        protected CompletableFuture<List<String>> runExecutable(List<String> params, boolean shouldUpdateUI) {
            lastFuture = new CompletableFuture<>();
            try {
                ProcessBuilder pb = new ProcessBuilder(params);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                List<String> output = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                }
                int exit = process.waitFor();
                if (exit != 0) {
                    lastFuture.completeExceptionally(new RuntimeException("External tool failed: exit " + exit));
                } else {
                    lastFuture.complete(output);
                }
            } catch (Exception ex) {
                lastFuture.completeExceptionally(ex);
            }
            return lastFuture;
        }

        private void awaitLast() {
            if (lastFuture != null) {
                lastFuture.join();
            }
        }
    }
}
