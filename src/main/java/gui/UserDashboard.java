package gui;

import controllers.back.shell.BackDashboardController;
import controllers.front.shell.FrontDashboardController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import models.users.User;

import java.io.IOException;

public class UserDashboard extends BorderPane {

    private BackDashboardController controller;
    private FrontDashboardController frontController;

    public UserDashboard(Stage primaryStage) {
        this(primaryStage, null);
    }

    public UserDashboard(Stage primaryStage, User currentUser) {
        try {
            // ✅ Choix de shell selon le rôle (ADMIN -> Back, sinon -> Front)
            boolean isAdmin = isAdmin(currentUser);
            String shellPath = isAdmin ? "/fxml/back/shell/BackDashboard.fxml" : "/fxml/front/shell/FrontDashboard.fxml";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(shellPath));
            BorderPane root = loader.load();

            // ✅ On copie la structure du BorderPane chargé
            this.setTop(root.getTop());
            this.setLeft(root.getLeft());
            this.setCenter(root.getCenter());
            this.setRight(root.getRight());
            this.setBottom(root.getBottom());

            // ✅ styles
            this.setStyle(root.getStyle());
            this.getStylesheets().setAll(root.getStylesheets());
            this.getStyleClass().setAll(root.getStyleClass());

            // ✅ injection stage + user
            Object c = loader.getController();
            if (c instanceof BackDashboardController back) {
                this.controller = back;
                this.controller.setPrimaryStage(primaryStage);
                if (currentUser != null) this.controller.setCurrentUser(currentUser);
            } else if (c instanceof FrontDashboardController front) {
                this.frontController = front;
                this.frontController.setPrimaryStage(primaryStage);
                if (currentUser != null) this.frontController.setCurrentUser(currentUser);
            }

        } catch (IOException e) {
            e.printStackTrace();
            // fallback minimal (évite écran vide)
            this.setCenter(new javafx.scene.control.Label("Erreur chargement dashboard: " + e.getMessage()));
        }
    }

    private boolean isAdmin(User u) {
        if (u == null) return false;
        String r = u.getRole();
        if (r == null) return false;
        String rr = r.trim().toLowerCase();
        return rr.equals("admin") || rr.contains("admin");
    }

    public void showOn(Stage stage, String title) {
        if (stage.getScene() == null) {
            stage.setScene(new Scene(this, 1200, 700));
        } else {
            stage.getScene().setRoot(this);
        }
        stage.setTitle(title);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();
    }
}