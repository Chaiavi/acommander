package org.chaiware.acommander;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class CommandsImpl implements ICommands {
    private static final Logger log = LoggerFactory.getLogger(CommandsImpl.class);
    private final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    private final FilesPanesHelper fileListsLoader;

    public CommandsImpl(FilesPanesHelper fileListsLoader) {
        this.fileListsLoader = fileListsLoader;
    }

    @Override
    public void rename(FileItem selectedItem, String newFilename) throws IOException {
        File currentFile = selectedItem.getFile();
        File newFile = new File(currentFile.getParent(), newFilename);
        Files.move(currentFile.toPath(), newFile.toPath());
        fileListsLoader.refreshFileListViews();
        log.debug("Renamed: {} to {}", currentFile.getName(), newFile.getName());
    }

    @Override
    public void view(FileItem fileItem) throws Exception {
        /*
        UniversalViewer (least features, 10mb),
        FileViewerLite (quite good, 98mb),
        QuickLook (best, 236mb)
        */
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "QuickLook\\QuickLook.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Viewed: {}", fileItem.getName());
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {
        List<String> command = new ArrayList<>();
//        command.add(APP_PATH + "TedNPad.exe");
        command.add(APP_PATH + "Notepad4.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
        log.debug("Edited: {}", fileItem.getName());
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "fastcopy\\FastCopy.exe");
        command.add("/cmd=diff");
        command.add("/auto_close");
        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command, true);
        log.debug("Copied: {} To: {}", sourceFile, targetFolder);
    }

    @Override
    public void move(FileItem sourceFile, String targetFolder) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "fastcopy\\FastCopy.exe");
        command.add("/cmd=move");
        command.add("/auto_close");
        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command, true);
        log.debug("Moved: {} To: {}", sourceFile, targetFolder);
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {
        Path path = Paths.get(parentDir, newDirName);
        Files.createDirectories(path);
        fileListsLoader.refreshFileListViews();
        log.debug("Created Directory: {}", newDirName);
    }

    @Override
    public void delete(FileItem selectedItem) throws IOException {
        Path path = selectedItem.getFile().toPath();
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        fileListsLoader.refreshFileListViews();
        log.debug("Deleted: {}", selectedItem.getName());
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
    public void pack(FileItem source, String archiveFilename, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "7zG.exe");
        command.add("a");
        command.add(destinationPath + "\\" + archiveFilename);
        command.add(source.getFullPath());
        runExecutable(command, true);
        log.debug("Archived (zip): {} to: {}", archiveFilename, destinationPath);
    }

    @Override
    public void unpack(FileItem fileItem, String destinationPath) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "7zG.exe");
        command.add("x");
        command.add("-o" + destinationPath);
        command.add(fileItem.getFile().toString());
        runExecutable(command, true);
        log.debug("UnPacked Archive: {} to: {}", fileItem.getName(), destinationPath);
    }

    private void runExecutable(List<String> params, boolean isWaitFor) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(params);
        log.debug("Running: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        if (isWaitFor) {
            process.waitFor();
            fileListsLoader.refreshFileListViews();
        }
    }
}
