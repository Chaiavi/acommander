package org.chaiware.acommander.helpers;

import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTagSupportTest {

    @TempDir
    Path dir;

    @Test
    void parsesTheGlobalSectionOfFfmetadataOutput() {
        String output = ";FFMETADATA1\ntitle=שלום \\= x\\;y\nARTIST=Me\nlyrics=line one\\\nline two\nencoder=Lavf63.1.102\n"
                + "[CHAPTER]\nTIMEBASE=1/1000\ntitle=Chapter 1\n";

        assertThat(MediaTagSupport.parseFfmetadata(output)).containsExactly(
                Map.entry("title", "שלום = x;y"),
                Map.entry("artist", "Me"),
                Map.entry("lyrics", "line one\nline two"),
                Map.entry("encoder", "Lavf63.1.102"));
    }

    @Test
    void rewritesIntoATempFileWithTheSameExtension() {
        Path song = Path.of("C:/music/song.mp3");
        Path temp = song.resolveSibling(MediaTagSupport.tempName("song.mp3"));

        assertThat(temp.getFileName().toString()).isEqualTo("song.acommander-tags.mp3");
        assertThat(MediaTagSupport.rewriteCommand(Path.of("ffmpeg.exe"), song, temp, List.of("-map", "0")))
                .containsExactly("ffmpeg.exe", "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-i", song.toString(),
                        "-map", "0", "-fflags", "+bitexact", "-id3v2_version", "3", temp.toString());
    }

    @Test
    void writesReadsAndRemovesUnicodeTagsWithTheBundledFfmpeg() throws Exception {
        File song = dir.resolve("שיר.mp3").toFile();
        ProcessRunner.Result made = ProcessRunner.of(BundledTool.FFMPEG.path().toString(), "-hide_banner", "-nostdin",
                "-loglevel", "error", "-f", "lavfi", "-i", "sine=duration=1", "-c:a", "libmp3lame", song.getPath()).run();
        assertThat(made.succeeded()).as(made.stderrText()).isTrue();
        FileTime modified = FileTime.fromMillis(1_600_000_000_000L);
        Files.setLastModifiedTime(song.toPath(), modified);

        MediaTagSupport.write(song, Map.of("title", "שלום", "date", "2020"), true);

        assertThat(MediaTagSupport.read(song)).containsEntry("title", "שלום").containsEntry("date", "2020");
        assertThat(Files.getLastModifiedTime(song.toPath())).isEqualTo(modified);
        assertThat(MediaTagSupport.remove(song)).isTrue();
        assertThat(MediaTagSupport.read(song)).doesNotContainKeys("title", "date");
        assertThat(dir.toFile().list()).containsExactly("שיר.mp3");
    }
}
