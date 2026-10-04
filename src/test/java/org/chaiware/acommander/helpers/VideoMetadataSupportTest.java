package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
