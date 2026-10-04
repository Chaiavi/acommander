package org.chaiware.acommander.services;

import org.chaiware.acommander.services.AudioConversionService.AudioCompressionProfile;
import org.chaiware.acommander.services.AudioConversionService.AudioConversionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class AudioConversionServiceTest {

    private static final Path SNDFILE = Path.of("sndfile-convert.exe");
    private static final Path FAAD = Path.of("faad.exe");
    private static final Path FAAC = Path.of("faac.exe");

    @TempDir
    Path dir;

    private final List<List<String>> ran = new ArrayList<>();

    /** Records each command and writes its output file, like the real tools. */
    private CompletableFuture<List<String>> fakeRun(List<String> command) {
        ran.add(command);
        int o = command.indexOf("-o");
        Path output = Path.of(o >= 0 ? command.get(o + 1) : command.getLast());
        try {
            Files.writeString(output, "audio");
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.completedFuture(List.of());
    }

    private static AudioConversionRequest request(String format, String flag, Integer sampleRate, String endian, String policy) {
        return new AudioConversionRequest(format, AudioCompressionProfile.LOSSLESS, flag, false, sampleRate, endian, "", policy);
    }

    @Test
    void opusDefaultsTo48kHzAndTheOpusCodec() {
        assertThat(AudioConversionService.convertCommand(SNDFILE, "in.wav", Path.of("out.opus"), request("opus", "", null, null, null)))
                .containsExactly("sndfile-convert.exe", "-override-sample-rate=48000", "-opus", "in.wav", "out.opus");
    }

    @Test
    void opusSampleRateSnapsToTheNearestAllowedOne() {
        assertThat(AudioConversionService.convertCommand(SNDFILE, "in.wav", Path.of("out.ogg"), request("ogg", "-opus", 22050, null, null)))
                .contains("-override-sample-rate=24000");
    }

    @Test
    void endianIsDroppedForFlacButKeptForWav() {
        assertThat(AudioConversionService.convertCommand(SNDFILE, "in.wav", Path.of("out.flac"), request("flac", "", null, "little", null)))
                .containsExactly("sndfile-convert.exe", "-pcm16", "in.wav", "out.flac");
        assertThat(AudioConversionService.convertCommand(SNDFILE, "in.flac", Path.of("out.wav"), request("wav", "-pcm24", null, "big", null)))
                .containsExactly("sndfile-convert.exe", "-endian=big", "-pcm24", "in.flac", "out.wav");
    }

    @Test
    void faacWritesRawStreamOnlyForAac() {
        assertThat(AudioConversionService.faacCommand(FAAC, Path.of("in.wav"), Path.of("out.aac")))
                .containsExactly("faac.exe", "-o", "out.aac", "--overwrite", "-r", "in.wav");
        assertThat(AudioConversionService.faacCommand(FAAC, Path.of("in.wav"), Path.of("out.m4a"))).doesNotContain("-r");
    }

    @Test
    void requiredToolsAddFaadForAacSourcesAndFaacForAacTargets() {
        AudioConversionService service = new AudioConversionService(this::fakeRun, SNDFILE, FAAD, FAAC);
        assertThat(service.requiredTools(List.of(Path.of("a.m4a")), "mp3")).containsExactly(SNDFILE, FAAD);
        assertThat(service.requiredTools(List.of(Path.of("a.wav")), "aac")).containsExactly(SNDFILE, FAAC);
        assertThat(service.requiredTools(List.of(Path.of("a.wav")), "flac")).containsExactly(SNDFILE);
    }

    @Test
    void collisionPolicies() throws IOException {
        Path existing = Files.writeString(dir.resolve("a.wav"), "x");
        assertThat(AudioConversionService.resolveCollision(existing, "skip")).isNull();
        assertThat(AudioConversionService.resolveCollision(existing, "auto-rename")).isEqualTo(dir.resolve("a (1).wav"));
        assertThat(AudioConversionService.resolveCollision(existing, "overwrite")).isEqualTo(existing);
        assertThat(existing).doesNotExist();
    }

    @Test
    void wavToM4aEncodesWithFaacInStagingAndMovesTheResult() throws Exception {
        Path source = Files.writeString(dir.resolve("song.wav"), "wav");
        Path out = Files.createDirectory(dir.resolve("out"));
        AudioConversionService service = new AudioConversionService(this::fakeRun, SNDFILE, FAAD, FAAC);

        Path first = service.convertAll(List.of(source), out, request("m4a", "", null, null, null)).get();

        assertThat(first).isEqualTo(out.resolve("song.m4a")).exists();
        assertThat(ran).hasSize(1);
        assertThat(ran.getFirst().getFirst()).isEqualTo("faac.exe");
    }

    @Test
    void nonAsciiNamesAreStagedUnderAsciiPaths() throws Exception {
        Path source = Files.writeString(dir.resolve("שיר.wav"), "wav");
        Path out = Files.createDirectory(dir.resolve("out"));
        AudioConversionService service = new AudioConversionService(this::fakeRun, SNDFILE, FAAD, FAAC);

        Path first = service.convertAll(List.of(source), out, request("flac", "", null, null, null)).get();

        assertThat(first).isEqualTo(out.resolve("שיר.flac")).exists();
        assertThat(String.join(" ", ran.getFirst())).doesNotContain("שיר");
    }
}
