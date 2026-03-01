package org.chaiware.acommander.vfs;

import org.chaiware.acommander.model.FileItem;

import java.io.IOException;
import java.util.List;

/**
 * Interface representing a virtual file system (local, archive, FTP).
 */
public interface VFileSystem {
    /**
     * Unique identifier for this file system instance.
     */
    String getIdentifier();

    /**
     * Display name for the root of this file system.
     */
    String getDisplayName();

    /**
     * Lists contents of a directory.
     * @param internalPath Relative path within the file system
     */
    List<FileItem> listContents(String internalPath) throws IOException;

    /**
     * Checks if this file system is read-only.
     */
    boolean isReadOnly();

    /**
     * Deletes an entry.
     */
    void delete(String internalPath) throws IOException;

    /**
     * Moves an entry from this file system to another.
     */
    void move(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException;

    /**
     * Copies an entry from this file system to another.
     */
    void copy(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException;

    /**
     * Gets the internal path for a given file item.
     */
    String getInternalPath(FileItem item);

    /**
     * Renames an entry.
     */
    void rename(String oldInternalPath, String newInternalPath) throws IOException;

    /**
     * Creates a directory.
     */
    void makeDirectory(String internalPath) throws IOException;

    /**
     * Creates an empty file.
     */
    void makeFile(String internalPath) throws IOException;

    /**
     * Checks if the given item is a virtual folder (e.g., an archive file).
     */
    boolean isVirtualFolder(FileItem item);

    /**
     * Creates a child file system from the given item.
     */
    VFileSystem enterVirtualFolder(FileItem item) throws IOException;

    /**
     * Finalizes any pending changes (e.g., repacking an archive).
     */
    void repack() throws IOException;

    /**
     * Cleans up resources.
     */
    void close() throws IOException;
    
    /**
     * Checks if a file needs to be repacked (if modified).
     */
    boolean needsRepack();
    
    /**
     * Marks the file system as needing repack.
     */
    void markModified();
}
