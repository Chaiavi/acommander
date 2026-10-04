package org.chaiware.acommander.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Root for every temp file the app makes: {@code %TEMP%/acommander-<pid>}. Deleted on exit; roots left by a crashed
 * run are deleted by {@link #deleteStaleRoots()} at the next start.
 */
public final class AppTempDir {
    private static final Logger logger = LoggerFactory.getLogger(AppTempDir.class);
    private static final String PREFIX = "acommander-";
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"));
    private static Path root;

    private AppTempDir() {
    }

    public static synchronized Path root() throws IOException {
        if (root == null) {
            Path created = Files.createDirectories(BASE.resolve(PREFIX + ProcessHandle.current().pid()));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileHelper.deleteQuietly(created)));
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

    public static void deleteStaleRoots() {
        deleteStaleRoots(BASE);
    }

    static void deleteStaleRoots(Path base) {
        try (Stream<Path> entries = Files.list(base)) {
            entries.filter(entry -> isStaleRoot(entry.getFileName().toString())).forEach(stale -> {
                logger.info("Deleting temp folder left by an earlier run: {}", stale);
                FileHelper.deleteQuietly(stale);
            });
        } catch (IOException e) {
            logger.debug("Could not scan {} for stale temp folders", base, e);
        }
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
