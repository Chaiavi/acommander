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

    public static List<String> buildCommand(Path caesiumPath, String outputFolder, List<String> sourcePaths,
                                            ImageConversionRequest options) {
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
        command.add(options.targetFormat());

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
        String format = options.targetFormat().toLowerCase(Locale.ROOT);
        List<String> extensions = switch (format) {
            case "jpeg" -> List.of("jpeg", "jpg");
            case "tiff" -> List.of("tiff", "tif");
            default -> List.of(format);
        };
        return extensions.stream().map(ext -> stem + suffix + "." + ext).toList();
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
