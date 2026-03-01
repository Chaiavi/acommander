package org.chaiware.acommander.model;

import lombok.Getter;

import java.nio.file.Path;

/**
 * Represents an active archive session.
 * Tracks the archive file, temp folder, and access mode.
 */
@Getter
public class ArchiveSession {
    private final String archivePath;        // Path to the original archive file
    private final Path tempFolder;           // Temp folder where archive is extracted
    private final ArchiveMode mode;          // Read-write or read-only
    private final String entryPath;          // Current path inside archive (for navigation)
    
    public boolean isNeedsRepack() {
        return root.needsRepack;
    }

    public void setNeedsRepack(boolean needsRepack) {
        root.needsRepack = needsRepack;
    }

    private final ArchiveSession root;       // Pointer to the root session which holds shared state
    private boolean needsRepack;             // True if archive needs to be repacked on exit (only relevant in root)

    public ArchiveSession(String archivePath, Path tempFolder, ArchiveMode mode) {
        this.archivePath = archivePath;
        this.tempFolder = tempFolder;
        this.mode = mode;
        this.entryPath = "";
        this.needsRepack = false;
        this.root = this;
    }

    private ArchiveSession(String archivePath, Path tempFolder, ArchiveMode mode, String entryPath, ArchiveSession root) {
        this.archivePath = archivePath;
        this.tempFolder = tempFolder;
        this.mode = mode;
        this.entryPath = entryPath;
        this.root = root;
    }

    /**
     * Creates a child session for navigating into a subdirectory.
     */
    public ArchiveSession createChild(String subDirName) {
        String newEntryPath = entryPath.isEmpty() ? subDirName : entryPath + "/" + subDirName;
        return new ArchiveSession(archivePath, tempFolder, mode, newEntryPath, root);
    }

    /**
     * Creates the parent session for navigating up one level.
     * Returns null if already at root.
     */
    public ArchiveSession getParent() {
        if (entryPath.isEmpty()) {
            return null;
        }

        int lastSlash = entryPath.lastIndexOf('/');
        if (lastSlash < 0) {
            // Parent is the root of the archive
            return root;
        }

        String parentEntryPath = entryPath.substring(0, lastSlash);
        return new ArchiveSession(archivePath, tempFolder, mode, parentEntryPath, root);
    }
    
    /**
     * Gets the full path inside the temp folder for the current entry path.
     */
    public Path getTempFolderPath() {
        if (entryPath.isEmpty()) {
            return tempFolder;
        }
        return tempFolder.resolve(entryPath);
    }
    
    /**
     * Gets the full path inside the temp folder for a specific entry name.
     */
    public Path getTempPathForEntry(String entryName) {
        return getTempFolderPath().resolve(entryName);
    }
    
    /**
     * Checks if this session is at the root of the archive.
     */
    public boolean isRoot() {
        return entryPath.isEmpty();
    }
    
    /**
     * Gets a display path for the combo box.
     */
    public String getDisplayPath() {
        String archiveName = new java.io.File(archivePath).getName();
        if (entryPath.isEmpty()) {
            return archiveName;
        }
        return archiveName + "://" + entryPath;
    }
    
    /**
     * Cleans up the temp folder when the session is closed.
     */
    public void cleanup() {
        if (tempFolder != null && tempFolder.toFile().exists()) {
            try {
                java.nio.file.Files.walk(tempFolder)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.delete(path);
                        } catch (java.io.IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            } catch (java.io.IOException e) {
                // Ignore cleanup errors
            }
        }
    }
}
