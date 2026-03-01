package org.chaiware.acommander.vfs;

import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.FileItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        File folder = new File(internalPath);
        File[] files = folder.listFiles();
        List<FileItem> items = new ArrayList<>();

        if (folder.getParentFile() != null) {
            items.add(new FileItem(folder, ".."));
        }

        if (files != null) {
            for (File f : files) {
                items.add(new FileItem(f));
            }
        }
        return items;
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
                copyDirectory(source, Paths.get(targetInternalPath));
            } else {
                Files.copy(source, Paths.get(targetInternalPath), StandardCopyOption.REPLACE_EXISTING);
            }
        } else if (targetFs instanceof ArchiveFileSystem archiveFs) {
            Path targetPathInTemp = archiveFs.getSession().getTempFolder().resolve(targetInternalPath);
            if (Files.isDirectory(source)) {
                copyDirectory(source, targetPathInTemp);
            } else {
                Files.copy(source, targetPathInTemp, StandardCopyOption.REPLACE_EXISTING);
            }
            archiveFs.markModified();
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
    public boolean isVirtualFolder(FileItem item) {
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
        // This will be handled by VfsManager to create an ArchiveFileSystem
        return null; 
    }

    @Override
    public void repack() throws IOException {
        // No-op for local FS
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
