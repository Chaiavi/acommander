package org.chaiware.acommander.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class FolderComparerTest {

    private static final FolderComparer.Options SIZE_ONLY = new FolderComparer.Options(false, false, true, false);

    @TempDir
    Path left;
    @TempDir
    Path right;

    @Test
    void marksItemsThatExistOnOneSideOnly() throws IOException {
        Files.writeString(left.resolve("a.txt"), "x");
        Files.writeString(right.resolve("b.txt"), "x");

        FolderComparer.Result result = FolderComparer.compare(left, right, SIZE_ONLY);

        assertThat(result.onlyLeftCount()).isEqualTo(1);
        assertThat(result.onlyRightCount()).isEqualTo(1);
        assertThat(result.leftMarks()).containsEntry(FolderComparer.key(left.resolve("a.txt")), FolderComparer.Mark.LEFT_ONLY);
        assertThat(result.rightMarks()).containsEntry(FolderComparer.key(right.resolve("b.txt")), FolderComparer.Mark.RIGHT_ONLY);
    }

    @Test
    void aDifferenceDeepInsideMarksTheTopLevelFolder() throws IOException {
        Files.createDirectories(left.resolve("sub/deep"));
        Files.createDirectories(right.resolve("sub/deep"));
        Files.writeString(left.resolve("sub/deep/f.txt"), "short");
        Files.writeString(right.resolve("sub/deep/f.txt"), "much longer");

        FolderComparer.Result result = FolderComparer.compare(left, right, SIZE_ONLY);

        assertThat(result.differentCount()).isEqualTo(1);
        assertThat(result.leftMarks()).containsOnlyKeys(FolderComparer.key(left.resolve("sub")));
        assertThat(result.leftMarks()).containsValue(FolderComparer.Mark.DIFFERENT);
    }

    @Test
    void dateCountsOnlyWhenAsked() throws IOException {
        Files.writeString(left.resolve("f.txt"), "same");
        Files.writeString(right.resolve("f.txt"), "same");
        Files.setLastModifiedTime(left.resolve("f.txt"), FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(right.resolve("f.txt"), FileTime.fromMillis(2_000_000));

        assertThat(FolderComparer.compare(left, right, SIZE_ONLY).differentCount()).isZero();
        assertThat(FolderComparer.compare(left, right, new FolderComparer.Options(true, false, true, false))
                .differentCount()).isEqualTo(1);
    }

    @Test
    void checksumFindsSameSizeDifferentContent() throws IOException {
        Files.writeString(left.resolve("f.txt"), "abc");
        Files.writeString(right.resolve("f.txt"), "abd");

        assertThat(FolderComparer.compare(left, right, SIZE_ONLY).differentCount()).isZero();
        assertThat(FolderComparer.compare(left, right, new FolderComparer.Options(false, true, true, false))
                .differentCount()).isEqualTo(1);
    }

    @Test
    void nonRecursiveIgnoresSubfolderContents() throws IOException {
        Files.createDirectories(left.resolve("sub"));
        Files.createDirectories(right.resolve("sub"));
        Files.writeString(left.resolve("sub/only-left.txt"), "x");

        FolderComparer.Result result = FolderComparer.compare(left, right, new FolderComparer.Options(false, false, false, false));

        assertThat(result.onlyLeftCount()).isZero();
        assertThat(result.leftMarks()).isEmpty();
    }

    @Test
    void namesMatchIgnoringCaseUnlessAsked() throws IOException {
        Files.writeString(left.resolve("Readme.txt"), "x");
        Files.writeString(right.resolve("README.txt"), "x");

        assertThat(FolderComparer.compare(left, right, SIZE_ONLY).onlyLeftCount()).isZero();
        assertThat(FolderComparer.compare(left, right, new FolderComparer.Options(false, false, true, true))
                .onlyLeftCount()).isEqualTo(1);
    }
}
