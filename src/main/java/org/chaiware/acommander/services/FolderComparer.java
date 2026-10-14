package org.chaiware.acommander.services;

import org.chaiware.acommander.helpers.BackgroundTasks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/** Compare Folders: marks the top-level items of each pane that are only on one side or differ. */
public final class FolderComparer {

    public enum Mark { LEFT_ONLY, RIGHT_ONLY, DIFFERENT }

    public record Options(boolean compareByDate, boolean compareContents, boolean recursive, boolean caseSensitiveNames) {}

    /** Marks are keyed by {@link #key(Path)} of the top-level item in that pane. */
    public record Result(Map<String, Mark> leftMarks, Map<String, Mark> rightMarks,
                         int onlyLeftCount, int onlyRightCount, int differentCount) {}

    private record Entry(Path absolutePath, Path topLevelPath, boolean directory, long size, long modifiedMillis) {}

    /** Large reads keep an HDD streaming instead of seeking between the two files (Files.mismatch reads 8 KB each). */
    private static final int CHUNK_SIZE = 32 * 1024 * 1024;

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
        // ponytail: two volumes on one physical disk still read in parallel and seek; per-disk detection would fix it
        boolean parallelReads = options.compareContents()
                && !Files.getFileStore(leftRoot).equals(Files.getFileStore(rightRoot));

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
            } else if (differ(left, right, options, parallelReads)) {
                different++;
                mark(leftMarks, left.topLevelPath(), Mark.DIFFERENT);
                mark(rightMarks, right.topLevelPath(), Mark.DIFFERENT);
            }
        }
        return new Result(leftMarks, rightMarks, onlyLeft, onlyRight, different);
    }

    private static boolean differ(Entry left, Entry right, Options options, boolean parallelReads) throws IOException {
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
        return options.compareContents()
                && contentsDiffer(left.absolutePath(), right.absolutePath(), parallelReads, CHUNK_SIZE);
    }

    /** Compares two files of equal size chunk by chunk; with {@code parallel} the left chunk is read on another thread. */
    static boolean contentsDiffer(Path left, Path right, boolean parallel, int chunkSize) throws IOException {
        long size = Files.size(left);
        int chunk = (int) Math.min(size, chunkSize);
        try (FileChannel leftChannel = FileChannel.open(left); FileChannel rightChannel = FileChannel.open(right)) {
            ByteBuffer leftBuffer = ByteBuffer.allocateDirect(chunk);
            ByteBuffer rightBuffer = ByteBuffer.allocateDirect(chunk);
            for (long done = 0; done < size; done += chunk) {
                CompletableFuture<Void> leftRead = parallel
                        ? BackgroundTasks.run(() -> fill(leftChannel, leftBuffer))
                        : CompletableFuture.completedFuture(fill(leftChannel, leftBuffer));
                fill(rightChannel, rightBuffer);
                leftRead.join();
                if (leftBuffer.mismatch(rightBuffer) != -1) {
                    return true;
                }
            }
            return false;
        } catch (CompletionException e) {
            if (e.getCause() instanceof UncheckedIOException io) {
                throw io.getCause();
            }
            throw e;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Void fill(FileChannel channel, ByteBuffer buffer) {
        buffer.clear();
        try {
            int read = 0;
            while (read >= 0 && buffer.hasRemaining()) {
                read = channel.read(buffer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        buffer.flip();
        return null;
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
