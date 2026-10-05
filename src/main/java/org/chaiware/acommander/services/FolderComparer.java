package org.chaiware.acommander.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Compare Folders: marks the top-level items of each pane that are only on one side or differ. */
public final class FolderComparer {

    public enum Mark { LEFT_ONLY, RIGHT_ONLY, DIFFERENT }

    public record Options(boolean compareByDate, boolean compareContents, boolean recursive, boolean caseSensitiveNames) {}

    /** Marks are keyed by {@link #key(Path)} of the top-level item in that pane. */
    public record Result(Map<String, Mark> leftMarks, Map<String, Mark> rightMarks,
                         int onlyLeftCount, int onlyRightCount, int differentCount) {}

    private record Entry(Path absolutePath, Path topLevelPath, boolean directory, long size, long modifiedMillis) {}

    private FolderComparer() {
    }

    public static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    public static Result compare(Path leftRoot, Path rightRoot, Options options) throws IOException {
        Map<String, Entry> leftEntries = collectEntries(leftRoot, options);
        Map<String, Entry> rightEntries = collectEntries(rightRoot, options);

        Set<String> allKeys = new TreeSet<>(leftEntries.keySet());
        allKeys.addAll(rightEntries.keySet());

        Map<String, Mark> leftMarks = new HashMap<>();
        Map<String, Mark> rightMarks = new HashMap<>();
        int onlyLeft = 0;
        int onlyRight = 0;
        int different = 0;

        for (String key : allKeys) {
            Entry left = leftEntries.get(key);
            Entry right = rightEntries.get(key);
            if (left == null) {
                onlyRight++;
                mark(rightMarks, right.topLevelPath(), Mark.RIGHT_ONLY);
            } else if (right == null) {
                onlyLeft++;
                mark(leftMarks, left.topLevelPath(), Mark.LEFT_ONLY);
            } else if (differ(left, right, options)) {
                different++;
                mark(leftMarks, left.topLevelPath(), Mark.DIFFERENT);
                mark(rightMarks, right.topLevelPath(), Mark.DIFFERENT);
            }
        }
        return new Result(leftMarks, rightMarks, onlyLeft, onlyRight, different);
    }

    private static boolean differ(Entry left, Entry right, Options options) throws IOException {
        if (left.directory() != right.directory()) {
            return true;
        }
        if (left.directory()) {
            return false;
        }
        if (left.size() != right.size()) {
            return true;
        }
        if (options.compareByDate() && left.modifiedMillis() != right.modifiedMillis()) {
            return true;
        }
        return options.compareContents() && Files.mismatch(left.absolutePath(), right.absolutePath()) != -1;
    }

    private static Map<String, Entry> collectEntries(Path root, Options options) throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        try (Stream<Path> stream = options.recursive() ? Files.walk(root) : Files.list(root)) {
            stream.filter(path -> !path.equals(root)).forEach(path -> addEntry(entries, root, path, options));
        }
        return entries;
    }

    private static void addEntry(Map<String, Entry> entries, Path root, Path path, Options options) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        String key = options.caseSensitiveNames() ? relative : relative.toLowerCase(Locale.ROOT);
        int slash = relative.indexOf('/');
        String topLevelName = slash < 0 ? relative : relative.substring(0, slash);
        boolean directory = Files.isDirectory(path);
        long size = 0L;
        long modified = 0L;
        if (!directory) {
            try {
                size = Files.size(path);
            } catch (IOException ignored) {
                // unreadable: compares as empty
            }
        }
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            // unreadable: compares as epoch
        }
        entries.putIfAbsent(key, new Entry(path.toAbsolutePath().normalize(),
                root.resolve(topLevelName).toAbsolutePath().normalize(), directory, size, modified));
    }

    /** A top-level item seen as left-only by one entry and right-only by another becomes DIFFERENT. */
    private static void mark(Map<String, Mark> marks, Path topLevelPath, Mark mark) {
        String key = key(topLevelPath);
        Mark existing = marks.get(key);
        if (existing == null || existing == mark) {
            marks.put(key, mark);
        } else {
            marks.put(key, Mark.DIFFERENT);
        }
    }
}
