package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PackVfsTest {

    @TempDir
    Path tempDir;

    private FilesPanesHelper filesPanesHelper;
    private CommandsAdvancedImpl commands;
    private VFileSystem sourceFs;
    private VFileSystem targetFs;

    @BeforeEach
    void setUp() throws IOException {
        filesPanesHelper = mock(FilesPanesHelper.class);
        commands = new CommandsAdvancedImpl(filesPanesHelper, null);
        sourceFs = mock(FtpFileSystem.class);
        targetFs = new LocalFileSystem(tempDir.toString());

        when(filesPanesHelper.getFocusedFileSystem()).thenReturn(sourceFs);
        when(filesPanesHelper.getUnfocusedFileSystem()).thenReturn(targetFs);
        when(sourceFs.getDisplayName()).thenReturn("ftp://host/");
    }

    @Test
    void doPack_skipsRemoteDirectories() throws Exception {
        // Arrange
        FileItem item1 = new FileItem(null, "dir1", 0, 0, true);
        FileItem item2 = new FileItem(null, "file1.txt", 100, 0, false);
        List<FileItem> items = List.of(item1, item2);

        when(sourceFs.getInternalPath(item2)).thenReturn("/file1.txt");

        Path archivePath = tempDir.resolve("test.zip");

        // Act
        commands.pack(items, archivePath.toString());

        // Assert
        // Should only copy the file, not the directory
        verify(sourceFs, times(1)).copy(eq("/file1.txt"), any(LocalFileSystem.class), anyString());
        verify(sourceFs, never()).copy(eq("/dir1"), any(), anyString());
    }
}
