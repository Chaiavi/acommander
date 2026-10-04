package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Rename, copy, move, delete, new folder / file, view, edit, terminal and explorer on the panes. Local copies and
 * cross-drive moves run the apps.json tools (FastCopy); archive and FTP panes go through the VFS.
 */
public class FileOperations {
    private static final Logger log = LoggerFactory.getLogger(FileOperations.class);

    private final FilesPanesHelper panes;
    private final AppRegistry registry;
    private final ExternalToolRunner runner;

    public FileOperations(FilesPanesHelper panes, AppRegistry registry, ExternalToolRunner runner) {
        this.panes = panes;
        this.registry = registry;
        this.runner = runner;
    }

    /** {@code items} without the ".." entry. */
    public List<FileItem> filterValidItems(List<FileItem> items) {
        return items.stream().filter(item -> !isParentEntry(item)).collect(Collectors.toList());
    }

    /** One item is renamed in place; several open the multi-rename tool. */
    public void rename(List<FileItem> items, String newFilename) throws IOException {
        List<FileItem> validItems = filterValidItems(items);
        if (validItems.isEmpty()) {
            return;
        }
        if (validItems.size() > 1) {
            runner.reportFailure(runner.runExecutable(command("multiRename", Map.of(), fullPaths(validItems)), true), "Multi Rename");
            return;
        }
        FileItem item = validItems.getFirst();
        VFileSystem fs = panes.getFocusedFileSystem();
        String oldInternalPath = fs.getInternalPath(item);
        int lastSeparator = oldInternalPath.lastIndexOf(fs.getSeparator());
        fs.rename(oldInternalPath, oldInternalPath.substring(0, lastSeparator + 1) + newFilename);
        panes.refreshFileListViews();
        log.debug("Renamed: {} to {}", item.getName(), newFilename);
    }

    /** Opens the viewer; an archive or FTP file is viewed from a temp copy. */
    public void view(FileItem item) throws IOException {
        if (isParentEntry(item)) {
            return;
        }
        VFileSystem fs = panes.getFocusedFileSystem();
        File fileToView = localCopy(fs, item, "acommander_view_");
        boolean isTemp = !(fs instanceof LocalFileSystem);
        runner.reportFailure(runner.runExecutable(command("view", Map.of(), List.of(fileToView.getAbsolutePath())), false)
                .thenRun(() -> {
                    if (isTemp) {
                        fileToView.delete();
                    }
                }), "View");
        log.debug("Viewed: {}", item.getName());
    }

