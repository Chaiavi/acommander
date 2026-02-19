package org.chaiware.acommander.commands;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;

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

        FileItem selectedItem = validItems.getFirst();
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
        Path source = Paths.get(sourceFile.getFullPath());
        if (sourceFile.isDirectory()) {
            Path targetDir = Paths.get(targetFolder);
            copyDirectory(source, targetDir);
        } else {
            Path target = Paths.get(targetFolder, sourceFile.getName());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        fileListsLoader.refreshFileListViews();
        log.debug("Copied: {} to {}", sourceFile.getName(), targetFolder);
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

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.walk(sourceDir).forEach(path -> {
            try {
                Path relative = sourceDir.relativize(path);
                Path target = targetDir.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
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
    public void openTerminal(String openHerePath) {
        List<String> command = Arrays.asList("cmd", "/c", "start", "powershell", "-NoExit", "-Command", "cd '" + openHerePath + "'");
        runExecutable(command, false)
                .thenAccept(output -> log.debug("Opened Powershell Here: {}", openHerePath))
                .exceptionally(throwable -> {
                    log.warn("PowerShell failed, trying Command Prompt: {}", throwable.getMessage());

                    // Fallback to Command Prompt
                    List<String> fallbackCommand = Arrays.asList("cmd", "/c", "start", "cmd", "/k", "cd /d " + openHerePath);
                    runExecutable(fallbackCommand, false)
                            .thenAccept(output -> log.debug("Opened Command Shell Here: {}", openHerePath))
                            .exceptionally(fallbackThrowable -> {
                                log.error("Both PowerShell and Command Prompt failed", fallbackThrowable);
                                return null;
                            });

                    return null;
                });
    }

    @Override
    public void openExplorer(String openHerePath) {
        List<String> command = Arrays.asList("explorer.exe", openHerePath);
        runExecutable(command, false);
        log.debug("Opened Explorer Here: {}", openHerePath);
    }

    @Override
    public void searchFiles(String sourcePath, String filenameWildcard) {
        log.info("Searching for: {} from: {}", filenameWildcard, sourcePath);
        List<String> command = List.of(
                "powershell",
                "-Command",
                "Get-ChildItem -Path '" + sourcePath + "' -Recurse -File | Where-Object { $_.Name -like '" + filenameWildcard + "' } | Select-Object -ExpandProperty FullName"
        );

        runExecutable(command, true)
                .thenAccept(output -> {
                    if (!output.isEmpty()) {
                        log.debug("Files found in the search are:");
                        output.forEach(log::debug);

                        Platform.runLater(() -> {
                            log.debug("Showing the found files to the user so they can select one");
                            FileItem selectedFile = getSelectedFileByUser(output);
                            if (selectedFile != null) {
                                log.info("From the search, the selected file is: {}", selectedFile.getFullPath());
                                fileListsLoader.setFocusedFileListPath(selectedFile.getFile().getParent());
                                fileListsLoader.selectFileItem(true, selectedFile);
                                ListView<FileItem> focusedList = fileListsLoader.getFileList(true);
                                focusedList.requestFocus();
                                int selectedIndex = focusedList.getSelectionModel().getSelectedIndex();
                                if (selectedIndex >= 0) {
                                    focusedList.getFocusModel().focus(selectedIndex);
                                }
                            } else {
                                log.info("User didn't select any file from the search results");
                            }
                        });
                    } else {
                        Platform.runLater(() -> {
                            log.info("No files were found in the search results");
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, "No files found :-(");
                            alert.setHeaderText(null);
                            alert.showAndWait();
                        });
                    }
                });
    }

    /**
     * From a list of files, it shows the user a dialog and when he chooses a file, it returns the selected-by-user FileItem
     * or Null in the case of canceling...
     */
    private FileItem getSelectedFileByUser(List<String> files) {
        List<FileItem> fileItems = files.stream().map(filename -> new FileItem(new File(filename))).toList();
        ListView<FileItem> fileList = new ListView<>();
        fileList.getItems().setAll(fileItems);
        fileList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFullPath());
            }
        });
        fileList.getSelectionModel().selectFirst();
        fileList.getFocusModel().focus(0);
        fileList.setPrefSize(980, 420);

        Dialog<FileItem> dialog = new Dialog<>();
        dialog.setTitle("Files Found");
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(fileList);
        ButtonType goToFileButton = new ButtonType("Go to File", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(goToFileButton, ButtonType.CANCEL);
        pane.setPrefSize(1020, 480);
        dialog.setResizable(true);
        dialog.setResultConverter(buttonType -> buttonType == goToFileButton ? fileList.getSelectionModel().getSelectedItem() : null);
        dialog.setOnShown(event -> Platform.runLater(() -> {
            fileList.requestFocus();
            fileList.getSelectionModel().selectFirst();
            fileList.getFocusModel().focus(0);
        }));

        pane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.setResult(null);
                dialog.close();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                dialog.setResult(fileList.getSelectionModel().getSelectedItem());
                dialog.close();
                event.consume();
            }
        });

        return dialog.showAndWait().orElse(null);
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

