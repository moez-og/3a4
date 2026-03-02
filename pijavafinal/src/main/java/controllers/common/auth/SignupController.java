package controllers.common.auth;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;
import utils.PasswordUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class SignupController {
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final String STYLE_FOCUSED = "-fx-font-size: 12; -fx-padding: 10 12 10 12; -fx-background-color: #ffffff; -fx-text-fill: #2c3e50; -fx-border-color: #d4af37; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: 'Segoe UI';";
    private static final String STYLE_NORMAL = "-fx-font-size: 12; -fx-padding: 10 12 10 12; -fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-border-color: transparent; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: 'Segoe UI';";
    private static final String STYLE_INVALID = "-fx-font-size: 12; -fx-padding: 10 12 10 12; -fx-background-color: #fff5f5; -fx-text-fill: #2c3e50; -fx-border-color: #e74c3c; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-family: 'Segoe UI';";

    @FXML private TextField nameField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField imageUrlField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private CheckBox termsCheckBox;
    @FXML private Hyperlink privacyLink;
    @FXML private Button signupBtn;
    @FXML private Hyperlink signInLink;

    @FXML
    private void initialize() {
        setupFocusStyle(nameField);
        setupFocusStyle(prenomField);
        setupFocusStyle(emailField);
        setupFocusStyle(phoneField);
        setupFocusStyle(imageUrlField);
        setupFocusStyle(passwordField);
        setupFocusStyle(confirmPasswordField);

        attachClearOnChange(nameField);
        attachClearOnChange(prenomField);
        attachClearOnChange(emailField);
        attachClearOnChange(phoneField);
        attachClearOnChange(imageUrlField);
        attachClearOnChange(passwordField);
        attachClearOnChange(confirmPasswordField);

        signupBtn.setOnMouseEntered(e -> {
            signupBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: linear-gradient(to right, #e8c547, #d4af37); -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.6), 15, 0, 0, 8);");
            signupBtn.setScaleY(1.02);
        });
        signupBtn.setOnMouseExited(e -> {
            signupBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: linear-gradient(to right, #d4af37, #c9920f); -fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; -fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.4), 10, 0, 0, 5);");
            signupBtn.setScaleX(1.0);
            signupBtn.setScaleY(1.0);
        });

        signupBtn.setOnAction(e -> doSignup());
        privacyLink.setOnAction(e -> showAlert("Info", "La politique de confidentialite sera bientot disponible"));

        signInLink.setOnAction(e -> {
            try {
                navigateToLogin();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur lors de l'ouverture du login: " + ex.getMessage());
            }
        });
    }

    private void setupFocusStyle(TextInputControl field) {
        field.focusedProperty().addListener((obs, oldVal, newVal) -> applyStyle(field, newVal));
        applyStyle(field, field.isFocused());
    }

    private void applyStyle(TextInputControl field, boolean focused) {
        if (Boolean.TRUE.equals(field.getProperties().get("invalid"))) {
            field.setStyle(STYLE_INVALID);
            return;
        }
        field.setStyle(focused ? STYLE_FOCUSED : STYLE_NORMAL);
    }

    private void attachClearOnChange(TextInputControl field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(field.getProperties().get("invalid"))) {
                clearInvalid(field);
            }
        });
    }

    private void markInvalid(Control field, String message) {
        field.getProperties().put("invalid", true);
        Tooltip tooltip = new Tooltip(message);
        field.getProperties().put("errorTooltip", tooltip);
        Tooltip.install(field, tooltip);

        if (field instanceof TextInputControl textInput) {
            textInput.setStyle(STYLE_INVALID);
        } else {
            field.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
        }
    }

    private void clearInvalid(Control field) {
        field.getProperties().remove("invalid");
        Object tooltip = field.getProperties().remove("errorTooltip");
        if (tooltip instanceof Tooltip t) {
            Tooltip.uninstall(field, t);
        }

        if (field instanceof TextInputControl textInput) {
            applyStyle(textInput, textInput.isFocused());
        } else {
            field.setStyle("");
        }
    }

    private void doSignup() {
        String nom = nameField.getText().trim();
        String prenom = prenomField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String imageUrl = imageUrlField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        clearInvalid(nameField);
        clearInvalid(prenomField);
        clearInvalid(emailField);
        clearInvalid(phoneField);
        clearInvalid(imageUrlField);
        clearInvalid(passwordField);
        clearInvalid(confirmPasswordField);

        boolean hasError = false;
        if (nom.isEmpty()) { markInvalid(nameField, "Nom obligatoire"); hasError = true; }
        if (prenom.isEmpty()) { markInvalid(prenomField, "Prenom obligatoire"); hasError = true; }
        if (email.isEmpty()) { markInvalid(emailField, "Email obligatoire"); hasError = true; }
        if (password.isEmpty()) { markInvalid(passwordField, "Mot de passe obligatoire"); hasError = true; }
        if (confirmPassword.isEmpty()) { markInvalid(confirmPasswordField, "Confirmation obligatoire"); hasError = true; }

        if (hasError) {
            showAlert("Validation", "Veuillez corriger les champs en rouge");
            return;
        }

        if (!isValidEmail(email)) {
            markInvalid(emailField, "Format email invalide");
            showAlert("Validation", "Email invalide");
            return;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            markInvalid(passwordField, "Au moins " + MIN_PASSWORD_LENGTH + " caracteres");
            showAlert("Validation", "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caracteres");
            return;
        }

        if (!password.equals(confirmPassword)) {
            markInvalid(confirmPasswordField, "Les mots de passe ne correspondent pas");
            showAlert("Validation", "Les mots de passe ne correspondent pas");
            confirmPasswordField.clear();
            return;
        }

        if (!termsCheckBox.isSelected()) {
            showAlert("Validation", "Veuillez accepter les conditions d'utilisation");
            return;
        }

        try {
            UserService userService = new UserService();
            if (userService.emailExiste(email)) {
                markInvalid(emailField, "Cet email est deja utilise");
                showAlert("Validation", "Cet email est deja utilise");
                return;
            }

            String passwordHash = PasswordUtil.hashPassword(password);
            String telephone = phone.isEmpty() ? null : phone;
            String imageUrlValue = imageUrl.isEmpty() ? null : imageUrl;

            User user = new User(nom, prenom, email, passwordHash, "abonne", telephone, imageUrlValue);
            userService.ajouter(user);

            showSuccess("Succes", "Compte cree avec succes");
            clearForm();
        } catch (SQLException ex) {
            showAlert("Erreur Base de Donnees", "Erreur lors de la creation du compte:\n" + ex.getMessage());
        } catch (Exception ex) {
            showAlert("Erreur", "Erreur: " + ex.getMessage());
        }
    }

    private void clearForm() {
        nameField.clear();
        prenomField.clear();
        emailField.clear();
        phoneField.clear();
        imageUrlField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        termsCheckBox.setSelected(false);
    }

    private void navigateToLogin() throws Exception {
        Stage stage = resolveStage();

        URL fxmlUrl = getClass().getResource("/fxml/common/auth/Login.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable: /fxml/common/auth/Login.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        // Pour garder le même stage dans le LoginController
        LoginController controller = loader.getController();
        controller.setPrimaryStage(stage);

        Scene scene = new Scene(root, 900, 700);
        stage.setTitle("Travel Guide - Login");
        stage.setResizable(true);
        stage.setScene(scene);
        stage.show();
    }

    private Stage resolveStage() {
        if (signInLink != null && signInLink.getScene() != null) {
            return (Stage) signInLink.getScene().getWindow();
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

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
}