// (FICHIER COMPLET) — colle exactement celui-ci
// ⚠️ Le fichier est long, mais je te le donne complet comme demandé.

package controllers.front.shell;

import controllers.front.evenements.EvenementDetailsController;
import controllers.front.lieux.LieuDetailsController;
import controllers.front.lieux.LieuxController;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import utils.ui.GestureNavigationManager;

public class FrontDashboardController implements ShellNavigator {

    public static final String ROUTE_HOME = "home";
    public static final String ROUTE_SORTIES = "sorties";
    public static final String ROUTE_LIEUX = "lieux";
    public static final String ROUTE_OFFRES = "offres";
    public static final String ROUTE_EVENTS = "events";
    public static final String ROUTE_HELP = "help";
    public static final String ROUTE_CHATBOT = "chatbot";
    public static final String ROUTE_PROFIL = "profil";
    public static final String ROUTE_FAVORIS = "favoris";
    public static final String ROUTE_GAMIFICATION = "gamification";

    public static final String ROUTE_LIEU_DETAILS_PREFIX = "lieu-details:";
    public static final String ROUTE_EVENEMENT_DETAILS_PREFIX = "evenement-details:";
    public static final String ROUTE_LIEUX_FILTER_PREFIX = "lieux-filter:";

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
    @FXML private ToggleButton navChatbot;
    @FXML private ToggleButton navGamif;

    @FXML private Label userName;
    @FXML private Label userRole;
    @FXML private Label avatarLetter;

    @FXML private StackPane overlay;
    @FXML private VBox accountDrawer;
    @FXML private Label avatarLetterLarge;
    @FXML private Label drawerName;
    @FXML private Label drawerEmail;

    private final ToggleGroup navGroup = new ToggleGroup();

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();

    private Stage primaryStage;
    private User currentUser;
    private boolean dark = false;

