package org.chaiware.acommander4j;

import javafx.scene.control.TextInputDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandsImpl implements ICommands {
    private static final Logger log = LoggerFactory.getLogger(CommandsImpl.class);
    private final String APP_PATH = Paths.get(System.getProperty("user.dir"), "apps") + "\\";
    private final FileListsLoader fileListsLoader;

    public CommandsImpl(FileListsLoader fileListsLoader) {
        this.fileListsLoader = fileListsLoader;
    }

    @Override
    public void view(FileItem fileItem) throws Exception {
        /* Found 3 alternatives for viewing files: UniversalViewer (least features, 10mb), FileViewerLite (quite good, 98mb), QuickLook (best, 236mb) */
        log.info("Viewing: {}", fileItem.getName());
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "QuickLook\\QuickLook.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
    }

    @Override
    public void rename(FileItem selectedItem, String newFilename) throws Exception {
        File currentFile = selectedItem.getFile();
        TextInputDialog dialog = new TextInputDialog(currentFile.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Enter new filename:");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) { // if user dismisses the dialog it won't rename...
            String newName = result.get();
            File newFile = new File(currentFile.getParent(), newName);
            Files.move(currentFile.toPath(), newFile.toPath());
            fileListsLoader.refreshFileListViews();
        }
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {
        log.info("Editing: {}", fileItem.getName());
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "TedNPad.exe");
        command.add(fileItem.getFile().toString());
        runExecutable(command, false);
    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {
        log.info("Copying: {} To: {}", sourceFile, targetFolder);
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "fastcopy\\FastCopy.exe");
        command.add("/cmd=diff");
        command.add("/auto_close");
        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command, true);
    }

    @Override
    public void move(FileItem sourceFile, String targetFolder) throws Exception {
        log.info("Moving: {} To: {}", sourceFile, targetFolder);
        List<String> command = new ArrayList<>();
        command.add(APP_PATH + "fastcopy\\FastCopy.exe");
        command.add("/cmd=move");
        command.add("/auto_close");
        command.add("/verify");
        command.add(sourceFile.getFile().toString());
        command.add("/to=" + targetFolder);
        runExecutable(command, true);
    }

    @Override
    public void delete(FileItem selectedItem) throws IOException {
        Files.delete(selectedItem.getFile().toPath());
        fileListsLoader.refreshFileListViews();
    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {
        try {
            new ProcessBuilder("cmd", "/c", "start", "powershell", "-NoExit", "-Command", "cd '" + openHerePath + "'").start();
        } catch (IOException e) {
            new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "cd /d " + openHerePath).start();
        }
    }

    @Override
    public void pack(FileItem selectedItem) {

    }

    @Override
    public void unpack(FileItem selectedItem) {

    }

    private void runExecutable(List<String> params, boolean isWaitFor) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(params);
        log.info("Running: {}", String.join(" ", pb.command()));
        Process process = pb.start();
        if (isWaitFor) {
            process.waitFor();
            fileListsLoader.refreshFileListViews();
        }
    }
}
