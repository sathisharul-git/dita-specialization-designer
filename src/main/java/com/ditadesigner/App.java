package com.ditadesigner;

import com.ditadesigner.ui.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        BorderPane root = loader.load();

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("DITA Specialization Designer");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.show();

        // Show the welcome dialog after the window is fully visible
        MainController controller = loader.getController();
        javafx.application.Platform.runLater(controller::showWelcomeDialog);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
