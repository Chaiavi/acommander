package org.chaiware.acommander.commands;

import javafx.application.Platform;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class CommandsAdvancedImpl extends ACommands {
    ACommands commandsSimpleImpl;
    private final AppRegistry appRegistry;

    public CommandsAdvancedImpl(FilesPanesHelper fileListsLoader, AppRegistry appRegistry) {
        super(fileListsLoader);
        commandsSimpleImpl = new CommandsSimpleImpl(fileListsLoader);
        this.appRegistry = appRegistry;
    }

    @Override
    public void setExternalCommandListener(ExternalCommandListener externalCommandListener) {
        super.setExternalCommandListener(externalCommandListener);
        commandsSimpleImpl.setExternalCommandListener(externalCommandListener);
    }

    @Override
    public int stopRunningExternalCommands() {
        int stoppedByAdvanced = super.stopRunningExternalCommands();
        int stoppedBySimple = commandsSimpleImpl.stopRunningExternalCommands();
        return stoppedByAdvanced + stoppedBySimple;
    }

    @Override
    protected void doRename(List<FileItem> validItems, String newFilename) throws Exception {
        if (validItems.size() == 1) {
            commandsSimpleImpl.doRename(validItems, newFilename);
        } else {
            ActionDefinition action = requireAction("multiRename");
            List<String> selectedFiles = validItems.stream()
                    .map(FileItem::getFullPath)
                    .collect(Collectors.toList());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of(),
                    selectedFiles
            );
            runExecutable(command, true);
            log.debug("Finished Multi File Rename Process");
        }
    }

    @Override
    protected void doView(FileItem fileItem) {
        try {
            VFileSystem fs = fileListsLoader.getFocusedFileSystem();
            File fileToView;
            boolean isTemp = false;

            if (fs instanceof LocalFileSystem) {
                fileToView = fileItem.getFile();
            } else {
                // Download to temp
                fileToView = File.createTempFile("acommander_view_", "_" + fileItem.getName());
                fileToView.deleteOnExit();
                isTemp = true;
                fs.copy(fs.getInternalPath(fileItem), new LocalFileSystem(""), fileToView.getAbsolutePath());
            }

            ActionDefinition action = requireAction("view");
            List<String> selectedFiles = List.of(fileToView.getAbsolutePath());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of(),
                    selectedFiles
            );
            
            final boolean finalIsTemp = isTemp;
            final File finalFileToView = fileToView;
            runExecutable(command, false).thenRun(() -> {
                if (finalIsTemp) {
                    finalFileToView.delete();
                }
            });
            log.debug("Viewed: {}", fileItem.getName());
        } catch (Exception e) {
            log.error("Failed to view file: {}", fileItem.getName(), e);
        }
    }

    @Override
    protected void doEdit(FileItem fileItem) {
        try {
            VFileSystem fs = fileListsLoader.getFocusedFileSystem();
            File fileToEdit;
            boolean isTemp = false;

            if (fs instanceof LocalFileSystem) {
                fileToEdit = fileItem.getFile();
            } else {
                // Download to temp
                fileToEdit = File.createTempFile("acommander_edit_", "_" + fileItem.getName());
                fileToEdit.deleteOnExit();
                isTemp = true;
                fs.copy(fs.getInternalPath(fileItem), new LocalFileSystem(""), fileToEdit.getAbsolutePath());
            }

            ActionDefinition action = requireAction("edit");
            List<String> selectedFiles = List.of(fileToEdit.getAbsolutePath());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of(),
                    selectedFiles
            );
            
            final boolean finalIsTemp = isTemp;
            final File finalFileToEdit = fileToEdit;
            final String internalPath = fs.getInternalPath(fileItem);
            
            runExecutable(command, false).thenRun(() -> {
                try {
                    if (finalIsTemp) {
                        // Upload back
                        new LocalFileSystem("").copy(finalFileToEdit.getAbsolutePath(), fs, internalPath);
                        finalFileToEdit.delete();
                    }
                    // Only mark for repack if in a read-write archive
                    if (fs != null) {
                        fs.markModified();
                    }
                } catch (IOException e) {
                    log.error("Failed to upload edited file back to VFS", e);
                }
            });
            log.debug("Edited: {}", fileItem.getName());
        } catch (Exception e) {
            log.error("Failed to edit file: {}", fileItem.getName(), e);
        }
    }

    @Override
    protected void doCopy(FileItem sourceFile, String targetFolder) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();
            
            // If either side is an archive or FTP, use the VFS-based copy from simple implementation
            if (sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
                sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem) {
                commandsSimpleImpl.doCopy(sourceFile, targetFolder);
                return;
            }

            ActionDefinition action = requireAction("copy");
            List<String> selectedFiles = List.of(sourceFile.getFullPath());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${targetFolder}", targetFolder),
                    selectedFiles
            );
            runExecutable(command, true).thenRun(() -> {
                // Mark target archive for repack if target is in archive
                markTargetArchiveForRepack(targetFolder);
            });
            log.debug("Copied: {} To: {}", sourceFile, targetFolder);
        } catch (Exception e) {
            log.error("Failed to copy file: {}", sourceFile.getName(), e);
        }
    }
    
    /**
     * Marks the archive for repack if the target folder is inside an archive temp folder.
     */
    private void markTargetArchiveForRepack(String targetFolder) {
        // Check if either pane has this target folder in its archive session
        for (FilesPanesHelper.FocusSide side : FilesPanesHelper.FocusSide.values()) {
            VFileSystem fs = fileListsLoader.getFileSystem(side);
            if (fs instanceof ArchiveFileSystem archiveFs) {
                if (targetFolder.startsWith(archiveFs.getSession().getTempFolderPath().toString())) {
                    fs.markModified();
                    log.debug("Marked archive for repack (copy target): {}", archiveFs.getSession().getArchivePath());
                    return;
                }
            }
        }
    }

    public void copyBatch(List<FileItem> selectedItems, String targetFolder) {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (validItems.isEmpty()) {
            return;
        }

        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

        if (sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
            sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem) {
            for (FileItem item : validItems) {
                try {
                    commandsSimpleImpl.doCopy(item, targetFolder);
                } catch (Exception e) {
                    log.error("Failed to copy item in batch: {}", item.getName(), e);
                }
            }
            return;
        }

        // If many items and both sides are local, run one batch command.
        if ((sourceFs == null || sourceFs instanceof LocalFileSystem) && (targetFs == null || targetFs instanceof LocalFileSystem) && validItems.size() > 1) {
            ActionDefinition action = requireAction("copy");

            List<String> selectedFilesList = validItems.stream()
                    .map(FileItem::getFullPath)
                    .collect(Collectors.toList());

            // Preserve action-defined argument order (important for tools like FastCopy).
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${targetFolder}", targetFolder),
                    selectedFilesList
            );

            log.debug("Built batch copy command: {}", command);
            runExecutable(command, true)
                    .thenAccept(output -> {
                        markTargetArchiveForRepack(targetFolder);
                        logBatchCopyVerification(validItems, targetFolder, command);
                        log.debug("Copied {} items To: {} using command", validItems.size(), targetFolder);
                    })
                    .exceptionally(ex -> {
                        log.error(
                                "Batch copy failed for {} item(s). target={} command={}",
                                validItems.size(),
                                targetFolder,
                                command,
                                ex
                        );
                        return null;
                    });
            return;
        }

        // Copy each item individually to avoid command line length issues with many files
        // or files with special characters (e.g., Hebrew, Unicode)
        for (FileItem item : validItems) {
            try {
                copyItemIndividually(item, targetFolder);
            } catch (Exception e) {
                log.error("Failed to copy item: {}", item.getName(), e);
            }
        }
        log.debug("Copied {} items To: {}", validItems.size(), targetFolder);
    }

    private void logBatchCopyVerification(List<FileItem> copiedItems, String targetFolder, List<String> command) {
        if (copiedItems == null || copiedItems.isEmpty() || targetFolder == null || targetFolder.isBlank()) {
            return;
        }

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
            log.error(
                    "Batch copy reported success but {} item(s) are missing in target. target={} missing={} command={}",
                    missing.size(),
                    targetFolder,
                    missing,
                    command
            );
            return;
        }

        log.debug("Batch copy verification passed for {} item(s) in target {}", copiedItems.size(), targetFolder);
    }

    private void copyItemIndividually(FileItem item, String targetFolder) {
        try {
            ActionDefinition action = requireAction("copy");
            List<String> selectedFiles = List.of(item.getFullPath());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${targetFolder}", targetFolder),
                    selectedFiles
            );
            runExecutable(command, true).thenRun(() -> {
                markTargetArchiveForRepack(targetFolder);
            });
        } catch (Exception e) {
            log.error("Failed to copy item individually: {}", item.getName(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doMove(FileItem sourceFile, String targetFolder) throws Exception {
        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

        // If either side is an archive or FTP, use the VFS-based move from simple implementation
        if (sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
            sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem) {
            commandsSimpleImpl.doMove(sourceFile, targetFolder);
            return;
        }

        try {
            Path sourcePath = sourceFile.getFile().toPath();
            Path targetPath = Paths.get(targetFolder);
            if (sourcePath.getRoot().toString().equalsIgnoreCase(targetPath.getRoot().toString())) {
                // Use FASTEST move in the case of moving file over same drive
                commandsSimpleImpl.doMove(sourceFile, targetFolder);
                return;
            }
        } catch (Exception e) {
            // If Paths.get fails (likely due to VFS path), fall back to external tool move
        }

        ActionDefinition action = requireAction("move");
        List<String> selectedFiles = List.of(sourceFile.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${targetFolder}", targetFolder),
                selectedFiles
        );
        runExecutable(command, true)
                .thenAccept(output -> log.debug("Moved: {} To: {}", sourceFile, targetFolder))
                .exceptionally(ex -> {
                    log.error("Failed to move {} to {} using external tool", sourceFile.getFullPath(), targetFolder, ex);
                    return null;
                });
    }

    public void moveBatch(List<FileItem> selectedItems, String targetFolder) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (validItems.isEmpty()) {
            log.debug("Move batch skipped: no valid items");
            return;
        }

        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();
        log.info(
                "Starting move batch: {} item(s), sourceFs={}, targetFs={}, target={}",
                validItems.size(),
                sourceFs == null ? "<null>" : sourceFs.getIdentifier(),
                targetFs == null ? "<null>" : targetFs.getIdentifier(),
                targetFolder
        );

        List<FileItem> failedItems = new ArrayList<>();
        Exception firstFailure = null;

        for (FileItem item : validItems) {
            try {
                moveBatchItem(item, targetFolder, sourceFs, targetFs);
            } catch (Exception ex) {
                failedItems.add(item);
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                log.error("Move batch item failed: {} -> {}", item.getFullPath(), targetFolder, ex);
            }
        }

        fileListsLoader.refreshFileListViews();
        if (!failedItems.isEmpty()) {
            String failedNames = failedItems.stream().map(FileItem::getName).collect(Collectors.joining(", "));
            throw new Exception("Failed moving " + failedItems.size() + " item(s): " + failedNames, firstFailure);
        }
        log.info("Move batch completed successfully: {} item(s) moved to {}", validItems.size(), targetFolder);
    }

    private void moveBatchItem(FileItem item, String targetFolder, VFileSystem sourceFs, VFileSystem targetFs) throws Exception {
        // For virtual file systems, rely on VFS move directly.
        if (sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
                sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem) {
            commandsSimpleImpl.doMove(item, targetFolder);
            return;
        }

        // Same drive local move is fastest and most reliable via java.nio move.
        try {
            Path sourcePath = item.getFile().toPath();
            Path targetPath = Paths.get(targetFolder);
            if (sourcePath.getRoot().toString().equalsIgnoreCase(targetPath.getRoot().toString())) {
                commandsSimpleImpl.doMove(item, targetFolder);
                return;
            }
        } catch (Exception ex) {
            log.debug("Falling back to external move for {} due to path inspection issue", item.getFullPath(), ex);
        }

        ActionDefinition action = requireAction("move");
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${targetFolder}", targetFolder),
                List.of(item.getFullPath())
        );
        log.debug("Executing external move command for {}: {}", item.getFullPath(), command);
        try {
            runExecutable(command, true).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            throw ex;
        }
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {
        commandsSimpleImpl.mkdir(parentDir, newDirName);
    }

    @Override
    public void mkFile(String parentDir, String newFileName) throws Exception {
        commandsSimpleImpl.mkFile(parentDir, newFileName);
    }

    @Override
    protected void doDelete(List<FileItem> validItems) throws Exception {
        List<FileItem> failedDeletes = new ArrayList<>();
        VFileSystem fs = fileListsLoader.getFocusedFileSystem();
        if (fs == null) {
            log.error("Cannot delete: Focused file system is null");
            return;
        }
        
        for (FileItem selectedItem : validItems) {
            try {
                fs.delete(fs.getInternalPath(selectedItem));
                log.info("Deleted: {}", selectedItem.getFullPath());
            } catch (Exception e) {
                log.error("Failed deleting: {}", selectedItem.getFullPath(), e);
                failedDeletes.add(selectedItem);
            }
        }

        if (!failedDeletes.isEmpty() && fs instanceof LocalFileSystem) {
            log.info("Failed to delete {} files, attempting to unlock them so you can delete them all", failedDeletes.size());
            unlockDelete(failedDeletes);
        }

        fileListsLoader.refreshFileListViews();
    }
    
    @Override
    protected void doUnlockDelete(List<FileItem> validItems) {
        ActionDefinition action = requireAction("unlockDelete");
        List<String> selectedFiles = validItems.stream()
                .map(FileItem::getFullPath)
                .collect(Collectors.toList());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of(),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Unlocked & Deleted: {}", validItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    @Override
    protected void doWipeDelete(List<FileItem> validItems) {
        ActionDefinition action = requireAction("wipeDelete");
        List<String> selectedFiles = validItems.stream()
                .map(FileItem::getFullPath)
                .collect(Collectors.toList());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of(),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Deleted & Wiped: {}", validItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {
        commandsSimpleImpl.openTerminal(openHerePath);
    }

    @Override
    public void openExplorer(String openHerePath) throws Exception {
        commandsSimpleImpl.openExplorer(openHerePath);
    }

    @Override
    public void searchFiles(String sourcePath, String filenameWildcard) throws Exception {
//        List<String> command = new ArrayList<>();
//        command.add(APP_PATH + "search\\SearchMyFiles.exe");
//        command.add("/StartSearch");
//        command.add("/scomma \"%TEMP%\\1.csv\"");
//        command.add("/BaseFolder \"" + sourcePath + "\"");
//        command.add("/FilesWildcard " + filenameWildcard);
//        runExecutable(command, true);
//        log.debug("Searched for: {} under: {}", filenameWildcard, sourcePath);
        commandsSimpleImpl.searchFiles(sourcePath, filenameWildcard);
    }

    @Override
    protected void doPack(List<FileItem> validItems, String archiveFilenameWithPath) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            List<File> tempFiles = new ArrayList<>();
            List<String> localPathsToPack = new ArrayList<>();

            // 1. Prepare source files (download if remote)
            for (FileItem item : validItems) {
                if (sourceFs instanceof LocalFileSystem) {
                    localPathsToPack.add(item.getFullPath());
                } else {
                    if (item.isDirectory()) {
                        log.warn("Skipping directory in remote VFS packing: {}. Recursive packing is not supported for remote systems yet.", item.getName());
                        continue;
                    }
                    File tempFile = File.createTempFile("acommander_pack_", "_" + item.getName());
                    tempFile.deleteOnExit();
                    tempFiles.add(tempFile);
                    sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), tempFile.getAbsolutePath());
                    localPathsToPack.add(tempFile.getAbsolutePath());
                }
            }

            if (localPathsToPack.isEmpty()) {
                log.info("No valid files to pack.");
                return;
            }

            // 2. Prepare target archive path (local or temp)
            String localArchivePath;
            boolean uploadRequired = false;
            if (targetFs instanceof LocalFileSystem) {
                localArchivePath = archiveFilenameWithPath;
            } else {
                File tempArchive = File.createTempFile("acommander_pack_target_", "_" + new File(archiveFilenameWithPath).getName());
                tempArchive.delete(); // Ensure it doesn't exist yet so 7z creates it
                tempArchive.deleteOnExit();
                tempFiles.add(tempArchive);
                localArchivePath = tempArchive.getAbsolutePath();
                uploadRequired = true;
            }

            ActionDefinition action = requireAction("pack");
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${archiveFile}", localArchivePath),
                    localPathsToPack
            );

            final boolean finalUploadRequired = uploadRequired;
            final String finalLocalArchivePath = localArchivePath;

            runExecutable(command, true).thenRun(() -> {
                try {
                    if (finalUploadRequired) {
                        // Upload the created archive back to remote VFS
                        targetFs.copy(finalLocalArchivePath, targetFs, targetFs.getInternalPath(new FileItem(new File(archiveFilenameWithPath))));
                    }
                    // Cleanup temp files
                    for (File f : tempFiles) {
                        f.delete();
                    }
                    // Refresh UI
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (IOException e) {
                    log.error("Failed to upload/cleanup after pack", e);
                }
            });
            log.debug("Archiving process started for: {}", archiveFilenameWithPath);
        } catch (Exception e) {
            log.error("Failed to pack files", e);
        }
    }

    @Override
    protected void doUnpack(FileItem selectedItem, String destinationPath) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            File archiveToUnpack;
            boolean isTempArchive = false;

            // 1. Prepare source archive (download if remote)
            if (sourceFs instanceof LocalFileSystem) {
                archiveToUnpack = selectedItem.getFile();
            } else {
                archiveToUnpack = File.createTempFile("acommander_unpack_", "_" + selectedItem.getName());
                archiveToUnpack.deleteOnExit();
                isTempArchive = true;
                sourceFs.copy(sourceFs.getInternalPath(selectedItem), new LocalFileSystem(""), archiveToUnpack.getAbsolutePath());
            }

            // 2. Prepare target destination (must be local for 7z)
            String localDestPath;
            boolean uploadRequired = false;
            File tempDestDir = null;

            if (targetFs instanceof LocalFileSystem) {
                localDestPath = destinationPath;
            } else {
                tempDestDir = Files.createTempDirectory("acommander_unpack_dest_").toFile();
                tempDestDir.deleteOnExit();
                localDestPath = tempDestDir.getAbsolutePath();
                uploadRequired = true;
            }

            ActionDefinition action = requireAction("unpack");
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${destinationPath}", localDestPath),
                    List.of(archiveToUnpack.getAbsolutePath())
            );

            final boolean finalIsTempArchive = isTempArchive;
            final File finalArchiveToUnpack = archiveToUnpack;
            final boolean finalUploadRequired = uploadRequired;
            final File finalTempDestDir = tempDestDir;

            runExecutable(command, true).thenRun(() -> {
                try {
                    if (finalUploadRequired && finalTempDestDir != null) {
                        // Upload all unpacked files to remote VFS
                        File[] files = finalTempDestDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                uploadRecursive(f, targetFs, destinationPath);
                            }
                        }
                        // Cleanup temp dir
                        deleteRecursive(finalTempDestDir);
                    }
                    if (finalIsTempArchive) {
                        finalArchiveToUnpack.delete();
                    }
                    // Refresh UI
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (Exception e) {
                    log.error("Failed to upload/cleanup after unpack", e);
                }
            });
            log.debug("Unpacking process started for: {}", selectedItem.getName());
        } catch (Exception e) {
            log.error("Failed to unpack file", e);
        }
    }

    private void uploadRecursive(File source, VFileSystem targetFs, String targetInternalDir) throws IOException {
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
            targetFs.copy(source.getAbsolutePath(), targetFs, targetPath);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Override
    protected void doExtractAll(FileItem selectedItem, String destinationPath) {
        // Similar to doUnpack, extractAll typically uses a different 7z flag ('x' instead of 'e')
        // but the VFS handling logic is identical.
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            File archiveToUnpack;
            boolean isTempArchive = false;

            if (sourceFs instanceof LocalFileSystem) {
                archiveToUnpack = selectedItem.getFile();
            } else {
                archiveToUnpack = File.createTempFile("acommander_extract_", "_" + selectedItem.getName());
                archiveToUnpack.deleteOnExit();
                isTempArchive = true;
                sourceFs.copy(sourceFs.getInternalPath(selectedItem), new LocalFileSystem(""), archiveToUnpack.getAbsolutePath());
            }

            String localDestPath;
            boolean uploadRequired = false;
            File tempDestDir = null;

            if (targetFs instanceof LocalFileSystem) {
                localDestPath = destinationPath;
            } else {
                tempDestDir = Files.createTempDirectory("acommander_extract_dest_").toFile();
                tempDestDir.deleteOnExit();
                localDestPath = tempDestDir.getAbsolutePath();
                uploadRequired = true;
            }

            ActionDefinition action = requireAction("extractAll");
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${destinationPath}", localDestPath),
                    List.of(archiveToUnpack.getAbsolutePath())
            );

            final boolean finalIsTempArchive = isTempArchive;
            final File finalArchiveToUnpack = archiveToUnpack;
            final boolean finalUploadRequired = uploadRequired;
            final File finalTempDestDir = tempDestDir;

            runExecutable(command, true).thenRun(() -> {
                try {
                    if (finalUploadRequired && finalTempDestDir != null) {
                        File[] files = finalTempDestDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                uploadRecursive(f, targetFs, destinationPath);
                            }
                        }
                        deleteRecursive(finalTempDestDir);
                    }
                    if (finalIsTempArchive) {
                        finalArchiveToUnpack.delete();
                    }
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (Exception e) {
                    log.error("Failed to upload/cleanup after extractAll", e);
                }
            });
            log.debug("ExtractAll process started for: {}", selectedItem.getName());
        } catch (Exception e) {
            log.error("Failed to extract file", e);
        }
    }

    @Override
    protected void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            List<File> tempFiles = new ArrayList<>();
            List<String> localPathsToMerge = new ArrayList<>();

            // 1. Prepare source files
            for (FileItem item : validItems) {
                if (sourceFs instanceof LocalFileSystem) {
                    localPathsToMerge.add(item.getFullPath());
                } else {
                    File tempFile = File.createTempFile("acommander_pdf_merge_", "_" + item.getName());
                    tempFile.deleteOnExit();
                    tempFiles.add(tempFile);
                    sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), tempFile.getAbsolutePath());
                    localPathsToMerge.add(tempFile.getAbsolutePath());
                }
            }

            // 2. Prepare target PDF
            String localPdfPath;
            boolean uploadRequired = false;
            if (targetFs instanceof LocalFileSystem) {
                localPdfPath = newPdfFilenameWithPath;
            } else {
                File tempPdf = File.createTempFile("acommander_pdf_merge_target_", "_" + new File(newPdfFilenameWithPath).getName());
                tempPdf.delete();
                tempPdf.deleteOnExit();
                tempFiles.add(tempPdf);
                localPdfPath = tempPdf.getAbsolutePath();
                uploadRequired = true;
            }

            ActionDefinition action = requireAction("mergePdf");
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${outputPdf}", localPdfPath),
                    localPathsToMerge
            );

            final boolean finalUploadRequired = uploadRequired;
            final String finalLocalPdfPath = localPdfPath;

            runExecutable(command, true).thenRun(() -> {
                try {
                    if (finalUploadRequired) {
                        targetFs.copy(finalLocalPdfPath, targetFs, targetFs.getInternalPath(new FileItem(new File(newPdfFilenameWithPath))));
                    }
                    for (File f : tempFiles) {
                        f.delete();
                    }
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (IOException e) {
                    log.error("Failed to upload/cleanup after PDF merge", e);
                }
            });
            log.debug("PDF Merge process started: {}", newPdfFilenameWithPath);
        } catch (Exception e) {
            log.error("Failed to merge PDFs", e);
        }
    }

    @Override
    protected void doExtractPDFPages(FileItem fileItem, String destinationPath) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            File pdfToExtract;
            boolean isTempPdf = false;

            // 1. Prepare source PDF
            if (sourceFs instanceof LocalFileSystem) {
                pdfToExtract = fileItem.getFile();
            } else {
                pdfToExtract = File.createTempFile("acommander_pdf_extract_", "_" + fileItem.getName());
                pdfToExtract.deleteOnExit();
                isTempPdf = true;
                sourceFs.copy(sourceFs.getInternalPath(fileItem), new LocalFileSystem(""), pdfToExtract.getAbsolutePath());
            }

            // 2. Prepare target destination
            String localDestPath;
            boolean uploadRequired = false;
            File tempDestDir = null;

            if (targetFs instanceof LocalFileSystem) {
                localDestPath = destinationPath;
            } else {
                tempDestDir = Files.createTempDirectory("acommander_pdf_extract_dest_").toFile();
                tempDestDir.deleteOnExit();
                localDestPath = tempDestDir.getAbsolutePath();
                uploadRequired = true;
            }

            // pdftk in this bundle is not Unicode-safe on Windows paths.
            // Always run extraction from an ASCII temp work directory.
            Path extractionWorkDir = Files.createTempDirectory("acommander_pdf_extract_work_");
            Path asciiInputPdf = extractionWorkDir.resolve("input.pdf");
            Files.copy(pdfToExtract.toPath(), asciiInputPdf, StandardCopyOption.REPLACE_EXISTING);

            ActionDefinition action = requireAction("extractPdfPages");
            String outputPattern = extractionWorkDir.resolve("page_%04d.pdf").toString();

            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${outputPattern}", outputPattern),
                    List.of(asciiInputPdf.toString())
            );

            final boolean finalIsTempPdf = isTempPdf;
            final File finalPdfToExtract = pdfToExtract;
            final boolean finalUploadRequired = uploadRequired;
            final File finalTempDestDir = tempDestDir;
            final Path finalExtractionWorkDir = extractionWorkDir;
            final String finalOutputPrefix = fileItem.getName().replaceFirst("(?i)\\.pdf$", "");
            final String finalLocalDestPath = localDestPath;

            runExecutable(command, true).thenRun(() -> {
                try {
                    Path effectiveDestDir = finalUploadRequired && finalTempDestDir != null
                            ? finalTempDestDir.toPath()
                            : Paths.get(finalLocalDestPath);
                    materializeExtractedPdfPages(finalExtractionWorkDir, effectiveDestDir, finalOutputPrefix);

                    if (finalUploadRequired && finalTempDestDir != null) {
                        File[] files = finalTempDestDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                uploadRecursive(f, targetFs, destinationPath);
                            }
                        }
                        deleteRecursive(finalTempDestDir);
                    }
                    if (finalIsTempPdf) {
                        finalPdfToExtract.delete();
                    }
                    deleteRecursive(finalExtractionWorkDir.toFile());
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (Exception e) {
                    log.error("Failed to upload/cleanup after PDF extraction", e);
                }
            });
            log.debug("PDF extraction process started from: {}", fileItem.getName());
        } catch (Exception e) {
            log.error("Failed to extract PDF pages", e);
        }
    }

    private void materializeExtractedPdfPages(Path extractionWorkDir, Path destinationDir, String outputPrefix) throws IOException {
        Files.createDirectories(destinationDir);
        List<Path> generatedPages;
        try (var stream = Files.list(extractionWorkDir)) {
            generatedPages = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        int pageNumber = 1;
        for (Path pagePath : generatedPages) {
            String targetName = String.format("%s_%04d.pdf", outputPrefix, pageNumber++);
            Path targetPath = destinationDir.resolve(targetName);
            Files.move(pagePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ActionDefinition requireAction(String id) {
        ActionDefinition action = appRegistry.findAction(id)
                .orElseThrow(() -> new IllegalStateException("Missing action config: " + id));
        if (action.getPath() == null || action.getPath().isBlank()) {
            throw new IllegalStateException("Missing action path: " + id);
        }
        return action;
    }
}
