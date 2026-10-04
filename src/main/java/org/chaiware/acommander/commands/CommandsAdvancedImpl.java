package org.chaiware.acommander.commands;

import javafx.application.Platform;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
            reportFailure(runExecutable(command, true), "Multi Rename");
            log.debug("Finished Multi File Rename Process");
        }
    }

    @Override
    protected void doView(FileItem fileItem) throws IOException {
        VFileSystem fs = fileListsLoader.getFocusedFileSystem();
        File fileToView;
        boolean isTemp = false;

        if (fs instanceof LocalFileSystem) {
            fileToView = fileItem.getFile();
        } else {
            // Download to temp
            fileToView = AppTempDir.createTempFile("acommander_view_", "_" + fileItem.getName()).toFile();
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
        reportFailure(runExecutable(command, false).thenRun(() -> {
            if (finalIsTemp) {
                finalFileToView.delete();
            }
        }), "View");
        log.debug("Viewed: {}", fileItem.getName());
    }

    @Override
    protected void doEdit(FileItem fileItem) throws IOException {
        VFileSystem fs = fileListsLoader.getFocusedFileSystem();
        File fileToEdit;
        boolean isTemp = false;

        if (fs instanceof LocalFileSystem) {
            fileToEdit = fileItem.getFile();
        } else {
            // Download to temp
            fileToEdit = AppTempDir.createTempFile("acommander_edit_", "_" + fileItem.getName()).toFile();
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

        reportFailure(runExecutable(command, false).thenRun(() -> {
            if (finalIsTemp) {
                try {
                    new LocalFileSystem("").copy(finalFileToEdit.getAbsolutePath(), fs, internalPath);
                } catch (IOException e) {
                    throw new UncheckedIOException("Your edit could not be saved to " + internalPath
                            + ". The edited copy stays at " + finalFileToEdit + " until ACommander closes.", e);
                }
                finalFileToEdit.delete();
            }
            // Only mark for repack if in a read-write archive
            fs.markModified();
        }), "Edit");
        log.debug("Edited: {}", fileItem.getName());
    }

    @Override
    protected void doCopy(FileItem sourceFile, String targetFolder) throws Exception {
        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

        // If either side is an archive or FTP, use the VFS-based copy from simple implementation
        if (sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
            sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem) {
            commandsSimpleImpl.doCopy(sourceFile, targetFolder);
            return;
        }
        copyItemIndividually(sourceFile, targetFolder);
        log.debug("Copied: {} To: {}", sourceFile, targetFolder);
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

    public void copyBatch(List<FileItem> selectedItems, String targetFolder) throws Exception {
        List<FileItem> validItems = filterValidItems(selectedItems);
        if (validItems.isEmpty()) {
            return;
        }

        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();
        boolean viaVfs = sourceFs instanceof ArchiveFileSystem || targetFs instanceof ArchiveFileSystem ||
                sourceFs instanceof FtpFileSystem || targetFs instanceof FtpFileSystem;

        // If many items and both sides are local, run one batch command.
        if (!viaVfs && (sourceFs == null || sourceFs instanceof LocalFileSystem) && (targetFs == null || targetFs instanceof LocalFileSystem) && validItems.size() > 1) {
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
            reportFailure(runExecutable(command, true)
                    .thenAccept(output -> {
                        markTargetArchiveForRepack(targetFolder);
                        verifyBatchCopy(validItems, targetFolder, command);
                        log.debug("Copied {} items To: {} using command", validItems.size(), targetFolder);
                    }), "Copy");
            return;
        }

        // Copy each item individually to avoid command line length issues with many files
        // or files with special characters (e.g., Hebrew, Unicode)
        List<String> failedNames = new ArrayList<>();
        Exception firstFailure = null;
        for (FileItem item : validItems) {
            try {
                if (viaVfs) {
                    commandsSimpleImpl.doCopy(item, targetFolder);
                } else {
                    copyItemIndividually(item, targetFolder);
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

    private void verifyBatchCopy(List<FileItem> copiedItems, String targetFolder, List<String> command) {
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
            throw new IllegalStateException("The copy tool reported success, but these are missing in " + targetFolder
                    + ": " + String.join(", ", missing));
        }

        log.debug("Batch copy verification passed for {} item(s) in target {}", copiedItems.size(), targetFolder);
    }

    private void copyItemIndividually(FileItem item, String targetFolder) {
        ActionDefinition action = requireAction("copy");
        List<String> selectedFiles = List.of(item.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${targetFolder}", targetFolder),
                selectedFiles
        );
        reportFailure(runExecutable(command, true).thenRun(() -> markTargetArchiveForRepack(targetFolder)), "Copy");
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
        reportFailure(runExecutable(command, true)
                .thenAccept(output -> log.debug("Moved: {} To: {}", sourceFile, targetFolder)), "Move");
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
        reportFailure(runExecutable(command, true), "Unlock and Delete");
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
        reportFailure(runExecutable(command, true), "Wipe Delete");
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
        commandsSimpleImpl.searchFiles(sourcePath, filenameWildcard);
    }

    @Override
    protected void doPack(List<FileItem> validItems, String archiveFilenameWithPath) throws IOException {
        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

        List<File> tempFiles = new ArrayList<>();
        List<String> localPathsToPack = new ArrayList<>();

        // 1. Non-local sources are copied out under their own names: the archive entries take the file names
        Path stagingDir = null;
        for (FileItem item : validItems) {
            if (sourceFs instanceof LocalFileSystem) {
                localPathsToPack.add(item.getFullPath());
            } else {
                if (stagingDir == null) {
                    stagingDir = AppTempDir.createTempDirectory("acommander_pack_");
                    tempFiles.add(stagingDir.toFile());
                }
                Path staged = stagingDir.resolve(item.getName()).normalize();
                if (!stagingDir.equals(staged.getParent())) {
                    throw new IOException("Can't pack an item with this name: " + item.getName());
                }
                sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), staged.toString());
                localPathsToPack.add(staged.toString());
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
            File tempArchive = AppTempDir.createTempFile("acommander_pack_target_", "_" + new File(archiveFilenameWithPath).getName()).toFile();
            tempArchive.delete(); // Ensure it doesn't exist yet so 7z creates it
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

        reportFailure(runExecutable(command, true).thenRun(() -> {
            if (finalUploadRequired) {
                // Upload the created archive back to remote VFS
                try {
                    new LocalFileSystem("").copy(finalLocalArchivePath, targetFs, targetFs.getInternalPath(new FileItem(new File(archiveFilenameWithPath))));
                } catch (IOException e) {
                    throw new UncheckedIOException("The archive was created but could not be uploaded to " + archiveFilenameWithPath, e);
                }
            }
            for (File f : tempFiles) {
                FileHelper.deleteQuietly(f.toPath());
            }
            Platform.runLater(fileListsLoader::refreshFileListViews);
        }), "Pack");
        log.debug("Archiving process started for: {}", archiveFilenameWithPath);
    }

    @Override
    protected void doUnpack(FileItem selectedItem, String destinationPath) throws IOException {
        unpackWith("unpack", "Unpack", selectedItem, destinationPath);
    }

    /** Unpack and Extract All differ only in the tool; remote sources and targets go through local temp copies. */
    private void unpackWith(String actionId, String title, FileItem selectedItem, String destinationPath) throws IOException {
        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

        File archiveToUnpack;
        boolean isTempArchive = false;

        // 1. Prepare source archive (download if remote)
        if (sourceFs instanceof LocalFileSystem) {
            archiveToUnpack = selectedItem.getFile();
        } else {
            archiveToUnpack = AppTempDir.createTempFile("acommander_unpack_", "_" + selectedItem.getName()).toFile();
            isTempArchive = true;
            sourceFs.copy(sourceFs.getInternalPath(selectedItem), new LocalFileSystem(""), archiveToUnpack.getAbsolutePath());
        }

        // 2. Prepare target destination (must be local for the tool)
        String localDestPath;
        File tempDestDir = null;
        if (targetFs instanceof LocalFileSystem) {
            localDestPath = destinationPath;
        } else {
            tempDestDir = AppTempDir.createTempDirectory("acommander_unpack_dest_").toFile();
            localDestPath = tempDestDir.getAbsolutePath();
        }

        ActionDefinition action = requireAction(actionId);
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${destinationPath}", localDestPath),
                List.of(archiveToUnpack.getAbsolutePath())
        );

        final boolean finalIsTempArchive = isTempArchive;
        final File finalTempDestDir = tempDestDir;
        reportFailure(runExecutable(command, true).thenRun(() -> {
            if (finalTempDestDir != null) {
                // Upload all unpacked files to the remote target
                File[] files = finalTempDestDir.listFiles();
                try {
                    if (files != null) {
                        for (File f : files) {
                            uploadRecursive(f, targetFs, destinationPath);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("The files were unpacked but could not be uploaded to " + destinationPath, e);
                }
                FileHelper.deleteQuietly(finalTempDestDir.toPath());
            }
            if (finalIsTempArchive) {
                archiveToUnpack.delete();
            }
            Platform.runLater(fileListsLoader::refreshFileListViews);
        }), title);
        log.debug("{} started for: {}", title, selectedItem.getName());
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
            new LocalFileSystem("").copy(source.getAbsolutePath(), targetFs, targetPath);
        }
    }

    @Override
    protected void doExtractAll(FileItem selectedItem, String destinationPath) throws IOException {
        unpackWith("extractAll", "Extract All", selectedItem, destinationPath);
    }

    @Override
    protected void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            // pdftk in this bundle is not Unicode-safe on Windows paths.
            // Always run merge from an ASCII temp work directory.
            Path asciiWorkDir = AppTempDir.createTempDirectory("acommander_pdf_merge_work_");
            List<Path> asciiInputPdfs = new ArrayList<>();
            List<File> tempFilesToCleanup = new ArrayList<>();

            // 1. Prepare source files - copy to ASCII work directory with simple names
            for (int i = 0; i < validItems.size(); i++) {
                FileItem item = validItems.get(i);
                log.debug("Processing PDF item {}: {}", i, item.getName());

                File sourcePdf;
                if (sourceFs instanceof LocalFileSystem) {
                    sourcePdf = item.getFile();
                } else {
                    sourcePdf = AppTempDir.createTempFile("acommander_pdf_merge_src_", "_" + item.getName()).toFile();
                    tempFilesToCleanup.add(sourcePdf);
                    sourceFs.copy(sourceFs.getInternalPath(item), new LocalFileSystem(""), sourcePdf.getAbsolutePath());
                }

                // Copy to ASCII work directory with simple name (like extractPDFPages does)
                Path asciiPdf = asciiWorkDir.resolve("input_" + i + ".pdf");
                Files.copy(sourcePdf.toPath(), asciiPdf, StandardCopyOption.REPLACE_EXISTING);
                asciiInputPdfs.add(asciiPdf);
            }

            // 2. Prepare target PDF in ASCII work directory
            Path asciiOutputPdf = asciiWorkDir.resolve("output.pdf");
            String finalOutputPath = newPdfFilenameWithPath;

            // Ensure the target PDF path has .pdf extension
            if (!finalOutputPath.toLowerCase().endsWith(".pdf")) {
                finalOutputPath = finalOutputPath + ".pdf";
            }

            ActionDefinition action = requireAction("mergePdf");
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${outputPdf}", asciiOutputPdf.toString()),
                    asciiInputPdfs.stream().map(Path::toString).collect(Collectors.toList())
            );

            final boolean finalUploadRequired = !(targetFs instanceof LocalFileSystem);
            final String finalLocalPdfPath = finalOutputPath;

            runExecutable(command, true)
                    .thenRun(() -> {
                        try {
                            // Copy result from ASCII work directory to final destination
                            Files.copy(asciiOutputPdf, Paths.get(finalLocalPdfPath), StandardCopyOption.REPLACE_EXISTING);

                            if (finalUploadRequired) {
                                targetFs.copy(finalLocalPdfPath, targetFs, targetFs.getInternalPath(new FileItem(new File(finalLocalPdfPath))));
                            }

                            Platform.runLater(fileListsLoader::refreshFileListViews);
                        } catch (IOException e) {
                            log.error("Failed to upload/cleanup after PDF merge", e);
                        }
                    })
                    .whenComplete((ignored, throwable) -> cleanupMergeTempFiles(asciiWorkDir, tempFilesToCleanup));
            log.debug("PDF Merge process started: {}", newPdfFilenameWithPath);
        } catch (Exception e) {
            log.error("Failed to merge PDFs", e);
        }
    }

    private void cleanupMergeTempFiles(Path asciiWorkDir, List<File> tempFilesToCleanup) {
        FileHelper.deleteQuietly(asciiWorkDir);
        if (tempFilesToCleanup != null) {
            for (File tempFile : tempFilesToCleanup) {
                if (tempFile != null) {
                    tempFile.delete();
                }
            }
        }
    }

    @Override
    protected void doExtractPDFPages(FileItem fileItem, String destinationPath, PdfExtractOptions options) {
        try {
            VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
            VFileSystem targetFs = fileListsLoader.getUnfocusedFileSystem();

            File pdfToExtract;
            boolean isTempPdf = false;

            // 1. Prepare source PDF
            if (sourceFs instanceof LocalFileSystem) {
                pdfToExtract = fileItem.getFile();
            } else {
                pdfToExtract = AppTempDir.createTempFile("acommander_pdf_extract_", "_" + fileItem.getName()).toFile();
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
                tempDestDir = AppTempDir.createTempDirectory("acommander_pdf_extract_dest_").toFile();
                localDestPath = tempDestDir.getAbsolutePath();
                uploadRequired = true;
            }

            // pdftk in this bundle is not Unicode-safe on Windows paths.
            // Always run extraction from an ASCII temp work directory.
            Path extractionWorkDir = AppTempDir.createTempDirectory("acommander_pdf_extract_work_");
            Path asciiInputPdf = extractionWorkDir.resolve("input.pdf");
            Files.copy(pdfToExtract.toPath(), asciiInputPdf, StandardCopyOption.REPLACE_EXISTING);

            ActionDefinition action = requireAction("extractPdfPages");
            PdfExtractOptions effectiveOptions = options == null ? PdfExtractOptions.extractAll() : options;
            int totalPages = effectiveOptions.knownTotalPages() != null && effectiveOptions.knownTotalPages() > 0
                    ? effectiveOptions.knownTotalPages()
                    : readPdfPageCount(action, asciiInputPdf);
            validatePdfExtractRequest(fileItem.getName(), totalPages, effectiveOptions);
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
            final PdfExtractOptions finalOptions = effectiveOptions;

            CompletableFuture<Void> extractFuture = runExecutable(command, false)
                    .handle((ignored, ex) -> {
                        if (ex == null) {
                            return null;
                        }
                        Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                                ? ex.getCause()
                                : ex;
                        log.warn(
                                "pdftk burst failed for '{}', falling back to page-by-page extraction",
                                fileItem.getName(),
                                cause
                        );
                        extractPdfPagesWithCat(action, asciiInputPdf, finalExtractionWorkDir, totalPages);
                        return null;
                    });

            extractFuture.thenRun(() -> {
                try {
                    Path effectiveDestDir = finalUploadRequired && finalTempDestDir != null
                            ? finalTempDestDir.toPath()
                            : Paths.get(finalLocalDestPath);
                    List<Path> generatedPages = collectGeneratedPageFiles(finalExtractionWorkDir);
                    if (generatedPages.isEmpty()) {
                        throw new IOException("PDF extraction produced no pages.");
                    }

                    switch (finalOptions.mode()) {
                        case SPECIFIC_PAGES_SINGLE -> {
                            List<Integer> selectedPages = parsePageExpression(finalOptions.pageExpression());
                            materializeSelectedPdfPages(generatedPages, effectiveDestDir, finalOutputPrefix, selectedPages);
                        }
                        case PAGES_PER_PDF -> {
                            int pagesPerPdf = finalOptions.pagesPerPdf() == null ? 0 : finalOptions.pagesPerPdf();
                            if (pagesPerPdf <= 0) {
                                throw new IllegalArgumentException("Pages per PDF must be greater than zero.");
                            }
                            ActionDefinition mergeAction = requireAction("mergePdf");
                            materializeChunkedPdfPages(generatedPages, finalExtractionWorkDir, effectiveDestDir, finalOutputPrefix, pagesPerPdf, mergeAction);
                        }
                        case ALL_PAGES_SINGLE ->
                                materializeExtractedPdfPages(generatedPages, effectiveDestDir, finalOutputPrefix);
                        default -> materializeExtractedPdfPages(generatedPages, effectiveDestDir, finalOutputPrefix);
                    }

                    if (finalUploadRequired && finalTempDestDir != null) {
                        File[] files = finalTempDestDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                uploadRecursive(f, targetFs, destinationPath);
                            }
                        }
                        FileHelper.deleteQuietly(finalTempDestDir.toPath());
                    }
                    if (finalIsTempPdf) {
                        finalPdfToExtract.delete();
                    }
                    FileHelper.deleteQuietly(finalExtractionWorkDir);
                    Platform.runLater(fileListsLoader::refreshFileListViews);
                } catch (Exception e) {
                    log.error("Failed to upload/cleanup after PDF extraction", e);
                }
            });
            log.debug("PDF extraction process started from: {}", fileItem.getName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract PDF pages", e);
        }
    }

    private void extractPdfPagesWithCat(ActionDefinition extractAction, Path asciiInputPdf, Path extractionWorkDir, int totalPages) {
        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            String outputPath = extractionWorkDir.resolve(String.format("page_%04d.pdf", pageNumber)).toString();
            List<String> catCommand = ToolCommandBuilder.buildCommand(
                    extractAction.getPath(),
                    List.of("${selectedFile}", "cat", String.valueOf(pageNumber), "output", "${outputPdf}"),
                    fileListsLoader,
                    Map.of("${outputPdf}", outputPath),
                    List.of(asciiInputPdf.toString())
            );
            runExecutable(catCommand, false).join();
        }
    }

    @Override
    protected int doGetPdfPageCount(FileItem selectedItem) throws Exception {
        VFileSystem sourceFs = fileListsLoader.getFocusedFileSystem();
        File pdfToCount;
        boolean isTempPdf = false;

        if (sourceFs instanceof LocalFileSystem) {
            pdfToCount = selectedItem.getFile();
        } else {
            pdfToCount = AppTempDir.createTempFile("acommander_pdf_count_", "_" + selectedItem.getName()).toFile();
            sourceFs.copy(sourceFs.getInternalPath(selectedItem), new LocalFileSystem(""), pdfToCount.getAbsolutePath());
            isTempPdf = true;
        }

        Path countWorkDir = AppTempDir.createTempDirectory("acommander_pdf_count_work_");
        try {
            Path asciiInputPdf = countWorkDir.resolve("input.pdf");
            Files.copy(pdfToCount.toPath(), asciiInputPdf, StandardCopyOption.REPLACE_EXISTING);
            ActionDefinition action = requireAction("extractPdfPages");
            return readPdfPageCount(action, asciiInputPdf);
        } finally {
            if (isTempPdf) {
                pdfToCount.delete();
            }
            FileHelper.deleteQuietly(countWorkDir);
        }
    }

    private List<Path> collectGeneratedPageFiles(Path extractionWorkDir) throws IOException {
        try (var stream = Files.list(extractionWorkDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    // Keep only pdftk burst outputs; skip temp input.pdf and any chunk outputs.
                    .filter(path -> path.getFileName().toString().matches("^page_\\d{4}\\.pdf$"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private void materializeExtractedPdfPages(List<Path> generatedPages, Path destinationDir, String outputPrefix) throws IOException {
        Files.createDirectories(destinationDir);

        int pageNumber = 1;
        for (Path pagePath : generatedPages) {
            String targetName = String.format("%s_%04d.pdf", outputPrefix, pageNumber++);
            Path targetPath = destinationDir.resolve(targetName);
            Files.move(pagePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void materializeSelectedPdfPages(
            List<Path> generatedPages,
            Path destinationDir,
            String outputPrefix,
            List<Integer> selectedPages
    ) throws IOException {
        Files.createDirectories(destinationDir);
        int extractedCount = 0;
        for (Integer pageNumber : selectedPages) {
            if (pageNumber == null || pageNumber <= 0 || pageNumber > generatedPages.size()) {
                continue;
            }
            Path pagePath = generatedPages.get(pageNumber - 1);
            String targetName = String.format("%s_%04d.pdf", outputPrefix, pageNumber);
            Files.move(pagePath, destinationDir.resolve(targetName), StandardCopyOption.REPLACE_EXISTING);
            extractedCount++;
        }
        if (extractedCount == 0) {
            throw new IllegalArgumentException("No selected pages matched the source PDF page count.");
        }
    }

    private void materializeChunkedPdfPages(
            List<Path> generatedPages,
            Path extractionWorkDir,
            Path destinationDir,
            String outputPrefix,
            int pagesPerPdf,
            ActionDefinition mergeAction
    ) throws Exception {
        Files.createDirectories(destinationDir);
        int totalPages = generatedPages.size();
        int chunkIndex = 1;
        for (int startPage = 1; startPage <= totalPages; startPage += pagesPerPdf) {
            int endPage = Math.min(startPage + pagesPerPdf - 1, totalPages);
            List<String> chunkPages = new ArrayList<>();
            for (int page = startPage; page <= endPage; page++) {
                chunkPages.add(generatedPages.get(page - 1).toString());
            }

            Path tempChunkOutput = extractionWorkDir.resolve(String.format("chunk_%04d.pdf", chunkIndex));
            List<String> chunkCommand = ToolCommandBuilder.buildCommand(
                    mergeAction.getPath(),
                    mergeAction.getArgs(),
                    fileListsLoader,
                    Map.of("${outputPdf}", tempChunkOutput.toString()),
                    chunkPages
            );
            runExecutable(chunkCommand, false).join();

            String targetName = String.format("%s_%04d-%04d.pdf", outputPrefix, startPage, endPage);
            Files.move(tempChunkOutput, destinationDir.resolve(targetName), StandardCopyOption.REPLACE_EXISTING);
            Platform.runLater(fileListsLoader::refreshFileListViews);
            chunkIndex++;
        }
    }

    private List<Integer> parsePageExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Page expression is empty.");
        }
        Set<Integer> pages = new LinkedHashSet<>();
        for (String rawToken : expression.split(",")) {
            String token = rawToken == null ? "" : rawToken.trim();
            token = token.replaceFirst("(?i)^pages?\\s*", "");
            token = token.replaceFirst("(?i)^p\\s*", "");
            if (token.isEmpty()) {
                continue;
            }
            try {
                if (token.contains("-") || token.contains(":")) {
                    String normalized = token.replace(':', '-');
                    String[] bounds = normalized.split("-");
                    if (bounds.length != 2) {
                        throw new IllegalArgumentException("Invalid page range: " + token);
                    }
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    if (start <= 0 || end <= 0) {
                        throw new IllegalArgumentException("Page numbers must be positive: " + token);
                    }
                    if (start > end) {
                        int tmp = start;
                        start = end;
                        end = tmp;
                    }
                    for (int page = start; page <= end; page++) {
                        pages.add(page);
                    }
                } else {
                    int page = Integer.parseInt(token);
                    if (page <= 0) {
                        throw new IllegalArgumentException("Page numbers must be positive: " + token);
                    }
                    pages.add(page);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid page token: " + token, ex);
            }
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("No pages were parsed from page expression.");
        }
        return pages.stream().sorted().toList();
    }

    private int readPdfPageCount(ActionDefinition extractAction, Path asciiInputPdf) throws Exception {
        List<String> countCommand = ToolCommandBuilder.buildCommand(
                extractAction.getPath(),
                List.of("${selectedFile}", "dump_data"),
                fileListsLoader,
                Map.of(),
                List.of(asciiInputPdf.toString())
        );
        List<String> output;
        try {
            output = runExecutable(countCommand, false).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalArgumentException("Failed to read PDF page count.", cause);
        }

        for (String line : output) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("NumberOfPages:")) {
                continue;
            }
            String value = trimmed.substring("NumberOfPages:".length()).trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Failed to parse PDF page count.", ex);
            }
        }
        throw new IllegalArgumentException("Could not determine PDF page count.");
    }

    private void validatePdfExtractRequest(String fileName, int totalPages, PdfExtractOptions options) {
        if (totalPages <= 1) {
            throw new IllegalArgumentException(
                    "Cannot extract pages from '" + fileName + "': the PDF contains only " + totalPages + " page."
            );
        }

        if (options == null || options.mode() == null) {
            return;
        }

        if (options.mode() == PdfExtractOptions.Mode.PAGES_PER_PDF) {
            int pagesPerPdf = options.pagesPerPdf() == null ? 0 : options.pagesPerPdf();
            if (pagesPerPdf <= 0) {
                throw new IllegalArgumentException("Pages per PDF must be greater than zero.");
            }
            if (pagesPerPdf > totalPages) {
                throw new IllegalArgumentException(
                        "Pages per PDF (" + pagesPerPdf + ") exceeds PDF length (" + totalPages + " pages)."
                );
            }
            return;
        }

        if (options.mode() == PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE) {
            List<Integer> pages = parsePageExpression(options.pageExpression());
            int maxRequested = pages.stream().mapToInt(Integer::intValue).max().orElse(0);
            if (maxRequested > totalPages) {
                throw new IllegalArgumentException(
                        "Requested page is out of bounds. PDF has " + totalPages + " pages, requested up to page " + maxRequested + "."
                );
            }
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
