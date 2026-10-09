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
    void findInFilesAddsFlagsAndAnExtensionGlob() {
        var options = new BundledToolCommands.FindInFilesOptions("needle", true, true, "..java", true);
        assertThat(BundledToolCommands.findInFiles(Path.of("rg.exe"), "C:\\src", options)).containsExactly(
                "rg.exe", "--files-with-matches", "--no-messages", "--fixed-strings", "--ignore-case",
                "--hidden", "--no-ignore", "--glob", "*.java", "needle", "C:\\src");
    }

    @Test
    void foundFilesResolvesRelativeLinesAndDropsDuplicates(@TempDir Path dir) {
        String absolute = dir.resolve("b.txt").toString();
        assertThat(BundledToolCommands.foundFiles(java.util.List.of(" a.txt ", "", absolute, "sub/../a.txt"), dir.toString()))
                .containsExactly(dir.resolve("a.txt").toString(), absolute);
    }

    @Test
    void checksumOptionsDeriveTheRhashFlagFromTheLabel() {
        assertThat(ChecksumOptions.of("CRC32", false, true, false))
                .isEqualTo(new ChecksumOptions("--crc32", "CRC32", false, true, false));
    }

    @Test
    void checksumDigestSkipsRhashErrorsAndShortTokens() {
        assertThat(BundledToolCommands.checksumDigest(java.util.List.of("rhash: warning", "ok", "  d41d8cd98f00b204  a.txt")))
                .isEqualTo("d41d8cd98f00b204");
        assertThat(BundledToolCommands.checksumDigest(java.util.List.of("ok"))).isEqualTo("ok");
        assertThat(BundledToolCommands.checksumDigest(null)).isEmpty();
    }

    @Test
    void checksumOutputPathNamesFilesAndFolderSums() {
        Path dir = Path.of("out");
        assertThat(BundledToolCommands.checksumOutputPath(dir, "a.txt", "SHA256", false)).isEqualTo(dir.resolve("a.txt.sha256"));
        assertThat(BundledToolCommands.checksumOutputPath(dir, "photos", "md5", true)).isEqualTo(dir.resolve("photos.MD5SUMS"));
    }

    @Test
    void analyzeFileUsesTheMagicFileOnlyWhenItExists(@TempDir Path dir) throws IOException {
        Path magic = dir.resolve("magic.mgc");
        assertThat(BundledToolCommands.analyzeFile(Path.of("file.exe"), magic, "x.bin"))
                .containsExactly("file.exe", "-b", "-z", "x.bin");

        Files.writeString(magic, "m");
        assertThat(BundledToolCommands.analyzeFile(Path.of("file.exe"), magic, "x.bin"))
                .containsExactly("file.exe", "-b", "-z", "-m", magic.toString(), "x.bin");
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
