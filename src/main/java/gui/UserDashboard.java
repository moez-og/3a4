package gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import controllers.UserDashboardController;
import java.io.IOException;

public class UserDashboard extends BorderPane {

    private UserDashboardController controller;

    public UserDashboard(Stage primaryStage) {
        this(primaryStage, null);
    }

    public UserDashboard(Stage primaryStage, models.User currentUser) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/UserDashboard.fxml"));
            BorderPane root = loader.load();
            
            // On copie le contenu du root chargé dans ce BorderPane
            this.setTop(root.getTop());
            this.setLeft(root.getLeft());
            this.setCenter(root.getCenter());
            this.setRight(root.getRight());
            this.setBottom(root.getBottom());
            this.setStyle(root.getStyle());
            
            this.controller = loader.getController();
            if (this.controller != null) {
                this.controller.setPrimaryStage(primaryStage);
                if (currentUser != null) {
                    this.controller.setCurrentUser(currentUser);
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showDashboard() {
        if (controller != null) {
            // La logique est maintenant dans le contrôleur
            // initialize() est appelé automatiquement par le FXMLLoader
        }
    }
}
