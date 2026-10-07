package org.chaiware.acommander.services;

import org.chaiware.acommander.services.MediaConversionService.AudioRequest;
import org.chaiware.acommander.services.MediaConversionService.Quality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaConversionServiceTest {

    private static final Path FFMPEG = Path.of("ffmpeg.exe");
    private static final List<String> START = List.of("ffmpeg.exe", "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-i");

    @TempDir
    Path dir;

    private final List<List<String>> ran = new ArrayList<>();

    /** Records each command and writes its output file (the last argument), like ffmpeg. */
    private CompletableFuture<List<String>> fakeRun(List<String> command) {
        ran.add(command);
        try {
            Files.writeString(Path.of(command.getLast()), "media");
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(List.of());
    }

    private static AudioRequest audio(String format, Quality quality, Integer sampleRate, boolean normalize, String policy) {
        return new AudioRequest(format, quality, sampleRate, normalize, "", policy);
    }

    private static List<String> command(String input, String... rest) {
        List<String> command = new ArrayList<>(START);
        command.add(input);
        command.addAll(List.of(rest));
        return command;
    }

    @Test
    void mp3UsesLameVbrAndId3v23() {
        assertThat(MediaConversionService.audioCommand(FFMPEG, Path.of("in.wav"), Path.of("out.mp3"),
                audio("mp3", Quality.HIGH, null, false, null)))
                .isEqualTo(command("in.wav", "-vn", "-map_metadata", "0", "-c:a", "libmp3lame", "-q:a", "0",
                        "-id3v2_version", "3", "out.mp3"));
    }

    @Test
    void sampleRateAndLoudnessAreAddedButOpusKeepsItsOwnRate() {
        assertThat(MediaConversionService.audioCommand(FFMPEG, Path.of("in.flac"), Path.of("out.m4a"),
                audio("m4a", Quality.SMALL, 44100, true, null)))
                .containsSequence("-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11");
        assertThat(MediaConversionService.audioCommand(FFMPEG, Path.of("in.flac"), Path.of("out.opus"),
                audio("opus", Quality.NORMAL, 44100, false, null)))
                .doesNotContain("-ar").containsSequence("-c:a", "libopus", "-b:a", "128k");
        assertThat(MediaConversionService.audioCommand(FFMPEG, Path.of("in.flac"), Path.of("out.flac"),
                audio("flac", Quality.NORMAL, null, true, null)))
                .containsSequence("-ar", "48000");
    }

    @Test
    void collisionPolicies() throws IOException {
        Path existing = Files.writeString(dir.resolve("a.wav"), "x");
        assertThat(MediaConversionService.resolveCollision(existing, "skip")).isNull();
        assertThat(MediaConversionService.resolveCollision(existing, "auto-rename")).isEqualTo(dir.resolve("a (1).wav"));
        assertThat(MediaConversionService.resolveCollision(existing, "overwrite")).isEqualTo(existing);
        assertThat(existing).doesNotExist();
    }

    @Test
    void neverOverwritesTheSourceItself() throws IOException {
        Path source = Files.writeString(dir.resolve("song.mp3"), "x");
        assertThat(MediaConversionService.target(source, dir, "", "mp3", "overwrite")).isEqualTo(dir.resolve("song (1).mp3"));
        assertThat(source).exists();
    }

    @Test
    void convertsEachFileIntoTheOutputFolder() throws Exception {
        Path first = Files.writeString(dir.resolve("שיר.wav"), "wav");
        Path second = Files.writeString(dir.resolve("b.flac"), "flac");
        Path out = Files.createDirectory(dir.resolve("out"));
        MediaConversionService service = new MediaConversionService(this::fakeRun, FFMPEG);

        Path converted = service.convertAudio(List.of(first, second), out, audio("ogg", Quality.NORMAL, null, false, null)).get();

        assertThat(converted).isEqualTo(out.resolve("שיר.ogg"));
        assertThat(out.resolve("b.ogg")).exists();
        assertThat(ran).hasSize(2);
    }

    @Test
    void aFailedRunDeletesItsPartialOutput() throws IOException {
        Path source = Files.writeString(dir.resolve("a.wav"), "wav");
        MediaConversionService service = new MediaConversionService(command -> {
            fakeRun(command);
            return CompletableFuture.failedFuture(new IOException("ffmpeg failed"));
        }, FFMPEG);
        Path out = Files.createDirectory(dir.resolve("out"));

        assertThatThrownBy(() -> service.convertAudio(List.of(source), out, audio("mp3", Quality.NORMAL, null, false, null)).get())
                .isInstanceOf(ExecutionException.class);
        assertThat(out.resolve("a.mp3")).doesNotExist();
    }
}
