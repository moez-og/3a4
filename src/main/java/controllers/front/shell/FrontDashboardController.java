package controllers.front.shell;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.users.User;
import utils.ui.ShellNavigator;
import utils.ui.ViewPaths;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FrontDashboardController implements ShellNavigator {

    // Routes stables (utilisées par l'accueil + autres pages)
    public static final String ROUTE_HOME = "home";
    public static final String ROUTE_SORTIES = "sorties";
    public static final String ROUTE_LIEUX = "lieux";
    public static final String ROUTE_OFFRES = "offres";
    public static final String ROUTE_EVENTS = "events";
    public static final String ROUTE_HELP = "help";
    public static final String ROUTE_PROFIL = "profil";

    @FXML private StackPane root;
    @FXML private StackPane dynamicContent;

    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;

    @FXML private TextField searchField;

    @FXML private ToggleButton navAccueil;
    @FXML private ToggleButton navSorties;
    @FXML private ToggleButton navLieux;
    @FXML private ToggleButton navOffres;
    @FXML private ToggleButton navEvents;
    @FXML private ToggleButton navHelp;

    @FXML private Label userName;
    @FXML private Label userRole;
    @FXML private Label avatarLetter;

    // drawer
    @FXML private StackPane overlay;
    @FXML private VBox accountDrawer;
    @FXML private Label avatarLetterLarge;
    @FXML private Label drawerName;
    @FXML private Label drawerEmail;

    private final ToggleGroup navGroup = new ToggleGroup();
    private final Map<String, Node> viewCache = new HashMap<>();

    private Stage primaryStage;
    private User currentUser;
    private boolean dark = false;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshAccountUI();
    }

    @FXML
    private void initialize() {
        bindNavGroup();
        refreshAccountUI();
        showAccueil();
    }

    private void bindNavGroup() {
        navAccueil.setToggleGroup(navGroup);
        navSorties.setToggleGroup(navGroup);
        navLieux.setToggleGroup(navGroup);
        navOffres.setToggleGroup(navGroup);
        navEvents.setToggleGroup(navGroup);
        navHelp.setToggleGroup(navGroup);
        navAccueil.setSelected(true);
    }

    /* ================= NAV ================= */

    @FXML
    public void showAccueil() {
        navAccueil.setSelected(true);
        setHeader("Accueil", "Vue d’ensemble des gestions");
        loadAndSet(ROUTE_HOME, ViewPaths.FRONT_HOME);
    }

    @FXML
    public void showSorties() {
        navSorties.setSelected(true);
        setHeader("Sorties", "Annonces · participations · suivi");
        loadAndSet(ROUTE_SORTIES, ViewPaths.FRONT_SORTIES);
    }

    @FXML
    public void showLieux() {
        navLieux.setSelected(true);
        setHeader("Lieux", "Découverte · favoris · détails");
        loadAndSet(ROUTE_LIEUX, ViewPaths.FRONT_LIEUX);
    }

    @FXML
    public void showOffres() {
        navOffres.setSelected(true);
        setHeader("Offres", "Promos · partenariats · coupons");
        loadAndSet(ROUTE_OFFRES, ViewPaths.FRONT_OFFRES);
    }

    @FXML
    public void showEvents() {
        navEvents.setSelected(true);
        setHeader("Événements", "Agenda · inscriptions · infos");
        loadAndSet(ROUTE_EVENTS, ViewPaths.FRONT_EVENEMENTS);
    }

    @FXML
    public void showHelp() {
        navHelp.setSelected(true);
        setHeader("Aide", "FAQ · guide · support");
        loadAndSet(ROUTE_HELP, ViewPaths.FRONT_HELP);
    }

    /* ================= ROUTER (pour l’accueil et les pages) ================= */

    @Override
    public void navigate(String route) {
        if (route == null) return;
        switch (route) {
            case ROUTE_HOME -> showAccueil();
            case ROUTE_SORTIES -> showSorties();
            case ROUTE_LIEUX -> showLieux();
            case ROUTE_OFFRES -> showOffres();
            case ROUTE_EVENTS -> showEvents();
            case ROUTE_HELP -> showHelp();
            case ROUTE_PROFIL -> showProfil();
            default -> info("Navigation", "Route inconnue: " + route);
        }
    }

    /* ================= SEARCH / ACTIONS ================= */

    @FXML
    public void doSearch() {
        String q = safe(searchField.getText()).trim();
        if (q.isEmpty()) {
            info("Recherche", "Entre un mot-clé puis clique sur Go.");
            return;
        }
        info("Recherche", "Recherche: " + q + "\n(Brancher recherche globale ici)");
    }

    @FXML
    public void showNotifications() {
        info("Notifications", "Brancher alertes: participations, validations, nouveautés.");
    }

    @FXML
    public void toggleTheme() {
        dark = !dark;
        if (root != null) {
            if (dark) root.getStyleClass().add("theme-dark");
            else root.getStyleClass().remove("theme-dark");
        }
    }

    @FXML
    public void newAction() {
        info("Nouveau", "Brancher: nouveau sortie / offre / événement.");
    }

    /* ================= ACCOUNT DRAWER ================= */

    @FXML
    public void openAccount() {
        if (overlay == null || accountDrawer == null) return;

        overlay.setVisible(true);
        overlay.setManaged(true);

        accountDrawer.setTranslateX(420);
        TranslateTransition tt = new TranslateTransition(Duration.millis(200), accountDrawer);
        tt.setFromX(420);
        tt.setToX(0);
        tt.play();

        overlay.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(160), overlay);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    @FXML
    public void closeAccount() {
        if (overlay == null || accountDrawer == null) return;

        TranslateTransition tt = new TranslateTransition(Duration.millis(180), accountDrawer);
        tt.setFromX(0);
        tt.setToX(420);

        FadeTransition ft = new FadeTransition(Duration.millis(160), overlay);
        ft.setFromValue(1);
        ft.setToValue(0);

        tt.play();
        ft.play();

        ft.setOnFinished(e -> {
            overlay.setVisible(false);
            overlay.setManaged(false);
        });
    }

    @FXML
    public void consume(MouseEvent e) {
        e.consume();
    }

    @FXML
    public void showProfil() {
        closeAccount();
        setHeader("Profil", "Informations du compte");
        loadAndSet(ROUTE_PROFIL, ViewPaths.FRONT_PROFIL);
    }

    @FXML public void showSecurity() { info("Sécurité", "Brancher: mot de passe / 2FA / sessions."); }
    @FXML public void showSettings() { info("Paramètres", "Brancher: préférences, langue, notifications."); }
    @FXML public void showMyActivity() { info("Activités", "Brancher: historique, favoris, participations."); }

    /* ================= LOGOUT ================= */

    @FXML
    public void logout() {
        try {
            Stage stage = resolveStage();
            viewCache.clear();
            currentUser = null;

            URL url = getClass().getResource(ViewPaths.LOGIN);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.LOGIN);

            FXMLLoader loader = new FXMLLoader(url);
            Parent loginRoot = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, stage);

            stage.setTitle("Fin Tokhroj - Login");
            stage.getScene().setRoot(loginRoot);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            error("Déconnexion", e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    /* ================= VIEW LOADING ================= */

    private void loadAndSet(String key, String fxmlPath) {
        Node cached = viewCache.get(key);
        if (cached != null) {
            animateSwap(cached);
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
            trySetNavigator(controller, this); // ✅ injection du navigator

            viewCache.put(key, view);
            animateSwap(view);

        } catch (Exception e) {
            error("Chargement vue",
                    "Vue: " + fxmlPath + "\n" +
                            e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private void animateSwap(Node node) {
        if (dynamicContent == null) return;

        dynamicContent.getChildren().setAll(node);

        node.setOpacity(0);
        node.setTranslateY(8);

        FadeTransition ft = new FadeTransition(Duration.millis(160), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        TranslateTransition tt = new TranslateTransition(Duration.millis(160), node);
        tt.setFromY(8);
        tt.setToY(0);

        ft.play();
        tt.play();
    }

    /* ================= UI HELPERS ================= */

    private void setHeader(String title, String subtitle) {
        if (pageTitle != null) pageTitle.setText(title);
        if (pageSubtitle != null) pageSubtitle.setText(subtitle);
    }

    private void refreshAccountUI() {
        String display = "Utilisateur";
        String role = "User";
        String email = "email@example.com";
        String letter = "U";

        if (currentUser != null) {
            String full = (safe(currentUser.getPrenom()) + " " + safe(currentUser.getNom())).trim();
            if (!full.isEmpty()) display = full;

            String r = safe(currentUser.getRole()).trim();
            if (!r.isEmpty()) role = r;

            String e = safe(currentUser.getEmail()).trim();
            if (!e.isEmpty()) email = e;

            String pick = safe(currentUser.getPrenom()).trim();
            if (pick.isEmpty()) pick = safe(currentUser.getNom()).trim();
            if (!pick.isEmpty()) letter = pick.substring(0, 1).toUpperCase();
        }

        if (userName != null) userName.setText(display);
        if (userRole != null) userRole.setText(role);
        if (avatarLetter != null) avatarLetter.setText(letter);

        if (drawerName != null) drawerName.setText(display);
        if (drawerEmail != null) drawerEmail.setText(email);
        if (avatarLetterLarge != null) avatarLetterLarge.setText(letter);
    }

    private Stage resolveStage() {
        if (primaryStage != null) return primaryStage;
        if (root != null && root.getScene() != null) return (Stage) root.getScene().getWindow();
        return new Stage();
    }

    private void trySetPrimaryStage(Object controller, Stage stage) {
        if (controller == null || stage == null) return;
        try { controller.getClass().getMethod("setPrimaryStage", Stage.class).invoke(controller, stage); }
        catch (Exception ignored) {}
    }

    private void trySetCurrentUser(Object controller, User user) {
        if (controller == null || user == null) return;
        try { controller.getClass().getMethod("setCurrentUser", User.class).invoke(controller, user); }
        catch (Exception ignored) {}
    }

    private void trySetNavigator(Object controller, ShellNavigator navigator) {
        if (controller == null || navigator == null) return;
        try { controller.getClass().getMethod("setNavigator", ShellNavigator.class).invoke(controller, navigator); }
        catch (Exception ignored) {}
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