    /** Navigation history — top = current route (used by GestureNavigationManager) */
    private final Deque<String> navHistory = new ArrayDeque<>();
    private GestureNavigationManager gestureNav = null;

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
        // Install gesture navigation as soon as the scene is attached
        if (root != null) {
            root.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null && gestureNav == null) {
                    gestureNav = GestureNavigationManager.install(newScene, navHistory, this);
                }
            });
            // Also try immediately if scene already available
            installGestureNavigation();
        }
    }

    @Override
    public Deque<String> getNavigationHistory() {
        return navHistory;
    }

    /**
     * Install gesture navigation once the Scene is available.
     * Call this from the application startup after the Scene is set on the Stage.
     */
    public void installGestureNavigation() {
        if (root == null) return;
        javafx.application.Platform.runLater(() -> {
            if (root.getScene() != null && gestureNav == null) {
                gestureNav = GestureNavigationManager.install(root.getScene(), navHistory, this);
            }
        });
    }

    private void bindNavGroup() {
        navAccueil.setToggleGroup(navGroup);
        navSorties.setToggleGroup(navGroup);
        navLieux.setToggleGroup(navGroup);
        navOffres.setToggleGroup(navGroup);
        navEvents.setToggleGroup(navGroup);
        navHelp.setToggleGroup(navGroup);
        navChatbot.setToggleGroup(navGroup);
        if (navGamif != null) navGamif.setToggleGroup(navGroup);
        navAccueil.setSelected(true);
    }

    @FXML
    /**
     * Pushes a route to the navigation history stack.
     * Called by every showXxx() method so gesture-back always has a valid history.
     */
    private void pushHistory(String route) {
        if (navHistory.isEmpty() || !route.equals(navHistory.peek())) {
            navHistory.push(route);
            while (navHistory.size() > 50) {
                ((ArrayDeque<String>) navHistory).removeLast();
            }
        }
    }

    public void showAccueil() {
        pushHistory(ROUTE_HOME);
        navAccueil.setSelected(true);
        setHeader("Accueil", "Vue d’ensemble des gestions");
        ensureLoadedAndShow(ROUTE_HOME, ViewPaths.FRONT_HOME);
    }

    @FXML
    public void showSorties() {
        pushHistory(ROUTE_SORTIES);
        navSorties.setSelected(true);
        setHeader("Sorties", "Annonces · participations · suivi");
        ensureLoadedAndShow(ROUTE_SORTIES, ViewPaths.FRONT_SORTIES);
    }

    @FXML
    public void showLieux() {
        pushHistory(ROUTE_LIEUX);
        openLieuxPageAndGetController();
    }

    @FXML
    public void showOffres() {
        pushHistory(ROUTE_OFFRES);
        navOffres.setSelected(true);
        setHeader("Offres", "Promos · partenariats · coupons");
        ensureLoadedAndShow(ROUTE_OFFRES, ViewPaths.FRONT_OFFRES);
    }

    @FXML
    public void showEvents() {
        pushHistory(ROUTE_EVENTS);
        navEvents.setSelected(true);
        setHeader("Événements", "Agenda · inscriptions · infos");
        ensureLoadedAndShow(ROUTE_EVENTS, ViewPaths.FRONT_EVENEMENTS);
    }

    @FXML
    public void showHelp() {
        pushHistory(ROUTE_HELP);
        navHelp.setSelected(true);
        setHeader("Aide", "FAQ · guide · support");
        ensureLoadedAndShow(ROUTE_HELP, ViewPaths.FRONT_HELP);
    }

    @FXML
    public void showChatbot() {
        pushHistory(ROUTE_CHATBOT);
        navChatbot.setSelected(true);
        setHeader("Assistant IA", "Posez vos questions sur les lieux");
        ensureLoadedAndShow(ROUTE_CHATBOT, ViewPaths.FRONT_CHATBOT);
    }

    @FXML
    public void showGamification() {
        pushHistory(ROUTE_GAMIFICATION);
        if (navGamif != null) navGamif.setSelected(true);
        setHeader("🏆 Classement & Badges", "Points fidélité · Badges explorateur · Classement local");
        ensureLoadedAndShow(ROUTE_GAMIFICATION, ViewPaths.FRONT_GAMIFICATION);
    }

    @Override
    public void navigate(String route) {
        if (route == null) return;
        // ── Push to history (avoid duplicating same route consecutively) ──
        if (navHistory.isEmpty() || !route.equals(navHistory.peek())) {
            navHistory.push(route);
            // Cap history at 50 entries
            while (navHistory.size() > 50) {
                ((ArrayDeque<String>) navHistory).removeLast();
            }
        }

        if (route.startsWith(ROUTE_LIEU_DETAILS_PREFIX)) {
            String raw = route.substring(ROUTE_LIEU_DETAILS_PREFIX.length()).trim();
            try {
                int id = Integer.parseInt(raw);
                showLieuDetails(id);
            } catch (Exception e) {
                info("Navigation", "ID lieu invalide: " + raw);
            }
            return;
        }

        if (route.startsWith(ROUTE_EVENEMENT_DETAILS_PREFIX)) {
            String raw = route.substring(ROUTE_EVENEMENT_DETAILS_PREFIX.length()).trim();
            try {
                int id = Integer.parseInt(raw);
                showEvenementDetails(id);
            } catch (Exception e) {
                info("Navigation", "ID événement invalide: " + raw);
            }
            return;
        }

        if (route.startsWith(ROUTE_LIEUX_FILTER_PREFIX)) {
            String payload = route.substring(ROUTE_LIEUX_FILTER_PREFIX.length());
            Map<String, String> params = parseParams(payload);

            String ville = decode(params.get("ville"));
            String cat = decode(params.get("cat"));
            String q = decode(params.get("q"));

            LieuxController lc = openLieuxPageAndGetController();
            if (lc != null) {
                lc.applyPreset(ville, cat, q);
            }
            return;
        }

        switch (route) {
            case ROUTE_HOME -> showAccueil();
            case ROUTE_SORTIES -> showSorties();
            case ROUTE_LIEUX -> showLieux();
            case ROUTE_OFFRES -> showOffres();
            case ROUTE_EVENTS -> showEvents();
            case ROUTE_HELP -> showHelp();
            case ROUTE_PROFIL -> showProfil();
            case ROUTE_CHATBOT -> showChatbot();
            case ROUTE_FAVORIS -> showMesFavoris();
            case ROUTE_GAMIFICATION -> showGamification();
            default -> info("Navigation", "Route inconnue: " + route);
        }
    }

    private LieuxController openLieuxPageAndGetController() {
        navLieux.setSelected(true);
        setHeader("Lieux", "Découverte · filtres · détails");

        Object controller = ensureLoadedAndShow(ROUTE_LIEUX, ViewPaths.FRONT_LIEUX);
        if (controller instanceof LieuxController lc) return lc;
        return null;
    }

    private void showLieuDetails(int id) {
        pushHistory(ROUTE_LIEU_DETAILS_PREFIX + id);
        navLieux.setSelected(true);
        setHeader("Lieux", "Détails du lieu");

        try {
            URL url = getClass().getResource(ViewPaths.FRONT_LIEU_DETAILS);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.FRONT_LIEU_DETAILS);

            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, resolveStage());
            trySetCurrentUser(controller, currentUser);
            trySetNavigator(controller, this);

            if (controller instanceof LieuDetailsController c) {
                c.setLieuId(id);
            }

            animateSwap(view);

        } catch (Exception e) {
            error("Chargement vue",
                    "Vue: " + ViewPaths.FRONT_LIEU_DETAILS + "\n" +
                            e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private void showEvenementDetails(int id) {
        pushHistory(ROUTE_EVENEMENT_DETAILS_PREFIX + id);
        navEvents.setSelected(true);
        setHeader("Événements", "Détails de l'événement");

        try {
            URL url = getClass().getResource(ViewPaths.FRONT_EVENEMENT_DETAILS);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.FRONT_EVENEMENT_DETAILS);

            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, resolveStage());
            trySetCurrentUser(controller, currentUser);
            trySetNavigator(controller, this);

            if (controller instanceof EvenementDetailsController c) {
                c.setEvenementId(id);
            }

            animateSwap(view);

        } catch (Exception e) {
            error("Chargement vue",
                    "Vue: " + ViewPaths.FRONT_EVENEMENT_DETAILS + "\n" +
                            e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    @FXML
    public void doSearch() {
        String q = safe(searchField.getText()).trim();
        if (q.isEmpty()) {
            info("Recherche", "Entre un mot-clé puis clique sur Go.");
            return;
        }
        navigate(ROUTE_LIEUX_FILTER_PREFIX + "q=" + q);
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
        ensureLoadedAndShow(ROUTE_PROFIL, ViewPaths.FRONT_PROFIL);
    }

    @FXML public void showSecurity() { info("Sécurité", "Brancher: mot de passe / 2FA / sessions."); }
    @FXML public void showSettings() { info("Paramètres", "Brancher: préférences, langue, notifications."); }
    @FXML public void showMyActivity() { info("Activités", "Brancher: historique, favoris, participations."); }

    @FXML
    public void showMesFavoris() {
        closeAccount();
        setHeader("Mes favoris", "Vos lieux enregistrés");
        ensureLoadedAndShow(ROUTE_FAVORIS, "/fxml/front/lieux/MesFavorisView.fxml");
    }

    @FXML
    public void logout() {
        try {
            Stage stage = resolveStage();
            viewCache.clear();
            controllerCache.clear();
            currentUser = null;

            URL url = getClass().getResource(ViewPaths.LOGIN);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.LOGIN);

            FXMLLoader loader = new FXMLLoader(url);
            stage.getScene().setRoot(loader.load());
        } catch (Exception e) {
            error("Déconnexion", e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private Object ensureLoadedAndShow(String key, String fxmlPath) {
        Node cached = viewCache.get(key);
        if (cached != null) {
            animateSwap(cached);
            return controllerCache.get(key);
        }

        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + fxmlPath);

            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, resolveStage());
            trySetCurrentUser(controller, currentUser);
            trySetNavigator(controller, this);

            viewCache.put(key, view);
            controllerCache.put(key, controller);

            animateSwap(view);
            return controller;

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            error("Chargement vue",
                    "Vue: " + fxmlPath + "\n" +
                            root.getClass().getSimpleName() + " : " + safe(root.getMessage()));
            e.printStackTrace();
            return null;
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

    private void setHeader(String title, String subtitle) {
        if (pageTitle != null) pageTitle.setText(title);
        if (pageSubtitle != null) pageSubtitle.setText(subtitle);
    }

    private void refreshAccountUI() {
        String display = "Utilisateur";
        String role = "USER";
        String email = "email@example.com";
        String letter = "U";

        if (currentUser != null) {
            String full = (safe(currentUser.getPrenom()) + " " + safe(currentUser.getNom())).trim();
            if (!full.isEmpty()) display = full;

            String r = safe(currentUser.getRole()).trim();
            if (!r.isEmpty()) role = r.toUpperCase();

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
        try {
            controller.getClass().getMethod("setPrimaryStage", Stage.class).invoke(controller, stage);
        } catch (Exception ignored) {}
    }

    private void trySetCurrentUser(Object controller, User user) {
        try {
            controller.getClass().getMethod("setCurrentUser", User.class).invoke(controller, user);
        } catch (Exception ignored) {}
    }

    private void trySetNavigator(Object controller, ShellNavigator nav) {
        try {
            controller.getClass().getMethod("setNavigator", ShellNavigator.class).invoke(controller, nav);
        } catch (Exception ignored) {}
    }

    private Map<String, String> parseParams(String payload) {
        Map<String, String> out = new HashMap<>();
        if (payload == null) return out;

        String p = payload.trim();
        if (p.isEmpty()) return out;

        String[] parts = p.split("[;&]");
        for (String part : parts) {
            if (part == null) continue;
            String s = part.trim();
            if (s.isEmpty()) continue;

            int idx = s.indexOf('=');
            if (idx < 0) {
                out.put(s.toLowerCase(), "");
                continue;
            }
            String k = s.substring(0, idx).trim().toLowerCase();
            String v = s.substring(idx + 1).trim();
            out.put(k, v);
        }
        return out;
    }

    private String decode(String raw) {
        if (raw == null) return null;
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void error(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String safe(String s) { return s == null ? "" : s; }
}