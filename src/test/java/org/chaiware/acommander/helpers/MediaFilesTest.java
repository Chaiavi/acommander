package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFilesTest {

    @TempDir
    Path dir;

    private FileItem file(String name) throws IOException {
        return new FileItem(Files.writeString(dir.resolve(name), "x"));
    }

    @Test
    void sortsFilesIntoAudioVideoAndMedia() throws IOException {
        assertThat(MediaFiles.areAllAudio(List.of(file("a.FLAC"), file("b.mp3")))).isTrue();
        assertThat(MediaFiles.areAllVideo(List.of(file("a.mkv")))).isTrue();
        assertThat(MediaFiles.areAllMedia(List.of(file("a.mkv"), file("b.mp3")))).isTrue();
        assertThat(MediaFiles.areAllMedia(List.of(file("a.mkv"), file("notes.txt")))).isFalse();
        assertThat(MediaFiles.areAllTaggableAudio(List.of(file("a.wav")))).isFalse();
    }

    @Test
    void joinTakesTwoOrMoreFilesOfOneType() throws IOException {
        assertThat(MediaFiles.joinProblem(List.of(file("a.mp4"), file("b.MP4")))).isNull();
        assertThat(MediaFiles.joinProblem(List.of(file("a.mp4")))).isNotNull();
        assertThat(MediaFiles.joinProblem(List.of(file("a.mp4"), file("b.mkv")))).contains("one type");
    }

    @Test
    void joinedNameKeepsTheExtensionOfItsParts() {
        assertThat(MediaFiles.withExtension(" all.mp4 ", "mp4")).isEqualTo("all.mp4");
        assertThat(MediaFiles.withExtension("all", "mp4")).isEqualTo("all.mp4");
    }
}
