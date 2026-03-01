package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.ArchiveManager;
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
            tempPath = tempPath.resolve(internalPath);
        }
        
        File folder = tempPath.toFile();
        File[] files = folder.listFiles();
        List<FileItem> items = new ArrayList<>();

        // Add ".." entry
        // We need a special way to represent the "up" action in archives
        // But for now let's use ArchiveParentItem if we want to maintain compatibility
        // Or just a regular FileItem with ".."
        items.add(new FileItem(folder, ".."));

        if (files != null) {
            for (File f : files) {
                items.add(new FileItem(f));
            }
        }
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
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            markModified();
        } else if (targetFs instanceof LocalFileSystem) {
            Path target = Paths.get(targetInternalPath);
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else if (targetFs instanceof ArchiveFileSystem targetArchiveFs) {
            Path target = targetArchiveFs.session.getTempFolder().resolve(targetInternalPath);
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            targetArchiveFs.markModified();
        }
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (var walk = Files.walk(sourceDir)) {
            walk.forEach(path -> {
                try {
                    Path relative = sourceDir.relativize(path);
                    Path target = targetDir.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
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
    public boolean isVirtualFolder(FileItem item) {
        // We don't support nested archives in the same VFileSystem instance
        // but VfsManager can handle entering a nested archive.
        if (item == null || item.isDirectory()) {
            return false;
        }
        String filename = item.getName();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            String ext = filename.substring(lastDot + 1).toLowerCase();
            return ArchiveMode.fromExtension(ext) != null;
        }
        return false;
    }

    @Override
    public VFileSystem enterVirtualFolder(FileItem item) throws IOException {
        // Again, VfsManager will handle this
        return null;
    }

    @Override
    public void repack() throws IOException {
        if (needsRepack()) {
            archiveManager.closeArchive(session);
            // After repacking, the session is closed and temp folder deleted.
            // If we want to keep using it, we might need to re-open or change how closeArchive works.
            // For now, let's assume repack happens on exit or when explicitly requested.
        }
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
