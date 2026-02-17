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
        FileItem item = new FileItem(file.toFile());

        Assertions.assertThat(item.getHumanReadableSize()).isEqualTo("");
    }

    @Test
    void formatsBytesAndKilobytesForFiles() throws IOException {
        Path bytesFile = Files.createTempFile(tempDir, "bytes", ".bin");
        Files.write(bytesFile, new byte[512]);
        FileItem bytesItem = new FileItem(bytesFile.toFile());

        Path kbFile = Files.createTempFile(tempDir, "kb", ".bin");
        Files.write(kbFile, new byte[1536]);
        FileItem kbItem = new FileItem(kbFile.toFile());

        Assertions.assertThat(bytesItem.getHumanReadableSize()).isEqualTo("512 B");
        Assertions.assertThat(kbItem.getHumanReadableSize()).isEqualTo("1.5 KB");
    }

    @Test
    void directorySizeUsesProvidedSize() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem item = new FileItem(dir.toFile());
        item.setSize(2048);

        Assertions.assertThat(item.getHumanReadableSize()).isEqualTo("2 KB");
    }

    @Test
    void parentFolderDateIsBlank() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem parent = new FileItem(dir.toFile(), "..");

        Assertions.assertThat(parent.getDate()).isEqualTo("");
    }

    @Test
    void toStringUsesPresentableFilename() throws IOException {
        Path dir = Files.createTempDirectory(tempDir, "dir");
        FileItem parent = new FileItem(dir.toFile(), "..");

        Assertions.assertThat(parent.toString()).isEqualTo("..");
    }
}
