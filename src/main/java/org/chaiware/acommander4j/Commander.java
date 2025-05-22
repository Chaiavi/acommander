package org.chaiware.acommander4j;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
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
    private ListView<String> leftFileList;
    @FXML
    private ListView<String> rightFileList;

    Properties properties = new Properties();


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


            switch (event.getCode()) {
                case F3 -> viewFile();
                case F4 -> editFile();
                case F5 -> copyFile();
                case F6 -> moveFile();
                case F7 -> makeDirectory();
                case F8 -> deleteFile();
                case F10 -> exitApp();
                case ENTER -> enterSelectedItem(getFocusedFileList());
                case TAB -> adjustTabBehavior(event);
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
        if (leftFileList.equals(getFocusedFileList()))
            rightFileList.requestFocus();
        else
            leftFileList.requestFocus();
        event.consume(); // Prevent default tab behavior
    }

    /**
     * Runs the action of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    private void enterSelectedItem(ListView<String> fileListView) {
        if (fileListView == null || fileListView.getItems().isEmpty() || fileListView.getSelectionModel().getSelectedItem() == null)
            return;

        String selectedItem = fileListView.getSelectionModel().getSelectedItem();
        String currentPath = ((ComboBox<String>) fileListView.getProperties().get("PathCombox")).getItems().get(0);
        Path target = Paths.get(currentPath, selectedItem).normalize();
        File f = target.toFile();

        if ("..".equals(selectedItem)) {
            File parent = new File(currentPath).getParentFile();
            if (parent != null) loadFolder(parent.getAbsolutePath(), fileListView);
        } else if (f.isDirectory()) {
            loadFolder(f.getAbsolutePath(), fileListView);
        } else {
            try {
                Desktop.getDesktop().open(f);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Loads the files in the path into the ListView
     */
    private void loadFolder(String path, ListView<String> fileListView) {
        File folder = new File(path);
        File[] files = folder.listFiles();
        ObservableList<String> items = FXCollections.observableArrayList();

        if (folder.getParentFile() != null)
            items.add("..");
        if (files != null)
            for (File f : files)
                items.add(f.getName());

        fileListView.setItems(items);
        ComboBox<String> folderNameCombox = (ComboBox<String>) fileListView.getProperties().get("PathCombox");
        folderNameCombox.getItems().clear();
        folderNameCombox.getItems().add(path);
        folderNameCombox.getSelectionModel().selectFirst();
    }

    /**
     * Returns the focused file list or NULL
     */
    private ListView<String> getFocusedFileList() {
        return leftFileList.isFocused() ? leftFileList :
                rightFileList.isFocused() ? rightFileList : null;
    }

    @FXML
    private void viewFile() {
        System.out.println("F3 View");
    }

    @FXML
    private void editFile() {
        System.out.println("F4 Edit");
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
