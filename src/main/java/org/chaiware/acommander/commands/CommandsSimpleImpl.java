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
import java.util.List;

/** Not sure if this class will be needed */
public class CommandsSimpleImpl extends ACommands {
    public CommandsSimpleImpl(FilesPanesHelper fileListsLoader) {
        super(fileListsLoader);
    }

    @Override
    public void rename(List<FileItem> selectedItems, String newFilename) throws Exception {
        if (selectedItems.size() > 1)
            throw new Exception("No nice way to rename more than a single file using the simplest method");

        FileItem selectedItem = selectedItems.get(0);
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
        if (sourceFile.isDirectory())
            target = Paths.get(targetFolder + "\\");

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        fileListsLoader.refreshFileListViews();
        log.debug("Moved: {} to {}", sourceFile.getName(), targetFolder);
    }

    @Override
    public void mkdir(String parentDir, String newDirName) throws IOException {

    }

    @Override
    public void mkFile(String focusedPath, String newFileName) throws Exception {

    }

    @Override
    public void delete(List<FileItem> selectedItems) throws Exception {

    }

    @Override
    public void unlockDelete(List<FileItem> selectedItems) throws Exception {

    }

    @Override
    public void openTerminal(String openHerePath) throws Exception {

    }

    @Override
    public void openExplorer(String openHerePath) throws Exception {

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
    public void pack(List<FileItem> selectedItem, String archiveFilenameWithPath) throws Exception {

    }

    @Override
    public void unpack(FileItem selectedItem, String destinationPath) throws Exception {

    }

    @Override
    public void extractAll(FileItem selectedItem, String destinationPath) throws Exception {

    }

    @Override
    public void mergePDFs(List<FileItem> selectedItem, String newPdfFilenameWithPath) throws Exception {

    }

    @Override
    public void extractPDFPages(FileItem selectedItem, String destinationPath) throws Exception {

    }
}
