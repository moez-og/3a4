package controllers.common.auth;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import services.common.auth.OtpMemoryService;
import services.users.UserService;
import utils.PasswordUtil;

import java.net.URL;

public class ResetPasswordController {
    private static final int MIN_PASSWORD_LENGTH = 6;

    @FXML private Label emailLabel;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button saveBtn;
    @FXML private Button cancelBtn;

    private final OtpMemoryService otpService = OtpMemoryService.getInstance();
    private Stage primaryStage;
    private String email;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setEmail(String email) {
        this.email = (email == null) ? "" : email.trim();
        if (emailLabel != null) {
            emailLabel.setText(this.email);
        }
    }

    @FXML
    private void initialize() {
        saveBtn.setOnAction(e -> onSavePassword());
        cancelBtn.setOnAction(e -> {
            try {
                navigateToLogin();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur navigation login: " + ex.getMessage());
            }
        });
    }

    private void onSavePassword() {
        if (email == null || email.isBlank()) {
            showAlert("Erreur", "Session invalide");
            return;
        }

        if (!otpService.isResetAuthorized(email)) {
            showAlert("Accès refusé", "Vous devez d'abord valider OTP/Face ID.");
            return;
        }

        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            showAlert("Validation", "Veuillez remplir tous les champs.");
            return;
        }

        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            showAlert("Validation", "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showAlert("Validation", "Les mots de passe ne correspondent pas.");
            return;
        }

        try {
            UserService userService = new UserService();
            userService.mettreAJourMotDePasseParEmail(email, PasswordUtil.hashPassword(newPassword));
            otpService.clear(email);
            showInfo("Succès", "Mot de passe modifié avec succès.");
            navigateToLogin();
        } catch (Exception ex) {
            showAlert("Erreur", "Erreur mise à jour mot de passe: " + ex.getMessage());
        }
    }

    private void navigateToLogin() throws Exception {
        Stage stage = resolveStage();

        URL fxmlUrl = getClass().getResource("/fxml/common/auth/auth/Login.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable: /fxml/common/auth/Login.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        LoginController controller = loader.getController();
        controller.setPrimaryStage(stage);

        Scene scene = new Scene(root, 900, 700);
        stage.setTitle("Fin Tokhroj - Login");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private Stage resolveStage() {
        if (primaryStage != null) {
            return primaryStage;
        }
        if (saveBtn != null && saveBtn.getScene() != null) {
            return (Stage) saveBtn.getScene().getWindow();
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

    private void showInfo(String title, String message) {
        showAlert(title, message);
    }
}
