package org.chaiware.acommander.commands;

import javafx.scene.control.Alert;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.helpers.FilesPanesHelper;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/** Simple implementation using Java code and Powershell (Not 3rd party executables) */
public class CommandsSimpleImpl extends ACommands {
    public CommandsSimpleImpl(FilesPanesHelper fileListsLoader) {
        super(fileListsLoader);
    }

    @Override
    protected void doRename(List<FileItem> validItems, String newFilename) throws Exception {
        if (validItems.size() > 1)
            throw new Exception("No nice way to rename more than a single file using the simplest method");

        FileItem selectedItem = validItems.get(0);
        File currentFile = selectedItem.getFile();
        File newFile = new File(currentFile.getParent(), newFilename);
        Files.move(currentFile.toPath(), newFile.toPath());
        fileListsLoader.refreshFileListViews();
        log.debug("Renamed: {} to {}", currentFile.getName(), newFile.getName());
    }

    @Override
    protected void doEdit(FileItem fileItem) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doView(FileItem fileItem) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doCopy(FileItem sourceFile, String targetFolder) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doMove(FileItem sourceFile, String targetFolder) throws Exception {
        Path source = Paths.get(sourceFile.getFullPath());
        Path target = Paths.get(targetFolder + "\\" + sourceFile.getName());
        if (sourceFile.isDirectory())
            target = Paths.get(targetFolder + "\\");

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        fileListsLoader.refreshFileListViews();
        log.debug("Moved: {} to {}", sourceFile.getName(), targetFolder);
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
    protected void doDelete(List<FileItem> validItems) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doUnlockDelete(List<FileItem> validItems) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doWipeDelete(List<FileItem> validItems) throws Exception {
        throw new Exception("Not implemented yet");
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
    public void openExplorer(String openHerePath) throws Exception {
        List<String> command = Arrays.asList("explorer.exe", openHerePath);
        runExecutable(command, false);
        log.debug("Opened Explorer Here: {}", openHerePath);
    }

    @Override
    public void searchFiles(String sourcePath, String filenameWildcard) throws Exception {
        log.info("Searching for: {} from: {}", filenameWildcard, sourcePath);
        List<String> command = List.of(
                "powershell",
                "-Command",
                "Get-ChildItem -Path '" + sourcePath + "' -Recurse -File | Where-Object { $_.Name -like '" + filenameWildcard + "' } | Select-Object -ExpandProperty FullName"
        );

        List<String> files = runExecutable(command, true);
        if (!files.isEmpty()) {
            log.debug("Files found in the search are:");
            files.forEach(log::debug);

            log.debug("Showing the found files to the user so he could select which he wants");
            FileItem selectedFile = getSelectedFileByUser(files);

            if (selectedFile != null) {
                log.info("From the search, the selected file is: {}", selectedFile.getFullPath());
                fileListsLoader.setFocusedFileListPath(selectedFile.getFile().getParent());
                fileListsLoader.selectFileItem(true, selectedFile);
            } else
                log.info("User didn't select any file from the search results");
        } else {
            log.info("No files were found in the search results");
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "No files found :-(");
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    /** From a list of files, it shows the user a dialog and when he chooses a file, it returns the selected-by-user FileItem
     * or Null in the case of canceling... */
    private FileItem getSelectedFileByUser(List<String> files) {
        List<FileItem> fileItems = files.stream().map(filename -> new FileItem(new File(filename))).toList();
        JList<FileItem> fileList = new JList<>(fileItems.toArray(new FileItem[0]));
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileItem) {
                    setText(((FileItem) value).getFullPath());
                }
                return this;
            }
        });
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setPreferredSize(new Dimension(300, 200));

        int result = JOptionPane.showConfirmDialog(
                null,
                scrollPane,
                "Files Found, Select if you wish to browse to one",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        FileItem selectedFile = null;
        if (result == JOptionPane.OK_OPTION)
            selectedFile = fileList.getSelectedValue();

        return selectedFile;
    }

    @Override
    protected void doPack(List<FileItem> validItems, String archiveFilenameWithPath) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doUnpack(FileItem selectedItem, String destinationPath) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doExtractAll(FileItem selectedItem, String destinationPath) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doMergePDFs(List<FileItem> validItems, String newPdfFilenameWithPath) throws Exception {
        throw new Exception("Not implemented yet");
    }

    @Override
    protected void doExtractPDFPages(FileItem selectedItem, String destinationPath) throws Exception {
        throw new Exception("Not implemented yet");
    }
}