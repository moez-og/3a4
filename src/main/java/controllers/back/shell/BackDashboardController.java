package controllers.back.shell;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import models.users.User;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class BackDashboardController {

    private static final String USERS_VIEW_PATH = "/fxml/back/users/UserDashboard.fxml";
    private static final String LOGIN_VIEW_PATH = "/fxml/common/auth/Login.fxml";

    @FXML private StackPane dynamicContent;

    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;

    @FXML private Label userName;
    @FXML private Label userRole;

    @FXML private Button btnDashboard;
    @FXML private Button btnUtilisateurs;
    @FXML private Button btnSorties;
    @FXML private Button btnLieux;
    @FXML private Button btnOffres;
    @FXML private Button btnEvents;
    @FXML private Button btnGoFront;

    private Stage primaryStage;
    private User currentUser;

    private final Map<String, Node> viewCache = new HashMap<>();

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshHeaderUser();
    }

    @FXML
    private void initialize() {
        refreshHeaderUser();
        showDashboard();
    }

    @FXML
    public void showDashboard() {
        setActive(btnDashboard);
        setHeader("Dashboard", "Vue d’ensemble");
        setContent(buildSimpleCard(
                "Bienvenue dans le Back Office",
                "Utilise la sidebar pour naviguer. Chaque module sera branché dans cette zone centrale."
        ));
    }

    @FXML
    public void showUsers() {
        setActive(btnUtilisateurs);
        setHeader("Gestion des Utilisateurs", "Recherche, ajout, modification et suppression");
        loadAndSetCachedView("users", USERS_VIEW_PATH);
    }

    @FXML
    public void showLieux() {
        setActive(btnLieux);
        setHeader("Gestion des Lieux", "Module à brancher");
        setContent(buildSimpleCard("Gestion des Lieux", "Branche ici l’écran CRUD des lieux عندما يكون جاهz."));
    }

    @FXML
    public void showSorties() {
        setActive(btnSorties);
        setHeader("Gestion des Sorties", "Module à brancher");
        setContent(buildSimpleCard("Gestion des Sorties", "Branche ici l’écran CRUD des sorties عندما يكون جاهz."));
    }

    @FXML
    public void showOffres() {
        setActive(btnOffres);
        setHeader("Gestion des Offres", "Module à brancher");
        setContent(buildSimpleCard("Gestion des Offres", "Branche ici l’écran CRUD des offres عندما يكون جاهz."));
    }

    @FXML
    public void showEvents() {
        setActive(btnEvents);
        setHeader("Gestion des Événements", "Module à brancher");
        setContent(buildSimpleCard("Gestion des Événements", "Branche ici l’écran CRUD des événements عندما يكون جاهz."));
    }

    @FXML
    public void goToFront() {
        try {
            Stage stage = resolveStage();
            currentUser = null;
            viewCache.clear();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(LOGIN_VIEW_PATH));
            Parent root = loader.load();

            Object controller = loader.getController();
            invokeIfExists(controller, "setPrimaryStage", Stage.class, stage);

            Scene scene = new Scene(root, 900, 700);
            stage.setTitle("Fin Tokhroj - Login");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            showError("Erreur", "Impossible de revenir au Login", e.getMessage());
        }
    }

    // ===== Helpers =====

    private Stage resolveStage() {
        if (primaryStage != null) return primaryStage;
        if (dynamicContent != null && dynamicContent.getScene() != null) {
            return (Stage) dynamicContent.getScene().getWindow();
        }
        return new Stage();
    }

    private void setHeader(String title, String subtitle) {
        if (pageTitle != null) pageTitle.setText(title);
        if (pageSubtitle != null) pageSubtitle.setText(subtitle);
    }

    // ✅ Fix: active state = classe "active" (CSS .navBtn.active)
    private void setActive(Button activeBtn) {
        Button[] all = {btnDashboard, btnUtilisateurs, btnSorties, btnLieux, btnOffres, btnEvents, btnGoFront};
        for (Button b : all) {
            if (b == null) continue;
            b.getStyleClass().remove("active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }
    }

    private void setContent(Node node) {
        if (dynamicContent == null) return;
        dynamicContent.getChildren().setAll(node);
    }

    private void refreshHeaderUser() {
        if (userName == null || userRole == null) return;

        String displayName = "Utilisateur";
        String role = "ROLE";

        if (currentUser != null) {
            String full = (safe(currentUser.getPrenom()) + " " + safe(currentUser.getNom())).trim();
            if (!full.isEmpty()) displayName = full;

            String r = safe(currentUser.getRole()).trim();
            if (!r.isEmpty()) role = r.toUpperCase();
        }

        userName.setText(displayName);
        userRole.setText(role);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void loadAndSetCachedView(String cacheKey, String fxmlPath) {
        Node cached = viewCache.get(cacheKey);
        if (cached != null) {
            setContent(cached);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) throw new RuntimeException("FXML introuvable: " + fxmlPath);

            Node view = loader.load();
            Object controller = loader.getController();

            invokeIfExists(controller, "setPrimaryStage", Stage.class, resolveStage());
            invokeIfExists(controller, "setCurrentUser", User.class, currentUser);

            viewCache.put(cacheKey, view);
            setContent(view);

        } catch (IOException e) {
            showError("Erreur de chargement", "Impossible de charger la vue", e.getMessage());
        }
    }

    private void invokeIfExists(Object target, String methodName, Class<?> paramType, Object value) {
        if (target == null || value == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, value);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            showError("Erreur", "Injection controller", "Problème lors de " + methodName + "(): " + e.getMessage());
        }
    }

    private Node buildSimpleCard(String title, String text) {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10);
        box.setStyle(
                "-fx-background-color: rgba(255,255,255,0.75);" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: rgba(15,23,42,0.06);" +
                        "-fx-border-radius: 16;" +
                        "-fx-padding: 18;"
        );

        Label t = new Label(title);
        t.setStyle("-fx-font-size: 18; -fx-font-weight: 900; -fx-text-fill: #163a5c;");

        Label p = new Label(text);
        p.setWrapText(true);
        p.setStyle("-fx-font-size: 12.5; -fx-font-weight: 700; -fx-text-fill: rgba(22,58,92,0.70);");

        box.getChildren().addAll(t, p);
        return box;
    }

    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }
}