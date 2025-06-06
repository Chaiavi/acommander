package org.chaiware.acommander;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import static java.awt.Desktop.getDesktop;
import static org.chaiware.acommander.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.FilesPanesHelper.FocusSide.RIGHT;

public class Commander {

    @FXML
    public BorderPane rootPane;
    @FXML
    public ComboBox<String> leftPathComboBox;
    @FXML
    public ComboBox<String> rightPathComboBox;
    @FXML
    public ListView<FileItem> leftFileList;
    @FXML
    public ListView<FileItem> rightFileList;

    Properties properties = new Properties();
    ICommands commands;

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    public FilesPanesHelper filesPanesHelper;


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        loadConfigFile();

        // Configure left & right defaults
        filesPanesHelper = new FilesPanesHelper(leftFileList, leftPathComboBox, rightFileList, rightPathComboBox);
        commands = new CommandsImpl(filesPanesHelper);
        configMouseDoubleClick();

        logger.debug("Loading file lists into the double panes file views");
        leftPathComboBox.setValue(new File(properties.getProperty("left_folder")).getPath());
        rightPathComboBox.setValue(new File(properties.getProperty("right_folder")).getPath());

        configPaneLookAndBehavior(leftFileList);
        configPaneLookAndBehavior(rightFileList);
        configFileListsFocus();

        filesPanesHelper.refreshFileListViews();
    }

    /** Setsup all of the keyboard bindings */
    public void setupBindings() {
        Scene scene = rootPane.getScene();
        KeyBindingManager keyBindingManager = new KeyBindingManager(this);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            keyBindingManager.handleKeyEvent(event);
        });
    }

    public KeyContext determineCurrentContext(Scene scene) {
        Node focused = scene.getFocusOwner();
        if (focused == leftFileList || focused == rightFileList) return KeyContext.FILE_PANE;
        if (focused == leftPathComboBox || focused == rightPathComboBox) return KeyContext.PATH_COMBO_BOX;
        return KeyContext.GLOBAL;
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

    private void configPaneLookAndBehavior(ListView<FileItem> listView) {
        logger.debug("Configuring the pane look and experience");
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
                    sizeLabel.setText(String.format("%s", item.gethumanReadableSize()));
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
            if (parent != null) {
                filesPanesHelper.getFocusedCombox().setValue(parent.getAbsolutePath());
                filesPanesHelper.refreshFileListViews();
            }
        } else if (selectedItem.isDirectory()) {
            filesPanesHelper.getFocusedCombox().setValue(selectedItem.getFullPath());
            filesPanesHelper.refreshFileListViews();
        } else {
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
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (selectedItem != null) { // todo ".." fileItem
                File currentFile = selectedItem.getFile();
                TextInputDialog dialog = new TextInputDialog(currentFile.getName());
                dialog.setTitle("File Rename");
                dialog.setHeaderText("");
                dialog.setContentText("New name");

                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) // if user dismisses the dialog it won't rename...
                    commands.rename(selectedItem, result.get());
            }
        } catch (Exception e) {
            error("Failed Renaming file", e);
        }
    }

    @FXML
    public void viewFile() {
        logger.info("View (F3)");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (selectedItem != null)
                commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    @FXML
    public void editFile() {
        logger.info("Edit (F4)");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (selectedItem != null) // todo what about the ".." fileItem ?
                commands.edit(selectedItem);
        } catch (Exception ex) {
            error("Failed Editing file", ex);
        }
    }

    @FXML
    public void copyFile() {
        logger.info("Copy (F5)");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (selectedItem != null) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.copy(selectedItem, targetFolder);
            }
        } catch (Exception e) {
            error("Failed Copying file", e);
        }
    }

    @FXML
    public void moveFile() {
        logger.info("Move (F6)");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (selectedItem != null) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.move(selectedItem, targetFolder);
            }
        } catch (Exception ex) {
            error("Failed Moving file", ex);
        }
    }

    @FXML
    public void makeDirectory() {
        logger.info("Create Directory (F7)");

        try {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Make Directory");
            dialog.setHeaderText("");
            dialog.setContentText("New Directory Name");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) // if user dismisses the dialog it won't create a directory...
                commands.mkdir((filesPanesHelper.getFocusedPath()), result.get());
        } catch (Exception e) {
            error("Failed Creating Directory", e);
        }
    }

    @FXML
    public void deleteFile() {
        logger.info("Delete (F8/DEL)");
        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        try {
            if (selectedItem != null)
                commands.delete(selectedItem);
        } catch (Exception ex) {
            error("Failed to delete: " + selectedItem.getName(), ex);
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

    @FXML
    public void exitApp() {
        logger.info("Exit App (F10)");
        Platform.exit();
    }

    @FXML
    public void pack() {
        logger.info("Pack (F11)");
        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        if (selectedItem != null)
            commands.pack(selectedItem);
    }

    @FXML
    public void unpackFile() {
        logger.info("UnPack (F12)");
        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        if (selectedItem != null)
            commands.unpack(selectedItem);
    }

    /** Alerts of an error and logs it */
    private void error(String error, Exception ex) {
        logger.error(error, ex);
        Alert alert = new Alert(Alert.AlertType.ERROR, error + " (" + ex.getMessage() + ")");
        alert.setHeaderText("Oops, error occurred");
        alert.showAndWait();
    }
}
