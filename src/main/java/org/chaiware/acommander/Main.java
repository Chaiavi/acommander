package org.chaiware.acommander;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Commander.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        stage.setMaximized(true);
        stage.setTitle("A Commander for Java");
        stage.setScene(scene);
        stage.show();

        Commander commander = loader.getController();
        commander.setupBindings();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
