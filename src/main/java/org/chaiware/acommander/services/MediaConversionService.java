package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.FileHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Converts audio (and video) files with ffmpeg, one run per file, into another folder. */
public final class MediaConversionService {
    public static final List<String> AUDIO_FORMATS = List.of("mp3", "m4a", "flac", "wav", "ogg", "opus");
    private static final String LOUDNORM = "loudnorm=I=-16:TP=-1.5:LRA=11";
    public enum Quality { HIGH, NORMAL, SMALL }

    /** {@code sampleRate} null keeps the source rate, or 48 kHz when normalizing (loudnorm outputs 192 kHz). */
    public record AudioRequest(String format, Quality quality, Integer sampleRate, boolean normalize, String suffix,
                               String conflictPolicy) {}

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
