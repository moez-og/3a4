package gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import controllers.LoginController;
import services.UserService;
import models.User;
import java.io.InputStream;
import java.sql.SQLException;

public class LoginApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Travel Guide - Login");
        primaryStage.setResizable(true);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/Login.fxml"));
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            Scene scene = new Scene(root, 900, 700);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private StackPane createRootWithGradient() {
        StackPane root = new StackPane();

        try {
            // Charger BG (2).png - la belle image de Colosseum
            InputStream is = getClass().getResourceAsStream("/bg.png");
            if (is != null) {
                Image bgImage = new Image(is);
                ImageView bgImageView = new ImageView(bgImage);
                bgImageView.setFitWidth(900);
                bgImageView.setFitHeight(700);
                bgImageView.setPreserveRatio(false);
                root.getChildren().add(bgImageView);
                System.out.println("✓ bg.png chargée avec succès");
            } else {
                System.out.println("⚠ bg.png non trouvée, utilisation du gradient");
                applyGradient(root);
            }
        } catch (Exception e) {
            System.err.println("Erreur chargement bg.png: " + e.getMessage());
            applyGradient(root);
        }

        return root;
    }

    private void applyGradient(StackPane root) {
        Stop[] stops = new Stop[]{
            new Stop(0.0, Color.web("#e8dcc8")),      // Beige clair
            new Stop(0.3, Color.web("#d9b8a8")),      // Beige rosé
            new Stop(0.6, Color.web("#e0b080")),      // Beige doré
            new Stop(1.0, Color.web("#d5a89a"))       // Rose/beige
        };
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE, stops);
        root.setBackground(new Background(new BackgroundFill(gradient, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    private VBox createLoginForm() {
        VBox mainForm = new VBox(18);
        mainForm.setPrefWidth(420);
        mainForm.setMaxHeight(580);

        // Fond transparent pour voir l'image de fond
        mainForm.setStyle("-fx-background-color: transparent; -fx-background-radius: 20; -fx-border-radius: 20;");
        mainForm.setPadding(new Insets(50, 45, 45, 45));
        mainForm.setAlignment(Pos.TOP_CENTER);
        mainForm.setFillWidth(false);

        // Ajouter une ombre portée élégante
        DropShadow shadow = new DropShadow();
        shadow.setRadius(25);
        shadow.setOffsetX(0);
        shadow.setOffsetY(10);
        shadow.setColor(Color.color(0, 0, 0, 0.3));
        mainForm.setEffect(shadow);

        // Logo/Icon Section
        VBox logoBox = new VBox();
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPrefHeight(60);
        logoBox.setStyle("-fx-spacing: 10;");

        try {
            InputStream is = getClass().getResourceAsStream("/logo.png");
            if (is != null) {
                Image logoImage = new Image(is);
                ImageView logoImageView = new ImageView(logoImage);
                logoImageView.setFitHeight(150);
                logoImageView.setFitWidth(150);
                logoImageView.setPreserveRatio(true);
                logoBox.getChildren().add(logoImageView);
            } else {
                Label fallback = new Label("✈");
                fallback.setStyle("-fx-font-size: 48; -fx-text-fill: #c9920f;");
                logoBox.getChildren().add(fallback);
            }
        } catch (Exception e) {
            Label fallback = new Label("✈");
            fallback.setStyle("-fx-font-size: 48; -fx-text-fill: #c9920f;");
            logoBox.getChildren().add(fallback);
        }

        // Titre principal - élégant et moderne
        Label welcomeLabel = new Label("Welcome Traveler");
        welcomeLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        welcomeLabel.setTextFill(Color.web("#4b5f73"));
        welcomeLabel.setAlignment(Pos.CENTER);

        // Sous-titre avec description
        Label subtitleLabel = new Label("Sign in to continue exploring\nthe world's greatest destinations");
        subtitleLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #7f8c8d; -fx-line-spacing: 4;");
        subtitleLabel.setAlignment(Pos.CENTER);
        subtitleLabel.setWrapText(true);

        // Ligne séparatrice élégante
        Region separatorTop = new Region();
        separatorTop.setPrefHeight(1);
        separatorTop.setStyle("-fx-background-color: linear-gradient(to right, transparent, #d4af37, transparent);");

        // Section Email
        Label emailLabel = new Label("Email Address");
        emailLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #34495e; -fx-font-weight: bold;");

        TextField emailField = new TextField();
        emailField.setPromptText("example@travel.com");
        emailField.setPrefHeight(45);
        emailField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; " +
                "-fx-font-family: 'Segoe UI';");
        emailField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                emailField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                        "-fx-background-color: #ffffff; -fx-text-fill: #2c3e50; " +
                        "-fx-border-color: #d4af37; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; " +
                        "-fx-font-family: 'Segoe UI';");
            } else {
                emailField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                        "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                        "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; " +
                        "-fx-font-family: 'Segoe UI';");
            }
        });

        // Section Password
        Label passwordLabel = new Label("Password");
        passwordLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #34495e; -fx-font-weight: bold;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setPrefHeight(45);
        passwordField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; " +
                "-fx-font-family: 'Segoe UI';");
        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                passwordField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                        "-fx-background-color: #ffffff; -fx-text-fill: #2c3e50; " +
                        "-fx-border-color: #d4af37; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; " +
                        "-fx-font-family: 'Segoe UI';");
            } else {
                passwordField.setStyle("-fx-font-size: 13; -fx-padding: 12 15 12 15; " +
                        "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                        "-fx-border-color: transparent; -fx-border-radius: 8; -fx-background-radius: 8; " +
                        "-fx-font-family: 'Segoe UI';");
            }
        });

        // Remember & Forgot Password Row
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        optionsBox.setPrefHeight(30);

        CheckBox rememberCheckBox = new CheckBox("Remember me");
        rememberCheckBox.setStyle("-fx-font-size: 12; -fx-text-fill: #34495e; -fx-padding: 5;");

        Hyperlink forgotLink = new Hyperlink("Forgot Password?");
        forgotLink.setStyle("-fx-font-size: 12; -fx-text-fill: #c9920f; -fx-underline: true; -fx-padding: 0;");
        forgotLink.setOnAction(e -> showAlert("Info", "Password reset link will be sent to your email"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        optionsBox.getChildren().addAll(rememberCheckBox, spacer, forgotLink);

        // Login Button - Design raffiné avec gradient
        Button loginBtn = new Button("Explore Now");
        loginBtn.setPrefWidth(280);
        loginBtn.setPrefHeight(50);
        loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; " +
                "-fx-background-color: linear-gradient(to right, #d4af37, #c9920f); " +
                "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; " +
                "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.4), 10, 0, 0, 5);");
        loginBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        // Effets au survol
        loginBtn.setOnMouseEntered(e -> {
            loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; " +
                    "-fx-background-color: linear-gradient(to right, #e8c547, #d4af37); " +
                    "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; " +
                    "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.6), 15, 0, 0, 8);");
            loginBtn.setScaleY(1.02);
        });

        loginBtn.setOnMouseExited(e -> {
            loginBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white; " +
                    "-fx-background-color: linear-gradient(to right, #d4af37, #c9920f); " +
                    "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 0; " +
                    "-fx-effect: dropshadow(gaussian, rgba(212, 175, 55, 0.4), 10, 0, 0, 5);");
            loginBtn.setScaleX(1.0);
            loginBtn.setScaleY(1.0);
        });

        loginBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            String password = passwordField.getText();

            if (email.isEmpty() || password.isEmpty()) {
                showAlert("Validation", "Veuillez remplir tous les champs");
                return;
            }

            try {
                // Authentifier l'utilisateur avec la base de données
                UserService userService = new UserService();
                User utilisateur = userService.obtenirUtilisateurConnecte(email, password);

                if (utilisateur != null) {
                    // Connexion réussie - Rediriger vers UserDisplayApp
                    showSuccess("Succès", "Bienvenue " + utilisateur.getPrenom() + " " + utilisateur.getNom());
                    navigateToDashboard(utilisateur);
                } else {
                    // Identifiants invalides
                    showAlert("Erreur d'authentification", "Email ou mot de passe incorrect");
                    passwordField.clear();
                }
            } catch (SQLException ex) {
                showAlert("Erreur Base de Données", "Erreur lors de la connexion à la base de données:\n" + ex.getMessage());
                System.err.println("Erreur SQL: " + ex.getMessage());
                ex.printStackTrace();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Sign Up Link - Moderne et discret
        HBox signUpBox = new HBox(8);
        signUpBox.setAlignment(Pos.CENTER);
        signUpBox.setPrefHeight(25);

        Label signUpLabel = new Label("New to Travel Guide? ");
        signUpLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #7f8c8d;");

        Hyperlink signUpLink = new Hyperlink("Create account");
        signUpLink.setStyle("-fx-font-size: 12; -fx-text-fill: #c9920f; -fx-underline: true; -fx-padding: 0;");
        signUpLink.setOnAction(e -> showAlert("Info", "Registration coming soon!"));

        signUpBox.getChildren().addAll(signUpLabel, signUpLink);

        // Copyright - discret et professionnel
        Label copyrightLabel = new Label("© 2026 Travel Guide Inc. All rights reserved");
        copyrightLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #bdc3c7;");
        copyrightLabel.setAlignment(Pos.CENTER);

        // Assemble tous les éléments
        mainForm.getChildren().addAll(
                logoBox,
                welcomeLabel,
                subtitleLabel,
                separatorTop,
                emailLabel,
                emailField,
                passwordLabel,
                passwordField,
                optionsBox,
                loginBtn,
                signUpBox,
                copyrightLabel
        );

        return mainForm;
    }

    private void navigateToDashboard(User currentUser) {
        try {
            primaryStage.setTitle("Gestion des Utilisateurs");
            primaryStage.setWidth(1200);
            primaryStage.setHeight(700);
            UserDashboard dashboard = new UserDashboard(primaryStage, currentUser);
            Scene scene = new Scene(dashboard);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public static void main(String[] args) {
        launch(args);
    }
}
