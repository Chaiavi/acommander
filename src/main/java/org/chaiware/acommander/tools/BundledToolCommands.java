package org.chaiware.acommander.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Argument lists for the bundled tools that {@code Commander} runs directly (rhash, file, ExamDiff, 7-Zip split). */
public final class BundledToolCommands {

    public record ChecksumOptions(String algorithmFlag, String algorithmLabel, boolean base32, boolean base64,
                                  boolean includeFileNames) {}

    public enum WhiteSpaceCompareMode {
        NONE("Do not ignore whitespace", ""),
        ALL("Ignore all whitespace", "w"),
        AMOUNT("Ignore changes in amount of whitespace", "b"),
        LEADING("Ignore leading whitespace", "l"),
        TRAILING("Ignore trailing whitespace", "e");

        private final String label;
        private final String examDiffFlag;

        WhiteSpaceCompareMode(String label, String examDiffFlag) {
            this.label = label;
            this.examDiffFlag = examDiffFlag;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record CompareFilesOptions(boolean ignoreCase, WhiteSpaceCompareMode whitespaceMode, boolean differencesOnly) {}

    /** A parsed split size; {@code sevenZipArg} goes after 7-Zip's {@code -v}. Invalid input carries a message. */
    public record SplitSize(boolean valid, long bytes, String sevenZipArg, String message) {
        private static SplitSize invalid(String message) {
            return new SplitSize(false, 0L, "", message);
        }
    }

    private BundledToolCommands() {
    }

    public static List<String> checksum(Path rhashPath, String targetPath, ChecksumOptions options, boolean recursive) {
        List<String> command = new ArrayList<>();
        command.add(rhashPath.toString());
        command.add(options.algorithmFlag());
        if (recursive) {
            command.add("--recursive");
        }
        command.add(options.base32() ? "--base32" : options.base64() ? "--base64" : "--hex");
        if (!options.includeFileNames()) {
            command.add("--simple");
        }
        command.add(targetPath);
        return command;
    }

    public static List<String> analyzeFile(Path fileToolPath, Path magicPath, String targetPath) {
        List<String> command = new ArrayList<>(List.of(fileToolPath.toString(), "-b", "-k", "-z"));
        if (Files.isRegularFile(magicPath)) {
            command.add("-m");
            command.add(magicPath.toString());
        }
        command.add(targetPath);
        return command;
    }

    public static List<String> compareFiles(String examDiffPath, String leftPath, String rightPath, CompareFilesOptions options) {
        List<String> command = new ArrayList<>(List.of(examDiffPath, leftPath, rightPath));
        command.add(options.ignoreCase() ? "/i" : "/!i");
        command.add("/t");
        command.add(options.differencesOnly() ? "/d" : "/!d");
        for (String flag : List.of("w", "b", "l", "e")) {
            command.add((flag.equals(options.whitespaceMode().examDiffFlag) ? "/" : "/!") + flag);
        }
        command.add("/n");
        return command;
    }

    public static SplitSize parseSplitSize(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return SplitSize.invalid("Please enter a split size.");
        }

        String unit = "";
        String digits = input;
        if (input.endsWith("kb") || input.endsWith("mb") || input.endsWith("gb")) {
            unit = input.substring(input.length() - 2, input.length() - 1);
            digits = input.substring(0, input.length() - 2);
        } else if (input.endsWith("k") || input.endsWith("m") || input.endsWith("g") || input.endsWith("b")) {
            unit = input.substring(input.length() - 1);
            digits = input.substring(0, input.length() - 1);
        }

        if (digits.isBlank() || !digits.chars().allMatch(Character::isDigit)) {
            return SplitSize.invalid("Use a positive size like 16m, 64k, 1g, or 16777216.");
        }

        long amount;
        try {
            amount = Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return SplitSize.invalid("Split size is too large.");
        }
        if (amount <= 0) {
            return SplitSize.invalid("Split size must be greater than zero.");
        }

        long multiplier = switch (unit) {
            case "k" -> 1024L;
            case "m" -> 1024L * 1024L;
            case "g" -> 1024L * 1024L * 1024L;
            default -> 1L;
        };
        long bytes;
        try {
            bytes = Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException ex) {
            return SplitSize.invalid("Split size is too large.");
        }
        return new SplitSize(true, bytes, amount + unit, "");
    }
}
