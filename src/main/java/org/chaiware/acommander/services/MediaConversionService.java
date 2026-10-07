package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Audio and video work with ffmpeg: convert, extract audio, trim, join, media info. Writes into another folder. */
public final class MediaConversionService {
    public static final List<String> AUDIO_FORMATS = List.of("mp3", "m4a", "flac", "wav", "ogg", "opus");
    private static final String LOUDNORM = "loudnorm=I=-16:TP=-1.5:LRA=11";
    private static final Pattern AUDIO_STREAM = Pattern.compile("^\\s*Stream #\\d+:\\d+.*?: Audio: (\\w+)");
    private static final Map<String, String> EXTENSION_BY_AUDIO_CODEC = Map.of(
            "aac", "m4a", "alac", "m4a", "mp3", "mp3", "opus", "opus", "vorbis", "ogg", "flac", "flac",
            "ac3", "ac3", "eac3", "eac3", "mp2", "mp2");
    public enum Quality { HIGH, NORMAL, SMALL }

    /** {@code sampleRate} null keeps the source rate, or 48 kHz when normalizing (loudnorm outputs 192 kHz). */
    public record AudioRequest(String format, Quality quality, Integer sampleRate, boolean normalize, String suffix,
                               String conflictPolicy) {}

    public enum VideoFormat {
        MP4_H264("mp4"), MP4_H265("mp4"), MP4_COPY("mp4"), MKV_COPY("mkv"), MP3("mp3"), M4A("m4a"), ORIGINAL_AUDIO(null);

        /** Null: decided per file from its audio codec. */
        public final String extension;

        VideoFormat(String extension) {
            this.extension = extension;
        }

        public boolean reencodesVideo() {
            return this == MP4_H264 || this == MP4_H265;
        }

        public boolean usesQuality() {
            return reencodesVideo() || this == MP3 || this == M4A;
        }
    }

    /** {@code maxHeight} null keeps the size; a smaller video is never enlarged. */
    public record VideoRequest(VideoFormat format, Quality quality, Integer maxHeight, String suffix, String conflictPolicy) {}

    /** {@code endMillis} null runs to the end; {@code exact} re-encodes to H.264 MP4 to cut on the exact frame. */
    public record TrimRequest(long startMillis, Long endMillis, boolean exact) {}

    private final Function<List<String>, CompletableFuture<List<String>>> runner;
    private final Path ffmpeg;

    /** {@code runner} runs one tool command and completes with its output (the app's progress bar + Stop). */
    public MediaConversionService(Function<List<String>, CompletableFuture<List<String>>> runner, Path ffmpeg) {
        this.runner = runner;
        this.ffmpeg = ffmpeg;
    }

    /** Completes with the first file written, or null when every one was skipped. */
    public CompletableFuture<Path> convertAudio(List<Path> sources, Path outputFolder, AudioRequest request) {
        return convertAll(sources, outputFolder, request.suffix(), request.conflictPolicy(),
                source -> CompletableFuture.completedFuture(request.format()),
                (input, output) -> audioCommand(ffmpeg, input, output, request));
    }

    /** Completes with the first file written, or null when every one was skipped. */
    public CompletableFuture<Path> convertVideo(List<Path> sources, Path outputFolder, VideoRequest request) {
        return convertAll(sources, outputFolder, request.suffix(), request.conflictPolicy(),
                source -> request.format().extension != null
                        ? CompletableFuture.completedFuture(request.format().extension)
                        : probe(source).thenApply(MediaConversionService::originalAudioExtension),
                (input, output) -> videoCommand(ffmpeg, input, output, request));
    }

    /** Writes {@code <name>_trimmed} into {@code outputFolder}; completes with it. */
    public CompletableFuture<Path> trim(Path source, Path outputFolder, TrimRequest request) {
        String extension = request.exact() ? "mp4" : extension(source);
        return convertAll(List.of(source), outputFolder, "_trimmed", "auto-rename",
                ignored -> CompletableFuture.completedFuture(extension),
                (input, output) -> trimCommand(ffmpeg, input, output, request));
    }

    /** Joins {@code sources} in order, without re-encoding, into {@code outputFolder/name}; completes with the file. */
    public CompletableFuture<Path> join(List<Path> sources, Path outputFolder, String name) {
        Path list;
        Path output;
        try {
            output = resolveCollision(outputFolder.resolve(name), "auto-rename");
            list = AppTempDir.createTempFile("acommander-join-", ".txt");
            Files.writeString(list, concatList(sources));
        } catch (IOException | RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return runner.apply(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-loglevel", "error", "-y",
                        "-f", "concat", "-safe", "0", "-i", list.toString(), "-map", "0", "-c", "copy", output.toString()))
                .whenComplete((lines, error) -> {
                    FileHelper.deleteQuietly(list);
                    if (error != null) {
                        FileHelper.deleteQuietly(output);
                    }
                })
                .thenApply(lines -> output);
    }

