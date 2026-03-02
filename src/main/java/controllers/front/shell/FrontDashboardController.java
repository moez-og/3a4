package controllers.front.shell;

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
import java.util.HashMap;
import java.util.Map;
import controllers.front.evenements.EvenementDetailsController;
import controllers.front.evenements.EvenementsController;
import controllers.front.evenements.PaiementController;

public class FrontDashboardController implements ShellNavigator {

    public static final String ROUTE_HOME = "home";
    public static final String ROUTE_SORTIES = "sorties";
    public static final String ROUTE_LIEUX = "lieux";
    public static final String ROUTE_OFFRES = "offres";
    public static final String ROUTE_EVENTS = "events";
    public static final String ROUTE_HELP = "help";
    public static final String ROUTE_PROFIL = "profil";
    public static final String ROUTE_EVENEMENT_DETAILS_PREFIX = "evenement-details:";
    public static final String ROUTE_PAIEMENT_PREFIX = "paiement:";

// "lieu-details:12"
    public static final String ROUTE_LIEU_DETAILS_PREFIX = "lieu-details:";

    // "lieux-filter:ville=Tunis;cat=CAFE;q=cafe"
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
        ensureLoadedAndShow(ROUTE_HOME, ViewPaths.FRONT_HOME);
    }

    @FXML
    public void showSorties() {
        navSorties.setSelected(true);
        setHeader("Sorties", "Annonces · participations · suivi");
        ensureLoadedAndShow(ROUTE_SORTIES, ViewPaths.FRONT_SORTIES);
    }

    @FXML
    public void showLieux() {
        openLieuxPageAndGetController();
    }

    @FXML
    public void showOffres() {
        navOffres.setSelected(true);
        setHeader("Offres", "Promos · partenariats · coupons");
        ensureLoadedAndShow(ROUTE_OFFRES, ViewPaths.FRONT_OFFRES);
    }

    @FXML
    public void showEvents() {
        navEvents.setSelected(true);
        setHeader("Événements", "Agenda · inscriptions · infos");
        Object ctrl = ensureLoadedAndShow(ROUTE_EVENTS, ViewPaths.FRONT_EVENEMENTS);
        // Toujours rafraîchir les cartes pour refléter l'état réel (paiement, inscription…)
        if (ctrl instanceof controllers.front.evenements.EvenementsController ec) {
            ec.refreshCards();
        }
    }

    @FXML
    public void showHelp() {
        navHelp.setSelected(true);
        setHeader("Aide", "FAQ · guide · support");
        ensureLoadedAndShow(ROUTE_HELP, ViewPaths.FRONT_HELP);
    }

    /* ================= ROUTER ================= */

    @Override
    public void navigate(String route) {
        if (route == null) return;

        // ✅ Détails lieu
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

        // ✅ Lieux avec filtre (ville/cat/q)
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

        // ✅ Paiement
        if (route.startsWith(ROUTE_PAIEMENT_PREFIX)) {
            String raw = route.substring(ROUTE_PAIEMENT_PREFIX.length()).trim();
            try {
                int inscriptionId = Integer.parseInt(raw);
                showPaiement(inscriptionId);
            } catch (Exception e) {
                info("Navigation", "ID inscription invalide: " + raw);
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

    /* ================= SEARCH / ACTIONS ================= */

    @FXML
    public void doSearch() {
        String q = safe(searchField.getText()).trim();
        if (q.isEmpty()) {
            info("Recherche", "Entre un mot-clé puis clique sur Go.");
            return;
        }
        // pour l'instant on redirige vers Lieux filtré
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
        ensureLoadedAndShow(ROUTE_PROFIL, ViewPaths.FRONT_PROFIL);
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

    /* ================= CORE LOADER ================= */

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
            error("Chargement vue",
                    "Vue: " + fxmlPath + "\n" +
                            e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
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

    private void showPaiement(int inscriptionId) {
        navEvents.setSelected(true);
        setHeader("Paiement", "Finaliser votre paiement");

        // ── Invalider le cache de la liste événements pour forcer le rafraîchissement
        //    après le paiement (sinon le tag reste "En attente de paiement") ──
        viewCache.remove(ROUTE_EVENTS);
        controllerCache.remove(ROUTE_EVENTS);

        try {
            URL url = getClass().getResource(ViewPaths.FRONT_PAIEMENT);
            if (url == null) throw new IllegalStateException("FXML introuvable: " + ViewPaths.FRONT_PAIEMENT);

            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            Object controller = loader.getController();
            trySetPrimaryStage(controller, resolveStage());
            trySetCurrentUser(controller, currentUser);
            trySetNavigator(controller, this);

            if (controller instanceof PaiementController c) {
                c.setInscriptionId(inscriptionId);
            }

            animateSwap(view);

        } catch (Exception e) {
            error("Chargement vue",
                    "Vue: " + ViewPaths.FRONT_PAIEMENT + "\n" +
                            e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
    }

    private void showEvenementDetails(int id) {
        navEvents.setSelected(true);
        setHeader("Événements", "Détails de l'événement");

        // ── Invalider le cache de la liste événements pour forcer le rafraîchissement
        //    quand on revient (état inscription/paiement peut avoir changé) ──
        viewCache.remove(ROUTE_EVENTS);
        controllerCache.remove(ROUTE_EVENTS);

        try {
            URL url = getClass().getResource(ViewPaths.FRONT_EVENEMENT_DETAILS); // ex: "/views/front/evenements/EvenementDetailsView.fxml"
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
}
