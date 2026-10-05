package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pack (7-Zip), Unpack (7-Zip) and Extract All (UniExtract) with the apps.json tools. Sources come from the focused
 * pane and results go to the other pane; archive and FTP sides go through local temp copies.
 */
public class ArchiveOperations {
    private static final Logger log = LoggerFactory.getLogger(ArchiveOperations.class);

    private final FilesPanesHelper panes;
    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public ArchiveOperations(FilesPanesHelper panes, AppRegistry registry, ExternalToolRunner runner) {
        this.panes = panes;
        this.registry = registry;
        this.runner = runner;
    }

    /** Packs {@code items} into {@code archivePath}; non-local items are copied out under their own names first. */
    public void pack(List<FileItem> items, String archivePath) throws IOException {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        List<Path> tempPaths = new ArrayList<>();
        List<String> localPathsToPack = new ArrayList<>();

        Path stagingDir = null;
        for (FileItem item : items) {
            if ("..".equals(item.getPresentableFilename())) {
                continue;
            }
            if (sourceFs instanceof LocalFileSystem) {
                localPathsToPack.add(item.getFullPath());
                continue;
            }
            if (stagingDir == null) {
                stagingDir = AppTempDir.createTempDirectory("acommander_pack_");
                tempPaths.add(stagingDir);
            }
            Path staged = stagingDir.resolve(item.getName()).normalize();
            if (!stagingDir.equals(staged.getParent())) {
                throw new IOException("Can't pack an item with this name: " + item.getName());
            }
            sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), staged.toString());
            localPathsToPack.add(staged.toString());
        }
        if (localPathsToPack.isEmpty()) {
            log.info("No valid files to pack.");
            return;
        }

        String localArchivePath = archivePath;
        boolean uploadRequired = !(targetFs instanceof LocalFileSystem);
        if (uploadRequired) {
            Path tempArchive = AppTempDir.createTempFile("acommander_pack_target_", "_" + new File(archivePath).getName());
            tempArchive.toFile().delete(); // 7-Zip must create it
            tempPaths.add(tempArchive);
            localArchivePath = tempArchive.toString();
        }

        ActionDefinition action = registry.requireAction("pack");
        List<String> command = ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), panes,
                Map.of("${archiveFile}", localArchivePath), localPathsToPack);
        String finalLocalArchivePath = localArchivePath;
        runner.reportFailure(runner.runExecutable(command, true).thenRun(() -> {
            if (uploadRequired) {
                try {
                    new LocalFileSystem("").copy(finalLocalArchivePath, targetFs,
                            targetFs.getInternalPath(new FileItem(Path.of(archivePath))));
                } catch (IOException e) {
                    throw new UncheckedIOException("The archive was created but could not be uploaded to " + archivePath, e);
                }
            }
            tempPaths.forEach(FileHelper::deleteQuietly);
            panes.refreshFileListViews();
        }), "Pack");
        log.debug("Archiving process started for: {}", archivePath);
    }

    /** Unpacks a 7-Zip archive into {@code destinationPath}; throws for a file 7-Zip can't unpack. */
    public void unpack(FileItem archive, String destinationPath) throws IOException {
        if ("..".equals(archive.getPresentableFilename())) {
            return;
        }
        if (archive.isDirectory() || !ArchiveMode.isUnpackable(archive.extension())) {
            log.warn("Unpack rejected file '{}': extension '{}' is not a supported archive format",
                    archive.getName(), archive.extension());
            throw new IllegalArgumentException("The selected file is not a supported archive: " + archive.getName());
        }
        unpackWith("unpack", "Unpack", archive, destinationPath);
    }

    /** Extracts anything UniExtract can open (installers, archives, …) into {@code destinationPath}. */
    public void extractAll(FileItem file, String destinationPath) throws IOException {
        if ("..".equals(file.getPresentableFilename())) {
            return;
        }
        unpackWith("extractAll", "Extract All", file, destinationPath);
    }

    /** Unpack and Extract All differ only in the tool; remote sources and targets go through local temp copies. */
    private void unpackWith(String actionId, String title, FileItem selectedItem, String destinationPath) throws IOException {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();

        Path archiveToUnpack = selectedItem.getPath();
        boolean isTempArchive = !(sourceFs instanceof LocalFileSystem);
        if (isTempArchive) {
            archiveToUnpack = AppTempDir.createTempFile("acommander_unpack_", "_" + selectedItem.getName());
            sourceFs.copy(sourceFs.getInternalPath(selectedItem), new LocalFileSystem(""), archiveToUnpack.toString());
        }

        String localDestPath = destinationPath;
        Path tempDestDir = null;
        if (!(targetFs instanceof LocalFileSystem)) {
            tempDestDir = AppTempDir.createTempDirectory("acommander_unpack_dest_");
            localDestPath = tempDestDir.toString();
        }

        ActionDefinition action = registry.requireAction(actionId);
        List<String> command = ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), panes,
                Map.of("${destinationPath}", localDestPath), List.of(archiveToUnpack.toAbsolutePath().toString()));

        Path finalArchive = archiveToUnpack;
        Path finalTempDestDir = tempDestDir;
        runner.reportFailure(runner.runExecutable(command, true).thenRun(() -> {
            if (finalTempDestDir != null) {
                File[] files = finalTempDestDir.toFile().listFiles();
                try {
                    if (files != null) {
                        for (File f : files) {
                            uploadRecursive(f, targetFs, destinationPath);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("The files were unpacked but could not be uploaded to " + destinationPath, e);
                }
                FileHelper.deleteQuietly(finalTempDestDir);
            }
            if (isTempArchive) {
                FileHelper.deleteQuietly(finalArchive);
            }
            panes.refreshFileListViews();
        }), title);
        log.debug("{} started for: {}", title, selectedItem.getName());
    }

    private static void uploadRecursive(File source, VFileSystem targetFs, String targetInternalDir) throws IOException {
        String targetPath = targetInternalDir + (targetInternalDir.endsWith("/") || targetInternalDir.endsWith("\\") ? "" : "/") + source.getName();
        if (source.isDirectory()) {
            targetFs.makeDirectory(targetPath);
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    uploadRecursive(child, targetFs, targetPath);
                }
            }
        } else {
            new LocalFileSystem("").copy(source.getAbsolutePath(), targetFs, targetPath);
        }
    }
}
