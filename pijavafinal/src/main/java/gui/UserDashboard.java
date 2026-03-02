package gui;

import controllers.back.shell.BackDashboardController;
import controllers.front.shell.FrontDashboardController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import models.users.User;

import java.io.IOException;

public class UserDashboard extends StackPane {

    private BackDashboardController backController;
    private FrontDashboardController frontController;

    public UserDashboard(Stage primaryStage) {
        this(primaryStage, null);
    }

    public UserDashboard(Stage primaryStage, User currentUser) {
        try {
            boolean isAdmin = isAdmin(currentUser);
            String shellPath = isAdmin
                    ? "/fxml/back/shell/BackDashboard.fxml"
                    : "/fxml/front/shell/FrontDashboard.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(shellPath));
            Parent root = loader.load(); // ✅ PLUS DE CAST

            // ✅ assure le remplissage de l'écran
            if (root instanceof Region r) {
                r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            }

            this.getChildren().setAll(root);

            // ✅ injection stage + user
            Object c = loader.getController();
            if (c instanceof BackDashboardController back) {
                this.backController = back;
                this.backController.setPrimaryStage(primaryStage);
                if (currentUser != null) this.backController.setCurrentUser(currentUser);
            } else if (c instanceof FrontDashboardController front) {
                this.frontController = front;
                this.frontController.setPrimaryStage(primaryStage);
                if (currentUser != null) this.frontController.setCurrentUser(currentUser);
            }

        } catch (IOException e) {
            e.printStackTrace();
            this.getChildren().setAll(new javafx.scene.control.Label(
                    "Erreur chargement dashboard: " + e.getMessage()
            ));
        }
    }

    private boolean isAdmin(User u) {
        if (u == null) return false;
        String r = u.getRole();
        if (r == null) return false;
        String rr = r.trim().toLowerCase();
        return rr.equals("admin") || rr.equals("partenaire") || rr.contains("admin");
    }

    public void showOn(Stage stage, String title) {
        if (stage.getScene() == null) {
            stage.setScene(new Scene(this, 1200, 720));
        } else {
            stage.getScene().setRoot(this);
        }
        stage.setTitle(title);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();
    }
}