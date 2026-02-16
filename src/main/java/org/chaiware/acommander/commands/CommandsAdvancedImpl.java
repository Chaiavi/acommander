package org.chaiware.acommander.commands;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.ToolCommandBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    protected void doView(FileItem fileItem) throws Exception {
        ActionDefinition action = requireAction("view");
        List<String> selectedFiles = List.of(fileItem.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of(),
                selectedFiles
        );
        runExecutable(command, false);
        log.debug("Viewed: {}", fileItem.getName());
    }

    @Override
    protected void doEdit(FileItem fileItem) throws Exception {
        ActionDefinition action = requireAction("edit");
        List<String> selectedFiles = List.of(fileItem.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of(),
                selectedFiles
        );
        runExecutable(command, false);
        log.debug("Edited: {}", fileItem.getName());
    }

    @Override
    protected void doCopy(FileItem sourceFile, String targetFolder) throws Exception {
        ActionDefinition action = requireAction("copy");
        List<String> selectedFiles = List.of(sourceFile.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${targetFolder}", targetFolder),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Copied: {} To: {}", sourceFile, targetFolder);
    }

    @Override
    protected void doMove(FileItem sourceFile, String targetFolder) throws Exception {
        if (sourceFile.getFile().toPath().getRoot().toString().equalsIgnoreCase(Paths.get(targetFolder).getRoot().toString())) // Use FASTEST move in the case of moving file over same drive
            commandsSimpleImpl.doMove(sourceFile, targetFolder);
        else {
            ActionDefinition action = requireAction("move");
            List<String> selectedFiles = List.of(sourceFile.getFullPath());
            List<String> command = ToolCommandBuilder.buildCommand(
                    action.getPath(),
                    action.getArgs(),
                    fileListsLoader,
                    Map.of("${targetFolder}", targetFolder),
                    selectedFiles
            );
            runExecutable(command, true);
            log.debug("Moved: {} To: {}", sourceFile, targetFolder);
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
        for (FileItem selectedItem : validItems) {
            Path path = selectedItem.getFile().toPath();
            Files.walk(path) // This is done for deleting folders recursively
                    .sorted(Comparator.reverseOrder())
                    .forEach(filePath -> {
                        try {
                            Files.delete(filePath);
                            log.info("Deleted: {}", filePath);
                        } catch (IOException e) {
                            log.error("Failed deleting: {}", filePath, e);
                            failedDeletes.add(new FileItem(filePath.toFile()));
                        }
                    });
        }

        if (!failedDeletes.isEmpty()) {
            log.info("Failed to delete {} files, attempting to unlock them so you can delete them all", failedDeletes.size());
            unlockDelete(failedDeletes);
        }

        fileListsLoader.refreshFileListViews();
    }

    @Override
    protected void doUnlockDelete(List<FileItem> validItems) throws Exception {
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
    protected void doWipeDelete(List<FileItem> validItems) throws Exception {
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
    protected void doPack(List<FileItem> validItems, String archiveFilenameWithPath) throws Exception {
        ActionDefinition action = requireAction("pack");
        List<String> selectedFiles = validItems.stream()
                .map(FileItem::getFullPath)
                .collect(Collectors.toList());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${archiveFile}", archiveFilenameWithPath),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Archived (zip): {}", archiveFilenameWithPath);
    }

    @Override
    protected void doUnpack(FileItem selectedItem, String destinationPath) throws Exception {
        ActionDefinition action = requireAction("unpack");
        List<String> selectedFiles = List.of(selectedItem.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${destinationPath}", destinationPath),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("UnPacked Archive: {} to: {}", selectedItem.getName(), destinationPath);
    }

    @Override
    protected void doExtractAll(FileItem selectedItem, String destinationPath) throws Exception {
        ActionDefinition action = requireAction("extractAll");
        List<String> selectedFiles = List.of(selectedItem.getFullPath());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${destinationPath}", destinationPath),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Extracted File: {} to: {}", selectedItem.getName(), destinationPath);
    }

    @Override
    protected void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) throws Exception {
        ActionDefinition action = requireAction("mergePdf");
        List<String> selectedFiles = validItems.stream()
                .map(FileItem::getFullPath)
                .collect(Collectors.toList());
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${outputPdf}", newPdfFilenameWithPath),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("The new Merged (pdf): {}", newPdfFilenameWithPath);
    }

    @Override
    protected void doExtractPDFPages(FileItem fileItem, String destinationPath) throws Exception {
        ActionDefinition action = requireAction("extractPdfPages");
        List<String> selectedFiles = List.of(fileItem.getFullPath());
        String outputPattern = destinationPath + "\\" + fileItem.getName().replaceFirst("\\.pdf$", "") + "_%04d.pdf";
        List<String> command = ToolCommandBuilder.buildCommand(
                action.getPath(),
                action.getArgs(),
                fileListsLoader,
                Map.of("${outputPattern}", outputPattern),
                selectedFiles
        );
        runExecutable(command, true);
        log.debug("Extracted PDF pages from: {} to: {}", fileItem.getName(), destinationPath);
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
