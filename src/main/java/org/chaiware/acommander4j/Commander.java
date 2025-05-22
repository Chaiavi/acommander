package org.chaiware.acommander4j;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Commander {

    @FXML private BorderPane rootPane;
    @FXML private ComboBox<String> leftPathComboBox;
    @FXML private ComboBox<String> rightPathComboBox;
    @FXML private ListView<String> leftFileList;
    @FXML private ListView<String> rightFileList;

    @FXML
    public void initialize() {
        Platform.runLater(() -> rootPane.requestFocus());

        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case F3 -> viewFile();
                case F4 -> editFile();
                case F5 -> copyFile();
                case F6 -> moveFile();
                case F7 -> makeDirectory();
                case F8 -> deleteFile();
                case F10 -> exitApp();
            }
        });

        // Dummy example
        leftFileList.getItems().addAll("C:/file1.txt", "C:/file2.txt");
        rightFileList.getItems().addAll("C:/file3.txt", "C:/file4.txt");

        String currentPath = System.getProperty("user.dir");
        Path configFile = Paths.get(currentPath, "config", "acommander4j.properties");
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        leftPathComboBox.getItems().add(properties.getProperty("left_folder"));
        rightPathComboBox.getItems().add(properties.getProperty("right_folder"));

        leftPathComboBox.getSelectionModel().selectFirst();
        rightPathComboBox.getSelectionModel().selectFirst();
    }

    @FXML private void viewFile() { System.out.println("F3 View"); }
    @FXML private void editFile() { System.out.println("F4 Edit"); }
    @FXML private void copyFile() { System.out.println("F5 Copy"); }
    @FXML private void moveFile() { System.out.println("F6 Move"); }
    @FXML private void makeDirectory() { System.out.println("F7 MkDir"); }
    @FXML private void deleteFile() { System.out.println("F8 Delete"); }
    @FXML private void exitApp() { Platform.exit(); }
    @FXML private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "ACommander4J v1.0\nNorton Commander-style file manager");
        alert.setHeaderText("About");
        alert.showAndWait();
    }
}
