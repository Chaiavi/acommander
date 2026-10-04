package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArchiveOperationsTest {

    @TempDir
    Path tempDir;

    private VFileSystem sourceFs;
    private ArchiveOperations operations;

    @BeforeEach
    void setUp() {
        FilesPanesHelper panes = mock(FilesPanesHelper.class);
        sourceFs = mock(FtpFileSystem.class);
        when(panes.getFocusedFileSystem()).thenReturn(sourceFs);
        when(panes.getUnfocusedFileSystem()).thenReturn(new LocalFileSystem(tempDir.toString()));
        AppConfig config = new AppConfig();
        config.setActions(List.of());
        operations = new ArchiveOperations(panes, new AppRegistry(config), new ExternalToolRunner(() -> {}));
    }

    @Test
    void packStagesRemoteItemsUnderTheirOwnNames() throws Exception {
        FileItem dir = new FileItem(null, "dir1", 0, 0, true);
        FileItem file = new FileItem(null, "file1.txt", 100, 0, false);
        when(sourceFs.getInternalPath(dir)).thenReturn("/dir1");
        when(sourceFs.getInternalPath(file)).thenReturn("/file1.txt");

        // No pack tool configured, so it stops after the downloads
        assertThatThrownBy(() -> operations.pack(List.of(dir, file), tempDir.resolve("test.zip").toString()))
                .hasMessageContaining("Missing action config: pack");

        ArgumentCaptor<String> dirTarget = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fileTarget = ArgumentCaptor.forClass(String.class);
        verify(sourceFs).copy(eq("/dir1"), any(LocalFileSystem.class), dirTarget.capture());
        verify(sourceFs).copy(eq("/file1.txt"), any(LocalFileSystem.class), fileTarget.capture());
        Path stagedDir = Path.of(dirTarget.getValue());
        Path stagedFile = Path.of(fileTarget.getValue());
        assertThat(stagedDir.getFileName()).hasToString("dir1");
        assertThat(stagedFile.getFileName()).hasToString("file1.txt");
        assertThat(stagedDir.getParent()).isEqualTo(stagedFile.getParent());
    }

    @Test
    void packRejectsANameThatLeavesTheStagingFolder() throws Exception {
        FileItem item = new FileItem(null, "../evil.txt", 0, 0, false);
        when(sourceFs.getInternalPath(item)).thenReturn("/../evil.txt");

        assertThatThrownBy(() -> operations.pack(List.of(item), tempDir.resolve("test.zip").toString()))
                .hasMessageContaining("Can't pack an item with this name");
        verify(sourceFs, never()).copy(anyString(), any(), anyString());
    }

    @Test
    void unpackRejectsAFileThatIsNotAnArchive() {
        assertThatThrownBy(() -> operations.unpack(new FileItem(null, "notes.txt", 1, 0, false), tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a supported archive");
    }
}
