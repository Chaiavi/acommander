package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClipboardTransferTest {

    @TempDir
    Path dir;

    @Test
    void duplicateNameSkipsTakenNames() {
        Set<String> taken = Set.of("a_copy.txt", "a_copy_2.txt");
        assertThat(ClipboardTransfer.duplicateName("a.txt", taken::contains)).isEqualTo("a_copy_3.txt");
        assertThat(ClipboardTransfer.duplicateName("README", name -> false)).isEqualTo("README_copy");
        assertThat(ClipboardTransfer.duplicateName(".gitignore", name -> false)).isEqualTo(".gitignore_copy");
    }

    @Test
    void localFoldersCompareIgnoringCaseAndTrailingSeparator() {
        LocalFileSystem local = new LocalFileSystem(dir.toString());
        assertThat(ClipboardTransfer.isSameFolder(local, local, dir.toString() + "\\", dir.toString().toUpperCase())).isTrue();
        assertThat(ClipboardTransfer.isSameFolder(local, local, dir.toString(), dir.resolve("sub").toString())).isFalse();
    }

    @Test
    void copyIntoItsOwnFolderPastesUnderTheDuplicateName() throws IOException {
        Files.writeString(dir.resolve("a.txt"), "x");
        LocalFileSystem local = new LocalFileSystem(dir.toString());
        ClipboardTransfer.State state = new ClipboardTransfer.State(
                List.of(new ClipboardTransfer.Entry("a.txt", false, dir.resolve("a.txt").toString())),
                false, FilesPanesHelper.FocusSide.LEFT, local, dir.toString());

        ClipboardTransfer.PasteResult result = ClipboardTransfer.paste(state, local, dir.toString(), name -> "a_copy.txt");

        assertThat(result.pasted()).extracting(ClipboardTransfer.Entry::name).containsExactly("a_copy.txt");
        assertThat(dir.resolve("a_copy.txt")).hasContent("x");
    }

    @Test
    void cutMovesEachItemAndListsTheOnesThatFailed() throws IOException {
        VFileSystem source = mock(VFileSystem.class);
        doThrow(new IOException("locked")).when(source).move(eq("/bad.txt"), any(), anyString());
        LocalFileSystem target = new LocalFileSystem(dir.toString());
        ClipboardTransfer.State state = new ClipboardTransfer.State(
                List.of(new ClipboardTransfer.Entry("good.txt", false, "/good.txt"),
                        new ClipboardTransfer.Entry("bad.txt", false, "/bad.txt")),
                true, FilesPanesHelper.FocusSide.RIGHT, source, "/");

        ClipboardTransfer.PasteResult result = ClipboardTransfer.paste(state, target, dir.toString(), name -> "unused");

        verify(source).move(eq("/good.txt"), eq(target), eq(dir.resolve("good.txt").toString()));
        verify(source, never()).copy(anyString(), any(), anyString());
        assertThat(result.pasted()).extracting(ClipboardTransfer.Entry::name).containsExactly("good.txt");
        assertThat(result.failed()).extracting(ClipboardTransfer.Entry::name).containsExactly("bad.txt");
    }
}
