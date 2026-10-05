package org.chaiware.acommander.vfs;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemTest {

    @TempDir
    Path tempDir;

    @Test
    void listsTheParentEntryThenTheFolderContents() throws IOException {
        Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("a.txt"), "x");

        var items = new LocalFileSystem(tempDir.toString()).listContents(tempDir.toString());

        assertThat(items).extracting(FileItem::getPresentableFilename).containsExactlyInAnyOrder("..", "sub", "a.txt");
        assertThat(items.get(0).getPresentableFilename()).isEqualTo("..");
        assertThat(items).filteredOn(item -> item.getPresentableFilename().equals("sub")).allMatch(FileItem::isDirectory);
    }

    @Test
    void missingFolderListsOnlyTheParentEntry() throws IOException {
        var items = new LocalFileSystem(tempDir.toString()).listContents(tempDir.resolve("gone").toString());

        assertThat(items).extracting(FileItem::getPresentableFilename).containsExactly("..");
    }

    @Test
    void unparsableFolderIsAnIoError() {
        assertThatThrownBy(() -> new LocalFileSystem("C:\\").listContents("C:\\bad*?name"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void deleteRefusesABlankOrRelativePath() {
        // "" is the app's own folder: an FTP item (no path) handed to a local pane must never delete it
        assertThatThrownBy(() -> new LocalFileSystem("").delete("")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new LocalFileSystem("").delete("config")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> new LocalFileSystem("").delete("C:\\")).isInstanceOf(IOException.class);
        assertThat(Path.of("config")).exists();
    }
}