    /** Opens the editor; an archive or FTP file is edited in a temp copy that is saved back when the editor closes. */
    public void edit(FileItem item) throws IOException {
        if (isParentEntry(item)) {
            return;
        }
        VFileSystem fs = panes.getFocusedFileSystem();
        File fileToEdit = localCopy(fs, item, "acommander_edit_");
        boolean isTemp = !(fs instanceof LocalFileSystem);
        String internalPath = fs.getInternalPath(item);
        runner.reportFailure(runner.runExecutable(command("edit", Map.of(), List.of(fileToEdit.getAbsolutePath())), false)
                .thenRun(() -> {
                    if (isTemp) {
                        try {
                            new LocalFileSystem("").copy(fileToEdit.getAbsolutePath(), fs, internalPath);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Your edit could not be saved to " + internalPath
                                    + ". The edited copy stays at " + fileToEdit + " until ACommander closes.", e);
                        }
                        fileToEdit.delete();
                    }
                    fs.markModified();
                }), "Edit");
        log.debug("Edited: {}", item.getName());
    }

    public void copy(FileItem item, String targetFolder) throws IOException {
        if (isParentEntry(item)) {
            return;
        }
        if (viaVfs()) {
            vfsCopy(item, targetFolder);
        } else {
            copyWithTool(item, targetFolder);
        }
        log.debug("Copied: {} To: {}", item, targetFolder);
    }

    /** Copies several items; local to local is one tool run, anything else goes item by item and names the failures. */
    public void copyBatch(List<FileItem> items, String targetFolder) throws Exception {
        List<FileItem> validItems = filterValidItems(items);
        if (validItems.isEmpty()) {
            return;
        }
        boolean viaVfs = viaVfs();
        if (!viaVfs && validItems.size() > 1) {
            List<String> command = command("copy", Map.of("${targetFolder}", targetFolder), fullPaths(validItems));
            log.debug("Built batch copy command: {}", command);
            runner.reportFailure(runner.runExecutable(command, true).thenAccept(output -> {
                markTargetArchiveForRepack(targetFolder);
                verifyBatchCopy(validItems, targetFolder, command);
                log.debug("Copied {} items To: {} using command", validItems.size(), targetFolder);
            }), "Copy");
            return;
        }

        // Item by item: no command-line length limit, and one bad name doesn't stop the rest
        List<String> failedNames = new ArrayList<>();
        Exception firstFailure = null;
        for (FileItem item : validItems) {
            try {
                if (viaVfs) {
                    vfsCopy(item, targetFolder);
                } else {
                    copyWithTool(item, targetFolder);
                }
            } catch (Exception e) {
                log.error("Failed to copy item: {}", item.getName(), e);
                failedNames.add(item.getName());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (!failedNames.isEmpty()) {
            throw new Exception("Failed copying " + failedNames.size() + " item(s): " + String.join(", ", failedNames), firstFailure);
        }
        log.debug("Copied {} items To: {}", validItems.size(), targetFolder);
    }

    /** Same drive and VFS moves are renames; a local cross-drive move runs the move tool in the background. */
    public void move(FileItem item, String targetFolder) throws IOException {
        if (isParentEntry(item)) {
            return;
        }
        if (viaVfs() || sameDrive(item, targetFolder)) {
            vfsMove(item, targetFolder);
            return;
        }
        runner.reportFailure(runner.runExecutable(command("move", Map.of("${targetFolder}", targetFolder), List.of(item.getFullPath())), true)
                .thenAccept(output -> log.debug("Moved: {} To: {}", item, targetFolder)), "Move");
    }

    /** Moves several items one by one, waiting for each; throws naming every item that failed. */
    public void moveBatch(List<FileItem> items, String targetFolder) throws Exception {
        List<FileItem> validItems = filterValidItems(items);
        if (validItems.isEmpty()) {
            log.debug("Move batch skipped: no valid items");
            return;
        }
        log.info("Starting move batch: {} item(s), target={}", validItems.size(), targetFolder);
        List<String> failedNames = new ArrayList<>();
        Exception firstFailure = null;
        boolean viaVfs = viaVfs();
        for (FileItem item : validItems) {
            try {
                if (viaVfs || sameDrive(item, targetFolder)) {
                    vfsMove(item, targetFolder);
                } else {
                    runner.runExecutable(command("move", Map.of("${targetFolder}", targetFolder), List.of(item.getFullPath())), true).join();
                }
            } catch (CompletionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                log.error("Move batch item failed: {} -> {}", item.getFullPath(), targetFolder, cause);
                failedNames.add(item.getName());
                if (firstFailure == null) {
                    firstFailure = cause instanceof Exception causeException ? causeException : ex;
                }
            } catch (Exception ex) {
                log.error("Move batch item failed: {} -> {}", item.getFullPath(), targetFolder, ex);
                failedNames.add(item.getName());
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }
        panes.refreshFileListViews();
        if (!failedNames.isEmpty()) {
            throw new Exception("Failed moving " + failedNames.size() + " item(s): " + String.join(", ", failedNames), firstFailure);
        }
        log.info("Move batch completed successfully: {} item(s) moved to {}", validItems.size(), targetFolder);
    }

    public void mkdir(String parentDir, String newDirName) throws IOException {
        VFileSystem fs = panes.getFocusedFileSystem();
        fs.makeDirectory(ClipboardTransfer.targetInternalPath(fs, parentDir, newDirName, true));
        panes.refreshFileListViews();
        log.debug("Created Directory: {}", newDirName);
    }

    public void mkFile(String parentDir, String newFileName) throws IOException {
        VFileSystem fs = panes.getFocusedFileSystem();
        fs.makeFile(ClipboardTransfer.targetInternalPath(fs, parentDir, newFileName, false));
        panes.refreshFileListViews();
        log.debug("Created File: {}", newFileName);
    }

    /** Deletes through the VFS; local items that fail (locked) go to the unlock-and-delete tool. */
    public void delete(List<FileItem> items) {
        List<FileItem> validItems = filterValidItems(items);
        VFileSystem fs = panes.getFocusedFileSystem();
        if (validItems.isEmpty() || fs == null) {
            return;
        }
        List<FileItem> failedDeletes = new ArrayList<>();
        for (FileItem item : validItems) {
            try {
                fs.delete(fs.getInternalPath(item));
                log.info("Deleted: {}", item.getFullPath());
            } catch (Exception e) {
                log.error("Failed deleting: {}", item.getFullPath(), e);
                failedDeletes.add(item);
            }
        }
        if (!failedDeletes.isEmpty() && fs instanceof LocalFileSystem) {
            log.info("Failed to delete {} files, attempting to unlock them so you can delete them all", failedDeletes.size());
            runner.reportFailure(runner.runExecutable(command("unlockDelete", Map.of(), fullPaths(failedDeletes)), true), "Unlock and Delete");
        }
        panes.refreshFileListViews();
    }

    /** Overwrites, then deletes (SDelete). */
    public void wipeDelete(List<FileItem> items) {
        List<FileItem> validItems = filterValidItems(items);
        if (validItems.isEmpty()) {
            return;
        }
        runner.reportFailure(runner.runExecutable(command("wipeDelete", Map.of(), fullPaths(validItems)), true), "Wipe Delete");
        log.debug("Deleted & Wiped: {}", validItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    public void openTerminal(String openHerePath) {
        List<String> command = List.of("cmd", "/c", "start", "powershell", "-NoExit", "-Command", "cd '" + openHerePath + "'");
        runner.runExecutable(command, false)
                .thenAccept(output -> log.debug("Opened Powershell Here: {}", openHerePath))
                .exceptionally(throwable -> {
                    log.warn("PowerShell failed, trying Command Prompt: {}", throwable.getMessage());
                    List<String> fallbackCommand = List.of("cmd", "/c", "start", "cmd", "/k", "cd /d " + openHerePath);
                    runner.reportFailure(runner.runExecutable(fallbackCommand, false), "Open Terminal");
                    return null;
                });
    }

    /** Opens Explorer on the folder (a file's own folder); explorer.exe exits 1 even when it worked. */
    public void openExplorer(String openHerePath) {
        if (openHerePath == null || openHerePath.isBlank()) {
            log.warn("Open Explorer Here skipped: path is blank");
            return;
        }
        File target = new File(openHerePath);
        if (target.isFile()) {
            target = target.getParentFile();
        }
        if (target == null || !target.exists()) {
            log.warn("Open Explorer Here skipped: path does not exist: {}", openHerePath);
            return;
        }
        runner.reportFailure(runner.runExecutable(List.of("explorer.exe", target.getAbsolutePath()), false, Set.of(1)), "Open Explorer");
    }

    private boolean viaVfs() {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        return sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem
                || sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem;
    }

    private void vfsCopy(FileItem item, String targetFolder) throws IOException {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        sourceFs.copy(sourceFs.getInternalPath(item), targetFs, targetFs.getInternalPath(new FileItem(new File(targetFolder, item.getName()))));
        panes.refreshFileListViews();
    }

    private void vfsMove(FileItem item, String targetFolder) throws IOException {
        VFileSystem sourceFs = panes.getFocusedFileSystem();
        VFileSystem targetFs = panes.getUnfocusedFileSystem();
        sourceFs.move(sourceFs.getInternalPath(item), targetFs, targetFs.getInternalPath(new FileItem(new File(targetFolder, item.getName()))));
        panes.refreshFileListViews();
    }

    private void copyWithTool(FileItem item, String targetFolder) {
        List<String> command = command("copy", Map.of("${targetFolder}", targetFolder), List.of(item.getFullPath()));
        runner.reportFailure(runner.runExecutable(command, true).thenRun(() -> markTargetArchiveForRepack(targetFolder)), "Copy");
    }

    /** Marks an archive for repack when {@code targetFolder} is inside its extracted temp folder. */
    private void markTargetArchiveForRepack(String targetFolder) {
        for (FilesPanesHelper.FocusSide side : FilesPanesHelper.FocusSide.values()) {
            if (panes.getFileSystem(side) instanceof ArchiveFileSystem archiveFs
                    && targetFolder.startsWith(archiveFs.getSession().getTempFolderPath().toString())) {
                archiveFs.markModified();
                return;
            }
        }
    }

    /** The copy tool can exit 0 and still skip files; throws naming the ones missing in {@code targetFolder}. */
    private static void verifyBatchCopy(List<FileItem> copiedItems, String targetFolder, List<String> command) {
        Path targetPath;
        try {
            targetPath = Paths.get(targetFolder);
        } catch (Exception ex) {
            log.warn("Skipping batch copy verification due to invalid target path: {}", targetFolder, ex);
            return;
        }
        List<String> missing = copiedItems.stream()
                .map(FileItem::getName)
                .filter(name -> !Files.exists(targetPath.resolve(name)))
                .toList();
        if (!missing.isEmpty()) {
            log.error("Batch copy reported success but {} item(s) are missing in target. target={} missing={} command={}",
                    missing.size(), targetFolder, missing, command);
            throw new IllegalStateException("The copy tool reported success, but these are missing in " + targetFolder
                    + ": " + String.join(", ", missing));
        }
    }

    private static boolean sameDrive(FileItem item, String targetFolder) {
        try {
            return item.getFile().toPath().getRoot().toString().equalsIgnoreCase(Paths.get(targetFolder).getRoot().toString());
        } catch (Exception e) {
            return false;
        }
    }

    private static File localCopy(VFileSystem fs, FileItem item, String prefix) throws IOException {
        if (fs instanceof LocalFileSystem) {
            return item.getFile();
        }
        File copy = AppTempDir.createTempFile(prefix, "_" + item.getName()).toFile();
        fs.copy(fs.getInternalPath(item), new LocalFileSystem(""), copy.getAbsolutePath());
        return copy;
    }

    private List<String> command(String actionId, Map<String, String> values, List<String> selectedFiles) {
        ActionDefinition action = registry.requireAction(actionId);
        return ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), panes, values, selectedFiles);
    }

    private static List<String> fullPaths(List<FileItem> items) {
        return items.stream().map(FileItem::getFullPath).collect(Collectors.toList());
    }

    private static boolean isParentEntry(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }
}
