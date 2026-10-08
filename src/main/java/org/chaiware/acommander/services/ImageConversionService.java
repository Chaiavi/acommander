package org.chaiware.acommander.services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Convert Graphics Files: the caesiumclt command and where its output lands. */
public final class ImageConversionService {

    public enum ImageCompressionMode { QUALITY, LOSSLESS, MAX_SIZE }

    public enum ImageResizeMode {
        NONE(null), WIDTH("--width"), HEIGHT("--height"), LONG_EDGE("--long-edge"), SHORT_EDGE("--short-edge");

        private final String caesiumFlag;

        ImageResizeMode(String caesiumFlag) {
            this.caesiumFlag = caesiumFlag;
        }
    }

    public record ImageConversionRequest(
            String targetFormat,
            ImageCompressionMode compressionMode,
            Integer quality,
            String maxSize,
            boolean keepExif,
            boolean keepDates,
            ImageResizeMode resizeMode,
            Integer resizeValue,
            boolean noUpscale,
            String suffix,
            String overwritePolicy
    ) {}

    private ImageConversionService() {
    }

    /**
     * The caesiumclt runs for {@code sourcePaths}. caesium refuses to convert a file to its own format ("Cannot convert
     * to the same format") yet exits 0, so files already in the target format get their own run with
     * {@code --format original}.
     */
    public static List<List<String>> buildCommands(Path caesiumPath, String outputFolder, List<String> sourcePaths,
                                                   ImageConversionRequest options) {
        List<String> extensions = extensions(options.targetFormat());
        List<String> same = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String source : sourcePaths) {
            (extensions.contains(extension(source)) ? same : other).add(source);
        }
        List<List<String>> commands = new ArrayList<>();
        if (!other.isEmpty()) {
            commands.add(buildCommand(caesiumPath, outputFolder, other, options, options.targetFormat()));
        }
        if (!same.isEmpty()) {
            commands.add(buildCommand(caesiumPath, outputFolder, same, options, "original"));
        }
        return commands;
    }

    /** caesium's lines about files it failed on; it exits 0 even then. */
    public static List<String> failures(List<String> output) {
        return output.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("[Error]") || line.startsWith("Error compressing"))
                .toList();
    }

    private static List<String> buildCommand(Path caesiumPath, String outputFolder, List<String> sourcePaths,
                                             ImageConversionRequest options, String format) {
        List<String> command = new ArrayList<>();
        command.add(caesiumPath.toString());

        switch (options.compressionMode()) {
            case QUALITY -> {
                command.add("--quality");
                command.add(String.valueOf(options.quality() == null ? 80 : options.quality()));
            }
            case LOSSLESS -> command.add("--lossless");
            case MAX_SIZE -> {
                command.add("--max-size");
                command.add(options.maxSize());
            }
        }

        command.add("--output");
        command.add(outputFolder);
        command.add("--format");
        command.add(format);
        command.add("--verbose");
        command.add("2");

        if (options.keepExif()) {
            command.add("--exif");
        }
        if (options.keepDates()) {
            command.add("--keep-dates");
        }
        if (options.resizeMode() != ImageResizeMode.NONE) {
            command.add(options.resizeMode().caesiumFlag);
            command.add(String.valueOf(options.resizeValue()));
            if (options.noUpscale()) {
                command.add("--no-upscale");
            }
        }
        if ("png".equals(options.targetFormat())) {
            command.add("--png-opt-level");
            command.add("3");
            command.add("--zopfli");
        }
        if (options.suffix() != null && !options.suffix().isBlank()) {
            command.add("--suffix");
            command.add(options.suffix());
        }

        command.add("--overwrite");
        command.add(caesiumOverwritePolicy(options.overwritePolicy()));

        command.addAll(sourcePaths);
        return command;
    }

    /** The first output file that exists, in source order; null when none does. */
    public static Path findFirstConverted(List<String> sourceNames, String outputFolder, ImageConversionRequest options) {
        for (String sourceName : sourceNames) {
            for (String outputName : candidateOutputNames(sourceName, options)) {
                Path candidate = Paths.get(outputFolder, outputName);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static List<String> candidateOutputNames(String sourceName, ImageConversionRequest options) {
        int dotIndex = sourceName.lastIndexOf('.');
        String stem = dotIndex > 0 ? sourceName.substring(0, dotIndex) : sourceName;
        String suffix = options.suffix() == null ? "" : options.suffix();
        return extensions(options.targetFormat()).stream().map(ext -> stem + suffix + "." + ext).toList();
    }

    private static List<String> extensions(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpeg" -> List.of("jpeg", "jpg");
            case "tiff" -> List.of("tiff", "tif");
            default -> List.of(format.toLowerCase(Locale.ROOT));
        };
    }

    private static String extension(String path) {
        String name = Paths.get(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String caesiumOverwritePolicy(String label) {
        if (label == null) {
            return "all";
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "never" -> "never";
            case "bigger" -> "bigger";
            default -> "all";
        };
    }
}
