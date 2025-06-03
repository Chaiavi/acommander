package org.chaiware.acommander4j;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import static java.awt.Desktop.getDesktop;
import static javafx.scene.input.KeyCode.ENTER;

public class Commander {

    @FXML
    private BorderPane rootPane;
    @FXML
    private ComboBox<String> leftPathComboBox;
    @FXML
    private ComboBox<String> rightPathComboBox;
    @FXML
    private ListView<FileItem> leftFileList;
    @FXML
    private ListView<FileItem> rightFileList;

    Properties properties = new Properties();
    ICommands commands;

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    private ListView<FileItem> lastFocusedListView;
    FileListsLoader fileListsLoader;


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        Path configFile = Paths.get(System.getProperty("user.dir"), "config", "acommander.properties");
        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Configure left & right defaults
        leftFileList.getProperties().put("PathCombox", leftPathComboBox);
        rightFileList.getProperties().put("PathCombox", rightPathComboBox);
        fileListsLoader = new FileListsLoader(leftFileList, rightFileList);
        commands = new CommandsImpl(fileListsLoader);

        logger.debug("Configuring Keyboard Bindings");
        Platform.runLater(() -> rootPane.requestFocus());
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == ENTER) {
                if (leftPathComboBox.getEditor().isFocused()) {
                    fileListsLoader.loadFolder(leftPathComboBox.getEditor().getText(), leftFileList);
                    return;
                } else if (rightPathComboBox.getEditor().isFocused()) {
                    fileListsLoader.loadFolder(rightPathComboBox.getEditor().getText(), rightFileList);
                    return;
                }
            }

            try {
                switch (event.getCode()) {
                    case F2 -> renameFile();
                    case F3 -> viewFile();
                    case F4 -> editFile();
                    case F5 -> copyFile();
                    case F6 -> moveFile();
                    case F7 -> makeDirectory();
                    case F8, DELETE -> deleteFile();
                    case F9 -> terminalHere();
                    case F10 -> exitApp();
                    case ENTER -> enterSelectedItem(lastFocusedListView);
                    case TAB -> adjustTabBehavior(event);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        logger.debug("Configuring Mouse Double Click");
        leftFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem(leftFileList);
            }
        });
        rightFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem(rightFileList);
            }
        });

        logger.debug("Loading file lists into the double panes file views");
        fileListsLoader.loadFolder(new File(properties.getProperty("left_folder")).getPath(), leftFileList);
        fileListsLoader.loadFolder(new File(properties.getProperty("right_folder")).getPath(), rightFileList);

        logger.debug("Configuring the Left pane look and experience");
        leftFileList.setCellFactory(lv -> new ListCell<>() {
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
                    sizeLabel.setText(String.format("%s", item.getSize()));
                    dateLabel.setText(item.getDate());

                    // Constrain width to ListView cell
                    hbox.setMaxWidth(rightFileList.getWidth() - 20); // leave margin for scrollbar
                    hbox.setPrefWidth(rightFileList.getWidth() - 20);
                    rightFileList.widthProperty().addListener((obs, oldVal, newVal) -> {
                        hbox.setMaxWidth(newVal.doubleValue() - 20);
                        hbox.setPrefWidth(newVal.doubleValue() - 20);
                    });

                    setGraphic(hbox);
                }
            }
        });

        logger.debug("Configuring the right pane look and experience");
        rightFileList.setCellFactory(lv -> new ListCell<>() {
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
                    sizeLabel.setText(String.format("%s", item.getSize()));
                    dateLabel.setText(item.getDate());

                    // Constrain width to ListView cell
                    hbox.setMaxWidth(rightFileList.getWidth() - 20); // leave margin for scrollbar
                    hbox.setPrefWidth(rightFileList.getWidth() - 20);
                    rightFileList.widthProperty().addListener((obs, oldVal, newVal) -> {
                        hbox.setMaxWidth(newVal.doubleValue() - 20);
                        hbox.setPrefWidth(newVal.doubleValue() - 20);
                    });

                    setGraphic(hbox);
                }
            }
        });

        logger.debug("Configure focus setting (so we will know where focus was last been)");
        leftFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) lastFocusedListView = leftFileList;
        });
        rightFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) lastFocusedListView = rightFileList;
        });
        Platform.runLater(() -> leftFileList.requestFocus());

        logger.debug("Binding the ENTER key");
        leftPathComboBox.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == ENTER) {
                String path = leftPathComboBox.getValue();
                fileListsLoader.loadFolder(path, leftFileList);
            }
        });
        rightPathComboBox.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == ENTER) {
                String path = rightPathComboBox.getValue();
                fileListsLoader.loadFolder(path, rightFileList);
            }
        });
    }

    /**
     * Adjusts the TAB key behavior so it would go between file lists
     */
    private void adjustTabBehavior(KeyEvent event) {
        logger.debug("Adjusting the TAB binding");
        if (leftFileList.equals(lastFocusedListView))
            rightFileList.requestFocus();
        else
            leftFileList.requestFocus();
        event.consume(); // Prevent default tab behavior
    }

    /**
     * Runs the command of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    private void enterSelectedItem(ListView<FileItem> fileListView) {
        logger.debug("User clicked ENTER (or mouse double-click)");
        if (fileListView == null || fileListView.getItems().isEmpty() || fileListView.getSelectionModel().getSelectedItem() == null)
            return;

        String currentPath = ((ComboBox<String>) fileListView.getProperties().get("PathCombox")).getItems().get(0);
        FileItem selectedItem = fileListView.getSelectionModel().getSelectedItem();
        logger.debug("Running: {}", selectedItem.getName());
        if ("..".equals(selectedItem.getPresentableFilename())) {
            File parent = new File(currentPath).getParentFile();
            if (parent != null) fileListsLoader.loadFolder(parent.getAbsolutePath(), fileListView);
        } else if (selectedItem.isDirectory()) {
            fileListsLoader.loadFolder(selectedItem.getFullPath(), fileListView);
        } else {
            try {
                getDesktop().open(selectedItem.getFile());
            } catch (Exception ex) {
                logger.error("Failed opening: {}", selectedItem.getName(), ex);
            }
        }
    }

    @FXML
    private void help() {
        logger.info("Show Help (F1)");
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "ACommander4J v1.0\nNorton Commander-style file manager");
        alert.setHeaderText("About");
        alert.showAndWait();
    }

    @FXML
    private void renameFile() {
        logger.info("Rename (F2)");

        try {
            FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
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
    private void viewFile() {
        logger.info("View (F3)");

        try {
            FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null)
                commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    @FXML
    private void editFile() {
        logger.info("Edit (F4)");

        try {
            FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null)
                commands.edit(selectedItem);
        } catch (Exception ex) {
            error("Failed Editing file", ex);
        }
    }

    @FXML
    private void copyFile() {
        logger.info("Copy (F5)");

        try {
            FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                String targetFolder = ((ComboBox<String>) rightFileList.getProperties().get("PathCombox")).getSelectionModel().getSelectedItem();
                if (lastFocusedListView == rightFileList)
                    targetFolder = ((ComboBox<String>) leftFileList.getProperties().get("PathCombox")).getSelectionModel().getSelectedItem();

                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.copy(selectedItem, targetFolder);
            }
        } catch (Exception e) {
            error ("Failed Copying file", e);
        }
    }

    @FXML
    private void moveFile() {
        logger.info("Move (F6)");

        try {
            FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                String targetFolder = ((ComboBox<String>) rightFileList.getProperties().get("PathCombox")).getSelectionModel().getSelectedItem();
                if (lastFocusedListView == rightFileList)
                    targetFolder = ((ComboBox<String>) leftFileList.getProperties().get("PathCombox")).getSelectionModel().getSelectedItem();

                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.move(selectedItem, targetFolder);
            }
        } catch (Exception ex) {
            error("Failed Moving file", ex);
        }
    }

    @FXML
    private void makeDirectory() {
        logger.info("Create Directory (F7)");
    }

    @FXML
    private void deleteFile() {
        logger.info("Delete (F8/DEL)");
        FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
        try {
            if (selectedItem != null)
                commands.delete(selectedItem);
        } catch (Exception ex) {
            error("Failed to delete: " + selectedItem.getName(), ex);
        }
    }

    @FXML
    private void terminalHere() {
        logger.info("Open Terminal Here (F9)");
        String openHerePath = ((ComboBox<String>) lastFocusedListView.getProperties().get("PathCombox")).getValue();
        try {
            commands.openTerminal(openHerePath);
        } catch (Exception ex) {
            error("Failed starting command line shell here: " + openHerePath, ex);
        }
    }

    @FXML
    private void exitApp() {
        logger.info("Exit App (F10)");
        Platform.exit();
    }

    @FXML
    private void pack() {
        logger.info("Pack (F11)");
        FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            commands.pack(selectedItem);
    }

    @FXML
    private void unpackFile() {
        logger.info("UnPack (F12)");
        FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            commands.unpack(selectedItem);
    }

    @FXML
    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "ACommander v1.0\nNorton Commander-style file manager");
        alert.setHeaderText("About");
        alert.showAndWait();
    }

    /** Alerts of an error and logs it */
    private void error(String error, Exception ex) {
        logger.error(error, ex);
        Alert alert = new Alert(Alert.AlertType.ERROR, error + " (" + ex.getMessage() + ")");
        alert.setHeaderText("Oops, error occurred");
        alert.showAndWait();
    }
}
