package controllers.common.auth;

import gui.UserDashboard;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.animation.Interpolator;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import services.common.auth.GoogleOAuthService;
import services.users.UserService;
import services.common.auth.GmailOtpMailService;
import services.common.auth.OtpMemoryService;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.prefs.Preferences;
import utils.PasswordUtil;

public class LoginController {
    private static final Pattern SUCCESS_TRUE_PATTERN = Pattern.compile("\\\"success\\\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final String PREF_REMEMBER_ME = "rememberMe";
    private static final String PREF_REMEMBERED_EMAIL = "rememberedEmail";
    private static final int OTP_EXPIRY_SECONDS = 60;
    private final Preferences preferences = Preferences.userNodeForPackage(LoginController.class);
    private final OtpMemoryService otpService = OtpMemoryService.getInstance();
    private final GmailOtpMailService otpMailService = new GmailOtpMailService();

    // IMPORTANT: fx:id="root" et fx:id="bgImage" existent déjà dans Login.fxml
    @FXML private StackPane root;
    @FXML private ImageView bgImage;
    @FXML private StackPane avatarPane;
    @FXML private Circle leftPupil;
    @FXML private Circle rightPupil;
    @FXML private Circle leftHand;
    @FXML private Circle rightHand;
    @FXML private Label avatarMouth;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberCheckBox;
    @FXML private Hyperlink forgotLink;
    @FXML private Button loginBtn;
    @FXML private Button googleLoginBtn;
    @FXML private Button faceLoginBtn;
    @FXML private Hyperlink signUpLink;

    private Stage primaryStage;
    private Timeline avatarTimeline;

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

        loadRememberedCredentials();
        setupAvatarBehavior();

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
        if (googleLoginBtn != null) {
            googleLoginBtn.setOnAction(e -> doGoogleLogin());
        }
        if (faceLoginBtn != null) {
            faceLoginBtn.setOnAction(e -> doFaceLogin());
        }
        forgotLink.setOnAction(e -> doForgotPassword());

        signUpLink.setOnAction(e -> {
            try {
                navigateToSignup();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur lors de l'ouverture du signup: " + ex.getMessage());
            }
        });
    }

