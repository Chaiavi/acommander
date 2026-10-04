package org.chaiware.acommander.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Getter
@EqualsAndHashCode(of = {"file", "presentableFilename"})
public class FileItem {
    private final File file;
    private String presentableFilename;
    private long size = -1;
    private Long lastModified = null;
    private boolean isDirectory = false;

    public FileItem(File file) {
        this.file = file;
        this.presentableFilename = file.getName();
        this.isDirectory = file != null && file.isDirectory();
    }

    public FileItem(File folder, String filenameStr) {
        this(folder);
        this.presentableFilename = filenameStr;
    }

    public FileItem(File file, String presentableFilename, long size, long lastModified) {
        this(file, presentableFilename, size, lastModified, file != null && file.isDirectory());
    }

    public FileItem(File file, String presentableFilename, long size, long lastModified, boolean isDirectory) {
        this.file = file;
        this.presentableFilename = presentableFilename;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    public String getName() {
        return file != null ? file.getName() : presentableFilename;
    }

    public String getFullPath() {
        return file != null ? file.getAbsolutePath() : "";
    }

    public String getHumanReadableSize() {
        long sizeInBytes = getSizeInBytes();
        return sizeInBytes <= 0 ? "" : humanSize(sizeInBytes);
    }

    /** "512 B", "2 KB", "1.5 MB": one decimal, dropped when it is zero. */
    public static String humanSize(long sizeInBytes) {
        if (sizeInBytes < 1024) return sizeInBytes + " B";
        int exp = (int) (Math.log(sizeInBytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        String value = String.format(Locale.ROOT, "%.1f", sizeInBytes / Math.pow(1024, exp));
        return (value.endsWith(".0") ? value.substring(0, value.length() - 2) : value) + " " + unit;
    }

    public void setSize(long sizeInBytes) {
        this.size = sizeInBytes;
    }

    public long getSizeInBytes() {
        if (size != -1 && (size != 0 || isDirectory())) return size;
        if (file != null && !isDirectory())
            return file.length();

        return size == -1 ? 0 : size;
    }

    public String getDate() {
        if ("..".equals(getPresentableFilename())) return "";

        try {
            long modifiedMillis;
            if (lastModified != null) {
                modifiedMillis = lastModified;
            } else if (file != null) {
                // Avoid Path parsing here: corrupted/bad media can surface names that are invalid on Windows.
                modifiedMillis = file.lastModified();
            } else {
                return "";
            }

            if (modifiedMillis <= 0) {
                return "";
            }

            Instant instant = Instant.ofEpochMilli(modifiedMillis);
            LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (RuntimeException e) {
            return "";
        }
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    @Override
    public String toString() {
        return presentableFilename; // Display name in ListView
    }
}
