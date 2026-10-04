package org.chaiware.acommander.helpers;

import org.chaiware.acommander.tools.BundledTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VideoMetadataSupportTest {

    @TempDir
    Path dir;

    @Test
    void deletesOnlyTheArtifactsTheRunLeft() throws IOException {
        File video = Files.writeString(dir.resolve("clip.mp4"), "v").toFile();
        Path older = Files.writeString(dir.resolve("clip-temp-111.mp4"), "user's");
        Files.writeString(dir.resolve("other-temp-1.mp4"), "unrelated");
        Set<String> before = VideoMetadataSupport.atomicParsleyArtifacts(video);

        Path left = Files.writeString(dir.resolve("clip-data-222.mp4"), "artifact");
        VideoMetadataSupport.deleteNewArtifacts(video, before);

        assertThat(left).doesNotExist();
        assertThat(older).exists();
        assertThat(dir.resolve("other-temp-1.mp4")).exists();
        assertThat(video).exists();
    }

    @Test
    void parsesTextDataIntoFieldsAndIgnoresUnknownAtoms() {
        String output = """
                \uFEFFAtom "\u00a9nam" contains: Holiday Clip
                Atom "\u00a9ART" contains: Jane Doe
                Atom "trkn" contains: 3 of 12
                Atom "gnre" contains: Rock
                Atom "----" [com.apple.iTunes;iTunNORM] contains: 0000
                Atom "\u00a9too" contains: Lavf58.29.100
                """;

        assertThat(VideoMetadataSupport.parseTextData(output)).containsExactly(
                Map.entry("title", "Holiday Clip"), Map.entry("artist", "Jane Doe"),
                Map.entry("tracknum", "3 of 12"), Map.entry("genre", "Rock"));
    }

    @Test
    void writeCommandEndsWithOverwriteAfterTheChanges() {
        File video = new File("C:\\videos\\clip.mp4");
        String atomicParsley = BundledTool.ATOMIC_PARSLEY.path().toString();

        assertThat(VideoMetadataSupport.writeCommand(video, List.of("--title", "Trip"), true))
                .containsExactly(atomicParsley, video.getAbsolutePath(), "--title", "Trip", "--preserveTime", "--overWrite");
        assertThat(VideoMetadataSupport.writeCommand(video, List.of("--year", "2020"), false))
                .containsExactly(atomicParsley, video.getAbsolutePath(), "--year", "2020", "--overWrite");
    }
}
