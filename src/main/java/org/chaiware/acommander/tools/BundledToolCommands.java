package org.chaiware.acommander.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Argument lists for the bundled tools that {@code Commander} runs directly (rhash, file, ExamDiff, 7-Zip split). */
public final class BundledToolCommands {

    public record ChecksumOptions(String algorithmFlag, String algorithmLabel, boolean base32, boolean base64,
                                  boolean includeFileNames) {
        /** {@code label} is an rhash algorithm name such as MD5 or SHA256; the flag is {@code --md5}, {@code --sha256}. */
        public static ChecksumOptions of(String label, boolean base32, boolean base64, boolean includeFileNames) {
            return new ChecksumOptions("--" + label.toLowerCase(Locale.ROOT), label, base32, base64, includeFileNames);
        }
    }

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

    /** {@code extension} is used only when {@code findInSpecificExtension}; leading dots are ignored. */
    public record FindInFilesOptions(String query, boolean caseInsensitive, boolean findInSpecificExtension,
                                     String extension, boolean includeHiddenAndIgnored) {}

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

    /** ripgrep listing the files under {@code sourcePath} that contain the literal query. */
    public static List<String> findInFiles(Path rgPath, String sourcePath, FindInFilesOptions options) {
        List<String> command = new ArrayList<>(List.of(rgPath.toString(), "--files-with-matches", "--no-messages", "--fixed-strings"));
        if (options.caseInsensitive()) {
            command.add("--ignore-case");
        }
        if (options.includeHiddenAndIgnored()) {
            command.add("--hidden");
            command.add("--no-ignore");
        }
        if (options.findInSpecificExtension()) {
            command.add("--glob");
            command.add("*." + options.extension().replaceFirst("^\\.+", ""));
        }
        command.add(options.query());
        command.add(sourcePath);
        return command;
    }

    /** ripgrep listing the files under {@code sourcePath} whose name matches a wildcard, ignoring case and .gitignore. */
    public static List<String> findByName(Path rgPath, String sourcePath, String wildcard) {
        return List.of(rgPath.toString(), "--files", "--no-messages", "--no-ignore", "--iglob", wildcard, sourcePath);
    }

    /** ripgrep's output lines as distinct absolute paths (relative lines resolve against {@code sourcePath}). */
    public static List<String> foundFiles(List<String> output, String sourcePath) {
        return output.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> Path.of(sourcePath).resolve(line).normalize().toString())
                .distinct()
                .toList();
    }

    /** The first hash on rhash's output (skipping its own error lines), or all of the output if none is found. */
    public static String checksumDigest(List<String> outputLines) {
        if (outputLines == null) {
            return "";
        }
        Pattern valuePrefix = Pattern.compile("^\\s*([A-Za-z0-9+/=]+)");
        for (String line : outputLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String lower = line.trim().toLowerCase(Locale.ROOT);
            if (lower.startsWith("rhash:") || lower.contains("error")) {
                continue;
            }
            Matcher matcher = valuePrefix.matcher(line);
            if (matcher.find() && matcher.group(1).trim().length() >= 8) {
                return matcher.group(1).trim();
            }
        }
        return String.join(System.lineSeparator(), outputLines).trim();
    }

    /** Where "Save Value As File" writes: {@code a.txt.sha256} for a file, {@code photos.SHA256SUMS} for a folder. */
    public static Path checksumOutputPath(Path outputDirectory, String targetName, String algorithmLabel, boolean folderMode) {
        return outputDirectory.resolve(folderMode
                ? targetName + "." + algorithmLabel.toUpperCase(Locale.ROOT) + "SUMS"
                : targetName + "." + algorithmLabel.toLowerCase(Locale.ROOT));
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
