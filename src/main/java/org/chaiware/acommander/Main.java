package org.chaiware.acommander;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Commander.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/app-theme.css")).toExternalForm()
        );
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png")));
        stage.getIcons().add(icon);
        stage.setMaximized(true);
        stage.setTitle("A Commander");
        stage.setScene(scene);

        Commander commander = loader.getController();
        commander.initializeTheme(scene);
        stage.show();
        stage.setOnCloseRequest(event -> commander.persistCurrentPaths());
        commander.setupBindings();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
