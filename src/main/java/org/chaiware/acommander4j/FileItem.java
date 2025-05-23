package org.chaiware.acommander4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileItem {
    private final File file;
    private String presentableFilename;

    public FileItem(File file) {
        this.file = file;
        presentableFilename = file.getName();
    }

    public FileItem(File folder, String filenameStr) {
        this(folder);
        this.presentableFilename = filenameStr;
    }

    public String getName() {
        return file.getName();
    }

    public String getFullPath() {
        return file.getAbsolutePath();
    }

    public String getSize() {
        if (isDirectory()) return "";
        return humanReadableSize(file.length());
    }

    private String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    public String getDate() {
        try {
            BasicFileAttributes attr = null;
            attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            Instant instant = attr.creationTime().toInstant();
            LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (IOException e) {
            return "Buggy Date";
        }
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public File getFile() {
        return file;
    }

    public String getPresentableFilename() {
        return presentableFilename;
    }

    @Override
    public String toString() {
        return presentableFilename; // Display name in ListView
    }
}
