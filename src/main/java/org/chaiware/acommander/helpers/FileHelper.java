package org.chaiware.acommander.helpers;

import org.chaiware.acommander.commands.Operation;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class FileHelper {
    private static final Logger logger = LoggerFactory.getLogger(FileHelper.class);

    /**
     * Checks if a file appears to be a text file (non-binary).
     * Uses null byte detection and suspicious character ratio analysis.
     */
    public static boolean isTextFile(FileItem fileItem) {
        return isTextFile(fileItem, new LocalFileSystem(""));
    }

    /**
     * Checks if a file appears to be a text file (non-binary).
     * Uses null byte detection and suspicious character ratio analysis.
     */
    public static boolean isTextFile(FileItem fileItem, VFileSystem fs) {
        if (fileItem == null || fileItem.isDirectory()) {
            return false;
        }

        Path file;
        if (fs instanceof LocalFileSystem) {
            file = fileItem.getPath();
            if (file == null || !Files.isReadable(file)) {
                return false;
            }
        } else {
            // For non-local, we'd have to download to check. 
            // For now, let's assume it's text based on extension or just return true to allow trying to edit.
            String name = fileItem.getName().toLowerCase();
            return name.endsWith(".txt") || name.endsWith(".java") || name.endsWith(".xml") || 
                   name.endsWith(".json") || name.endsWith(".properties") || name.endsWith(".md") ||
                   name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js") ||
                   name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h") ||
                   name.endsWith(".py") || name.endsWith(".sh") || name.endsWith(".bat");
        }

        byte[] buffer = new byte[8192];
        int read;
        try (InputStream inputStream = Files.newInputStream(file)) {
            read = inputStream.read(buffer);
        } catch (IOException ex) {
            logger.debug("Failed reading file while checking if it is text: {}", fileItem.getFullPath(), ex);
            return false;
        }

        if (read <= 0) {
            return true;
        }

        // Check for BOM (Byte Order Mark)
        if (read >= 2) {
            boolean utf16LeBom = (buffer[0] & 0xFF) == 0xFF && (buffer[1] & 0xFF) == 0xFE;
            boolean utf16BeBom = (buffer[0] & 0xFF) == 0xFE && (buffer[1] & 0xFF) == 0xFF;
            if (utf16LeBom || utf16BeBom) {
                return true;
            }
        }

        int suspicious = 0;
        for (int i = 0; i < read; i++) {
            int value = buffer[i] & 0xFF;
            // Null bytes are a strong indicator of a binary file
            if (value == 0) {
                return false;
            }
            // Control characters (excluding tab, LF, CR, etc.)
            if (value < 0x09 || (value > 0x0D && value < 0x20)) {
                suspicious++;
            }
        }
        
        // If more than 30% of characters are "suspicious", consider it binary
        double suspiciousRatio = (double) suspicious / read;
        return suspiciousRatio <= 0.30d;
    }

    /** Total bytes of all files under {@code folder}; entries that can't be read are skipped, not fatal. */
    public static long folderSize(Path folder) throws IOException {
        AtomicLong total = new AtomicLong();
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    total.addAndGet(attrs.size());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return total.get();
    }

    /** Copies a folder tree into {@code target}; {@code options} apply to each file (e.g. REPLACE_EXISTING). Stop ends it between files. */
    public static void copyTree(Path source, Path target, CopyOption... options) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Operation.checkNotStopped();
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, options);
                }
            }
        }
    }

    /** Best-effort delete of a temp/staging folder tree; entries that can't be deleted are left and logged. */
    public static void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    logger.debug("Failed to delete {}", path, e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            logger.debug("Failed to walk {} for cleanup", root, e);
        }
    }
}
