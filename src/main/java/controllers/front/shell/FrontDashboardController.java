package controllers.front.shell;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import utils.ui.ViewPaths;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FrontDashboardController {

    @FXML private BorderPane rootPane;
    @FXML private StackPane dynamicContent;

    @FXML private Label breadcrumb;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;

    @FXML private TextField searchField;

    @FXML private Label userName;
    @FXML private Label userHint;
    @FXML private Label avatarLetter;

    @FXML private Button btnAccueil;
    @FXML private Button btnSorties;
    @FXML private Button btnLieux;
    @FXML private Button btnOffres;
    @FXML private Button btnEvents;
    @FXML private Button btnProfil;
    @FXML private Button btnHelp;

    @FXML private Button btnLogout;
    @FXML private Button btnNew;
    @FXML private Button btnNotif;
    @FXML private Button btnTheme;

    private Stage primaryStage;
    private User currentUser;

    private final Map<String, Node> viewCache = new HashMap<>();
    private boolean darkMode = false;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshHeaderUser();
    }

    @FXML
    private void initialize() {
        enhanceHover(btnAccueil, btnSorties, btnLieux, btnOffres, btnEvents, btnProfil, btnHelp, btnLogout, btnNew, btnNotif, btnTheme);
        refreshHeaderUser();
        showAccueil();
    }

    @FXML
    public void showAccueil() {
        setActive(btnAccueil);
        setHeader("Accueil", "Vue d’ensemble", "Front Office / Accueil");
        loadAndSetCachedView("home", ViewPaths.FRONT_HOME);
    }

    @FXML
    public void showSorties() {
        setActive(btnSorties);
        setHeader("Sorties", "Explorer et participer", "Front Office / Sorties");
        loadAndSetCachedView("sorties", ViewPaths.FRONT_SORTIES);
    }

    @FXML
    public void showLieux() {
        setActive(btnLieux);
        setHeader("Lieux", "Découvrir des endroits", "Front Office / Lieux");
        loadAndSetCachedView("lieux", ViewPaths.FRONT_LIEUX);
    }

    @FXML
    public void showOffres() {
        setActive(btnOffres);
        setHeader("Offres", "Bons plans et promotions", "Front Office / Offres");
        loadAndSetCachedView("offres", ViewPaths.FRONT_OFFRES);
    }

    @FXML
    public void showEvents() {
        setActive(btnEvents);
        setHeader("Événements", "Agenda et inscriptions", "Front Office / Événements");
        loadAndSetCachedView("evenements", ViewPaths.FRONT_EVENEMENTS);
    }

    @FXML
    public void showProfil() {
        setActive(btnProfil);
        setHeader("Profil", "Compte et préférences", "Front Office / Profil");
        loadAndSetCachedView("profil", ViewPaths.FRONT_PROFIL);
    }

    @FXML
    public void showHelp() {
        setActive(btnHelp);
        setHeader("Aide", "Support & guide", "Front Office / Aide");
        loadAndSetCachedView("help", ViewPaths.FRONT_HELP);
    }

    @FXML
    public void doSearch() {
        String q = safe(searchField.getText()).trim();
        if (q.isEmpty()) {
            info("Recherche", "Entre un mot-clé puis clique sur Rechercher.");
            return;
        }
        info("Recherche", "Résultats pour: " + q + "\n(Ici tu branches un filtre global)");
    }

    @FXML
    public void showNotifications() {
        info("Notifications", "Ici tu branches tes alertes (participations, validations, nouveautés).");
    }

    @FXML
    public void toggleTheme() {
        darkMode = !darkMode;
        if (rootPane != null) {
            if (darkMode) rootPane.getStyleClass().add("theme-dark");
            else rootPane.getStyleClass().remove("theme-dark");
        }
    }

    @FXML
    public void newAction() {
        info("Nouveau", "Ici tu branches un menu: nouvelle sortie / offre / événement…");
    }

    @FXML
    public void logout() {
        try {
            Stage stage = resolveStage();
            currentUser = null;
            viewCache.clear();

            URL url = getClass().getResource(ViewPaths.LOGIN);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.LOGIN);

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, stage);

            stage.setTitle("Fin Tokhroj - Login");
            stage.getScene().setRoot(root);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            error("Déconnexion", e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private void loadAndSetCachedView(String key, String fxmlPath) {
        Node cached = viewCache.get(key);
        if (cached != null) {
            setContent(cached);
            return;
        }

        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + fxmlPath);

            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, resolveStage());
            trySetCurrentUser(controller, currentUser);

            viewCache.put(key, view);
            setContent(view);

        } catch (Exception e) {
            error("Chargement vue",
                    "Vue: " + fxmlPath + "\n"
                            + e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private void trySetPrimaryStage(Object controller, Stage stage) {
        if (controller == null || stage == null) return;
        try {
            controller.getClass().getMethod("setPrimaryStage", Stage.class).invoke(controller, stage);
        } catch (Exception ignored) {}
    }

    private void trySetCurrentUser(Object controller, User user) {
        if (controller == null || user == null) return;
        try {
            controller.getClass().getMethod("setCurrentUser", User.class).invoke(controller, user);
        } catch (Exception ignored) {}
    }

    private Stage resolveStage() {
        if (primaryStage != null) return primaryStage;
        if (dynamicContent != null && dynamicContent.getScene() != null) {
            return (Stage) dynamicContent.getScene().getWindow();
        }
        return new Stage();
    }

    private void setHeader(String title, String subtitle, String crumb) {
        if (pageTitle != null) pageTitle.setText(title);
        if (pageSubtitle != null) pageSubtitle.setText(subtitle);
        if (breadcrumb != null) breadcrumb.setText(crumb);
    }

    private void refreshHeaderUser() {
        String displayName = "Utilisateur";
        String letter = "U";

        if (currentUser != null) {
            String full = (safe(currentUser.getPrenom()) + " " + safe(currentUser.getNom())).trim();
            if (!full.isEmpty()) displayName = full;

            String pick = safe(currentUser.getPrenom()).trim();
            if (pick.isEmpty()) pick = safe(currentUser.getNom()).trim();
            if (!pick.isEmpty()) letter = pick.substring(0, 1).toUpperCase();
        }

        if (userName != null) userName.setText(displayName);
        if (userHint != null) userHint.setText("Bon retour");
        if (avatarLetter != null) avatarLetter.setText(letter);
    }

    private void setActive(Button activeBtn) {
        Button[] all = {btnAccueil, btnSorties, btnLieux, btnOffres, btnEvents, btnProfil, btnHelp};
        for (Button b : all) if (b != null) b.getStyleClass().remove("active");
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) activeBtn.getStyleClass().add("active");
    }

    private void setContent(Node node) {
        if (dynamicContent == null) return;
        dynamicContent.getChildren().setAll(node);

        FadeTransition ft = new FadeTransition(Duration.millis(180), node);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    private void enhanceHover(Button... buttons) {
        for (Button b : buttons) {
            if (b == null) continue;

            ScaleTransition in = new ScaleTransition(Duration.millis(120), b);
            in.setToX(1.02);
            in.setToY(1.02);

            ScaleTransition out = new ScaleTransition(Duration.millis(120), b);
            out.setToX(1.0);
            out.setToY(1.0);

            b.setOnMouseEntered(e -> in.playFromStart());
            b.setOnMouseExited(e -> out.playFromStart());
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void error(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(title);
        a.setContentText(msg);
        a.showAndWait();
    }
}