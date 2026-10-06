package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaneDragDropTest {

    @TempDir
    Path dir;

    @Test
    void filesOnDiskDropsTheParentEntry() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "x");

        List<File> files = PaneDragDrop.filesOnDisk(List.of(new FileItem(dir, ".."), new FileItem(file)));

        assertThat(files).containsExactly(file.toFile());
    }

    @Test
    void downloadCopiesEachItemUnderItsOwnName() throws IOException {
        VFileSystem ftp = mock(VFileSystem.class);
        ClipboardTransfer.State state = new ClipboardTransfer.State(
                List.of(new ClipboardTransfer.Entry("a.txt", false, "/pub/a.txt")),
                false, FilesPanesHelper.FocusSide.LEFT, ftp, "/pub");

        List<File> files = PaneDragDrop.download(state, dir);

        verify(ftp).copy(eq("/pub/a.txt"), any(LocalFileSystem.class), eq(dir.resolve("a.txt").toString()));
        assertThat(files).containsExactly(dir.resolve("a.txt").toFile());
    }

    @Test
    void downloadFailsNamingTheFilesThatDidNotArrive() throws IOException {
        VFileSystem ftp = mock(VFileSystem.class);
        doThrow(new IOException("gone")).when(ftp).copy(eq("/pub/b.txt"), any(), anyString());
        ClipboardTransfer.State state = new ClipboardTransfer.State(
                List.of(new ClipboardTransfer.Entry("a.txt", false, "/pub/a.txt"),
                        new ClipboardTransfer.Entry("b.txt", false, "/pub/b.txt")),
                false, FilesPanesHelper.FocusSide.LEFT, ftp, "/pub");

        assertThatThrownBy(() -> PaneDragDrop.download(state, dir)).isInstanceOf(IOException.class).hasMessageContaining("b.txt");
    }

    @Test
    void onlyArchivesRefuseMovingOut() {
        assertThat(PaneDragDrop.allowsMoveOut(new LocalFileSystem(dir.toString()))).isTrue();
        assertThat(PaneDragDrop.allowsMoveOut(mock(FtpFileSystem.class))).isTrue();
        assertThat(PaneDragDrop.allowsMoveOut(mock(ArchiveFileSystem.class))).isFalse();
    }

    @Test
    void droppedFilesBecomeALocalCopy() throws IOException {
        Path file = Files.writeString(dir.resolve("a.txt"), "x");
        Path folder = Files.createDirectory(dir.resolve("sub"));

        ClipboardTransfer.State state = PaneDragDrop.fromDroppedFiles(List.of(file.toFile(), folder.toFile()),
                FilesPanesHelper.FocusSide.RIGHT);

        assertThat(state.entries()).containsExactly(
                new ClipboardTransfer.Entry("a.txt", false, file.toString()),
                new ClipboardTransfer.Entry("sub", true, folder.toString()));
        assertThat(state.cut()).isFalse();
        assertThat(state.sourceFs()).isInstanceOf(LocalFileSystem.class);
        assertThat(state.sourceFolder()).isEqualTo(dir.toString());
    }

    @Test
    void folderOfUsesTheFullPathLocallyAndTheInternalPathOnFtp() {
        FileItem row = new FileItem(dir.resolve("sub"), "sub", -1, 0, true);
        assertThat(PaneDragDrop.folderOf(new LocalFileSystem(dir.toString()), row)).isEqualTo(dir.resolve("sub").toString());

        FtpFileSystem ftp = mock(FtpFileSystem.class);
        FileItem ftpRow = new FileItem(null, "sub", -1, 0, true);
        when(ftp.getInternalPath(ftpRow)).thenReturn("/pub/sub");
        assertThat(PaneDragDrop.folderOf(ftp, ftpRow)).isEqualTo("/pub/sub");
    }

    @Test
    void aFolderCannotBeDroppedIntoItself() {
        LocalFileSystem local = new LocalFileSystem(dir.toString());
        ClipboardTransfer.State state = new ClipboardTransfer.State(
                List.of(new ClipboardTransfer.Entry("Sub", true, dir.resolve("Sub").toString())),
                false, FilesPanesHelper.FocusSide.LEFT, local, dir.toString());
        FileItem sub = new FileItem(dir.resolve("sub"), "sub", -1, 0, true);
        FileItem other = new FileItem(dir.resolve("other"), "other", -1, 0, true);

        assertThat(PaneDragDrop.isDraggedFolder(state, local, dir.toString(), sub)).isTrue();
        assertThat(PaneDragDrop.isDraggedFolder(state, local, dir.toString(), other)).isFalse();
        assertThat(PaneDragDrop.isDraggedFolder(state, local, dir.resolve("elsewhere").toString(), sub)).isFalse();
    }
}
