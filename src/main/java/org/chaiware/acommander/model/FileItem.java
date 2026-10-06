package org.chaiware.acommander.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Getter
@EqualsAndHashCode(of = {"path", "presentableFilename"})
public class FileItem {
    /** Null on FTP panes: the item is only a name there, the pane knows the folder. */
    private final Path path;
    private final String presentableFilename;
    private long size = -1;
    private Long lastModified = null;
    private boolean isDirectory = false;

    /** Reads the metadata once now; the pane, sort and footer then never touch the disk for it. */
    public FileItem(Path path) {
        this(path, fileName(path));
    }

    public FileItem(Path path, String presentableFilename) {
        this.path = path;
        this.presentableFilename = presentableFilename;
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            this.isDirectory = attributes.isDirectory();
            this.size = isDirectory ? -1 : attributes.size();
            this.lastModified = attributes.lastModifiedTime().toMillis();
        } catch (IOException | RuntimeException e) {
            // Gone or unreadable: no size, no date
            this.size = 0;
            this.lastModified = 0L;
        }
    }

    public FileItem(Path path, String presentableFilename, long size, long lastModified, boolean isDirectory) {
        this.path = path;
        this.presentableFilename = presentableFilename;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    public String getName() {
        return path != null ? fileName(path) : presentableFilename;
    }

    public String getFullPath() {
        return path != null ? path.toAbsolutePath().toString() : "";
    }

    /** Lower-case extension without the dot; "" when the name has none. */
    public String extension() {
        return extension(getName());
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** True when {@code items} is not empty and each is a file (not a folder or "..") whose extension passes. */
    public static boolean allFilesWithExtension(List<FileItem> items, Predicate<String> extension) {
        return items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null && !item.isDirectory()
                && !"..".equals(item.getPresentableFilename()) && extension.test(item.extension()));
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

    /** The size read when the item was listed; a folder's is 0 until F3 sums it. */
    public long getSizeInBytes() {
        return Math.max(size, 0);
    }

    /** The time read when the item was listed; 0 when unknown. */
    public long modifiedMillis() {
        return lastModified == null ? 0 : lastModified;
    }

    public String getDate() {
        if ("..".equals(getPresentableFilename())) return "";

        try {
            long modifiedMillis = modifiedMillis();
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
