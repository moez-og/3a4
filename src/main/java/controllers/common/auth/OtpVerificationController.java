package controllers.common.auth;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import services.common.auth.GmailOtpMailService;
import services.common.auth.OtpMemoryService;
import services.users.UserService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class OtpVerificationController {
    private static final int OTP_EXPIRY_SECONDS = 60;
    private static final int BLOCK_SECONDS = 30;
    private static final Pattern SUCCESS_TRUE_PATTERN = Pattern.compile("\\\"success\\\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE);

    @FXML private Label emailLabel;
    @FXML private Label timerLabel;
    @FXML private Label attemptsLabel;
    @FXML private Label blockLabel;
    @FXML private TextField otpField;
    @FXML private Button verifyBtn;
    @FXML private Button resendBtn;
    @FXML private Hyperlink backLoginLink;

    private final OtpMemoryService otpService = OtpMemoryService.getInstance();
    private final GmailOtpMailService mailService = new GmailOtpMailService();
    private Stage primaryStage;
    private String email;
    private Timeline countdownTimeline;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setEmail(String email) {
        this.email = (email == null) ? "" : email.trim();
        if (emailLabel != null) {
            emailLabel.setText(this.email);
        }
        startCountdown();
        refreshStatusLabels();
    }

    @FXML
    private void initialize() {
        verifyBtn.setOnAction(e -> onVerifyOtp());
        resendBtn.setOnAction(e -> onResendOtp());
        backLoginLink.setOnAction(e -> {
            try {
                navigateToLogin();
            } catch (Exception ex) {
                showAlert("Erreur", "Erreur navigation login: " + ex.getMessage());
            }
        });
    }

    private void onVerifyOtp() {
        if (isEmailMissing()) {
            showAlert("Erreur", "Email invalide pour la vérification OTP");
            return;
        }

        String candidate = otpField.getText() == null ? "" : otpField.getText().trim();
        if (candidate.isEmpty()) {
            showAlert("Validation", "Veuillez saisir le code OTP");
            return;
        }

        OtpMemoryService.VerificationResult result = otpService.verifyOtp(email, candidate);
        switch (result.status()) {
            case SUCCESS -> {
                otpService.authorizeReset(email);
                try {
                    navigateToResetPassword();
                } catch (Exception ex) {
                    showAlert("Erreur", "Erreur navigation reset: " + ex.getMessage());
                }
            }
            case INVALID -> {
                attemptsLabel.setText("Tentatives restantes: " + result.attemptsLeft());
                showAlert("OTP invalide", "Code OTP incorrect. Tentatives restantes: " + result.attemptsLeft());
            }
            case EXPIRED -> {
                timerLabel.setText("OTP expiré");
                showAlert("OTP expiré", "Le code OTP a expiré. Cliquez sur 'Renvoyer OTP'.");
            }
            case BLOCKED -> {
                blockLabel.setText("Bloqué: " + result.remainingBlockSeconds() + "s");
                showAlert("Bloqué", "Veuillez patienter " + result.remainingBlockSeconds() + " secondes.");
            }
            case REQUIRES_FACE_VERIFICATION -> triggerFaceVerification();
            case NOT_FOUND -> showAlert("Session introuvable", "Veuillez redemander un OTP.");
        }
        refreshStatusLabels();
    }

    private void onResendOtp() {
        if (isEmailMissing()) {
            showAlert("Erreur", "Email invalide pour renvoi OTP");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String otp = otpService.createOtp(email, OTP_EXPIRY_SECONDS);
                mailService.sendOtpMail(email, otp, OTP_EXPIRY_SECONDS);
                return null;
            }
        };

        resendBtn.setDisable(true);
        verifyBtn.setDisable(true);

        task.setOnSucceeded(e -> {
            resendBtn.setDisable(false);
            verifyBtn.setDisable(false);
            startCountdown();
            attemptsLabel.setText("Tentatives restantes: 3");
            blockLabel.setText("");
            showInfo("OTP envoyé", "Un nouveau code OTP a été envoyé.");
        });

        task.setOnFailed(e -> {
            resendBtn.setDisable(false);
            verifyBtn.setDisable(false);
            String message = task.getException() == null ? "Erreur inconnue" : task.getException().getMessage();
            showAlert("Erreur envoi OTP", message);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void triggerFaceVerification() {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return verifyFaceForCurrentEmail();
            }
        };

        verifyBtn.setDisable(true);

        task.setOnSucceeded(e -> {
            verifyBtn.setDisable(false);
            boolean matched = task.getValue();
            if (matched) {
                otpService.authorizeReset(email);
                showInfo("Vérification réussie", "Face ID validé. Vous pouvez changer votre mot de passe.");
                try {
                    navigateToResetPassword();
                } catch (Exception ex) {
                    showAlert("Erreur", "Erreur navigation reset: " + ex.getMessage());
                }
            } else {
                otpService.setBlockedForSeconds(email, BLOCK_SECONDS);
                refreshStatusLabels();
                showAlert("Vérification échouée", "Face non reconnue. Réessayez après " + BLOCK_SECONDS + " secondes.");
            }
        });

        task.setOnFailed(e -> {
            verifyBtn.setDisable(false);
            otpService.setBlockedForSeconds(email, BLOCK_SECONDS);
            refreshStatusLabels();
            String message = task.getException() == null ? "Erreur inconnue" : task.getException().getMessage();
            showAlert("Erreur Face ID", message + "\nNouveau blocage de " + BLOCK_SECONDS + " secondes.");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean verifyFaceForCurrentEmail() throws Exception {
        UserService userService = new UserService();
        User user = userService.trouverParEmail(email);
        if (user == null) {
            throw new IllegalStateException("Utilisateur introuvable");
        }
        if (user.getImageUrl() == null || user.getImageUrl().trim().isEmpty()) {
            throw new IllegalStateException("Image de référence absente pour cet utilisateur");
        }

        BufferedImage captured = captureWebcamWithPreview();
        String dataUrl = buildDataUrl(captured);
        String response = callFaceCompareApi(dataUrl, user.getImageUrl());
        return parseSuccess(response);
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
                previewStage.setTitle("Vérification identité");

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

    private void refreshStatusLabels() {
        if (isEmailMissing()) {
            return;
        }
        long blockSeconds = otpService.getRemainingBlockSeconds(email);
        if (blockSeconds > 0) {
            blockLabel.setText("Bloqué: " + blockSeconds + "s");
        } else {
            blockLabel.setText("");
        }
    }

    private void startCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isEmailMissing()) {
                return;
            }
            long remaining = otpService.getRemainingOtpSeconds(email);
            if (remaining > 0) {
                timerLabel.setText("Expire dans: " + remaining + "s");
            } else {
                timerLabel.setText("OTP expiré");
            }

            long blockRemaining = otpService.getRemainingBlockSeconds(email);
            if (blockRemaining > 0) {
                blockLabel.setText("Bloqué: " + blockRemaining + "s");
            } else {
                blockLabel.setText("");
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void navigateToResetPassword() throws Exception {
        Stage stage = resolveStage();
        URL fxmlUrl = getClass().getResource("/fxml/common/auth/auth/ResetPassword.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable: /fxml/common/auth/ResetPassword.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        ResetPasswordController controller = loader.getController();
        controller.setPrimaryStage(stage);
        controller.setEmail(email);

        Scene scene = new Scene(root, 900, 700);
        stage.setTitle("Fin Tokhroj - Reset Password");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
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
        if (otpField != null && otpField.getScene() != null) {
            return (Stage) otpField.getScene().getWindow();
        }
        return new Stage();
    }

    private boolean isEmailMissing() {
        return email == null || email.isBlank();
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
