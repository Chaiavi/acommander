package org.chaiware.acommander.model;

import lombok.Getter;

/**
 * Represents a virtual folder inside an archive file.
 * The realPath is a temp directory or the archive path itself.
 * The archivePath is the path to the .zip/.7z/.rar file on disk.
 * The entryPath is the path inside the archive (e.g., "folder/subfolder/").
 */
@Getter
public class ArchiveVirtualFolder extends VirtualFolder {
    private final String entryPath; // Path inside the archive (empty string for root)
    
    public ArchiveVirtualFolder(String archivePath, String entryPath) {
        super(
            archivePath,  // realPath = archive file path
            buildDisplayPath(archivePath, entryPath),
            archivePath,
            entryPath.isEmpty()
        );
        this.entryPath = entryPath;
    }
    
    /**
     * Creates a child virtual folder for navigating into a subdirectory within the archive.
     */
    public ArchiveVirtualFolder createChild(String subDirName) {
        String newEntryPath = entryPath.isEmpty() ? subDirName : entryPath + "/" + subDirName;
        return new ArchiveVirtualFolder(getArchivePath(), newEntryPath);
    }
    
    /**
     * Creates the parent virtual folder for navigating up one level.
     * Returns null if already at root.
     */
    public ArchiveVirtualFolder getParent() {
        if (isRoot()) {
            return null;
        }
        
        int lastSlash = entryPath.lastIndexOf('/');
        if (lastSlash < 0) {
            // Parent is the root of the archive
            return new ArchiveVirtualFolder(getArchivePath(), "");
        }
        
        String parentEntryPath = entryPath.substring(0, lastSlash);
        return new ArchiveVirtualFolder(getArchivePath(), parentEntryPath);
    }
    
    /**
     * Gets the full path inside the archive including the entry path and an entry name.
     */
    public String getFullEntryPath(String entryName) {
        return entryPath.isEmpty() ? entryName : entryPath + "/" + entryName;
    }
    
    private static String buildDisplayPath(String archivePath, String entryPath) {
        String archiveName = new java.io.File(archivePath).getName();
        if (entryPath.isEmpty()) {
            return archiveName;
        }
        return archiveName + "://" + entryPath;
    }
    
    @Override
    public String toString() {
        return getDisplayPath();
    }
}
