package org.chaiware.acommander.commands;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
