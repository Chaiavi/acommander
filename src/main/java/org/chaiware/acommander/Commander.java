package org.chaiware.acommander;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import org.chaiware.acommander.actions.ActionContext;
import org.chaiware.acommander.actions.ActionRegistry;
import org.chaiware.acommander.commands.ACommands;
import org.chaiware.acommander.commands.CommandsAdvancedImpl;
import org.chaiware.acommander.helpers.ComboBoxSetup;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.palette.CommandPaletteController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.awt.Desktop.getDesktop;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;


public class Commander {

    @FXML
    public BorderPane rootPane;
    @FXML
    public ComboBox<Folder> leftPathComboBox;
    @FXML
    public ComboBox<Folder> rightPathComboBox;
    @FXML
    public ListView<FileItem> leftFileList;
    @FXML
    public ListView<FileItem> rightFileList;
    @FXML
    Button btnF1, btnF2, btnF3, btnF4, btnF5, btnF6, btnF7, btnF8, btnF9, btnF10, btnF11, btnF12;
    @FXML
    private CommandPaletteController commandPaletteController;

    Properties properties = new Properties();
    ACommands commands;

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    public FilesPanesHelper filesPanesHelper;


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        loadConfigFile();

        // Configure left & right defaults
        filesPanesHelper = new FilesPanesHelper(leftFileList, leftPathComboBox, rightFileList, rightPathComboBox);
        commands = new CommandsAdvancedImpl(filesPanesHelper);
        configMouseDoubleClick();

        logger.debug("Loading file lists into the double panes file views");
        ComboBoxSetup setup = new ComboBoxSetup();
        setup.setupComboBox(leftPathComboBox);
        setup.setupComboBox(rightPathComboBox);
        filesPanesHelper.setFileListPath(LEFT, new File(properties.getProperty("left_folder")).getPath());
        filesPanesHelper.setFileListPath(RIGHT, new File(properties.getProperty("right_folder")).getPath());

        leftPathComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                filesPanesHelper.refreshFileListView(LEFT);
            }
        });
        rightPathComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                filesPanesHelper.refreshFileListView(RIGHT);
            }
        });

        configListViewLookAndBehavior(leftFileList);
        configListViewLookAndBehavior(rightFileList);
        configFileListsFocus();
        commandPaletteController.configure(new ActionRegistry(), new ActionContext(this));

        updateBottomButtons(null);
        filesPanesHelper.refreshFileListViews();
        filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        Platform.runLater(() -> leftFileList.requestFocus());
    }

    /** Setup all of the keyboard bindings */
    public void setupBindings() {
        Scene scene = rootPane.getScene();
        KeyBindingManager keyBindingManager = new KeyBindingManager(this);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            keyBindingManager.handleKeyEvent(event);
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            keyBindingManager.handleReleasedKeyEvent(event);
        });
    }

    public KeyContext determineCurrentContext(Scene scene) {
        if (isCommandPaletteOpen())
            return KeyContext.COMMAND_PALETTE;

        Node focused = scene.getFocusOwner();

        // Check if we're in a JavaFX Dialog
        if (focused != null) {
            Window focusedWindow = focused.getScene().getWindow();
            // Check if the focused window is different from main window (likely a dialog)
            if (focusedWindow != scene.getWindow())
                return KeyContext.DIALOG;
        }

        if (focused == leftFileList || focused == rightFileList) return KeyContext.FILE_PANE;
        if (focused == leftPathComboBox || focused == rightPathComboBox) return KeyContext.PATH_COMBO_BOX;
        return KeyContext.GLOBAL;
    }

    public void openCommandPalette() {
        commandPaletteController.open();
    }

    public void closeCommandPalette() {
        commandPaletteController.close();
        if (filesPanesHelper.getFocusedSide() == LEFT)
            leftFileList.requestFocus();
        else
            rightFileList.requestFocus();
    }

    public boolean isCommandPaletteOpen() {
        return commandPaletteController != null && commandPaletteController.isOpen();
    }

    public void executeCommandPaletteSelection() {
        commandPaletteController.executeSelected();
    }

    public void selectNextCommandPaletteAction() {
        commandPaletteController.selectNext();
    }

    public void selectPreviousCommandPaletteAction() {
        commandPaletteController.selectPrevious();
    }

    private void loadConfigFile() {
        Path configFile = Paths.get(System.getProperty("user.dir"), "config", "acommander.properties");
        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void configMouseDoubleClick() {
        logger.debug("Configuring Mouse Double Click");
        leftFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem();
            }
        });
        rightFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem();
            }
        });
    }

    private void configListViewLookAndBehavior(ListView<FileItem> listView) {
        logger.debug("Configuring the ListViews look and experience");
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new ListCell<>() {
            final Label nameLabel = new Label();
            final Label sizeLabel = new Label();
            final Label dateLabel = new Label();
            final HBox hbox = new HBox(nameLabel, sizeLabel, dateLabel);

            {
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setEllipsisString("...");
                nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                sizeLabel.setMinWidth(100);
                sizeLabel.setMaxWidth(100);
                sizeLabel.setAlignment(Pos.CENTER_RIGHT);

                dateLabel.setMinWidth(120);
                dateLabel.setMaxWidth(120);
                dateLabel.setAlignment(Pos.CENTER_RIGHT);

                hbox.setSpacing(10);
                hbox.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: 14px; -fx-hbar-policy: never;");
            }

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getPresentableFilename());
                    sizeLabel.setText(String.format("%s", item.getHumanReadableSize()));
                    dateLabel.setText(item.getDate());

                    // Constrain width to ListView cell
                    hbox.setMaxWidth(rightFileList.getWidth() - 20); // leave margin for scrollbar
                    hbox.setPrefWidth(rightFileList.getWidth() - 20);
                    listView.widthProperty().addListener((obs, oldVal, newVal) -> {
                        hbox.setMaxWidth(newVal.doubleValue() - 20);
                        hbox.setPrefWidth(newVal.doubleValue() - 20);
                    });

                    setGraphic(hbox);
                }
            }
        });
    }

    private void configFileListsFocus() {
        logger.debug("Configure focus setting (so we will know where focus was last been)");
        leftFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) filesPanesHelper.setFocusedFileList(LEFT);
        });
        rightFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) filesPanesHelper.setFocusedFileList(RIGHT);
        });
        leftFileList.requestFocus();
    }

    /**
     * Runs the command of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    public void enterSelectedItem() {
        logger.debug("User clicked ENTER (or mouse double-click)");

        String currentPath = filesPanesHelper.getFocusedPath();
        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        logger.debug("Running: {}", selectedItem.getName());
        if ("..".equals(selectedItem.getPresentableFilename())) {
            File parent = new File(currentPath).getParentFile();
            if (parent != null)
                filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
        } else if (selectedItem.isDirectory())
            filesPanesHelper.setFocusedFileListPath(selectedItem.getFullPath());
        else {
            try {
                getDesktop().open(selectedItem.getFile());
            } catch (Exception ex) {
                logger.error("Failed opening: {}", selectedItem.getName(), ex);
            }
        }
    }

    @FXML
    public void help() {
        logger.info("Help (F1)");

        try {
            File helpFile = (Paths.get(System.getProperty("user.dir"), "config", "f1-help.md")).toFile();
            FileItem selectedItem = new FileItem(helpFile, helpFile.getName());
            commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    @FXML
    public void renameFile() {
        logger.info("Rename (F2)");

        try {
            List<FileItem> selectedItems = commands.filterValidItems(filesPanesHelper.getSelectedItems());
            if (selectedItems.isEmpty())
                return;
            if (selectedItems.size() == 1) {
                FileItem selectedItem = selectedItems.getFirst();
                Optional<String> result = getUserFeedback(selectedItem.getFile().getName(), "File Rename", "New name");
                if (result.isPresent()) { // if user dismisses the dialog it won't rename...
                    commands.rename(Collections.singletonList(selectedItem), result.get());
                    FileItem renamedFileItem = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                    filesPanesHelper.selectFileItem(true, renamedFileItem);
                }
            } else // Multi files selected (multi rename)
                commands.rename(selectedItems, "");
        } catch (Exception e) {
            error("Failed Renaming file/s", e);
        }
    }

    @FXML
    public void viewFile() {
        logger.info("View (F3)");

        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            for (FileItem selectedItem : selectedItems)
                commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    public void calculateDirSpace() {
        logger.info("calculateDirSpace (F3 (on folder))");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (!selectedItem.isDirectory()) {
                logger.error("Error: Trying to calculate size of a file and not a folder ??");
                return;
            }

            long sizeOfFolder = Files.walk(selectedItem.getFile().toPath())
                    .parallel()
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();

            selectedItem.setSize(sizeOfFolder);
            filesPanesHelper.getFileList(true).refresh();
        } catch (Exception ex) {
            error("Failed calculating folder size", ex);
        }
    }

    @FXML
    public void editFile() {
        logger.info("Edit (F4)");

        try {
            List<FileItem> fileItems = filesPanesHelper.getSelectedItems();
            for (FileItem fileItem : fileItems)
                commands.edit(fileItem);
        } catch (Exception ex) {
            error("Failed Editing file", ex);
        }
    }

    @FXML
    public void copyFile() {
        logger.info("Copy (F5)");

        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.copy(selectedItem, targetFolder);

                // taking care of the selected files
                File target = selectedItem.isDirectory()
                        ? new File(targetFolder)
                        : new File(targetFolder, selectedItem.getName());
                filesPanesHelper.selectFileItem(false, new FileItem(target));
            }
        } catch (Exception e) {
            error("Failed Copying file", e);
        }
    }

    @FXML
    public void moveFile() {
        logger.info("Move (F6)");

        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.move(selectedItem, targetFolder);

                // taking care of the selected files
                filesPanesHelper.getFileList(true)
                        .getSelectionModel()
                        .selectFirst();
                File target = selectedItem.isDirectory()
                        ? new File(targetFolder)
                        : new File(targetFolder, selectedItem.getName());
                filesPanesHelper.selectFileItem(false, new FileItem(target));
            }
        } catch (Exception ex) {
            error("Failed Moving file", ex);
        }
    }

    @FXML
    public void makeDirectory() {
        logger.info("Create Directory (F7)");

        try {
            Optional<String> result = getUserFeedback("", "Make Directory", "New Directory Name");
            if (result.isPresent()) { // if user dismisses the dialog it won't create a directory...
                commands.mkdir((filesPanesHelper.getFocusedPath()), result.get());
                FileItem newFolder = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                filesPanesHelper.selectFileItem(true, newFolder);
            }
        } catch (Exception e) {
            error("Failed Creating Directory", e);
        }
    }

    public void makeFile() {
        logger.info("Create File (ALT+F7)");

        try {
            Optional<String> result = getUserFeedback("", "Make File", "New File Name");
            if (result.isPresent()) {// if user dismisses the dialog it won't create a file...
                commands.mkFile((filesPanesHelper.getFocusedPath()), result.get());
                FileItem newFile = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                filesPanesHelper.selectFileItem(true, newFile);
            }
        } catch (Exception e) {
            error("Failed Creating File", e);
        }
    }

    @FXML
    public void deleteFile() {
        logger.info("Delete (F8/DEL)");
        try {
            commands.delete(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        } catch (Exception ex) {
            error("Failed to delete", ex);
        }
    }

    public void deleteWipe() {
        logger.info("Delete & Wipe (Shift+F8/DEL)");
        try {
            commands.wipeDelete(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        } catch (Exception ex) {
            error("Failed to delete", ex);
        }
    }

    @FXML
    public void terminalHere() {
        logger.info("Open Terminal Here (F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            commands.openTerminal(openHerePath);
        } catch (Exception ex) {
            error("Failed starting command line shell here: " + openHerePath, ex);
        }
    }

    public void explorerHere() {
        logger.info("Open Explorer Here (ALT+F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            commands.openExplorer(openHerePath);
        } catch (Exception ex) {
            error("Failed opening explorer here: " + openHerePath, ex);
        }
    }

    @FXML
    public void search() {
        logger.info("Search Files (F10)");

        Optional<String> result = getUserFeedback("", "Search for File/s", "Enter (partial/wildcard) filename");
        if (result.isPresent()) {
            String searchFromPath = filesPanesHelper.getFocusedPath();
            try {
                commands.searchFiles(searchFromPath, result.get().contains("*") ? result.get() : "*" + result.get() + "*");
            } catch (Exception e) {
                error("Failed searching for: " + result.get(), e);
            }
        }
    }

    @FXML
    public void pack() {
        logger.info("Pack (F11)");
        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            String firstFilename = selectedItems.getFirst().getName();
            String zipFilename = firstFilename.contains(".")
                    ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".zip"
                    : firstFilename + ".zip";
            Optional<String> result = getUserFeedback(zipFilename, "Pack to zip", "Zip filename");
            if (result.isPresent()) {
                String filenameWithPath = filesPanesHelper.getUnfocusedPath() + "\\" + result.get();
                commands.pack(selectedItems, filenameWithPath);
                FileItem packedFile = new FileItem(new File(filenameWithPath));
                filesPanesHelper.selectFileItem(false, packedFile);
            } else
                logger.info("User cancelled the packing");
        } catch (Exception e) {
            error("Failed Packing file", e);
        }
    }

    @FXML
    public void unpackFile() {
        logger.info("UnPack (F12)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.unpack(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed UNPacking file", e);
        }
    }

    public void extractAll() {
        logger.info("Extract All (ALT+F12)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractAll(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed UNPacking file", e);
        }
    }

    public void mergePDFFiles() {
        logger.info("Merge PDF Files (SHIFT+F1)");
        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            String firstFilename = selectedItems.getFirst().getName();
            String zipFilename = firstFilename.contains(".")
                    ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".pdf"
                    : firstFilename + ".pdf";
            Optional<String> result = getUserFeedback(zipFilename, "Merge PDF Files", "PDF filename");
            if (result.isPresent()) {
                FileItem mergedFile = new FileItem(new File(filesPanesHelper.getUnfocusedPath() + "\\" + result.get()));
                commands.mergePDFs(selectedItems, mergedFile.getFullPath());
                filesPanesHelper.selectFileItem(false, mergedFile);
            } else
                logger.info("User cancelled the packing");
        } catch (Exception e) {
            error("Failed Packing file", e);
        }
    }

    public void extractPDFPages() {
        logger.info("Extract PDF Pages (SHIFT+F2)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractPDFPages(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed Extracting Pages from PDF file", e);
        }
    }

    public void filterByChar(char selectedChar) {
        ObservableList<FileItem> fileItems = filesPanesHelper.getFileList(true).getItems();
        fileItems.stream()
                .filter(f -> f.getName().toLowerCase().startsWith(String.valueOf(selectedChar)))
                .findFirst().ifPresent(match -> filesPanesHelper.selectFileItem(true, match));
    }

    /** Opens a dialog with the title asking the requested question returning the optional user's input */
    private Optional<String> getUserFeedback(String defaultValue, String title, String question) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setHeaderText("");
        dialog.setTitle(title);
        dialog.setContentText(question);
        dialog.getEditor().setPrefWidth(300);

        return dialog.showAndWait();
    }

    /** Alerts of an error and logs it */
    private void error(String error, Exception ex) {
        logger.error(error, ex);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(error + " (" + ex.getMessage() + ")");
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    public void updateBottomButtons(KeyCode whichKeyWasPressed) {
        switch (whichKeyWasPressed) {
            case null -> {
                btnF1.setText("F1 Help");
                btnF2.setText("F2 Rename");
                btnF3.setText("F3 View");
                btnF4.setText("F4 Edit");
                btnF5.setText("F5 Copy");
                btnF6.setText("F6 Move");
                btnF7.setText("F7 MkDir");
                btnF8.setText("F8 Delete");
                btnF9.setText("F9 Terminal");
                btnF10.setText("F10 Search");
                btnF11.setText("F11 Pack");
                btnF12.setText("F12 UnPack");
            }
            case ALT -> {
                btnF1.setText("ALT+F1 Left Folder");
                btnF2.setText("ALT+F2 Right Folder");
                btnF4.setText("ALT+F4 Exit");
                btnF7.setText("ALT+F7 MkFile");
                btnF9.setText("ALT+F9 Explorer");
                btnF12.setText("ALT+F12 Extract All");
            }
            case SHIFT -> {
                btnF1.setText("SHIFT+F1 Merge PDF");
                btnF2.setText("SHIFT+F2 Extract PDF");
                btnF6.setText("SHIFT+F6 Rename");
                btnF8.setText("SHIFT+F8 Delete & Wipe");
            }
            case CONTROL -> {
            }

            default -> throw new IllegalStateException("Which key was pressed?: " + whichKeyWasPressed);
        }
    }
}
