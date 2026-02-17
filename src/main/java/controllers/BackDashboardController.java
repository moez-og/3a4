package controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.User;

import java.io.IOException;

public class BackDashboardController {
    private static final String USERS_VIEW_PATH = "/fxml/UserDashboard.fxml";

    @FXML private BorderPane root;
    @FXML private VBox dynamicContent;
    @FXML private Label pageTitle;
    @FXML private Label userName;
    @FXML private Label userRole;

    private Stage primaryStage;
    private User currentUser;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        updateUserLabels();
        loadUsersView();
    }

    @FXML
    private void initialize() {
        applyBaseStylesheet();
        updateUserLabels();
        loadUsersView();
    }

    @FXML
    private void refresh() {
        loadUsersView();
    }

    @FXML
    private void showDashboard() {
        setPageTitle("Dashboard");
        loadUsersView();
    }

    @FXML
    private void showUsersTable() {
        setPageTitle("Utilisateurs");
        loadUsersView();
    }

    @FXML
    private void showLieux() {
        setPageTitle("Lieux");
    }

    @FXML
    private void showOffres() {
        setPageTitle("Offres");
    }

    @FXML
    private void showEvents() {
        setPageTitle("Evenements");
    }

    @FXML
    private void goToFront() {
        // TODO: wire up front navigation when available
    }

    private void loadUsersView() {
        if (dynamicContent == null) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(USERS_VIEW_PATH));
            Node view = loader.load();
            UserDashboardController controller = loader.getController();
            if (controller != null) {
                controller.setPrimaryStage(primaryStage);
                if (currentUser != null) {
                    controller.setCurrentUser(currentUser);
                }
            }
            if (view instanceof Region region) {
                VBox.setVgrow(region, Priority.ALWAYS);
                region.setMaxWidth(Double.MAX_VALUE);
            }
            dynamicContent.getChildren().setAll(view);
        } catch (IOException e) {
            showLoadError(e);
        }
    }

    private void updateUserLabels() {
        if (userName == null || userRole == null) {
            return;
        }

        if (currentUser == null) {
            userName.setText("Utilisateur");
            userRole.setText("ABONNE");
            return;
        }

        String fullName = (currentUser.getPrenom() != null ? currentUser.getPrenom() : "")
                + " " + (currentUser.getNom() != null ? currentUser.getNom() : "");
        userName.setText(fullName.trim().isEmpty() ? "Utilisateur" : fullName.trim());
        String role = currentUser.getRole() != null ? currentUser.getRole().toUpperCase() : "";
        userRole.setText(role.isEmpty() ? "ABONNE" : role);
    }

    private void setPageTitle(String title) {
        if (pageTitle != null && title != null) {
            pageTitle.setText(title);
        }
    }

    private void applyBaseStylesheet() {
        if (root == null) {
            return;
        }
        var cssUrl = getClass().getResource("/styles/base-layout.css");
        if (cssUrl != null) {
            String css = cssUrl.toExternalForm();
            if (!root.getStylesheets().contains(css)) {
                root.getStylesheets().add(css);
            }
        }
    }

    private void showLoadError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText("Impossible de charger la vue");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
