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
        logger.debug("Creating LocalFileSystem for path: {}", rootPath);
        return new LocalFileSystem(rootPath);
    }

    public VFileSystem createFtpFileSystem(FtpConnectionOptions options) {
        logger.debug("Creating FtpFileSystem for host: {}", options.getHost());
        return new FtpFileSystem(options);
    }

    /**
     * Checks if the item is a virtual folder and returns a new VFileSystem if so.
     */
    public VFileSystem enterVirtualFolder(VFileSystem currentFs, FileItem item) throws IOException {
        if (currentFs.isVirtualFolder(item)) {
            String archivePath = item.getFullPath();
            logger.info("Entering virtual folder (archive): {}", archivePath);
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
            logger.debug("Closing file system: {}", fs.getIdentifier());
            try {
                fs.close();
            } catch (IOException e) {
                logger.error("Failed to close file system: {}", fs.getIdentifier(), e);
            }
        }
    }
}
