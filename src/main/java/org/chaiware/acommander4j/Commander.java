package org.chaiware.acommander4j;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

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
    IActions actions = new BasicActionsImpl();
    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    private ListView<FileItem> lastFocusedListView;


    @FXML
    public void initialize() {
        // Loads properties
        Path configFile = Paths.get(System.getProperty("user.dir"), "config", "acommander4j.properties");
        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Configuring Keyboard Bindings
        Platform.runLater(() -> rootPane.requestFocus());
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == ENTER) {
                if (leftPathComboBox.getEditor().isFocused()) {
                    loadFolder(leftPathComboBox.getEditor().getText(), leftFileList);
                    return;
                } else if (rightPathComboBox.getEditor().isFocused()) {
                    loadFolder(rightPathComboBox.getEditor().getText(), rightFileList);
                    return;
                }
            }

            try {
                switch (event.getCode()) {
                    case F3 -> viewFile();
                    case F4 -> editFile();
                    case F5 -> copyFile();
                    case F6 -> moveFile();
                    case F7 -> makeDirectory();
                    case F8 -> deleteFile();
                    case F10 -> exitApp();
                    case ENTER -> enterSelectedItem(lastFocusedListView);
                    case TAB -> adjustTabBehavior(event);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Mouse Double Click
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

        // Configure left & right defaults
        leftFileList.getProperties().put("PathCombox", leftPathComboBox);
        rightFileList.getProperties().put("PathCombox", rightPathComboBox);
        loadFolder(new File(properties.getProperty("left_folder")).getPath(), leftFileList);
        loadFolder(new File(properties.getProperty("right_folder")).getPath(), rightFileList);

        leftFileList.setCellFactory(lv -> new ListCell<FileItem>() {
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

        rightFileList.setCellFactory(lv -> new ListCell<FileItem>() {
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

        // Configure focus setting (so we will know where focus was last been)
        leftFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) lastFocusedListView = leftFileList;
        });
        rightFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) lastFocusedListView = rightFileList;
        });
        Platform.runLater(() -> leftFileList.requestFocus());

        leftPathComboBox.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == ENTER) {
                String path = leftPathComboBox.getValue();
                loadFolder(path, leftFileList);
            }
        });
        rightPathComboBox.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == ENTER) {
                String path = rightPathComboBox.getValue();
                loadFolder(path, rightFileList);
            }
        });
    }

    /**
     * Adjusts the TAB key behavior so it would go between file lists
     */
    private void adjustTabBehavior(KeyEvent event) {
        if (leftFileList.equals(lastFocusedListView))
            rightFileList.requestFocus();
        else
            leftFileList.requestFocus();
        event.consume(); // Prevent default tab behavior
    }

    /**
     * Runs the action of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    private void enterSelectedItem(ListView<FileItem> fileListView) {
        if (fileListView == null || fileListView.getItems().isEmpty() || fileListView.getSelectionModel().getSelectedItem() == null)
            return;

        String currentPath = ((ComboBox<String>) fileListView.getProperties().get("PathCombox")).getItems().get(0);
        FileItem selectedItem = fileListView.getSelectionModel().getSelectedItem();
        if ("..".equals(selectedItem.getPresentableFilename())) {
            File parent = new File(currentPath).getParentFile();
            if (parent != null) loadFolder(parent.getAbsolutePath(), fileListView);
        } else if (selectedItem.isDirectory()) {
            loadFolder(selectedItem.getFullPath(), fileListView);
        } else {
            try {
                Desktop.getDesktop().open(selectedItem.getFile());
            } catch (Exception ex) {
                logger.error("Failed opening: {}", selectedItem.getName(), ex);
            }
        }
    }

    /**
     * Loads the files in the path into the ListView
     */
    private void loadFolder(String path, ListView<FileItem> fileListView) {
        File folder = new File(path);
        File[] files = folder.listFiles();
        ObservableList<FileItem> items = FXCollections.observableArrayList();

        if (folder.getParentFile() != null)
            items.add(new FileItem(folder, ".."));
        if (files != null)
            for (File f : files)
                items.add(new FileItem(f));

        fileListView.setItems(items);
        ComboBox<String> folderNameCombox = (ComboBox<String>) fileListView.getProperties().get("PathCombox");
        folderNameCombox.getItems().clear();
        folderNameCombox.getItems().add(path);
        folderNameCombox.getSelectionModel().selectFirst();
    }

    @FXML
    private void viewFile() throws IOException {
        FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            actions.view(selectedItem);
    }

    @FXML
    private void editFile() throws IOException {
        FileItem selectedItem = lastFocusedListView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
            actions.edit(selectedItem);
    }

    @FXML
    private void copyFile() {
        System.out.println("F5 Copy");
    }

    @FXML
    private void moveFile() {
        System.out.println("F6 Move");
    }

    @FXML
    private void makeDirectory() {
        System.out.println("F7 MkDir");
    }

    @FXML
    private void deleteFile() {
        System.out.println("F8 Delete");
    }

    @FXML
    private void exitApp() {
        Platform.exit();
    }

    @FXML
    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "ACommander4J v1.0\nNorton Commander-style file manager");
        alert.setHeaderText("About");
        alert.showAndWait();
    }
}
