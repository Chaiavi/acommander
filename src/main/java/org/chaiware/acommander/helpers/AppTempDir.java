package org.chaiware.acommander.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Root for every temp file the app makes: {@code %TEMP%/acommander-<pid>}. Deleted on exit; roots left by a crashed
 * run are deleted by {@link #deleteStaleRoots()} at the next start. A root holding unsaved edits ({@link #retain}) is
 * kept by both.
 */
public final class AppTempDir {
    private static final Logger logger = LoggerFactory.getLogger(AppTempDir.class);
    private static final String PREFIX = "acommander-";
    static final String KEEP_MARKER = "KEEP-unsaved-edits.txt";
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"));
    private static final Set<Path> retained = new LinkedHashSet<>();
    private static Path root;

    private AppTempDir() {
    }

    public static synchronized Path root() throws IOException {
        if (root == null) {
            Path created = Files.createDirectories(BASE.resolve(PREFIX + ProcessHandle.current().pid()));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteUnlessRetained(created)));
            root = created;
        }
        return root;
    }

    public static Path createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(root(), prefix, suffix);
    }

    public static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(root(), prefix);
    }

    /**
     * Keeps {@code path}, which holds the only copy of unsaved edits, on exit and at later starts until
     * {@link #release}. The marker file in the root lists what is kept.
     */
    // ponytail: keeps the whole root, other temp files too; per-path cleanup if retained roots ever grow large.
    public static synchronized void retain(Path path) throws IOException {
        if (retained.add(path)) {
            writeMarker();
        }
    }

    public static synchronized void release(Path path) {
        if (retained.remove(path)) {
            try {
                writeMarker();
            } catch (IOException e) {
                logger.warn("Could not update {}", KEEP_MARKER, e);
            }
        }
    }

    private static void writeMarker() throws IOException {
        Path marker = root().resolve(KEEP_MARKER);
        if (retained.isEmpty()) {
            Files.deleteIfExists(marker);
            return;
        }
        List<String> lines = new ArrayList<>(List.of(
                "A Commander keeps this folder: it holds edits that could not be saved yet.", ""));
        retained.forEach(path -> lines.add(path.toString()));
        Files.write(marker, lines);
    }

    public static void deleteStaleRoots() {
        deleteStaleRoots(BASE);
    }

    static void deleteStaleRoots(Path base) {
        try (Stream<Path> entries = Files.list(base)) {
            entries.filter(entry -> isStaleRoot(entry.getFileName().toString())).forEach(AppTempDir::deleteUnlessRetained);
        } catch (IOException e) {
            logger.debug("Could not scan {} for stale temp folders", base, e);
        }
    }

    static void deleteUnlessRetained(Path tempRoot) {
        if (Files.exists(tempRoot.resolve(KEEP_MARKER))) {
            logger.warn("Keeping temp folder with unsaved edits: {}", tempRoot);
            return;
        }
        logger.info("Deleting temp folder: {}", tempRoot);
        FileHelper.deleteQuietly(tempRoot);
    }

    /** A root whose process is gone. Other names, like {@code acommander-audio-123}, are never touched. */
    static boolean isStaleRoot(String name) {
        if (!name.startsWith(PREFIX)) {
            return false;
        }
        try {
            return ProcessHandle.of(Long.parseLong(name.substring(PREFIX.length()))).isEmpty();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
