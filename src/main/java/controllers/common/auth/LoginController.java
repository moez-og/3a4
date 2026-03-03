package controllers.common.auth;

import gui.UserDashboard;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;

import java.net.URL;
import java.sql.SQLException;

public class LoginController {

    // IMPORTANT: fx:id="root" et fx:id="bgImage" existent déjà dans Login.fxml
    @FXML private StackPane root;
    @FXML private ImageView bgImage;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Hyperlink forgotLink;
    @FXML private Button loginBtn;
    @FXML private Hyperlink signUpLink;

    private Stage primaryStage;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    private void initialize() {

        // ✅ Fix principal: l’image de fond couvre TOUJOURS toute la fenêtre
        if (root != null) {
            root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        if (bgImage != null && root != null) {
            bgImage.fitWidthProperty().bind(root.widthProperty());
            bgImage.fitHeightProperty().bind(root.heightProperty());
            bgImage.setPreserveRatio(false);
            bgImage.setSmooth(true);
        }

        emailField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                emailField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; -fx-background-color: #ffffff; -fx-text-fill: #2c3e50; -fx-border-color: #d4af37; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-family: 'Segoe UI';");
            } else {
                emailField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; -fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-family: 'Segoe UI';");
            }
        });

        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                passwordField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; -fx-background-color: #ffffff; -fx-text-fill: #2c3e50; -fx-border-color: #d4af37; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-family: 'Segoe UI';");
            } else {
                passwordField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; -fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; -fx-font-family: 'Segoe UI';");
            }
        });

        loginBtn.setOnMouseEntered(e -> {
            loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: linear-gradient(to right, #e8c547, #d4af37); -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.6), 15, 0, 0, 8);");
            loginBtn.setScaleY(1.02);
        });

        loginBtn.setOnMouseExited(e -> {
            loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: linear-gradient(to right, #d4af37, #c9920f); -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.4), 10, 0, 0, 5);");
            loginBtn.setScaleX(1.0);
            loginBtn.setScaleY(1.0);
        });

        loginBtn.setOnAction(e -> doLogin());
        forgotLink.setOnAction(e -> showAlert("Info", "Password reset link will be sent to your email"));

        signUpLink.setOnAction(e -> {
            try {
                navigateToSignup();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur lors de l'ouverture du signup: " + ex.getMessage());
            }
        });
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Validation", "Veuillez remplir tous les champs");
            return;
        }

        try {
            UserService userService = new UserService();
            User utilisateur = userService.obtenirUtilisateurConnecte(email, password);

            if (utilisateur != null) {
                showSuccess("Succès", "Bienvenue " + utilisateur.getPrenom() + " " + utilisateur.getNom());
                navigateToDashboard(utilisateur);
            } else {
                showAlert("Erreur d'authentification", "Email ou mot de passe incorrect");
                passwordField.clear();
            }
        } catch (SQLException ex) {
            showAlert("Erreur Base de Données", "Erreur lors de la connexion à la base de données:\n" + ex.getMessage());
        } catch (Exception ex) {
            showAlert("Erreur", "Erreur: " + ex.getMessage());
        }
    }

    private void navigateToDashboard(User currentUser) throws Exception {
        Stage stage = (primaryStage != null) ? primaryStage : new Stage();
        stage.setTitle("Fin Tokhroj");
        stage.setWidth(1200);
        stage.setHeight(720);

        UserDashboard dashboard = new UserDashboard(stage, currentUser);
        Scene scene = new Scene(dashboard, 1200, 720);
        stage.setScene(scene);
        stage.show();
    }

    private void navigateToSignup() throws Exception {
        Stage stage = resolveStage();

        URL fxmlUrl = getClass().getResource("/fxml/common/auth/Signup.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable: /fxml/common/auth/Signup.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 700);
        stage.setTitle("Fin Tokhroj - Signup");
        stage.setResizable(true);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private Stage resolveStage() {
        if (primaryStage != null) return primaryStage;
        if (signUpLink != null && signUpLink.getScene() != null) {
            return (Stage) signUpLink.getScene().getWindow();
        }
        return new Stage();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}