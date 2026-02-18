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
@EqualsAndHashCode
public class FileItem {
    private final File file;
    private String presentableFilename;
    private long size = 0;

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

    public String getHumanReadableSize() {
        long sizeInBytes = getSizeInBytes();
        if (sizeInBytes == 0) return "";

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

    private long getSizeInBytes() {
        if (!isDirectory())
            return file.length();

        return size;
    }

    public String getDate() {
        try {
            if (getPresentableFilename().equals("..")) return "";

            Instant instant = Files.getLastModifiedTime(file.toPath()).toInstant();
            LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

            return ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (IOException e) {
            return "Buggy Date";
        }
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public String toString() {
        return presentableFilename; // Display name in ListView
    }
}
