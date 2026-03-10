package org.chaiware.acommander;

import org.assertj.core.api.Assertions;
import org.chaiware.acommander.commands.CommandsSimpleImpl;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommanderCopyTest {

    @TempDir
    Path tempDir;

    @Test
    void copyFileDuplicatesWhenPanesPointToSameFolder() throws Exception {
        Path sourceFile = tempDir.resolve("sample.txt");
        Files.writeString(sourceFile, "data");

        FilesPanesHelper panesHelper = mock(FilesPanesHelper.class);
        VFileSystem localFs = new LocalFileSystem("");
        when(panesHelper.getSelectedItems()).thenReturn(List.of(new FileItem(sourceFile.toFile())));
        when(panesHelper.getFocusedPath()).thenReturn(tempDir.toString());
        when(panesHelper.getUnfocusedPath()).thenReturn(tempDir.toString());
        when(panesHelper.getFocusedFileSystem()).thenReturn(localFs);
        when(panesHelper.getUnfocusedFileSystem()).thenReturn(localFs);

        Commander commander = new Commander();
        commander.filesPanesHelper = panesHelper;
        commander.commands = new CommandsSimpleImpl(panesHelper);

        commander.copyFile();

        Path duplicate = tempDir.resolve("sample_copy.txt");
        Assertions.assertThat(sourceFile).exists();
        Assertions.assertThat(duplicate).exists();
        Assertions.assertThat(Files.readString(duplicate)).isEqualTo("data");
    }
}
