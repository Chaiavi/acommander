package org.chaiware.acommander.services;

import org.chaiware.acommander.services.MediaConversionService.AudioRequest;
import org.chaiware.acommander.services.MediaConversionService.Quality;
import org.chaiware.acommander.services.MediaConversionService.TrimRequest;
import org.chaiware.acommander.services.MediaConversionService.VideoFormat;
import org.chaiware.acommander.services.MediaConversionService.VideoRequest;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    @Test
    void h264ScalesDownOnlyAndUsesAPlayablePixelFormat() {
        VideoRequest request = new VideoRequest(VideoFormat.MP4_H264, Quality.HIGH, 720, "", null);
        assertThat(MediaConversionService.videoCommand(FFMPEG, Path.of("in.mkv"), Path.of("out.mp4"), request))
                .containsSequence("-map", "0:V:0", "-map", "0:a?", "-vf", "scale=-2:'min(720,ih)'",
                        "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p")
                .endsWith("out.mp4");
    }

    @Test
    void extractingMp3ReusesTheAudioCommand() {
        VideoRequest request = new VideoRequest(VideoFormat.MP3, Quality.NORMAL, 720, "", null);
        assertThat(MediaConversionService.videoCommand(FFMPEG, Path.of("in.mp4"), Path.of("out.mp3"), request))
                .isEqualTo(MediaConversionService.audioCommand(FFMPEG, Path.of("in.mp4"), Path.of("out.mp3"),
                        audio("mp3", Quality.NORMAL, null, false, null)));
    }

    @Test
    void trimSeeksOnTheInputAndCopiesUnlessExact() {
        assertThat(MediaConversionService.trimCommand(FFMPEG, Path.of("in.mp3"), Path.of("out.mp3"), new TrimRequest(90_500, null, false)))
                .isEqualTo(List.of("ffmpeg.exe", "-hide_banner", "-nostdin", "-loglevel", "error", "-y", "-ss", "90.500",
                        "-i", "in.mp3", "-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero", "-map_metadata", "0", "out.mp3"));
        assertThat(MediaConversionService.trimCommand(FFMPEG, Path.of("in.mkv"), Path.of("out.mp4"), new TrimRequest(0, 5_000L, true)))
                .containsSequence("-ss", "0.000", "-to", "5.000", "-i", "in.mkv").contains("libx264");
    }

    @Test
    void parsesTimes() {
        assertThat(MediaConversionService.parseTime("90")).isEqualTo(90_000);
        assertThat(MediaConversionService.parseTime("1:30")).isEqualTo(90_000);
        assertThat(MediaConversionService.parseTime(" 1:02:03.5 ")).isEqualTo(3_723_500);
        assertThat(MediaConversionService.parseTime("1:75")).isNull();
        assertThat(MediaConversionService.parseTime("1:2:3:4")).isNull();
        assertThat(MediaConversionService.parseTime("abc")).isNull();
    }

    @Test
    void concatListQuotesEveryPathAndEscapesQuotes() {
        assertThat(MediaConversionService.concatList(List.of(Path.of("C:\\v\\it's.mp4"), Path.of("C:\\v\\b.mp4"))))
                .isEqualTo("file 'C:/v/it'\\''s.mp4'\nfile 'C:/v/b.mp4'\n");
    }

    @Test
    void mediaInfoKeepsTheInputReportAndOriginalAudioPicksItsContainer() {
        List<String> probe = List.of(
                "Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'w.mp4':",
                "  Metadata:",
                "    title           : Vid",
                "  Duration: 00:00:03.00, start: 0.000000, bitrate: 123 kb/s",
                "  Stream #0:0[0x1](und): Video: h264 (High) (avc1 / 0x31637661), yuv420p(progressive), 320x240, 25 fps",
                "  Stream #0:1[0x2](und): Audio: aac (LC) (mp4a / 0x6134706D), 44100 Hz, mono, fltp, 69 kb/s (default)",
                "Stream mapping:",
                "  Stream #0:0 -> #0:0 (h264 (native) -> wrapped_avframe (native))",
                "Output #0, null, to 'pipe:':");

        assertThat(MediaConversionService.mediaInfo(probe)).hasSize(5).first().isEqualTo("  Metadata:");
        assertThat(MediaConversionService.originalAudioExtension(probe)).isEqualTo("m4a");
        assertThat(MediaConversionService.originalAudioExtension(List.of("Input #0, wav", "  Stream #0:0: Audio: pcm_s16le, 44100 Hz")))
                .isEqualTo("wav");
        assertThatThrownBy(() -> MediaConversionService.originalAudioExtension(probe.subList(0, 5)))
                .hasMessage("The video has no audio track.");
    }

    /** Runs the bundled ffmpeg for real: catches argument mistakes the fakes can't. */
    private static CompletableFuture<List<String>> realRun(List<String> command) {
        try {
            ProcessRunner.Result result = ProcessRunner.of(command).mergeStderr().run();
            return result.succeeded() ? CompletableFuture.completedFuture(result.stdout())
                    : CompletableFuture.failedFuture(new IOException(result.stdoutText()));
        } catch (IOException | InterruptedException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Test
    void convertsTrimsJoinsAndExtractsWithTheBundledFfmpeg() throws Exception {
        Path clip = dir.resolve("it's קליפ.mkv");
        assertThat(realRun(List.of(BundledTool.FFMPEG.path().toString(), "-hide_banner", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=25", "-f", "lavfi", "-i", "sine=duration=2",
                "-c:v", "libx264", "-c:a", "aac", "-shortest", clip.toString()))).succeedsWithin(Duration.ofSeconds(30));
        Path out = Files.createDirectory(dir.resolve("out"));
        MediaConversionService service = new MediaConversionService(MediaConversionServiceTest::realRun, BundledTool.FFMPEG.path());

        Path mp4 = service.convertVideo(List.of(clip), out, new VideoRequest(VideoFormat.MP4_H264, Quality.SMALL, 120, "", null)).get();
        Path audio = service.convertVideo(List.of(clip), out, new VideoRequest(VideoFormat.ORIGINAL_AUDIO, null, null, "", null)).get();
        Path trimmed = service.trim(mp4, out, new TrimRequest(500, 1_500L, true)).get();
        Path joined = service.join(List.of(mp4, mp4), out, "joined.mp4").get();

        assertThat(String.join("\n", service.probe(mp4).get())).contains("x120").contains("yuv420p");
        assertThat(audio).isEqualTo(out.resolve("it's קליפ.m4a")).exists();
        assertThat(String.join("\n", service.probe(trimmed).get())).contains("Duration: 00:00:01.0");
        assertThat(String.join("\n", service.probe(joined).get())).contains("Duration: 00:00:04.0");
    }
}
