package org.chaiware.acommander.model;

import lombok.Getter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a file or directory entry inside an archive.
 * This is a virtual file item that doesn't exist as a standalone file on disk,
 * but rather as an entry within an archive file.
 */
@Getter
public class VirtualFileItem extends FileItem {
    private final String archivePath;      // Path to the .zip/.7z/.rar file
    private final String entryPath;        // Full path inside the archive
    private final long compressedSize;
    private final LocalDateTime modifiedTime;
    private final boolean isDirectoryEntry;
    
    /**
     * Creates a virtual file item representing an entry in an archive.
     * 
     * @param archivePath Path to the archive file on disk
     * @param entryName Name of the entry (filename or directory name)
     * @param entryPath Full path inside the archive (may include directories)
     * @param size Uncompressed size of the entry
     * @param compressedSize Compressed size of the entry
     * @param modifiedTime Modified time from the archive entry
     * @param isDirectory Whether this entry is a directory
     */
    public VirtualFileItem(String archivePath, String entryName, String entryPath, 
                           long size, long compressedSize, Long modifiedTime, boolean isDirectory) {
        super(createDummyFile(archivePath, entryName));
        this.archivePath = archivePath;
        this.entryPath = entryPath;
        this.compressedSize = compressedSize;
        this.isDirectoryEntry = isDirectory;
        
        // Set size from archive entry
        setSize(size);
        
        // Parse modified time from archive
        if (modifiedTime != null && modifiedTime > 0) {
            this.modifiedTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(modifiedTime), ZoneId.systemDefault());
        } else {
            this.modifiedTime = null;
        }
    }
    
    /**
     * Creates a dummy File object for the FileItem base class.
     * The File doesn't actually exist on disk - it's just for compatibility.
     */
    private static File createDummyFile(String archivePath, String entryName) {
        // Create a dummy file path that represents the virtual location
        // This won't exist on disk but allows FileItem to work
        return new File(archivePath + "/" + entryName);
    }
    
    @Override
    public boolean isDirectory() {
        return isDirectoryEntry;
    }
    
    @Override
    public String getDate() {
        if (modifiedTime == null) {
            return "";
        }
        return modifiedTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
    
    /**
     * Gets the name of this entry (filename or directory name).
     */
    public String getEntryName() {
        int lastSlash = entryPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return entryPath;
        }
        return entryPath.substring(lastSlash + 1);
    }
    
    @Override
    public String toString() {
        return getPresentableFilename();
    }
}
