package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.model.FileItem;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of VFileSystem for the local file system.
 */
public class LocalFileSystem implements VFileSystem {
    private final String rootPath;

    public LocalFileSystem(String rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public String getIdentifier() {
        return "local";
    }

    @Override
    public String getDisplayName() {
        return rootPath;
    }

    @Override
    public List<FileItem> listContents(String internalPath) throws IOException {
        Path folder;
        try {
            folder = Path.of(internalPath);
        } catch (InvalidPathException e) {
            throw new IOException("Not a valid folder: " + internalPath, e);
        }
        List<FileItem> items = new ArrayList<>();
        if (folder.getParent() != null) {
            items.add(new FileItem(folder, ".."));
        }
        addEntries(folder, items);
        return items;
    }

    /**
     * Adds each entry of {@code folder}; an unreadable or missing folder adds none. Entries come straight from the
     * directory listing, so a name Windows would reject when parsed (bad media) still lists.
     */
    static void addEntries(Path folder, List<FileItem> items) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                items.add(new FileItem(entry));
            }
        } catch (IOException | DirectoryIteratorException e) {
            // The pane shows whatever was read before the failure.
        }
    }

    @Override
    public boolean isReadOnly() {
        return false; // Local FS is generally read-write, though OS permissions may apply
    }

    @Override
    public void delete(String internalPath) throws IOException {
        Path path = Paths.get(internalPath);
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
    }

    @Override
    public void move(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        if (targetFs instanceof LocalFileSystem) {
            Files.move(Paths.get(sourceInternalPath), Paths.get(targetInternalPath), StandardCopyOption.REPLACE_EXISTING);
        } else {
            copy(sourceInternalPath, targetFs, targetInternalPath);
            delete(sourceInternalPath);
        }
    }

    @Override
    public void copy(String sourceInternalPath, VFileSystem targetFs, String targetInternalPath) throws IOException {
        Path source = Paths.get(sourceInternalPath);
        if (targetFs instanceof LocalFileSystem) {
            if (Files.isDirectory(source)) {
                FileHelper.copyTree(source, Paths.get(targetInternalPath), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, Paths.get(targetInternalPath), StandardCopyOption.REPLACE_EXISTING);
            }
        } else if (targetFs instanceof ArchiveFileSystem archiveFs) {
            Path targetPathInTemp = archiveFs.getSession().getTempFolder().resolve(targetInternalPath);
            if (Files.isDirectory(source)) {
                FileHelper.copyTree(source, targetPathInTemp, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, targetPathInTemp, StandardCopyOption.REPLACE_EXISTING);
            }
            archiveFs.markModified();
        } else if (targetFs instanceof FtpFileSystem targetFtpFs) {
            // Upload to FTP
            targetInternalPath = targetFtpFs.sanitizePath(targetInternalPath);
            List<String> uploadCmd = targetFtpFs.createBaseCurlCommand();
            uploadCmd.add("-T");
            uploadCmd.add(sourceInternalPath);
            uploadCmd.add(targetFtpFs.getOptions().getFullUrl(targetInternalPath));
            targetFtpFs.runCurl(uploadCmd);
        }
    }

    @Override
    public String getInternalPath(FileItem item) {
        return item.getFullPath();
    }

    @Override
    public void rename(String oldInternalPath, String newInternalPath) throws IOException {
        Files.move(Paths.get(oldInternalPath), Paths.get(newInternalPath));
    }

    @Override
    public void makeDirectory(String internalPath) throws IOException {
        Files.createDirectories(Paths.get(internalPath));
    }

    @Override
    public void makeFile(String internalPath) throws IOException {
        Files.createFile(Paths.get(internalPath));
    }

    @Override
    public String getSeparator() {
        return "\\";
    }

    @Override
    public void close() throws IOException {
        // No-op for local FS
    }

    @Override
    public boolean needsRepack() {
        return false;
    }

    @Override
    public void markModified() {
        // No-op for local FS
    }
}
