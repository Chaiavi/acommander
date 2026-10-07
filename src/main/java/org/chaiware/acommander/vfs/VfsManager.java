package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.ArchiveManager;
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
     * Opens an archive file as a browsable file system (7-Zip extracts it to a temp folder). {@code outer} is the
     * archive the file lies in, or null for a file on disk.
     */
    public ArchiveFileSystem openArchive(String archivePath, ArchiveFileSystem outer) throws IOException {
        logger.info("Entering archive: {}", archivePath);
        return new ArchiveFileSystem(archiveManager.openArchive(archivePath), archiveManager, outer);
    }

    /** Closes a file system; for an archive this repacks it, and a failure says where the edits were saved. */
    public void closeFileSystem(VFileSystem fs) throws IOException {
        if (fs != null) {
            logger.debug("Closing file system: {}", fs.getIdentifier());
            fs.close();
        }
    }
}
