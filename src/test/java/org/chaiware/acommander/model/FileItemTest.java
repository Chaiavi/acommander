package org.chaiware.acommander.model;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileItemTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsBlankSizeForZeroLengthFile() throws IOException {
        Path file = Files.createTempFile(tempDir, "empty", ".txt");
        FileItem item = new FileItem(file);

        Assertions.assertThat(item.getHumanReadableSize()).isEqualTo("");
    }

    @Test
    void formatsBytesAndKilobytesForFiles() throws IOException {
        Path bytesFile = Files.createTempFile(tempDir, "bytes", ".bin");
        Files.write(bytesFile, new byte[512]);
        FileItem bytesItem = new FileItem(bytesFile);

        Path kbFile = Files.createTempFile(tempDir, "kb", ".bin");
        Files.write(kbFile, new byte[1536]);
        FileItem kbItem = new FileItem(kbFile);

        Assertions.assertThat(bytesItem.getHumanReadableSize()).isEqualTo("512 B");
        Assertions.assertThat(kbItem.getHumanReadableSize()).isEqualTo("1.5 KB");
    }

    @Test
    void directorySizeUsesProvidedSize() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem item = new FileItem(dir);
        item.setSize(2048);

        Assertions.assertThat(item.getHumanReadableSize()).isEqualTo("2 KB");
    }

    @Test
    void extensionIsLowerCaseWithoutTheDot() {
        Assertions.assertThat(FileItem.extension("Photo.JPG")).isEqualTo("jpg");
        Assertions.assertThat(FileItem.extension("a.tar.gz")).isEqualTo("gz");
        Assertions.assertThat(FileItem.extension("README")).isEmpty();
        Assertions.assertThat(FileItem.extension("trailing.")).isEmpty();
        Assertions.assertThat(FileItem.extension(".gitignore")).isEqualTo("gitignore");
    }

    @Test
    void allFilesWithExtensionSkipsFoldersAndTheParentEntry() {
        FileItem pdf = new FileItem(null, "a.PDF", 1, 0, false);
        FileItem folder = new FileItem(null, "b.pdf", 0, 0, true);
        FileItem parent = new FileItem(null, "..", 0, 0, false);

        Assertions.assertThat(FileItem.allFilesWithExtension(java.util.List.of(pdf), "pdf"::equals)).isTrue();
        Assertions.assertThat(FileItem.allFilesWithExtension(java.util.List.of(pdf, folder), "pdf"::equals)).isFalse();
        Assertions.assertThat(FileItem.allFilesWithExtension(java.util.List.of(parent), ext -> true)).isFalse();
        Assertions.assertThat(FileItem.allFilesWithExtension(java.util.List.of(), ext -> true)).isFalse();
    }

    @Test
    void humanSizeUsesADotInEveryLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            Assertions.assertThat(FileItem.humanSize(0)).isEqualTo("0 B");
            Assertions.assertThat(FileItem.humanSize(3L * 1024 * 1024 / 2)).isEqualTo("1.5 MB");
            Assertions.assertThat(FileItem.humanSize(5L * 1024 * 1024 * 1024)).isEqualTo("5 GB");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void parentFolderDateIsBlank() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem parent = new FileItem(dir, "..");

        Assertions.assertThat(parent.getDate()).isEqualTo("");
    }

    @Test
    void toStringUsesPresentableFilename() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem parent = new FileItem(dir, "..");

        Assertions.assertThat(parent.toString()).isEqualTo("..");
    }

    @Test
    void vanishedFileHasBlankDateAndNoSize() {
        FileItem gone = new FileItem(tempDir.resolve("gone.txt"));

        Assertions.assertThat(gone.getDate()).isEqualTo("");
        Assertions.assertThat(gone.getSizeInBytes()).isZero();
    }

    @Test
    void modifiedMillisPrefersTheListedTime() throws IOException {
        Path file = Files.createTempFile(tempDir, "f", ".txt");

        Assertions.assertThat(new FileItem(file, "f.txt", 1, 42, false).modifiedMillis()).isEqualTo(42);
        Assertions.assertThat(new FileItem(file).modifiedMillis()).isEqualTo(Files.getLastModifiedTime(file).toMillis());
    }

    @Test
    void metadataIsReadOnceWhenListedNotWhenShownOrSorted() throws IOException {
        Path empty = Files.createFile(tempDir.resolve("empty.txt"));
        Path file = Files.writeString(tempDir.resolve("file.txt"), "12345");
        FileItem emptyItem = new FileItem(empty);
        FileItem fileItem = new FileItem(file);
        long listedTime = fileItem.modifiedMillis();

        // Changing or deleting the files afterwards does not reach the listed items: rows, sort and footer stay put
        Files.writeString(empty, "now with content");
        Files.delete(file);

        Assertions.assertThat(emptyItem.getSizeInBytes()).isZero();
        Assertions.assertThat(fileItem.getSizeInBytes()).isEqualTo(5);
        Assertions.assertThat(fileItem.modifiedMillis()).isEqualTo(listedTime).isPositive();
        Assertions.assertThat(fileItem.isDirectory()).isFalse();
    }
}
