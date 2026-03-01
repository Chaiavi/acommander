package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages virtual file systems and transitions between them.
 */
public class VfsManager {
    private static final Logger logger = LoggerFactory.getLogger(VfsManager.class);
    private final ArchiveManager archiveManager = new ArchiveManager();

    public ArchiveManager getArchiveManager() {
        return archiveManager;
    }

    /**
     * Creates a file system for a given path.
     * Currently supports local paths.
     */
    public VFileSystem createLocalFileSystem(String rootPath) {
        return new LocalFileSystem(rootPath);
    }

    /**
     * Checks if the item is a virtual folder and returns a new VFileSystem if so.
     */
    public VFileSystem enterVirtualFolder(VFileSystem currentFs, FileItem item) throws IOException {
        if (currentFs.isVirtualFolder(item)) {
            String archivePath = item.getFullPath();
            ArchiveSession session = archiveManager.openArchive(archivePath);
            return new ArchiveFileSystem(session, archiveManager);
        }
        return null;
    }

    /**
     * Cleans up an archive file system.
     */
    public void closeFileSystem(VFileSystem fs) {
        if (fs != null) {
            try {
                fs.close();
            } catch (IOException e) {
                logger.error("Failed to close file system: {}", fs.getIdentifier(), e);
            }
        }
    }
}
