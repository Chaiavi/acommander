package org.chaiware.acommander.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@EqualsAndHashCode(of = {"file", "presentableFilename"})
public class FileItem {
    private final File file;
    private String presentableFilename;
    private long size = -1;
    private Long lastModified = null;

    public FileItem(File file) {
        this.file = file;
        this.presentableFilename = file.getName();
    }

    public FileItem(File folder, String filenameStr) {
        this(folder);
        this.presentableFilename = filenameStr;
    }

    public FileItem(File file, String presentableFilename, long size, long lastModified) {
        this.file = file;
        this.presentableFilename = presentableFilename;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getName() {
        return file != null ? file.getName() : presentableFilename;
    }

    public String getFullPath() {
        return file != null ? file.getAbsolutePath() : "";
    }

    public String getHumanReadableSize() {
        long sizeInBytes = getSizeInBytes();
        if (sizeInBytes <= 0) return "";

        if (sizeInBytes < 1024) return sizeInBytes + " B";
        int exp = (int) (Math.log(sizeInBytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        double value = sizeInBytes / Math.pow(1024, exp);
        return (Double.parseDouble(String.format("%.1f", value)) % 1 == 0)
                ? String.format("%.0f %s", value, unit)
                : String.format("%.1f %s", value, unit);
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
        try {
            if (getPresentableFilename().equals("..")) return "";

            Instant instant;
            if (lastModified != null) {
                instant = Instant.ofEpochMilli(lastModified);
            } else if (file != null) {
                instant = Files.getLastModifiedTime(file.toPath()).toInstant();
            } else {
                return "";
            }
            
            LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (IOException e) {
            return "Buggy Date";
        }
    }

    public boolean isDirectory() {
        return file != null ? file.isDirectory() : false;
    }

    @Override
    public String toString() {
        return presentableFilename; // Display name in ListView
    }
}
