package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CommandsAdvancedImpl extends ACommands {
    ACommands commandsSimpleImpl;

    public CommandsAdvancedImpl(FilesPanesHelper fileListsLoader) {
        super(fileListsLoader);
        commandsSimpleImpl = new CommandsSimpleImpl(fileListsLoader);
    }

    @Override
    public void rename(List<FileItem> selectedItems, String newFilename) throws Exception {
        if (selectedItems.size() == 1) {
            FileItem selectedItem = selectedItems.get(0);
            File currentFile = selectedItem.getFile();
            File newFile = new File(currentFile.getParent(), newFilename);
            Files.move(currentFile.toPath(), newFile.toPath());
            fileListsLoader.refreshFileListViews();
            log.debug("Renamed: {} to {}", currentFile.getName(), newFile.getName());
        } else {
            List<String> command = new ArrayList<>();
            command.add(APP_PATH + "multi_rename\\Renamer.exe");
            command.add("-af");
            command.add(selectedItems.stream().map(f -> "\"" + f.getFullPath() + "\"").collect(Collectors.joining(" ")));
            runExecutable(command, true);
            log.debug("Finished Multi File Rename Process");
        }
    }

    @Override
    public void view(FileItem fileItem) throws Exception {
        /*
        UniversalViewer (least features, 10mb),
        FileViewerLite (quite good, 98mb),
        QuickLook (best, 236mb)
        1 File Viewer (200mb)
        */
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "view\\QuickLook.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Viewed: {}", fileItem.getName());
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {
        List<String> command = new ArrayList<>();
//          command.add(APP_PATH + "TedNPad.exe");
        command.add(APP_PATH + "edit\\Notepad4.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Edited: {}", fileItem.getName());
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "copy\\FastCopy.exe");
        command.add("/cmd=diff");
        command.add("/auto_close");
//        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command, true);
        log.debug("Copied: {} To: {}", sourceFile, targetFolder);
    }

    @Override
    public void move(FileItem sourceFile, String targetFolder) throws Exception {
        if (sourceFile.getFile().toPath().getRoot().toString().equalsIgnoreCase(Paths.get(targetFolder).getRoot().toString())) // Use FASTEST move in the case of moving file over same drive
            commandsSimpleImpl.move(sourceFile, targetFolder);
        else {
            List<String> command = new ArrayList<>();
            command.add(APP_PATH + "copy\\FastCopy.exe");
            command.add("/cmd=move");
            command.add("/auto_close");
//        command.add("/verify");
            command.add(sourceFile.getFile().toString());
            command.add("/to=" + targetFolder);
            runExecutable(command, true);
            log.debug("Moved: {} To: {}", sourceFile, targetFolder);
        }
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {
        Path path = Paths.get(parentDir, newDirName);
        Files.createDirectories(path);
        fileListsLoader.refreshFileListViews();
        log.debug("Created Directory: {}", newDirName);
    }

    @Override
    public void mkFile(String parentDir, String newFileName) throws Exception {
        Path path = Paths.get(parentDir, newFileName);
        Files.createFile(path);
        fileListsLoader.refreshFileListViews();
        log.debug("Created File: {}", newFileName);
    }

    @Override
    public void delete(List<FileItem> selectedItems) throws Exception {
        List<FileItem> failedDeletes = new ArrayList<>();
        for (FileItem selectedItem : selectedItems) {
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
            log.info("Failed to delete {} files, attempting to unlock then delete them all", failedDeletes.size());
            unlockDelete(failedDeletes);
        }

        fileListsLoader.refreshFileListViews();
    }

    @Override
    public void unlockDelete(List<FileItem> selectedItems) throws Exception {
        String fullPaths = selectedItems.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" "));

        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "unlock_delete\\ThisIsMyFile.exe");
        command.add(fullPaths);
        runExecutable(command, true);
        fileListsLoader.refreshFileListViews();
        log.debug("Unlocked & Deleted: {}", selectedItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {
        try {
            List<String> command = Arrays.asList("cmd", "/c", "start", "powershell", "-NoExit", "-Command", "cd '" + openHerePath + "'");
            runExecutable(command, false);
            log.debug("Opened Powershell Here: {}", openHerePath);
        } catch (IOException e) {
            List<String> command = Arrays.asList("cmd", "/c", "start", "cmd", "/k", "cd /d " + openHerePath);
            runExecutable(command, false);
            log.debug("Opened Command Shell Here: {}", openHerePath);
        }
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
    public void pack(List<FileItem> sources, String archiveFilename, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pack_unpack\\7zG.exe");
        command.add("a");
        command.add(destinationPath + "\\" + archiveFilename);
        command.add(sources.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" ")));
        runExecutable(command, true);
        log.debug("Archived (zip): {} to: {}", archiveFilename, destinationPath);
    }

    @Override
    public void unpack(FileItem fileItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pack_unpack\\7zG.exe");
        command.add("x");
        command.add("-o" + destinationPath);
        command.add(fileItem.getFile().toString());
        runExecutable(command, true);
        log.debug("UnPacked Archive: {} to: {}", fileItem.getName(), destinationPath);
    }

    @Override
    public void extractAll(FileItem selectedItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "extract_all\\UniExtract\\UniExtract.exe");
//        command.add("/remove");
        command.add(selectedItem.getFile().toString());
        command.add(destinationPath);
        runExecutable(command, true);
        log.debug("Extracted File: {} to: {}", selectedItem.getName(), destinationPath);
    }
}
