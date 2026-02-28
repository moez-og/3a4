// (FICHIER COMPLET) — colle exactement celui-ci
// ⚠️ Le fichier est long, mais je te le donne complet comme demandé.

package controllers.front.shell;

import controllers.front.evenements.EvenementDetailsController;
import controllers.front.lieux.LieuDetailsController;
import controllers.front.lieux.LieuxController;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.lieux.Lieu;
import models.notifications.LieuNotification;
import models.users.User;
import services.notifications.LieuSuggestionService;
import utils.ui.GestureNavigationManager;
import utils.ui.ShellNavigator;
import utils.ui.ViewPaths;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

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

    @FXML private StackPane bellPane;
    @FXML private Button    btnNotif;
    @FXML private Label     notifBadge;
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

    // ── Notification state ──────────────────────────────────────────────────
    private final List<LieuNotification>  notifications      = new ArrayList<>();
    private final LieuSuggestionService   suggestionService  = new LieuSuggestionService();
    private Popup                          notifPopup         = null;

    /** Navigation history — top = current route (used by GestureNavigationManager) */
    private final Deque<String> navHistory = new ArrayDeque<>();
    private GestureNavigationManager gestureNav = null;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshAccountUI();
        // Generate fresh suggestions based on this user's favorites
        javafx.application.Platform.runLater(this::loadNotifications);
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
        if (notifPopup != null && notifPopup.isShowing()) {
            notifPopup.hide();
            return;
        }
        buildAndShowNotifPopup();
    }

    // ── Notification popup ───────────────────────────────────────────────────

    private void buildAndShowNotifPopup() {
        // ── Header ──────────────────────────────────────────────────────────
        long unread = notifications.stream().filter(n -> !n.isRead()).count();

        javafx.scene.control.Label titleLbl = new javafx.scene.control.Label("🔔  Suggestions pour vous");
        titleLbl.getStyleClass().add("notifPopupTitle");

        javafx.scene.control.Label subtitleLbl = new javafx.scene.control.Label(
            unread > 0 ? unread + " nouvelle(s) suggestion(s)" : "Tout est lu"
        );
        subtitleLbl.getStyleClass().add("notifPopupSubtitle");

        javafx.scene.control.Button markAllBtn = new javafx.scene.control.Button("Tout marquer lu");
        markAllBtn.getStyleClass().add("notifMarkAllBtn");
        markAllBtn.setOnAction(e -> {
            notifications.forEach(LieuNotification::markRead);
            updateBadge();
            if (notifPopup != null) {
                notifPopup.hide();
                buildAndShowNotifPopup();
            }
        });

        Region hSpacer = new Region();
        javafx.scene.layout.HBox.setHgrow(hSpacer, Priority.ALWAYS);

        javafx.scene.layout.HBox titleRow = new javafx.scene.layout.HBox(8,
            new VBox(2, titleLbl, subtitleLbl), hSpacer, markAllBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("notifPopupHeader");
        titleRow.setPadding(new Insets(14, 16, 10, 16));
        // Solid header bg — prevents transparency bleed
        titleRow.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 14px 14px 0px 0px;" +
            "-fx-border-color: transparent transparent #e2e8f0 transparent;" +
            "-fx-border-width: 0 0 1px 0;"
        );

        // ── Notification rows ────────────────────────────────────────────────
        VBox listBox = new VBox(0);
        // Solid list background — no transparency
        listBox.setStyle("-fx-background-color: #ffffff;");

        if (notifications.isEmpty()) {
            javafx.scene.control.Label empty = new javafx.scene.control.Label("✨  Ajoutez des lieux en favoris pour recevoir des suggestions !");
            empty.getStyleClass().add("notifEmpty");
            empty.setWrapText(true);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(28, 20, 28, 20));
            listBox.getChildren().add(empty);
        } else {
            for (int i = 0; i < notifications.size(); i++) {
                LieuNotification n = notifications.get(i);
                listBox.getChildren().add(buildNotifRow(n));
                if (i < notifications.size() - 1) {
                    Region div = new Region();
                    div.getStyleClass().add("notifDivider");
                    div.setMaxWidth(Double.MAX_VALUE);
                    listBox.getChildren().add(div);
                }
            }
        }

        ScrollPane sp = new ScrollPane(listBox);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Keep the ScrollPane viewport solid white (not transparent)
        sp.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-background: #ffffff;" +
            "-fx-border-color: transparent;"
        );
        sp.setPrefHeight(Math.min(notifications.size() * 72 + 20, 380));

        // ── Footer ───────────────────────────────────────────────────────────
        javafx.scene.control.Button refreshBtn = new javafx.scene.control.Button("🔄  Actualiser les suggestions");
        refreshBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #64748b;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 800;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 6px 0;");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> {
            if (notifPopup != null) notifPopup.hide();
            loadNotifications();
            javafx.application.Platform.runLater(this::buildAndShowNotifPopup);
        });

        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(refreshBtn);
        footer.getStyleClass().add("notifFooter");
        footer.setAlignment(Pos.CENTER);
        // Solid footer bg
        footer.setStyle(
            "-fx-background-color: #f8fafc;" +
            "-fx-background-radius: 0px 0px 14px 14px;" +
            "-fx-border-color: #e2e8f0 transparent transparent transparent;" +
            "-fx-border-width: 1px 0 0 0;" +
            "-fx-padding: 8px 14px;"
        );

        // ── Assemble ─────────────────────────────────────────────────────────
        VBox popupRoot = new VBox(titleRow, sp, footer);
        popupRoot.getStyleClass().add("notifPopupRoot");
        popupRoot.setPrefWidth(360);
        popupRoot.setMaxWidth(360);

        // ── Force fully opaque white background at the inline style level.
        // JavaFX Popup scenes are transparent by default; the CSS class alone
        // is not enough — we set the background directly so no content from
        // behind the popup bleeds through.
        popupRoot.setStyle(
            "-fx-background-color: #ffffff;" +
            "-fx-background-radius: 14px;" +
            "-fx-border-color: #dee5ef;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 14px;" +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.22), 28, 0, 0, 10);"
        );

        // Copy stylesheet from main scene so CSS classes still apply
        if (root != null && root.getScene() != null) {
            for (String ss : root.getScene().getStylesheets()) {
                if (!popupRoot.getStylesheets().contains(ss)) popupRoot.getStylesheets().add(ss);
            }
        }

        notifPopup = new Popup();
        notifPopup.setAutoHide(true);
        // Make the popup window itself opaque (no OS-level transparency)
        notifPopup.getScene().setFill(javafx.scene.paint.Color.TRANSPARENT);
        notifPopup.getContent().add(popupRoot);

        // Position: below the bell button
        javafx.geometry.Bounds bellBounds = null;
        try {
            if (btnNotif != null && btnNotif.getScene() != null)
                bellBounds = btnNotif.localToScreen(btnNotif.getBoundsInLocal());
        } catch (Exception ignored) {}

        double x = bellBounds != null ? bellBounds.getMinX() - 300 : 200;
        double y = bellBounds != null ? bellBounds.getMaxY() + 8   : 60;

        // Fade in
        popupRoot.setOpacity(0);
        popupRoot.setTranslateY(-8);
        notifPopup.show(resolveStage(), x, y);

        FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(180), popupRoot);
        ft.setToValue(1); ft.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        TranslateTransition tt = new TranslateTransition(javafx.util.Duration.millis(180), popupRoot);
        tt.setToY(0); tt.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        ft.play(); tt.play();
    }

    private javafx.scene.Node buildNotifRow(LieuNotification n) {
        // Icon circle
        javafx.scene.control.Label iconLbl = new javafx.scene.control.Label(n.getCategoryIcon());
        iconLbl.getStyleClass().add("notifIcon");
        iconLbl.setMinSize(40, 40); iconLbl.setMaxSize(40, 40);
        iconLbl.setAlignment(Pos.CENTER);

        // Text content
        javafx.scene.control.Label nameLbl = new javafx.scene.control.Label(safe(n.getLieu().getNom()));
        nameLbl.getStyleClass().add("notifLieuName");
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(220);

        String meta = buildMeta(n.getLieu());
        javafx.scene.control.Label metaLbl = new javafx.scene.control.Label(meta);
        metaLbl.getStyleClass().add("notifLieuMeta");

        javafx.scene.control.Label reasonLbl = new javafx.scene.control.Label(n.getReason());
        reasonLbl.getStyleClass().add("notifReason");
        reasonLbl.setWrapText(true);
        reasonLbl.setMaxWidth(220);

        // Type chip
        javafx.scene.control.Label typeLbl = new javafx.scene.control.Label(n.getTypeLabel());
        typeLbl.getStyleClass().add("notifTypeChip");

        VBox textCol = new VBox(2, nameLbl, metaLbl, reasonLbl);
        javafx.scene.layout.HBox.setHgrow(textCol, Priority.ALWAYS);

        // Unread blue dot
        Region dot = new Region();
        dot.getStyleClass().add("notifUnreadDot");
        dot.setMinSize(8, 8); dot.setMaxSize(8, 8);
        dot.setVisible(!n.isRead()); dot.setManaged(!n.isRead());

        VBox rightCol = new VBox(dot);
        rightCol.setAlignment(Pos.TOP_CENTER);
        rightCol.setPadding(new Insets(4, 0, 0, 0));

        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(12, iconLbl, textCol, rightCol);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.getStyleClass().addAll("notifRow", n.isRead() ? "" : "notifRowUnread");
        row.getStyleClass().removeIf(String::isEmpty);
        row.setMinWidth(340);
        // Ensure solid background per row (belt-and-suspenders vs CSS)
        row.setStyle(n.isRead()
            ? "-fx-background-color: #ffffff; -fx-cursor: hand;"
            : "-fx-background-color: #eff6ff; -fx-cursor: hand;");
        // Hover: swap bg via mouse events since JavaFX doesn't animate CSS :hover transitions
        final String normalBg = n.isRead() ? "#ffffff" : "#eff6ff";
        final String hoverBg  = n.isRead() ? "#f8fafc" : "#dbeafe";
        row.setOnMouseEntered(ev -> row.setStyle("-fx-background-color: " + hoverBg + "; -fx-cursor: hand;"));
        row.setOnMouseExited(ev  -> row.setStyle("-fx-background-color: " + normalBg + "; -fx-cursor: hand;"));

        // Click → mark read + navigate to lieu details
        row.setOnMouseClicked(e -> {
            n.markRead();
            dot.setVisible(false);
            dot.setManaged(false);
            row.getStyleClass().remove("notifRowUnread");
            // Update inline style to read state
            row.setStyle("-fx-background-color: #ffffff; -fx-cursor: hand;");
            row.setOnMouseEntered(ev -> row.setStyle("-fx-background-color: #f8fafc; -fx-cursor: hand;"));
            row.setOnMouseExited(ev  -> row.setStyle("-fx-background-color: #ffffff; -fx-cursor: hand;"));
            updateBadge();
            if (notifPopup != null) notifPopup.hide();
            navigate(ROUTE_LIEU_DETAILS_PREFIX + n.getLieu().getId());
        });

        return row;
    }

    private String buildMeta(models.lieux.Lieu l) {
        String cat   = safe(l.getCategorie()).trim();
        String ville = safe(l.getVille()).trim();
        if (!cat.isEmpty() && !ville.isEmpty()) return cat + "  ·  " + ville;
        if (!cat.isEmpty())  return cat;
        if (!ville.isEmpty()) return ville;
        return "";
    }

    // ── Badge management ──────────────────────────────────────────────────────

    private void updateBadge() {
        if (notifBadge == null) return;
        long unread = notifications.stream().filter(n -> !n.isRead()).count();
        javafx.application.Platform.runLater(() -> {
            if (unread <= 0) {
                notifBadge.setVisible(false);
                notifBadge.setManaged(false);
            } else {
                notifBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
                notifBadge.setVisible(true);
                notifBadge.setManaged(true);
                // Pulse animation on badge update
                javafx.animation.ScaleTransition pulse =
                    new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), notifBadge);
                pulse.setFromX(1.0); pulse.setFromY(1.0);
                pulse.setToX(1.3);   pulse.setToY(1.3);
                javafx.animation.ScaleTransition shrink =
                    new javafx.animation.ScaleTransition(javafx.util.Duration.millis(150), notifBadge);
                shrink.setToX(1.0); shrink.setToY(1.0);
                pulse.setOnFinished(ev -> shrink.play());
                pulse.play();
            }
        });
    }

    // ── Load/generate notifications ───────────────────────────────────────────

    private void loadNotifications() {
        if (currentUser == null) return;
        notifications.clear();
        try {
            List<LieuNotification> suggestions = suggestionService.generateSuggestions(currentUser.getId());
            notifications.addAll(suggestions);
        } catch (Exception ignored) {}
        updateBadge();
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