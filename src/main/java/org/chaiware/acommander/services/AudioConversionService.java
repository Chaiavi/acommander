package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Convert Audio Files: sndfile-convert, with faad (decode) / faac (encode) around it for AAC and M4A. The tools
 * can't open non-ASCII paths, so such files go through an ASCII-only staging folder.
 */
public final class AudioConversionService {
    private static final Logger log = LoggerFactory.getLogger(AudioConversionService.class);
    private static final int[] OPUS_SAMPLE_RATES = {8000, 12000, 16000, 24000, 48000};

    public enum AudioCompressionProfile { LOSSLESS, LOSSY, CUSTOM }

    public record AudioConversionRequest(
            String targetFormat,
            AudioCompressionProfile compressionProfile,
            String encodingFlag,
            boolean normalize,
            Integer sampleRateOverride,
            String endian,
            String suffix,
            String conflictPolicy
    ) {}

    private final Function<List<String>, CompletableFuture<List<String>>> runner;
    private final Path sndfileConvert;
    private final Path faad;
    private final Path faac;

    /** {@code runner} runs one tool command and completes with its output (the app's progress bar + Stop). */
    public AudioConversionService(Function<List<String>, CompletableFuture<List<String>>> runner,
                                  Path sndfileConvert, Path faad, Path faac) {
        this.runner = runner;
        this.sndfileConvert = sndfileConvert;
        this.faad = faad;
        this.faac = faac;
    }

    /** The tools this conversion runs. */
    public List<Path> requiredTools(List<Path> sources, String targetFormat) {
        List<Path> tools = new ArrayList<>(List.of(sndfileConvert));
        if (isAacFamily(targetFormat)) {
            tools.add(faac);
        }
        if (sources.stream().map(AudioConversionService::extension).anyMatch(AudioConversionService::isAacFamily)) {
            tools.add(faad);
        }
        return tools;
    }

