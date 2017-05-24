package sec_crawler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.Optional;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("ui.fxml"));
        primaryStage.setTitle("SEC Crawler");
        primaryStage.setScene(new Scene(root, 690, 816));
        primaryStage.setResizable(false);
        primaryStage.show();

        Platform.setImplicitExit(false);
        primaryStage.setOnCloseRequest(event -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Exit Application");
            alert.setHeaderText("Crawling will terminate!");
            alert.setContentText("Choose \"OK\" to end crawling. Otherwise, choose \"Cancel\".");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() != ButtonType.OK) {
                event.consume();
            } else {
                Platform.exit();
            }
        });
    }

    public static void main(String[] args) throws IOException{
        launch(args);
    }
}
