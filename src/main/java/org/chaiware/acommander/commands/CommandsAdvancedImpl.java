package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    protected void doRename(List<FileItem> validItems, String newFilename) throws Exception {
        if (validItems.size() == 1) {
            commandsSimpleImpl.doRename(validItems, newFilename);
        } else {
            List<String> command = new ArrayList<>();
            command.add(APP_PATH + "multi_rename\\Renamer.exe");
            command.add("-af");
            command.add(validItems.stream().map(f -> "\"" + f.getFullPath() + "\"").collect(Collectors.joining(" ")));
            runExecutable(command, true);
            log.debug("Finished Multi File Rename Process");
        }
    }

    @Override
    protected void doView(FileItem fileItem) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "view\\UniversalViewer\\Viewer.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Viewed: {}", fileItem.getName());
    }

    @Override
    protected void doEdit(FileItem fileItem) throws Exception {
        List<String> command = new ArrayList<>();
//          command.add(APP_PATH + "TedNPad.exe");
        command.add(APP_PATH + "edit\\Notepad4.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Edited: {}", fileItem.getName());
    }

    @Override
    protected void doCopy(FileItem sourceFile, String targetFolder) throws Exception {
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
    protected void doMove(FileItem sourceFile, String targetFolder) throws Exception {
        if (sourceFile.getFile().toPath().getRoot().toString().equalsIgnoreCase(Paths.get(targetFolder).getRoot().toString())) // Use FASTEST move in the case of moving file over same drive
            commandsSimpleImpl.doMove(sourceFile, targetFolder);
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
        String fullPaths = validItems.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" "));

        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "delete\\unlock_delete\\ThisIsMyFile.exe");
        command.add(fullPaths);
        runExecutable(command, true);
        log.debug("Unlocked & Deleted: {}", validItems.stream().map(FileItem::getName).collect(Collectors.joining(", ")));
    }

    @Override
    protected void doWipeDelete(List<FileItem> validItems) throws Exception {
        String fullPaths = validItems.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" "));

        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "delete\\wipe\\sdelete64.exe");
        command.add(fullPaths);
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
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pack_unpack\\7zG.exe");
        command.add("a");
        command.add(archiveFilenameWithPath);
        command.add(validItems.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" ")));
        runExecutable(command, true);
        log.debug("Archived (zip): {}", archiveFilenameWithPath);
    }

    @Override
    protected void doUnpack(FileItem selectedItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pack_unpack\\7zG.exe");
        command.add("x");
        command.add("-o" + destinationPath);
        command.add(selectedItem.getFile().toString());
        runExecutable(command, true);
        log.debug("UnPacked Archive: {} to: {}", selectedItem.getName(), destinationPath);
    }

    @Override
    protected void doExtractAll(FileItem selectedItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "extract_all\\UniExtract\\UniExtract.exe");
//        command.add("/remove");
        command.add(selectedItem.getFile().toString());
        command.add(destinationPath);
        runExecutable(command, true);
        log.debug("Extracted File: {} to: {}", selectedItem.getName(), destinationPath);
    }

    @Override
    protected void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pdf\\pdftk.exe");
        command.add(validItems.stream()
                .map(f -> "\"" + f.getFullPath() + "\"")
                .collect(Collectors.joining(" ")));
        command.add("cat");
        command.add("output");
        command.add(newPdfFilenameWithPath);
        runExecutable(command, true);
        log.debug("The new Merged (pdf): {}", newPdfFilenameWithPath);
    }

    @Override
    protected void doExtractPDFPages(FileItem fileItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "pdf\\pdftk.exe");
        command.add(fileItem.getFullPath());
        command.add("burst");
        command.add("output");
        command.add(destinationPath + "\\" + fileItem.getName().replaceFirst("\\.pdf$", "") + "_%04d.pdf");
        runExecutable(command, true);
        log.debug("Extracted PDF pages from: {} to: {}", fileItem.getName(), destinationPath);
    }
}