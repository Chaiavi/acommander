package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void isTextFile_returnsTrueForEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);
        assertTrue(FileHelper.isTextFile(new FileItem(file.toFile())));
    }

    @Test
    void isTextFile_returnsTrueForSimpleText() throws IOException {
        Path file = tempDir.resolve("text.txt");
        Files.writeString(file, "Hello, World!");
        assertTrue(FileHelper.isTextFile(new FileItem(file.toFile())));
    }

    @Test
    void isTextFile_returnsTrueForUtf16LeBom() throws IOException {
        Path file = tempDir.resolve("utf16le.txt");
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            fos.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
            fos.write("Hello".getBytes());
        }
        assertTrue(FileHelper.isTextFile(new FileItem(file.toFile())));
    }

    @Test
    void isTextFile_returnsFalseForNullByte() throws IOException {
        Path file = tempDir.resolve("binary.dat");
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            fos.write(new byte[]{'T', 'e', 'x', 't', 0, 'B', 'i', 'n'});
        }
        assertFalse(FileHelper.isTextFile(new FileItem(file.toFile())));
    }

    @Test
    void isTextFile_returnsFalseForHighSuspiciousRatio() throws IOException {
        Path file = tempDir.resolve("suspicious.dat");
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            byte[] data = new byte[100];
            for (int i = 0; i < 100; i++) {
                data[i] = (byte) (i < 50 ? 1 : 65); // 50% control characters
            }
            fos.write(data);
        }
        assertFalse(FileHelper.isTextFile(new FileItem(file.toFile())));
    }

    @Test
    void isTextFile_returnsFalseForDirectory() {
        assertFalse(FileHelper.isTextFile(new FileItem(tempDir.toFile())));
    }
}
