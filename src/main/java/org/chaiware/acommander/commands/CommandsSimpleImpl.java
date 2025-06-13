package org.chaiware.acommander.commands;

import javafx.scene.control.Alert;
import org.chaiware.acommander.FileItem;
import org.chaiware.acommander.FilesPanesHelper;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CommandsSimpleImpl extends ACommands {
    FilesPanesHelper filesPanesHelper;

    public CommandsSimpleImpl(FilesPanesHelper filesPanesHelper) {
        super(filesPanesHelper);
    }

    @Override
    public void rename(FileItem selectedItem, String newFilename) throws Exception {
        selectedItem.getFile().renameTo(new File(selectedItem.getFile().getParent(), newFilename));
        log.debug("Renamed: {} to {}", selectedItem.getName(), newFilename);
    }

    @Override
    public void edit(FileItem fileItem) throws Exception {

    }

    @Override
    public void view(FileItem fileItem) throws Exception {

    }

    @Override
    public void copy(FileItem sourceFile, String targetFolder) throws Exception {

    }

    @Override
    public void move(FileItem sourceFile, String targetFolder) throws Exception {
        Path source = Paths.get(sourceFile.getFullPath());
        Path target = Paths.get(targetFolder + "\\" + sourceFile.getName());

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        filesPanesHelper.refreshFileListViews();
        log.debug("Moved: {} to {}", sourceFile.getName(), targetFolder);
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {

    }

    @Override
    public void delete(FileItem selectedItem) throws IOException {

    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {

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
                fileListsLoader.setFileListPath(fileListsLoader.getFocusedSide(), selectedFile.getFile().getParent());
                fileListsLoader.getFocusedFileList().getSelectionModel().select(selectedFile);
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
    public void pack(FileItem selectedItem, String archiveFilename, String destinationPath) throws Exception {

    }

    @Override
    public void unpack(FileItem selectedItem, String destinationPath) throws Exception {

    }
}