    private void setupAvatarBehavior() {
        if (avatarPane == null || leftPupil == null || rightPupil == null || avatarMouth == null || leftHand == null || rightHand == null) {
            return;
        }

        animateAvatar(0, 0, 1.6, 0, false, "◡");

        emailField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal && !passwordField.isFocused()) {
                animateEmailTracking();
            } else if (!passwordField.isFocused()) {
                animateAvatar(0, 0, 1.6, 0, false, "◡");
            }
        });

        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (emailField.isFocused() && !passwordField.isFocused()) {
                animateEmailTracking();
            }
        });

        emailField.caretPositionProperty().addListener((obs, oldVal, newVal) -> {
            if (emailField.isFocused() && !passwordField.isFocused()) {
                animateEmailTracking();
            }
        });

        passwordField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                animateAvatar(-14, 0, 0.5, 0, true, "◠");
            } else if (emailField.isFocused()) {
                animateEmailTracking();
            } else {
                animateAvatar(0, 0, 1.6, 0, false, "◡");
            }
        });
    }

    private void animateEmailTracking() {
        String email = emailField.getText() == null ? "" : emailField.getText();
        int caret = Math.max(emailField.getCaretPosition(), 0);
        int range = Math.max(18, email.length());
        double progress = Math.min(1.0, (double) caret / range);
        double direction = (progress - 0.5) * 2.0;

        double pupilX = direction * 2.2;
        double pupilY = 1.3 + (Math.min(email.length(), 24) / 28.0);
        double headRotate = direction * 5.0;
        animateAvatar(headRotate, pupilX, pupilY, pupilX, false, "◡");
    }

    private void animateAvatar(double headRotate, double leftX, double leftY, double rightX, boolean hideEyesWithHands, String mouth) {
        if (avatarPane == null || leftPupil == null || rightPupil == null || avatarMouth == null || leftHand == null || rightHand == null) {
            return;
        }

        avatarMouth.setText(mouth);

        if (avatarTimeline != null) {
            avatarTimeline.stop();
        }

        avatarTimeline = new Timeline(
                new KeyFrame(Duration.millis(170),
                        new KeyValue(avatarPane.rotateProperty(), headRotate, Interpolator.EASE_BOTH),
                new KeyValue(avatarPane.translateYProperty(), hideEyesWithHands ? 2.5 : 0.0, Interpolator.EASE_BOTH),
                        new KeyValue(leftPupil.translateXProperty(), leftX, Interpolator.EASE_BOTH),
                        new KeyValue(leftPupil.translateYProperty(), leftY, Interpolator.EASE_BOTH),
                        new KeyValue(rightPupil.translateXProperty(), rightX, Interpolator.EASE_BOTH),
                new KeyValue(rightPupil.translateYProperty(), leftY, Interpolator.EASE_BOTH),
                new KeyValue(leftPupil.opacityProperty(), hideEyesWithHands ? 0.15 : 1.0, Interpolator.EASE_BOTH),
                new KeyValue(rightPupil.opacityProperty(), hideEyesWithHands ? 0.15 : 1.0, Interpolator.EASE_BOTH),
                new KeyValue(leftHand.opacityProperty(), hideEyesWithHands ? 1.0 : 0.0, Interpolator.EASE_BOTH),
                new KeyValue(rightHand.opacityProperty(), hideEyesWithHands ? 1.0 : 0.0, Interpolator.EASE_BOTH),
                new KeyValue(leftHand.translateYProperty(), hideEyesWithHands ? -6.0 : -20.0, Interpolator.EASE_BOTH),
                new KeyValue(rightHand.translateYProperty(), hideEyesWithHands ? -6.0 : -20.0, Interpolator.EASE_BOTH),
                new KeyValue(leftHand.translateXProperty(), hideEyesWithHands ? -6.5 : 0.0, Interpolator.EASE_BOTH),
                new KeyValue(rightHand.translateXProperty(), hideEyesWithHands ? 6.5 : 0.0, Interpolator.EASE_BOTH),
                new KeyValue(leftHand.scaleXProperty(), hideEyesWithHands ? 1.05 : 1.0, Interpolator.EASE_BOTH),
                new KeyValue(leftHand.scaleYProperty(), hideEyesWithHands ? 1.05 : 1.0, Interpolator.EASE_BOTH),
                new KeyValue(rightHand.scaleXProperty(), hideEyesWithHands ? 1.05 : 1.0, Interpolator.EASE_BOTH),
                new KeyValue(rightHand.scaleYProperty(), hideEyesWithHands ? 1.05 : 1.0, Interpolator.EASE_BOTH)
                )
        );
        avatarTimeline.play();
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
                saveRememberMeState(email);
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

    private void doFaceLogin() {
        if (faceLoginBtn != null) {
            faceLoginBtn.setDisable(true);
        }

        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                BufferedImage captured = captureWebcamWithPreview();
                String dataUrl = buildDataUrl(captured);
                return matchUserByFace(dataUrl);
            }
        };

        task.setOnSucceeded(e -> {
            if (faceLoginBtn != null) {
                faceLoginBtn.setDisable(false);
            }
            User utilisateur = task.getValue();
            if (utilisateur != null) {
                saveRememberMeState(utilisateur.getEmail());
                showSuccess("Succès", "Reconnaissance faciale réussie");
                try {
                    navigateToDashboard(utilisateur);
                } catch (Exception ex) {
                    showAlert("Erreur", "Erreur lors de la navigation: " + ex.getMessage());
                }
            } else {
                showAlert("Erreur", "Aucune correspondance trouvée");
            }
        });

        task.setOnFailed(e -> {
            if (faceLoginBtn != null) {
                faceLoginBtn.setDisable(false);
            }
            Throwable ex = task.getException();
            String message = (ex != null) ? ex.getMessage() : "Erreur inconnue";
            showAlert("Erreur", "Erreur lors de la reconnaissance faciale: " + message);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void doGoogleLogin() {
        if (googleLoginBtn != null) {
            googleLoginBtn.setDisable(true);
        }

        Task<GoogleLoginResult> task = new Task<>() {
            @Override
            protected GoogleLoginResult call() throws Exception {
                GoogleOAuthService oauthService = new GoogleOAuthService();
                GoogleOAuthService.GoogleUserProfile profile = oauthService.authenticate();
                return findOrCreateUserFromGoogle(profile);
            }
        };

        task.setOnSucceeded(e -> {
            if (googleLoginBtn != null) {
                googleLoginBtn.setDisable(false);
            }

            GoogleLoginResult loginResult = task.getValue();
            User utilisateur = (loginResult == null) ? null : loginResult.user();
            if (utilisateur == null) {
                showAlert("Erreur", "Impossible de récupérer le compte Google.");
                return;
            }

            if (loginResult.newlyCreated()) {
                boolean configured = promptInitialPasswordSetup(utilisateur);
                if (!configured) {
                    showAlert("Information", "Compte Google créé. Vous pourrez définir un mot de passe plus tard via 'Forgot password?'.");
                }
            }

            saveRememberMeState(utilisateur.getEmail());
            showSuccess("Succès", "Connexion Google réussie");
            try {
                navigateToDashboard(utilisateur);
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur lors de la navigation: " + ex.getMessage());
            }
        });

        task.setOnFailed(e -> {
            if (googleLoginBtn != null) {
                googleLoginBtn.setDisable(false);
            }
            Throwable ex = task.getException();
            String message = (ex != null) ? ex.getMessage() : "Erreur inconnue";
            showAlert("Erreur", "Connexion Google impossible: " + message);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private GoogleLoginResult findOrCreateUserFromGoogle(GoogleOAuthService.GoogleUserProfile profile) throws SQLException {
        if (profile == null || profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw new IllegalStateException("Email Google manquant");
        }

        UserService userService = new UserService();
        String normalizedEmail = profile.getEmail().trim().toLowerCase();
        User existing = userService.trouverParEmail(normalizedEmail);
        if (existing != null) {
            return new GoogleLoginResult(existing, false);
        }

        String prenom = safeValue(profile.getGivenName(), extractFirstNameFromEmail(normalizedEmail));
        String nom = safeValue(profile.getFamilyName(), "Google");
        String randomPassword = UUID.randomUUID().toString();
        String passwordHash = PasswordUtil.hashPassword(randomPassword);
        String imageUrl = (profile.getPictureUrl() == null || profile.getPictureUrl().isBlank()) ? null : profile.getPictureUrl().trim();

        User user = new User(nom, prenom, normalizedEmail, passwordHash, "abonne", null, imageUrl);
        userService.ajouter(user);
        return new GoogleLoginResult(user, true);
    }

    private boolean promptInitialPasswordSetup(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Définir un mot de passe");
        dialog.setHeaderText("Première connexion Google\nDéfinissez un mot de passe pour la connexion classique.");

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButtonType = new ButtonType("Plus tard", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, laterButtonType);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Nouveau mot de passe");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmer le mot de passe");

        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: 11;");

        VBox content = new VBox(8,
                new Label("Email: " + user.getEmail()),
                passwordField,
                confirmField,
                validationLabel
        );
        dialog.getDialogPane().setContent(content);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String password = passwordField.getText() == null ? "" : passwordField.getText();
            String confirm = confirmField.getText() == null ? "" : confirmField.getText();

            if (password.length() < 8) {
                validationLabel.setText("Le mot de passe doit contenir au moins 8 caractères.");
                event.consume();
                return;
            }
            if (!password.equals(confirm)) {
                validationLabel.setText("Les mots de passe ne correspondent pas.");
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return false;
        }

        String newPassword = passwordField.getText();
        String hash = PasswordUtil.hashPassword(newPassword);

        try {
            UserService userService = new UserService();
            userService.mettreAJourMotDePasseParEmail(user.getEmail(), hash);
            return true;
        } catch (SQLException ex) {
            showAlert("Erreur", "Impossible d'enregistrer le mot de passe: " + ex.getMessage());
            return false;
        }
    }

    private record GoogleLoginResult(User user, boolean newlyCreated) {
    }

    private String extractFirstNameFromEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "Google";
        }
        String localPart = email.substring(0, email.indexOf('@')).trim();
        if (localPart.isBlank()) {
            return "Google";
        }
        String normalized = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isBlank()) {
            return "Google";
        }
        String[] chunks = normalized.split("\\s+");
        String first = chunks.length > 0 ? chunks[0] : "Google";
        if (first.isBlank()) {
            return "Google";
        }
        return Character.toUpperCase(first.charAt(0)) + first.substring(1).toLowerCase();
    }

    private String safeValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void doForgotPassword() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim().toLowerCase();

        if (email.isBlank()) {
            showAlert("Validation", "Veuillez saisir votre email");
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showAlert("Validation", "Format email invalide");
            return;
        }

        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                UserService userService = new UserService();
                User user = userService.trouverParEmail(email);
                if (user == null) {
                    throw new IllegalStateException("Aucun compte associé à cet email");
                }
                String otp = otpService.createOtp(email, OTP_EXPIRY_SECONDS);
                otpMailService.sendOtpMail(email, otp, OTP_EXPIRY_SECONDS);
                return user;
            }
        };

        forgotLink.setDisable(true);

        task.setOnSucceeded(e -> {
            forgotLink.setDisable(false);
            showSuccess("OTP envoyé", "Un code OTP a été envoyé à votre email.");
            try {
                navigateToOtpVerification(email);
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur ouverture page OTP: " + ex.getMessage());
            }
        });

        task.setOnFailed(e -> {
            forgotLink.setDisable(false);
            Throwable ex = task.getException();
            String message = (ex != null) ? ex.getMessage() : "Erreur inconnue";
            showAlert("Erreur", "Impossible d'envoyer OTP: " + message);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private BufferedImage captureWebcamWithPreview() throws Exception {
        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);
        Java2DFrameConverter converter = new Java2DFrameConverter();
        AtomicReference<BufferedImage> captured = new AtomicReference<>();
        AtomicReference<Exception> previewError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            grabber.start();
            Platform.runLater(() -> {
                Stage previewStage = new Stage();
                previewStage.initModality(Modality.NONE);
                previewStage.setTitle("Face ID");

                ImageView imageView = new ImageView();
                imageView.setFitWidth(480);
                imageView.setFitHeight(360);
                imageView.setPreserveRatio(true);

                BorderPane cameraRoot = new BorderPane(imageView);
                Scene scene = new Scene(cameraRoot, 500, 380);
                previewStage.setScene(scene);
                previewStage.show();

                Timeline frameTimeline = new Timeline(new KeyFrame(Duration.millis(33), ev -> {
                    try {
                        Frame frame = grabber.grab();
                        if (frame == null) {
                            return;
                        }
                        BufferedImage image = converter.convert(frame);
                        if (image != null) {
                            Image fxImage = SwingFXUtils.toFXImage(image, null);
                            imageView.setImage(fxImage);
                            captured.set(image);
                        }
                    } catch (Exception ex) {
                        previewError.set(ex);
                    }
                }));
                frameTimeline.setCycleCount(Timeline.INDEFINITE);

                Timeline stopTimeline = new Timeline(new KeyFrame(Duration.seconds(2), ev -> {
                    frameTimeline.stop();
                    previewStage.close();
                    latch.countDown();
                }));
                stopTimeline.setCycleCount(1);

                previewStage.setOnCloseRequest(ev -> {
                    frameTimeline.stop();
                    stopTimeline.stop();
                    latch.countDown();
                });

                frameTimeline.play();
                stopTimeline.play();
            });

            boolean completed = latch.await(7, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("Capture caméra timeout");
            }
            if (previewError.get() != null) {
                throw previewError.get();
            }
            if (captured.get() == null) {
                throw new IllegalStateException("Aucune image capturée depuis la caméra");
            }
            return captured.get();
        } finally {
            try {
                grabber.stop();
            } catch (Exception ignored) {
            }
            try {
                grabber.release();
            } catch (Exception ignored) {
            }
        }
    }

    private String buildDataUrl(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        String base64 = Base64.getEncoder().encodeToString(output.toByteArray());
        return "data:image/jpeg;base64," + base64;
    }

    private User matchUserByFace(String dataUrl) throws Exception {
        UserService userService = new UserService();
        List<User> users = userService.obtenirTous();
        for (User user : users) {
            if (user.getImageUrl() == null || user.getImageUrl().trim().isEmpty()) {
                continue;
            }
            String response = callFaceCompareApi(dataUrl, user.getImageUrl());
            System.out.println("[FaceID] Compare user=" + user.getId() + " email=" + user.getEmail() + " => " + response);
            if (parseSuccess(response)) {
                System.out.println("[FaceID] MATCH user=" + user.getId() + " email=" + user.getEmail());
                return user;
            }
        }
        System.out.println("[FaceID] Aucun match trouvé");
        return null;
    }

    private String callFaceCompareApi(String imageDataUrl, String referenceImagePath) throws Exception {
        String json = "{\"image\":\"" + escapeJson(imageDataUrl) + "\",\"reference\":\"" + escapeJson(referenceImagePath) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:5501/compare-face"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private boolean parseSuccess(String responseBody) {
        if (responseBody == null) {
            return false;
        }
        return SUCCESS_TRUE_PATTERN.matcher(responseBody).find();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private void loadRememberedCredentials() {
        boolean remember = preferences.getBoolean(PREF_REMEMBER_ME, false);
        String rememberedEmail = preferences.get(PREF_REMEMBERED_EMAIL, "");

        if (remember && rememberedEmail != null && !rememberedEmail.isBlank()) {
            rememberCheckBox.setSelected(true);
            emailField.setText(rememberedEmail);
            passwordField.requestFocus();
        } else {
            rememberCheckBox.setSelected(false);
        }
    }

    private void saveRememberMeState(String email) {
        if (rememberCheckBox != null && rememberCheckBox.isSelected()) {
            preferences.putBoolean(PREF_REMEMBER_ME, true);
            preferences.put(PREF_REMEMBERED_EMAIL, email == null ? "" : email.trim());
        } else {
            preferences.putBoolean(PREF_REMEMBER_ME, false);
            preferences.remove(PREF_REMEMBERED_EMAIL);
        }
    }

    private void navigateToSignup() throws Exception {
        Stage stage = resolveStage();

        URL fxmlUrl = getClass().getResource("/fxml/common/auth/auth/Signup.fxml");
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

    private void navigateToOtpVerification(String email) throws Exception {
        Stage stage = resolveStage();

        URL fxmlUrl = getClass().getResource("/fxml/common/auth/auth/OtpVerification.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable: /fxml/common/auth/OtpVerification.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        OtpVerificationController controller = loader.getController();
        controller.setPrimaryStage(stage);
        controller.setEmail(email);

        Scene scene = new Scene(root, 900, 700);
        stage.setTitle("Fin Tokhroj - OTP");
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