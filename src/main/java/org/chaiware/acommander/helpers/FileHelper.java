package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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

        File file;
        if (fs instanceof LocalFileSystem) {
            file = fileItem.getFile();
            if (file == null || !file.exists() || !file.canRead()) {
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
