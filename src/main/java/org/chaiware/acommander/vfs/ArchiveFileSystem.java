package org.chaiware.acommander.vfs;

import org.chaiware.acommander.commands.Operation;
import org.chaiware.acommander.helpers.AppTempDir;
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
    /** The archive (folder) this archive file was opened from, or null for an archive on disk. */
    private final ArchiveFileSystem outer;

    public ArchiveFileSystem(ArchiveSession session, ArchiveManager archiveManager) {
        this(session, archiveManager, null);
    }

    public ArchiveFileSystem(ArchiveSession session, ArchiveManager archiveManager, ArchiveFileSystem outer) {
        this.session = session;
        this.archiveManager = archiveManager;
        this.outer = outer;
    }

    /** The same archive at another folder ({@code session} shares this one's root). */
    public ArchiveFileSystem at(ArchiveSession session) {
        return new ArchiveFileSystem(session, archiveManager, outer);
    }

    public ArchiveFileSystem getOuter() {
        return outer;
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
        // An archive inside a read-only one can't be saved back either.
        return session.getMode() == ArchiveMode.READ_ONLY || (outer != null && outer.isReadOnly());
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
            Operation.checkNotStopped();
            delete(sourceInternalPath);
        }
    }

    /** The extracted folder is on disk, so this is a local copy from it, to any target. */
    @Override
    public void copy(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        new LocalFileSystem("").copy(session.getTempFolder().resolve(sourceInternalPath).toString(), targetFs, targetInternalPath);
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

    /** Closes this archive and every archive it was opened from (the pane leaves them all). */
    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            closeOwn();
        } catch (IOException e) {
            failure = e;
        }
        if (outer != null) {
            try {
                outer.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    IOException both = new IOException(failure.getMessage() + "\n" + e.getMessage(), failure);
                    both.addSuppressed(e);
                    failure = both;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Repacks and closes only this archive; it is a file in the outer archive, so a change there marks that too. */
    public void closeOwn() throws IOException {
        boolean changed = session.isNeedsRepack();
        try {
            archiveManager.closeArchive(session);
        } finally {
            if (changed && outer != null) {
                outer.markModified();
            }
        }
    }

    @Override
    public boolean needsRepack() {
        return session.isNeedsRepack();
    }

    @Override
    public void markModified() {
        if (isReadOnly() || session.isNeedsRepack()) {
            return;
        }
        session.setNeedsRepack(true);
        try {
            AppTempDir.retain(session.getTempFolder());
        } catch (IOException e) {
            logger.warn("Could not mark {} as holding unsaved edits", session.getTempFolder(), e);
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