    /** Converts one file at a time; completes with the first file written, or null when every one was skipped. */
    public CompletableFuture<Path> convertAll(List<Path> sources, Path outputFolder, AudioConversionRequest request) {
        Path[] firstConverted = {null};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Path source : sources) {
            chain = chain.thenCompose(ignored -> {
                Path output;
                try {
                    output = resolveCollision(outputPath(outputFolder, source.getFileName().toString(), request),
                            request.conflictPolicy());
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
                if (output == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return convert(source, output, request).thenAccept(lines -> {
                    if (firstConverted[0] == null) {
                        firstConverted[0] = output;
                    }
                });
            });
        }
        return chain.thenApply(ignored -> firstConverted[0]);
    }

    private CompletableFuture<List<String>> convert(Path input, Path output, AudioConversionRequest request) {
        String targetFormat = request.targetFormat().toLowerCase(Locale.ROOT);
        if (!isAacFamily(extension(input)) && !isAacFamily(targetFormat)) {
            return convertWithSndfile(input, output, request);
        }
        return inStaging(output, request, stagingDir -> {
            Path stagedInput = stagingDir.resolve("input" + extensionWithDot(input));
            Files.copy(input, stagedInput, StandardCopyOption.REPLACE_EXISTING);
            return convertAacInStaging(stagedInput, stagingDir.resolve("output." + targetFormat), request, stagingDir);
        });
    }

    private CompletableFuture<List<String>> convertWithSndfile(Path input, Path output, AudioConversionRequest request) {
        if (isAscii(input) && isAscii(output)) {
            return runner.apply(convertCommand(sndfileConvert, input.toString(), output, request));
        }
        return inStaging(output, request, stagingDir -> {
            Path stagedInput = input;
            if (!isAscii(input)) {
                stagedInput = stagingDir.resolve("input" + extensionWithDot(input));
                Files.copy(input, stagedInput, StandardCopyOption.REPLACE_EXISTING);
            }
            Path stagedOutput = stagingDir.resolve("output." + request.targetFormat().toLowerCase(Locale.ROOT));
            return runner.apply(convertCommand(sndfileConvert, stagedInput.toString(), stagedOutput, request));
        });
    }

    private interface StagedRun {
        CompletableFuture<List<String>> run(Path stagingDir) throws IOException;
    }

    /** Runs in a fresh staging folder, moves its {@code output.<format>} to {@code output}, deletes the folder. */
    private CompletableFuture<List<String>> inStaging(Path output, AudioConversionRequest request, StagedRun run) {
        Path stagingDir = null;
        try {
            stagingDir = createStagingDirectory();
            Path stagedOutput = stagingDir.resolve("output." + request.targetFormat().toLowerCase(Locale.ROOT));
            Path finalStagingDir = stagingDir;
            return run.run(stagingDir)
                    .thenApply(lines -> {
                        try {
                            Files.move(stagedOutput, output, StandardCopyOption.REPLACE_EXISTING);
                            return lines;
                        } catch (IOException moveException) {
                            throw new CompletionException(moveException);
                        }
                    })
                    .whenComplete((ignored, throwable) -> FileHelper.deleteQuietly(finalStagingDir));
        } catch (IOException ioException) {
            FileHelper.deleteQuietly(stagingDir);
            return CompletableFuture.failedFuture(ioException);
        }
    }

    private CompletableFuture<List<String>> convertAacInStaging(Path stagedInput, Path stagedOutput,
                                                                AudioConversionRequest request, Path stagingDir) {
        CompletableFuture<Path> inputForFinalStep = CompletableFuture.completedFuture(stagedInput);
        if (isAacFamily(extension(stagedInput))) {
            Path decodedWav = stagingDir.resolve("decoded.wav");
            inputForFinalStep = runner.apply(List.of(faad.toString(), "-q", "-o", decodedWav.toString(), stagedInput.toString()))
                    .thenApply(lines -> decodedWav);
        }

        if (!isAacFamily(request.targetFormat())) {
            return inputForFinalStep.thenCompose(input ->
                    runner.apply(convertCommand(sndfileConvert, input.toString(), stagedOutput, request)));
        }

        return inputForFinalStep.thenCompose(input -> {
            CompletableFuture<Path> wavForFaac = CompletableFuture.completedFuture(input);
            boolean needsPreprocessing = request.normalize() || request.sampleRateOverride() != null
                    || !"wav".equals(extension(input));
            if (needsPreprocessing) {
                Path preparedWav = stagingDir.resolve("prepared.wav");
                wavForFaac = runner.apply(toWavCommand(sndfileConvert, input.toString(), preparedWav, request))
                        .thenApply(lines -> preparedWav);
            }
            return wavForFaac.thenCompose(wav -> runner.apply(faacCommand(faac, wav, stagedOutput)));
        });
    }

    static List<String> convertCommand(Path converter, String input, Path output, AudioConversionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(converter.toString());

        String encodingFlag = request.encodingFlag();
        if (encodingFlag == null || encodingFlag.isBlank()) {
            encodingFlag = defaultEncoding(request.targetFormat());
        }

        boolean opusOutput = isOpus(request.targetFormat(), encodingFlag);
        Integer sampleRate = request.sampleRateOverride();
        if (opusOutput) {
            sampleRate = opusSampleRate(sampleRate);
        }
        if (sampleRate != null) {
            command.add("-override-sample-rate=" + sampleRate);
        }

        if (request.endian() != null && endianAllowed(request.targetFormat())) {
            String endian = request.endian().trim().toLowerCase(Locale.ROOT);
            if (endian.equals("little") || endian.equals("big") || endian.equals("cpu")) {
                command.add("-endian=" + endian);
            }
        }
        if (request.normalize()) {
            command.add("-normalize");
        }
        if (!encodingFlag.isBlank()) {
            command.add(encodingFlag);
        }

        command.add(input);
        command.add(output.toString());
        return command;
    }

    static List<String> toWavCommand(Path converter, String input, Path output, AudioConversionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(converter.toString());
        if (request.sampleRateOverride() != null) {
            command.add("-override-sample-rate=" + request.sampleRateOverride());
        }
        if (request.normalize()) {
            command.add("-normalize");
        }
        command.add("-pcm16");
        command.add(input);
        command.add(output.toString());
        return command;
    }

    static List<String> faacCommand(Path faacPath, Path wavInput, Path output) {
        List<String> command = new ArrayList<>(List.of(faacPath.toString(), "-o", output.toString(), "--overwrite"));
        if ("aac".equals(extension(output))) {
            command.add("-r");
        }
        command.add(wavInput.toString());
        return command;
    }

    /** Encoding choices for the dialog: label → sndfile-convert flag ("" = the format's default). */
    public static Map<String, String> encodingOptionsFor(String targetFormat, AudioCompressionProfile profile) {
        String fmt = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        Map<String, String> options = new LinkedHashMap<>();
        switch (profile) {
            case LOSSLESS -> {
                switch (fmt) {
                    case "wav", "aif", "au", "rf64", "w64", "raw", "flac" -> {
                        options.put("16-bit PCM", "-pcm16");
                        options.put("24-bit PCM", "-pcm24");
                        if (!"flac".equals(fmt)) {
                            options.put("32-bit PCM", "-pcm32");
                            options.put("32-bit Float", "-float32");
                        }
                    }
                    case "caf" -> {
                        options.put("ALAC 16-bit", "-alac16");
                        options.put("ALAC 24-bit", "-alac24");
                        options.put("PCM 24-bit", "-pcm24");
                    }
                    default -> options.put("Auto (source/default)", "");
                }
            }
            case LOSSY -> {
                switch (fmt) {
                    case "ogg", "oga" -> {
                        options.put("Vorbis", "-vorbis");
                        options.put("Opus", "-opus");
                    }
                    case "opus" -> options.put("Opus", "-opus");
                    case "aac", "m4a" -> options.put("AAC (FAAC default)", "");
                    case "wav" -> {
                        options.put("IMA ADPCM", "-ima-adpcm");
                        options.put("MS ADPCM", "-ms-adpcm");
                        options.put("GSM 6.10", "-gsm610");
                    }
                    case "mp3" -> options.put("MP3 (container default)", "");
                    default -> options.put("Auto (source/default)", "");
                }
            }
            case CUSTOM -> {
                options.put("Auto (source/default)", "");
                options.put("16-bit PCM", "-pcm16");
                options.put("24-bit PCM", "-pcm24");
                options.put("32-bit PCM", "-pcm32");
                options.put("32-bit Float", "-float32");
                options.put("64-bit Float", "-float64");
                options.put("uLaw", "-ulaw");
                options.put("aLaw", "-alaw");
                if ("flac".equals(fmt)) {
                    options.put("FLAC-safe PCM 16-bit", "-pcm16");
                    options.put("FLAC-safe PCM 24-bit", "-pcm24");
                }
                if ("caf".equals(fmt)) {
                    options.put("ALAC 16-bit (CAF)", "-alac16");
                    options.put("ALAC 24-bit (CAF)", "-alac24");
                }
                if ("wav".equals(fmt)) {
                    options.put("IMA ADPCM (WAV)", "-ima-adpcm");
                    options.put("MS ADPCM (WAV)", "-ms-adpcm");
                    options.put("GSM 6.10 (WAV)", "-gsm610");
                }
                if ("ogg".equals(fmt) || "oga".equals(fmt) || "opus".equals(fmt)) {
                    options.put("Vorbis (OGG)", "-vorbis");
                    options.put("Opus (OGG)", "-opus");
                }
            }
        }
        return options;
    }

    static Path outputPath(Path outputFolder, String sourceName, AudioConversionRequest request) {
        int dot = sourceName.lastIndexOf('.');
        String stem = dot > 0 ? sourceName.substring(0, dot) : sourceName;
        String suffix = request.suffix() == null ? "" : request.suffix();
        return outputFolder.resolve(stem + suffix + "." + request.targetFormat().toLowerCase(Locale.ROOT));
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

    private static Path createStagingDirectory() throws IOException {
        for (Path candidate : List.of(AppTempDir.root(), AppPaths.root())) {
            if (isAscii(candidate) && Files.isDirectory(candidate)) {
                return Files.createTempDirectory(candidate, "acommander-audio-");
            }
        }
        return AppTempDir.createTempDirectory("acommander-audio-");
    }

    private static String defaultEncoding(String targetFormat) {
        return switch (targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT)) {
            case "flac", "wav", "aif", "au", "rf64", "w64", "raw", "caf" -> "-pcm16";
            case "ogg", "oga" -> "-vorbis";
            case "opus" -> "-opus";
            default -> "";
        };
    }

    public static boolean isOpus(String targetFormat, String encodingFlag) {
        return "opus".equalsIgnoreCase(targetFormat) || (encodingFlag != null && "-opus".equalsIgnoreCase(encodingFlag.trim()));
    }

    public static boolean isOpusSampleRate(int sampleRate) {
        return Arrays.stream(OPUS_SAMPLE_RATES).anyMatch(rate -> rate == sampleRate);
    }

    /** Opus allows only a few sample rates: default 48 kHz, otherwise the nearest allowed one. */
    private static int opusSampleRate(Integer requested) {
        if (requested == null) {
            return 48000;
        }
        int nearest = OPUS_SAMPLE_RATES[0];
        for (int candidate : OPUS_SAMPLE_RATES) {
            if (Math.abs(requested - candidate) < Math.abs(requested - nearest)) {
                nearest = candidate;
            }
        }
        if (nearest != requested) {
            log.warn("Adjusted unsupported Opus sample rate {} to {}", requested, nearest);
        }
        return nearest;
    }

    private static boolean endianAllowed(String targetFormat) {
        return switch (targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT)) {
            case "flac", "ogg", "oga", "opus", "mp3" -> false;
            default -> true;
        };
    }

    static boolean isAacFamily(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        return "aac".equals(normalized) || "m4a".equals(normalized);
    }

    private static String extension(Path path) {
        String withDot = extensionWithDot(path);
        return withDot.isEmpty() ? "" : withDot.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String extensionWithDot(Path path) {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot);
    }

    private static boolean isAscii(Path path) {
        return path.toString().chars().allMatch(c -> c <= 127);
    }
}