    /** ffmpeg's report on {@code source}: format, duration, bitrate, streams, tags (see {@link #mediaInfo}). */
    public CompletableFuture<List<String>> probe(Path source) {
        return runner.apply(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-i", source.toString(),
                "-t", "0", "-f", "null", "-"));
    }

    /** The input part of a {@link #probe} report, without its first line (the file name). */
    public static List<String> mediaInfo(List<String> probeOutput) {
        List<String> info = new ArrayList<>();
        boolean inInput = false;
        for (String line : probeOutput) {
            if (line.startsWith("Input #0")) {
                inInput = true;
            } else if (inInput && line.startsWith(" ")) {
                info.add(line);
            } else if (inInput && !line.startsWith("[")) {
                break;
            }
        }
        return info;
    }

    /** The extension that holds the first audio stream of a {@link #probe} report as it is. */
    static String originalAudioExtension(List<String> probeOutput) {
        for (String line : mediaInfo(probeOutput)) {
            Matcher matcher = AUDIO_STREAM.matcher(line);
            if (matcher.find()) {
                String codec = matcher.group(1);
                return codec.startsWith("pcm_") ? "wav" : EXTENSION_BY_AUDIO_CODEC.getOrDefault(codec, "mka");
            }
        }
        throw new IllegalStateException("The video has no audio track.");
    }

    /** "90", "1:30", "1:02:03" or "1:02:03.5" in milliseconds; null when it is none of them. */
    public static Long parseTime(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.matches("\\d+(:\\d{1,2}){0,2}(\\.\\d{1,3})?")) {
            return null;
        }
        String[] secondsAndFraction = trimmed.split("\\.");
        long seconds = 0;
        String[] parts = secondsAndFraction[0].split(":");
        for (int i = 0; i < parts.length; i++) {
            long value = Long.parseLong(parts[i]);
            if (i > 0 && value >= 60) {
                return null;
            }
            seconds = seconds * 60 + value;
        }
        long millis = secondsAndFraction.length > 1 ? Long.parseLong((secondsAndFraction[1] + "00").substring(0, 3)) : 0;
        return seconds * 1000 + millis;
    }

    /** One file at a time; a failed or stopped run deletes its half-written output. */
    private CompletableFuture<Path> convertAll(List<Path> sources, Path outputFolder, String suffix, String conflictPolicy,
                                               Function<Path, CompletableFuture<String>> extensionFor,
                                               BiFunction<Path, Path, List<String>> commandFor) {
        Path[] firstConverted = {null};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Path source : sources) {
            chain = chain.thenCompose(ignored -> extensionFor.apply(source)).thenCompose(extension -> {
                Path output;
                try {
                    output = target(source, outputFolder, suffix, extension, conflictPolicy);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
                if (output == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return runner.apply(commandFor.apply(source, output))
                        .whenComplete((lines, error) -> {
                            if (error != null) {
                                FileHelper.deleteQuietly(output);
                            }
                        })
                        .thenAccept(lines -> {
                            if (firstConverted[0] == null) {
                                firstConverted[0] = output;
                            }
                        });
            });
        }
        return chain.thenApply(ignored -> firstConverted[0]);
    }

    static List<String> audioCommand(Path ffmpeg, Path input, Path output, AudioRequest request) {
        String format = request.format().toLowerCase(Locale.ROOT);
        List<String> command = start(ffmpeg, input);
        command.addAll(List.of("-vn", "-map_metadata", "0"));
        command.addAll(audioCodec(format, request.quality()));
        // libopus takes only 8/12/16/24/48 kHz, and ffmpeg picks 48 kHz by itself
        Integer sampleRate = request.sampleRate() == null && request.normalize() ? Integer.valueOf(48000) : request.sampleRate();
        if (sampleRate != null && !"opus".equals(format)) {
            command.addAll(List.of("-ar", String.valueOf(sampleRate)));
        }
        if (request.normalize()) {
            command.addAll(List.of("-af", LOUDNORM));
        }
        if ("mp3".equals(format)) {
            command.addAll(List.of("-id3v2_version", "3"));
        }
        command.add(output.toString());
        return command;
    }

    private static List<String> audioCodec(String format, Quality quality) {
        int level = quality == null ? Quality.NORMAL.ordinal() : quality.ordinal();
        return switch (format) {
            case "mp3" -> List.of("-c:a", "libmp3lame", "-q:a", List.of("0", "2", "5").get(level));
            case "m4a" -> List.of("-c:a", "aac", "-b:a", List.of("256k", "192k", "128k").get(level));
            case "ogg" -> List.of("-c:a", "libvorbis", "-q:a", List.of("8", "6", "4").get(level));
            case "opus" -> List.of("-c:a", "libopus", "-b:a", List.of("160k", "128k", "96k").get(level));
            case "flac" -> List.of("-c:a", "flac");
            case "wav" -> List.of("-c:a", "pcm_s16le");
            default -> throw new IllegalArgumentException("Unsupported audio format: " + format);
        };
    }

    public static boolean isLossless(String format) {
        return "flac".equals(format) || "wav".equals(format);
    }

    static List<String> videoCommand(Path ffmpeg, Path input, Path output, VideoRequest request) {
        Quality quality = request.quality() == null ? Quality.NORMAL : request.quality();
        if (request.format() == VideoFormat.MP3 || request.format() == VideoFormat.M4A) {
            return audioCommand(ffmpeg, input, output, new AudioRequest(request.format().extension, quality, null, false, "", null));
        }
        List<String> command = start(ffmpeg, input);
        switch (request.format()) {
            case ORIGINAL_AUDIO -> command.addAll(List.of("-vn", "-map", "0:a:0", "-c:a", "copy"));
            case MP4_COPY -> command.addAll(List.of("-map", "0:V", "-map", "0:a?", "-c", "copy", "-movflags", "+faststart"));
            case MKV_COPY -> command.addAll(List.of("-map", "0", "-c", "copy"));
            default -> {
                // V = video streams that are not cover art
                command.addAll(List.of("-map", "0:V:0", "-map", "0:a?"));
                if (request.maxHeight() != null) {
                    command.addAll(List.of("-vf", "scale=-2:'min(" + request.maxHeight() + ",ih)'"));
                }
                int level = quality.ordinal();
                if (request.format() == VideoFormat.MP4_H265) {
                    command.addAll(List.of("-c:v", "libx265", "-preset", "medium", "-crf", List.of("22", "28", "32").get(level),
                            "-tag:v", "hvc1", "-x265-params", "log-level=error"));
                } else {
                    command.addAll(List.of("-c:v", "libx264", "-preset", "medium", "-crf", List.of("18", "23", "28").get(level)));
                }
                // yuv420p: the pixel format every player decodes
                command.addAll(List.of("-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart"));
            }
        }
        command.addAll(List.of("-map_metadata", "0"));
        command.add(output.toString());
        return command;
    }

    static List<String> trimCommand(Path ffmpeg, Path input, Path output, TrimRequest request) {
        List<String> command = new ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-loglevel", "error",
                "-y", "-ss", seconds(request.startMillis())));
        if (request.endMillis() != null) {
            command.addAll(List.of("-to", seconds(request.endMillis())));
        }
        command.addAll(List.of("-i", input.toString()));
        if (request.exact()) {
            command.addAll(List.of("-map", "0:V:0", "-map", "0:a?", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart"));
        } else {
            // a copy cuts at the keyframe before the start
            command.addAll(List.of("-map", "0", "-c", "copy", "-avoid_negative_ts", "make_zero"));
        }
        command.addAll(List.of("-map_metadata", "0", output.toString()));
        return command;
    }

    /** The concat demuxer's list: one quoted absolute path per line, {@code '} written as {@code '\''}. */
    static String concatList(List<Path> sources) {
        StringBuilder list = new StringBuilder();
        for (Path source : sources) {
            String path = source.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''");
            list.append("file '").append(path).append("'\n");
        }
        return list.toString();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%d.%03d", millis / 1000, millis % 1000);
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static List<String> start(Path ffmpeg, Path input) {
        return new ArrayList<>(List.of(ffmpeg.toString(), "-hide_banner", "-nostdin", "-loglevel", "error", "-y",
                "-i", input.toString()));
    }

    /** Where {@code source} converts to; null = skip. Never the source itself: that one gets a numbered name. */
    static Path target(Path source, Path outputFolder, String suffix, String extension, String conflictPolicy) throws IOException {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path output = outputFolder.resolve(stem + (suffix == null ? "" : suffix) + "." + extension.toLowerCase(Locale.ROOT));
        boolean isSource = output.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(source.toAbsolutePath().normalize().toString());
        return resolveCollision(output, isSource ? "auto-rename" : conflictPolicy);
    }

    /** Where to write when {@code output} exists: null = skip, a numbered name, or the same path after deleting it. */
    static Path resolveCollision(Path output, String conflictPolicy) throws IOException {
        if (!Files.exists(output)) {
            return output;
        }
        String policy = conflictPolicy == null ? "overwrite" : conflictPolicy.trim().toLowerCase(Locale.ROOT);
        if ("skip".equals(policy)) {
            return null;
        }
        if ("auto-rename".equals(policy)) {
            String filename = output.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String stem = dot > 0 ? filename.substring(0, dot) : filename;
            String ext = dot > 0 ? filename.substring(dot) : "";
            Path candidate = output;
            for (int counter = 1; Files.exists(candidate); counter++) {
                candidate = output.resolveSibling(stem + " (" + counter + ")" + ext);
            }
            return candidate;
        }
        Files.delete(output);
        return output;
    }
}
