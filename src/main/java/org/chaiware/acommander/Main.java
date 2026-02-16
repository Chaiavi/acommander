package org.chaiware.acommander;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Commander.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon.png")));
        stage.getIcons().add(icon);
        stage.setMaximized(true);
        stage.setTitle("A Commander for Java");
        stage.setScene(scene);
        stage.show();

        Commander commander = loader.getController();
        stage.setOnCloseRequest(event -> commander.persistCurrentPaths());
        commander.setupBindings();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
