package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHelper {
    private static final Logger logger = LoggerFactory.getLogger(FileHelper.class);

    /**
     * Checks if a file appears to be a text file (non-binary).
     * Uses null byte detection and suspicious character ratio analysis.
     */
    public static boolean isTextFile(FileItem fileItem) {
        if (fileItem == null || fileItem.isDirectory()) {
            return false;
        }

        Path path = fileItem.getFile().toPath();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return false;
        }

        byte[] buffer = new byte[8192];
        int read;
        try (FileInputStream inputStream = new FileInputStream(fileItem.getFile())) {
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
}
