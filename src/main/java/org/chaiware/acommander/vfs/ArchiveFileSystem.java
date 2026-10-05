package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of VFileSystem for archives (7z, zip, etc.).
 * Extends the functionality by extracting to a temp folder and repacking on close.
 */
public class ArchiveFileSystem implements VFileSystem {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveFileSystem.class);
    private final ArchiveSession session;
    private final ArchiveManager archiveManager;

    public ArchiveFileSystem(ArchiveSession session, ArchiveManager archiveManager) {
        this.session = session;
        this.archiveManager = archiveManager;
    }

    @Override
    public String getIdentifier() {
        return "archive:" + session.getArchivePath();
    }

    @Override
    public String getDisplayName() {
        return session.getDisplayPath();
    }

    @Override
    public List<FileItem> listContents(String internalPath) throws IOException {
        // The internalPath is relative to the archive root
        Path tempPath = session.getTempFolder();
        if (internalPath != null && !internalPath.isEmpty()) {
            // Check if internalPath already contains archive prefix which it shouldn't
            String cleanPath = internalPath;
            String archiveName = new File(session.getArchivePath()).getName();
            if (cleanPath.startsWith(archiveName + "://")) {
                cleanPath = cleanPath.substring((archiveName + "://").length());
            }

            tempPath = tempPath.resolve(cleanPath);
        }
        
        List<FileItem> items = new ArrayList<>();
        items.add(new FileItem(tempPath, ".."));
        LocalFileSystem.addEntries(tempPath, items);
        return items;
    }

    @Override
    public boolean isReadOnly() {
        return session.getMode() == ArchiveMode.READ_ONLY;
    }

    @Override
    public void delete(String internalPath) throws IOException {
        checkReadOnly();
        Path path = session.getTempFolder().resolve(internalPath);
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                }
                throw e;
            }
        } else {
            Files.deleteIfExists(path);
        }
        markModified();
    }

    @Override
    public void move(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        if (targetFs == this) {
            rename(sourceInternalPath, targetInternalPath);
        } else {
            copy(sourceInternalPath, targetFs, targetInternalPath);
            delete(sourceInternalPath);
        }
    }

    @Override
    public void copy(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        Path source = session.getTempFolder().resolve(sourceInternalPath);
        if (targetFs instanceof ArchiveFileSystem targetArchiveFs && targetArchiveFs.session.getTempFolder().equals(this.session.getTempFolder())) {
            Path target = session.getTempFolder().resolve(targetInternalPath);
            if (Files.isDirectory(source)) {
                FileHelper.copyTree(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            markModified();
        } else if (targetFs instanceof LocalFileSystem) {
            Path target = Paths.get(targetInternalPath);
            if (Files.isDirectory(source)) {
                FileHelper.copyTree(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else if (targetFs instanceof ArchiveFileSystem targetArchiveFs) {
            Path target = targetArchiveFs.session.getTempFolder().resolve(targetInternalPath);
            if (Files.isDirectory(source)) {
                FileHelper.copyTree(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            targetArchiveFs.markModified();
        } else if (targetFs instanceof FtpFileSystem targetFtpFs) {
            // Upload from archive to FTP
            List<String> uploadCmd = targetFtpFs.createBaseCurlCommand();
            uploadCmd.add("-T");
            uploadCmd.add(source.toString());
            uploadCmd.add(targetFtpFs.getOptions().getFullUrl(targetInternalPath));
            targetFtpFs.runCurl(uploadCmd);
        }
    }

    @Override
    public String getInternalPath(FileItem item) {
        Path fullPath = Paths.get(item.getFullPath());
        Path tempFolder = session.getTempFolder();
        if (fullPath.startsWith(tempFolder)) {
            return tempFolder.relativize(fullPath).toString();
        }
        return item.getName(); // Fallback if item is not in temp folder
    }

    @Override
    public void rename(String oldInternalPath, String newInternalPath) throws IOException {
        checkReadOnly();
        Path oldPath = session.getTempFolder().resolve(oldInternalPath);
        Path newPath = session.getTempFolder().resolve(newInternalPath);
        Files.move(oldPath, newPath);
        markModified();
    }

    @Override
    public void makeDirectory(String internalPath) throws IOException {
        checkReadOnly();
        Path path = session.getTempFolder().resolve(internalPath);
        Files.createDirectories(path);
        markModified();
    }

    @Override
    public void makeFile(String internalPath) throws IOException {
        checkReadOnly();
        Path path = session.getTempFolder().resolve(internalPath);
        Files.createFile(path);
        markModified();
    }

    @Override
    public void close() throws IOException {
        archiveManager.closeArchive(session);
    }

    @Override
    public boolean needsRepack() {
        return session.isNeedsRepack();
    }

    @Override
    public void markModified() {
        if (!isReadOnly()) {
            session.setNeedsRepack(true);
        }
    }

    private void checkReadOnly() throws IOException {
        if (isReadOnly()) {
            throw new IOException("Cannot modify a read-only archive.");
        }
    }
    
    public ArchiveSession getSession() {
        return session;
    }
}
