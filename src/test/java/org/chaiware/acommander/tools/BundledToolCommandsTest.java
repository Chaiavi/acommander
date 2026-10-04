package org.chaiware.acommander.tools;

import org.chaiware.acommander.tools.BundledToolCommands.ChecksumOptions;
import org.chaiware.acommander.tools.BundledToolCommands.CompareFilesOptions;
import org.chaiware.acommander.tools.BundledToolCommands.SplitSize;
import org.chaiware.acommander.tools.BundledToolCommands.WhiteSpaceCompareMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BundledToolCommandsTest {

    @Test
    void checksumOfAFolderWithNames() {
        ChecksumOptions options = new ChecksumOptions("--sha256", "SHA256", false, false, true);
        assertThat(BundledToolCommands.checksum(Path.of("rhash.exe"), "C:\\data", options, true))
                .containsExactly("rhash.exe", "--sha256", "--recursive", "--hex", "C:\\data");
    }

    @Test
    void checksumOfAFileInBase32WithoutNames() {
        ChecksumOptions options = new ChecksumOptions("--md5", "MD5", true, true, false);
        assertThat(BundledToolCommands.checksum(Path.of("rhash.exe"), "a.txt", options, false))
                .containsExactly("rhash.exe", "--md5", "--base32", "--simple", "a.txt");
    }

    @Test
    void analyzeFileUsesTheMagicFileOnlyWhenItExists(@TempDir Path dir) throws IOException {
        Path magic = dir.resolve("magic.mgc");
        assertThat(BundledToolCommands.analyzeFile(Path.of("file.exe"), magic, "x.bin"))
                .containsExactly("file.exe", "-b", "-k", "-z", "x.bin");

        Files.writeString(magic, "m");
        assertThat(BundledToolCommands.analyzeFile(Path.of("file.exe"), magic, "x.bin"))
                .containsExactly("file.exe", "-b", "-k", "-z", "-m", magic.toString(), "x.bin");
    }

    @Test
    void compareFilesTurnsOnOnlyTheChosenWhitespaceFlag() {
        CompareFilesOptions options = new CompareFilesOptions(true, WhiteSpaceCompareMode.LEADING, false);
        assertThat(BundledToolCommands.compareFiles("ExamDiff.exe", "l.txt", "r.txt", options))
                .containsExactly("ExamDiff.exe", "l.txt", "r.txt", "/i", "/t", "/!d", "/!w", "/!b", "/l", "/!e", "/n");
    }

    @Test
    void compareFilesWithNoWhitespaceIgnoring() {
        CompareFilesOptions options = new CompareFilesOptions(false, WhiteSpaceCompareMode.NONE, true);
        assertThat(BundledToolCommands.compareFiles("ExamDiff.exe", "l", "r", options))
                .containsExactly("ExamDiff.exe", "l", "r", "/!i", "/t", "/d", "/!w", "/!b", "/!l", "/!e", "/n");
    }

    @Test
    void splitSizeUnits() {
        assertThat(BundledToolCommands.parseSplitSize("16m")).isEqualTo(new SplitSize(true, 16L << 20, "16m", ""));
        assertThat(BundledToolCommands.parseSplitSize(" 64KB ")).isEqualTo(new SplitSize(true, 64L << 10, "64k", ""));
        assertThat(BundledToolCommands.parseSplitSize("1g").bytes()).isEqualTo(1L << 30);
        assertThat(BundledToolCommands.parseSplitSize("512").sevenZipArg()).isEqualTo("512");
        assertThat(BundledToolCommands.parseSplitSize("512b").sevenZipArg()).isEqualTo("512b");
    }

    @Test
    void splitSizeRejectsBadInput() {
        assertThat(BundledToolCommands.parseSplitSize("").message()).isEqualTo("Please enter a split size.");
        assertThat(BundledToolCommands.parseSplitSize("-5m").valid()).isFalse();
        assertThat(BundledToolCommands.parseSplitSize("0").message()).isEqualTo("Split size must be greater than zero.");
        assertThat(BundledToolCommands.parseSplitSize("1.5g").valid()).isFalse();
        assertThat(BundledToolCommands.parseSplitSize("99999999999999g").message()).isEqualTo("Split size is too large.");
    }
}
