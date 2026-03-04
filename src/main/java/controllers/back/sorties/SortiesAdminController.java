package controllers.back.sorties;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import controllers.front.sorties.GroupeChatController;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.users.User;
import services.sorties.AnnonceSortieService;
import services.sorties.ChatService;
import services.sorties.ParticipationSortieService;
import services.users.UserService;
import utils.files.UploadStore;
import utils.geo.TunisiaGeo;
import utils.json.JsonStringArray;
import utils.sorties.SortieActivities;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

public class SortiesAdminController {

    private static final int PAGE_SIZE = 6;

    @FXML private ScrollPane cardsScroll;
    @FXML private TilePane cardsPane;

    @FXML private HBox paginationBar;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Label pageLabel;

    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortCombo;
    @FXML private ToggleButton sortDirBtn;
    @FXML private Button btnAdd;

    @FXML private Label kpiTotal;
    @FXML private Label kpiOuvertes;
    @FXML private Label kpiAvenir;

    private final AnnonceSortieService service = new AnnonceSortieService();
    private final ParticipationSortieService participationService = new ParticipationSortieService();
    private final UserService userService = new UserService();
    private final ChatService chatService = new ChatService();
    private final ObservableList<AnnonceSortie> masterList = FXCollections.observableArrayList();
    private final FilteredList<AnnonceSortie> filteredList = new FilteredList<>(masterList, p -> true);

    private User currentUser;

    private Node selectedCard = null;
    private double lastViewportW = -1;

    private int currentPage = 0;
    private int totalPages = 1;
    private List<AnnonceSortie> lastViewList = List.of();

    private final Map<Integer, List<Label>> chatBadgesByAnnonceId = new HashMap<>();
    private volatile long chatUnreadRefreshToken = 0;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");
    private List<String> regionsForVille(String ville) {
        return TunisiaGeo.regionsForVille(ville);
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refreshChatUnreadBadgesAsync();
    }

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupSearchFilter();
        setupSortControls();
        setupButtons();
        setupResponsiveTiles();
        try { chatService.ensureSchema(); } catch (Exception ignored) {}
        loadData();
    }

    private void setupFilterCombo() {
        filterCombo.setItems(FXCollections.observableArrayList("Titre", "Ville", "Activité", "Statut"));
        filterCombo.getSelectionModel().select("Titre");
        filterCombo.valueProperty().addListener((obs, o, n) -> {
            currentPage = 0;
            renderCards(getViewList());
        });
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, o, n) -> {
            filteredList.setPredicate(a -> matchesFilter(a, n));
            currentPage = 0;
            renderCards(getViewList());
        });
    }

    private void setupSortControls() {
        if (sortCombo != null) {
            sortCombo.setItems(FXCollections.observableArrayList(
                    "Date sortie", "Titre", "Ville", "Statut", "Places"
            ));
            sortCombo.getSelectionModel().select("Date sortie");
            sortCombo.valueProperty().addListener((obs, o, n) -> {
                currentPage = 0;
                renderCards(getViewList());
            });
        }

        if (sortDirBtn != null) {
            sortDirBtn.setSelected(false);
            sortDirBtn.setText("↓");
            sortDirBtn.selectedProperty().addListener((obs, o, selected) -> {
                sortDirBtn.setText(selected ? "↑" : "↓");
                currentPage = 0;
                renderCards(getViewList());
            });
        }
    }

    private List<AnnonceSortie> getViewList() {
        List<AnnonceSortie> view = new ArrayList<>(filteredList);

        String key = sortCombo == null || sortCombo.getValue() == null ? "Date sortie" : sortCombo.getValue();
        boolean asc = sortDirBtn != null && sortDirBtn.isSelected();

        Comparator<AnnonceSortie> cmp = switch (key) {
            case "Titre"  -> Comparator.comparing(a -> safe(a.getTitre()).toLowerCase());
            case "Ville"  -> Comparator.comparing(a -> safe(a.getVille()).toLowerCase());
            case "Statut" -> Comparator.comparing(a -> safe(a.getStatut()).toLowerCase());
            case "Places" -> Comparator.comparingInt(a -> Math.max(0, a.getNbPlaces()));
            default -> Comparator.comparing(AnnonceSortie::getDateSortie,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };

        view.sort(asc ? cmp : cmp.reversed());
        return view;
    }

    private boolean matchesFilter(AnnonceSortie a, String q) {
        String query = (q == null) ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return true;

        String mode = filterCombo.getValue() == null ? "Titre" : filterCombo.getValue();

        return switch (mode) {
            case "Ville"    -> contains(a.getVille(), query) || contains(a.getLieuTexte(), query);
            case "Activité" -> contains(a.getTypeActivite(), query);
            case "Statut"   -> contains(a.getStatut(), query);
            default         -> contains(a.getTitre(), query) || contains(a.getDescription(), query);
        };
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private void setupButtons() {
        btnAdd.setOnAction(e -> openEditor(null));
    }

    private void setupResponsiveTiles() {
        if (cardsScroll == null || cardsPane == null) return;

        cardsScroll.setFitToWidth(true);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        cardsPane.setPrefColumns(3);

        cardsScroll.viewportBoundsProperty().addListener((obs, oldB, b) -> {
            if (b == null) return;
            double viewportW = b.getWidth();
            if (lastViewportW > 0 && Math.abs(viewportW - lastViewportW) < 0.5) return;
            lastViewportW = viewportW;
            applyFixedGrid(viewportW);
        });

        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) {
            applyFixedGrid(b.getWidth());
        }
    }

    private void applyFixedGrid(double viewportW) {
        Insets in = cardsPane.getInsets();
        double insets = (in == null) ? 0 : (in.getLeft() + in.getRight());
        double gap = cardsPane.getHgap() > 0 ? cardsPane.getHgap() : 14;
        double available = Math.max(0, viewportW - insets);

        int cols = 3;
        double tileW = Math.floor((available - gap * (cols - 1)) / cols);
        tileW = Math.max(0, tileW - 1);

        cardsPane.setPrefColumns(cols);
        cardsPane.setPrefTileWidth(tileW);
    }

    private void loadData() {
        try {
            masterList.clear();
            List<AnnonceSortie> all = service.getAll();
            masterList.addAll(all);

            updateKpis(all);
            currentPage = 0;
            renderCards(getViewList());

        } catch (Exception e) {
            showError("Erreur", "Chargement des sorties impossible", safe(e.getMessage()));
        }
    }

    private void updateKpis(List<AnnonceSortie> all) {
        if (kpiTotal != null) kpiTotal.setText(String.valueOf(all.size()));

        long opened = all.stream()
                .filter(a -> "OUVERTE".equalsIgnoreCase(safe(a.getStatut()))).count();
        if (kpiOuvertes != null) kpiOuvertes.setText(String.valueOf(opened));

        LocalDateTime now = LocalDateTime.now();
        long futur = all.stream()
                .filter(a -> a.getDateSortie() != null && a.getDateSortie().isAfter(now)).count();
        if (kpiAvenir != null) kpiAvenir.setText(String.valueOf(futur));
    }

    private void renderCards(List<AnnonceSortie> list) {
        lastViewList = (list == null) ? List.of() : list;
        selectedCard = null;

        if (lastViewList.isEmpty()) {
            cardsPane.getChildren().clear();
            Label empty = new Label("Aucune annonce trouvée.");
            empty.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 800;");
            cardsPane.getChildren().add(empty);
            setPaginationVisible(false);
            return;
        }

        renderCurrentPage();
    }

    private void renderCurrentPage() {
        List<AnnonceSortie> list = (lastViewList == null) ? List.of() : lastViewList;

        totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
        if (currentPage < 0) currentPage = 0;
        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(list.size(), from + PAGE_SIZE);

        cardsPane.getChildren().clear();
        chatBadgesByAnnonceId.clear();
        for (int i = from; i < to; i++) {
            cardsPane.getChildren().add(createCard(list.get(i)));
        }

        refreshChatUnreadBadgesAsync();

        boolean showPager = list.size() > PAGE_SIZE;
        setPaginationVisible(showPager);
        if (showPager) {
            if (pageLabel   != null) pageLabel.setText("Page " + (currentPage + 1) + "/" + totalPages);
            if (btnPrevPage != null) btnPrevPage.setDisable(currentPage == 0);
            if (btnNextPage != null) btnNextPage.setDisable(currentPage >= totalPages - 1);
        }

        if (cardsScroll != null) cardsScroll.setVvalue(0);
    }

    private void refreshChatUnreadBadgesAsync() {
        if (currentUser == null || currentUser.getId() <= 0) return;
        if (chatBadgesByAnnonceId.isEmpty()) return;

        final long token = ++chatUnreadRefreshToken;
        final int uid = currentUser.getId();
        final Map<Integer, List<Label>> snapshot = new HashMap<>(chatBadgesByAnnonceId);

        new Thread(() -> {
            try { chatService.ensureSchema(); } catch (Exception ignored) {}
            for (Map.Entry<Integer, List<Label>> e : snapshot.entrySet()) {
                if (token != chatUnreadRefreshToken) return;
                int annonceId = e.getKey();
                List<Label> badges = e.getValue();
                long count;
                try {
                    count = chatService.getUnreadCount(annonceId, uid);
                } catch (Exception ex) {
                    count = 0;
                }
                long finalCount = count;
                Platform.runLater(() -> {
                    if (badges == null) return;
                    for (Label b : badges) setChatBadgeValue(b, finalCount);
                });
            }
        }, "back-chat-unread-refresh").start();
    }

    private StackPane wrapChatButtonWithBadge(Button chatButton, int annonceId) {
        Label badge = new Label();
        badge.getStyleClass().add("chatUnreadBadge");
        badge.setVisible(false);
        badge.setManaged(false);
        badge.setMouseTransparent(true);

        StackPane wrapper = new StackPane(chatButton, badge);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        StackPane.setMargin(badge, new Insets(-6, -6, 0, 0));
        wrapper.setPickOnBounds(false);

        if (annonceId > 0) {
            chatBadgesByAnnonceId.computeIfAbsent(annonceId, k -> new ArrayList<>()).add(badge);
        }
        return wrapper;
    }

    private void setChatBadgeValue(Label badge, long count) {
        if (badge == null) return;
        long v = Math.max(0, count);
        boolean show = v > 0;
        badge.setText(v > 99 ? "99+" : String.valueOf(v));
        badge.setVisible(show);
        badge.setManaged(show);
    }

    private void setPaginationVisible(boolean visible) {
        if (paginationBar == null) return;
        paginationBar.setVisible(visible);
        paginationBar.setManaged(visible);
    }

    @FXML private void prevPage() {
        if (currentPage <= 0) return;
        currentPage--;
        renderCurrentPage();
    }

    @FXML private void nextPage() {
        if (currentPage >= totalPages - 1) return;
        currentPage++;
        renderCurrentPage();
    }

    private Node createCard(AnnonceSortie a) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortie-card");
        card.setMinWidth(0);
        card.prefWidthProperty().bind(cardsPane.prefTileWidthProperty());
        card.maxWidthProperty().bind(cardsPane.prefTileWidthProperty());

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");
        imgWrap.setMaxWidth(Double.MAX_VALUE);

        ImageView iv = new ImageView();
        iv.fitWidthProperty().bind(imgWrap.widthProperty());
        iv.setFitHeight(170);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);

        Rectangle clip = new Rectangle(0, 170);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(iv.fitWidthProperty());
        iv.setClip(clip);

        iv.setImage(loadImageOrFallback(a.getImageUrl()));
        imgWrap.getChildren().add(iv);

        String st = safe(a.getStatut()).trim();
        Label statut = new Label(st);
        statut.getStyleClass().add("statusChip");
        if (!st.isEmpty()) statut.getStyleClass().add("status-" + st.toLowerCase());
        StackPane.setAlignment(statut, Pos.TOP_LEFT);
        StackPane.setMargin(statut, new Insets(10, 0, 0, 10));
        imgWrap.getChildren().add(statut);

        // Badge 👑 si annonce créée par l'admin connecté
        if (currentUser != null && a.getUserId() == currentUser.getId()) {
            Label adminBadge = new Label("👑 Ma sortie");
            adminBadge.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; " +
                    "-fx-background-radius: 20; -fx-padding: 3 10; -fx-font-size: 11px; -fx-font-weight: 800;");
            StackPane.setAlignment(adminBadge, Pos.TOP_RIGHT);
            StackPane.setMargin(adminBadge, new Insets(10, 10, 0, 0));
            imgWrap.getChildren().add(adminBadge);
        }

        Label title = new Label(safe(a.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        Label meta = new Label(
                safe(a.getVille()) + " • " + safe(a.getLieuTexte()) + " • " + safe(a.getTypeActivite())
        );
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        String when   = a.getDateSortie() == null ? "" : DT_FMT.format(a.getDateSortie());
        String budget = a.getBudgetMax() <= 0
                ? "Aucun budget"
                : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");
        Label line = new Label("📅 " + when + "   •   💰 " + budget + "   •   👥 " + a.getNbPlaces() + " places");
        line.getStyleClass().add("cardLine");
        line.setWrapText(true);

        Label meet = new Label("🤝 " + safe(a.getPointRencontre()));
        meet.getStyleClass().add("cardLine");
        meet.setWrapText(true);

        // Séparateur discret
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMaxWidth(Double.MAX_VALUE);
        sep.setStyle("-fx-background-color: #f1f5f9; -fx-margin: 0 14;");
        sep.setTranslateX(14);
        VBox.setMargin(sep, new Insets(4, 14, 4, 14));

        Button quickEdit   = new Button("✏ Modifier");
        Button quickParts  = new Button("👥 Participations");
        Button quickChat   = new Button("💬");
        Button quickDelete = new Button("🗑");
        quickEdit.getStyleClass().add("card-btn");
        quickParts.getStyleClass().add("card-btn");
        quickChat.getStyleClass().add("card-btn");
        quickChat.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1e40af; -fx-border-color: #bfdbfe;");
        quickDelete.getStyleClass().addAll("card-btn", "danger");
        quickChat.setTooltip(new Tooltip("Ouvrir le chat de groupe"));
        quickDelete.setTooltip(new Tooltip("Supprimer"));

        quickEdit.setOnAction(e -> openEditor(a));
        quickParts.setOnAction(e -> openParticipations(a));
        quickChat.setOnAction(e -> openChatAdmin(a));
        quickDelete.setOnAction(e -> {
            if (confirmDelete("Supprimer l'annonce: " + safe(a.getTitre()) + " ?")) {
                try {
                    service.delete(a.getId());
                    loadData();
                } catch (Exception ex) {
                    showError("Erreur", "Suppression impossible", safe(ex.getMessage()));
                }
            }
        });

        Region actSpacer = new Region();
        HBox.setHgrow(actSpacer, Priority.ALWAYS);
        Node chatNode = wrapChatButtonWithBadge(quickChat, a.getId());
        HBox actions = new HBox(8, quickEdit, quickParts, actSpacer, chatNode, quickDelete);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("card-actions");

        card.getChildren().addAll(imgWrap, title, meta, line, meet, sep, actions);
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> selectCard(card));
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2) openEditor(a);
        });

        return card;
    }

    private void openChatAdmin(AnnonceSortie a) {
        if (a == null) return;
        if (currentUser == null) {
            showError("Chat", "Utilisateur non défini", "Connexion requise.");
            return;
        }

        // Ouvrir le chat = considérer comme "lu" (badge retombe directement)
        int annonceId = a.getId();
        int uid = currentUser.getId();
        if (annonceId > 0 && uid > 0) {
            new Thread(() -> {
                try {
                    chatService.ensureSchema();
                    chatService.markAllRead(annonceId, uid);
                } catch (Exception ignored) {
                }
                Platform.runLater(this::refreshChatUnreadBadgesAsync);
            }, "back-chat-mark-read").start();
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/front/sorties/GroupeChatView.fxml"));
            Parent chatRoot = loader.load();

            GroupeChatController ctrl = loader.getController();
            boolean isAdmin = "admin".equalsIgnoreCase(safe(currentUser.getRole()));
            if (isAdmin) ctrl.setContextAdmin(currentUser, a);
            else ctrl.setContext(currentUser, a);

            Stage chatStage = new Stage();
            chatStage.initModality(Modality.NONE);
            chatStage.setTitle("Chat — " + safe(a.getTitre()));
            chatStage.setResizable(true);

            Scene scene = new Scene(chatRoot, 680, 600);
            try {
                var url = getClass().getResource("/styles/sorties/chat.css");
                if (url != null) scene.getStylesheets().add(url.toExternalForm());
            } catch (Exception ignored) {}

            chatStage.setScene(scene);
            chatStage.setOnCloseRequest(e -> ctrl.stopPolling());
            chatStage.show();
        } catch (Exception ex) {
            showError("Chat", "Ouverture impossible", safe(ex.getMessage()));
        }
    }

    private void openParticipations(AnnonceSortie a) {
        if (a == null) return;

        List<ParticipationSortie> all;
        try {
            all = participationService.getByAnnonce(a.getId());
        } catch (Exception ex) {
            showError("Erreur", "Chargement impossible", safe(ex.getMessage()));
            return;
        }

        // Séparer par statut
        List<ParticipationSortie> pending   = all.stream().filter(p -> "EN_ATTENTE".equalsIgnoreCase(safe(p.getStatut()).trim())).toList();
        List<ParticipationSortie> accepted  = all.stream().filter(p -> { String s = safe(p.getStatut()).trim().toUpperCase(); return s.equals("CONFIRMEE") || s.equals("ACCEPTEE"); }).toList();
        List<ParticipationSortie> refused   = all.stream().filter(p -> safe(p.getStatut()).trim().toUpperCase().startsWith("REFUS")).toList();

        // Couleur selon créateur : admin (back) ou utilisateur normal
        boolean createdByAdmin = (currentUser != null && a.getUserId() == currentUser.getId());
        String headerColor     = createdByAdmin ? "#1a3a5c" : "#2e7d32"; // bleu admin / vert user normal

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Participations • " + safe(a.getTitre()));

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f4f6fb;");
        root.setPadding(new Insets(0));

        // ── Header ────────────────────────────────────────────────────
        VBox header = new VBox(6);
        header.setPadding(new Insets(16, 20, 14, 20));
        header.setStyle("-fx-background-color: " + headerColor + "; -fx-background-radius: 0;");

        Label lblTitre = new Label("📋 " + safe(a.getTitre()));
        lblTitre.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: 800;");
        lblTitre.setWrapText(true);

        String ownerLabel = createdByAdmin ? "👑 Créée par vous (Admin)" : "👤 Créée par un utilisateur";
        Label lblOwner = new Label(ownerLabel + "  •  " + safe(a.getVille()) + "  •  " + safe(a.getStatut()));
        lblOwner.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 12px;");

        // Compteurs
        HBox counters = new HBox(14);
        counters.setPadding(new Insets(6, 0, 0, 0));
        counters.getChildren().addAll(
                counterChip("⏳ En attente",  pending.size(),  "#f39c12"),
                counterChip("✅ Acceptés",    accepted.size(), "#27ae60"),
                counterChip("❌ Refusés",     refused.size(),  "#e74c3c")
        );

        header.getChildren().addAll(lblTitre, lblOwner, counters);
        root.setTop(header);

        // ── Onglets ───────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #f4f6fb;");

        // Onglet 1 : En attente
        VBox pendingBox = buildParticipantsList(a, pending, "pending", headerColor);
        Tab tabPending = new Tab("⏳ En attente (" + pending.size() + ")", new ScrollPane(pendingBox) {{
            setFitToWidth(true); getStyleClass().add("participScroll");
        }});

        // Onglet 2 : Acceptés
        VBox acceptedBox = buildAcceptedList(accepted, a);
        Tab tabAccepted = new Tab("✅ Acceptés (" + accepted.size() + ")", new ScrollPane(acceptedBox) {{
            setFitToWidth(true); getStyleClass().add("participScroll");
        }});

        // Onglet 3 : Refusés
        VBox refusedBox = buildSimpleList(refused, "Aucun participant refusé.", "#e74c3c");
        Tab tabRefused = new Tab("❌ Refusés (" + refused.size() + ")", new ScrollPane(refusedBox) {{
            setFitToWidth(true); getStyleClass().add("participScroll");
        }});

        tabPane.getTabs().addAll(tabPending, tabAccepted, tabRefused);
        root.setCenter(tabPane);

        // ── Footer ────────────────────────────────────────────────────
        Button btnClose = new Button("Fermer");
        btnClose.getStyleClass().add("ghostBtn");
        btnClose.setOnAction(e -> dialog.close());

        Button btnChat = new Button("💬 Chat groupe");
        btnChat.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #1e40af; " +
                "-fx-background-radius: 10; -fx-border-radius: 10; " +
                "-fx-border-color: #bfdbfe; -fx-border-width: 1.5; " +
                "-fx-font-weight: 700; -fx-padding: 9 16; -fx-cursor: hand;");
        btnChat.setOnAction(e -> openChatAdmin(a));

        StackPane chatWrap = wrapChatButtonWithBadge(btnChat, a.getId());
        try {
            if (currentUser != null && currentUser.getId() > 0) {
                chatService.ensureSchema();
                long unread = chatService.getUnreadCount(a.getId(), currentUser.getId());
                List<Label> badges = chatBadgesByAnnonceId.get(a.getId());
                if (badges != null) for (Label b : badges) setChatBadgeValue(b, unread);
            }
        } catch (Exception ignored) {
        }

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(10, chatWrap, footerSpacer, btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 16, 12, 16));
        footer.setStyle("-fx-background-color: white; -fx-border-color: #dde3ed; -fx-border-width: 1 0 0 0;");
        root.setBottom(footer);

        Scene scene = new Scene(root, 760, 660);
        try {
            var url = getClass().getResource("/styles/sorties/participation-dialog.css");
            if (url != null) scene.getStylesheets().add(url.toExternalForm());
        } catch (Exception ignored) {}

        dialog.setScene(scene);
        dialog.showAndWait();
    }

    // ── Helper : chips compteurs ──────────────────────────────────────
    private Label counterChip(String label, int count, String color) {
        Label l = new Label(label + " : " + count);
        l.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-background-radius: 20; -fx-padding: 4 12; -fx-font-weight: 700; -fx-font-size: 11px;");
        return l;
    }

    // ── Liste en attente (avec boutons Accepter/Refuser) ──────────────
    private VBox buildParticipantsList(AnnonceSortie a, List<ParticipationSortie> list, String type, String headerColor) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        if (list.isEmpty()) {
            Label empty = new Label("Aucune demande en attente.");
            empty.setStyle("-fx-text-fill: #999; -fx-font-size: 13px;");
            box.getChildren().add(empty);
            return box;
        }

        for (ParticipationSortie p : list) {
            box.getChildren().add(adminRequestRow(a, p, () -> {
                // refresh: reopen dialog
            }));
        }
        return box;
    }

    // ── Liste acceptés (affichage riche) ─────────────────────────────
    private VBox buildAcceptedList(List<ParticipationSortie> list, AnnonceSortie a) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        // Créateur en premier avec badge
        VBox ownerCard = new VBox(4);
        ownerCard.setPadding(new Insets(12));
        ownerCard.setStyle("-fx-background-color: #fff3e0; -fx-border-color: #e67e22; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");

        String ownerName = "Organisateur #" + a.getUserId();
        try {
            User owner = userService.trouverParId(a.getUserId());
            if (owner != null) {
                String full = (safe(owner.getPrenom()) + " " + safe(owner.getNom())).trim();
                if (!full.isEmpty()) ownerName = full;
            }
        } catch (Exception ignored) {}

        Label ownerLbl = new Label("👑  " + ownerName);
        ownerLbl.setStyle("-fx-font-weight: 800; -fx-font-size: 14px; -fx-text-fill: #c0392b;");
        Label ownerRole = new Label("Organisateur / Admin du groupe");
        ownerRole.setStyle("-fx-font-size: 11px; -fx-text-fill: #e67e22;");
        ownerCard.getChildren().addAll(ownerLbl, ownerRole);
        box.getChildren().add(ownerCard);

        if (list.isEmpty()) {
            Label empty = new Label("Aucun participant accepté pour l'instant.");
            empty.setStyle("-fx-text-fill: #999; -fx-font-size: 13px; -fx-padding: 10 0 0 0;");
            box.getChildren().add(empty);
            return box;
        }

        // Participants acceptés
        for (ParticipationSortie p : list) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(12));
            card.setStyle("-fx-background-color: #e8f5e9; -fx-border-color: #27ae60; " +
                    "-fx-border-radius: 10; -fx-background-radius: 10;");

            String name = "Utilisateur #" + p.getUserId();
            String email = "";
            try {
                User u = userService.trouverParId(p.getUserId());
                if (u != null) {
                    String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                    if (!full.isEmpty()) name = full;
                    email = safe(u.getEmail());
                }
            } catch (Exception ignored) {}

            Label nameLbl = new Label("👤  " + name);
            nameLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #1b5e20;");

            HBox info = new HBox(14);
            if (!email.isEmpty()) {
                Label emailLbl = new Label("✉ " + email);
                emailLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
                info.getChildren().add(emailLbl);
            }
            Label placesLbl = new Label("👥 " + Math.max(1, p.getNbPlaces()) + " place(s)");
            placesLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
            Label dateLbl = new Label("📅 Accepté le " + (p.getDateDemande() != null ?
                    p.getDateDemande().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—"));
            dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");
            info.getChildren().addAll(placesLbl, dateLbl);

            card.getChildren().addAll(nameLbl, info);
            box.getChildren().add(card);
        }
        return box;
    }

    // ── Liste simple (refusés) ────────────────────────────────────────
    private VBox buildSimpleList(List<ParticipationSortie> list, String emptyMsg, String color) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));

        if (list.isEmpty()) {
            Label empty = new Label(emptyMsg);
            empty.setStyle("-fx-text-fill: #999; -fx-font-size: 13px;");
            box.getChildren().add(empty);
            return box;
        }

        for (ParticipationSortie p : list) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #fce4ec; -fx-border-color: " + color + "; " +
                    "-fx-border-radius: 10; -fx-background-radius: 10;");

            String name = "Utilisateur #" + p.getUserId();
            try {
                User u = userService.trouverParId(p.getUserId());
                if (u != null) {
                    String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                    if (!full.isEmpty()) name = full;
                }
            } catch (Exception ignored) {}

            Label nameLbl = new Label("❌  " + name);
            nameLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #b71c1c;");
            Label placesLbl = new Label("👥 " + Math.max(1, p.getNbPlaces()) + " place(s) demandée(s)");
            placesLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #777;");

            card.getChildren().addAll(nameLbl, placesLbl);
            box.getChildren().add(card);
        }
        return box;
    }

    public void openParticipationsByAnnonceId(int annonceId) {
        if (annonceId <= 0) return;
        try {
            AnnonceSortie a = service.getById(annonceId);
            if (a == null) {
                showError("Introuvable", "Annonce introuvable", "ID: " + annonceId);
                return;
            }
            openParticipations(a);
        } catch (Exception ex) {
            showError("Erreur", "Chargement impossible", safe(ex.getMessage()));
        }
    }

    private Node adminRequestRow(AnnonceSortie a, ParticipationSortie p, Runnable onResolved) {
        VBox box = new VBox(8);
        box.getStyleClass().add("participCard");

        String name = "Utilisateur #" + p.getUserId();
        try {
            User u = userService.trouverParId(p.getUserId());
            if (u != null) {
                String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                if (!full.isEmpty()) name = full;
            }
        } catch (Exception ignored) {}

        Label title = new Label(name);
        title.getStyleClass().add("participRowTitle");

        Label places = new Label(Math.max(1, p.getNbPlaces()) + " place(s)");
        places.getStyleClass().add("participPlacesChip");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(10, title, spacer, places);
        top.setAlignment(Pos.CENTER_LEFT);

        String comment = safe(p.getCommentaire()).trim();
        Label sub = new Label(comment.isEmpty() ? "" : comment);
        sub.setWrapText(true);
        sub.getStyleClass().add("participComment");
        sub.setVisible(!comment.isEmpty());
        sub.setManaged(!comment.isEmpty());

        Button accept = new Button("Accepter");
        Button refuse = new Button("Refuser");
        accept.getStyleClass().add("primaryBtn");
        refuse.getStyleClass().add("dangerBtn");

        accept.setOnAction(e -> {
            try {
                int accepted  = participationService.countAcceptedPlaces(a.getId());
                int remaining = Math.max(0, a.getNbPlaces() - accepted);
                if (p.getNbPlaces() > remaining) {
                    showError("Places insuffisantes", "Impossible d'accepter",
                            "Places restantes: " + remaining + ". Demandées: " + p.getNbPlaces());
                    return;
                }
                boolean notifOk = participationService.updateStatus(p.getId(), "CONFIRMEE");
                if (notifOk) {
                    showInfo("Acceptée", "La demande a été acceptée. Notification envoyée.");
                } else {
                    showInfo("Acceptée", "La demande a été acceptée, mais la notification (email/SMS) n'a pas pu être envoyée. Vérifie la configuration SMTP/Twilio et la console.");
                }
                box.setDisable(true);
                box.setOpacity(0.55);
                if (onResolved != null) onResolved.run();
            } catch (Exception ex) {
                showError("Erreur", "Action impossible", safe(ex.getMessage()));
            }
        });

        refuse.setOnAction(e -> {
            try {
                boolean notifOk = participationService.updateStatus(p.getId(), "REFUSEE");
                if (notifOk) {
                    showInfo("Refusée", "La demande a été refusée. Notification envoyée.");
                } else {
                    showInfo("Refusée", "La demande a été refusée, mais la notification (email/SMS) n'a pas pu être envoyée. Vérifie la configuration SMTP/Twilio et la console.");
                }
                box.setDisable(true);
                box.setOpacity(0.55);
                if (onResolved != null) onResolved.run();
            } catch (Exception ex) {
                showError("Erreur", "Action impossible", safe(ex.getMessage()));
            }
        });

        HBox actions = new HBox(10, accept, refuse);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("participActions");

        box.getChildren().addAll(top, sub, actions);
        return box;
    }

    private void selectCard(Node card) {
        if (selectedCard != null) selectedCard.getStyleClass().remove("selected");
        selectedCard = card;
        card.getStyleClass().add("selected");
    }

    /* ===================== Editor (CRUD) + PREVIEW LIVE ===================== */

    private void openEditor(AnnonceSortie existing) {
        boolean isEdit = existing != null;
        if (currentUser == null) {
            showError("Session", "Utilisateur non défini", "Le back dashboard n'a pas injecté currentUser.");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Modifier annonce" : "Créer annonce");

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de la sortie");

        TextArea taDesc = new TextArea();
        taDesc.setPromptText("Description (optionnel)");
        taDesc.setPrefRowCount(3);

        ComboBox<String> cbVille = new ComboBox<>(FXCollections.observableArrayList(TunisiaGeo.villes()));
        cbVille.setPromptText("Ville");

        ComboBox<String> cbRegion = new ComboBox<>();
        cbRegion.setPromptText("Région / quartier");
        cbRegion.setMaxWidth(Double.MAX_VALUE);

        TextField tfRegionAutre = new TextField();
        tfRegionAutre.setPromptText("Écrire la région / quartier");
        tfRegionAutre.setVisible(false);
        tfRegionAutre.setManaged(false);

        cbVille.valueProperty().addListener((obs, o, villeSel) -> {
            cbRegion.getItems().setAll(regionsForVille(villeSel));
            cbRegion.getSelectionModel().clearSelection();
            tfRegionAutre.clear();
            tfRegionAutre.setVisible(false);
            tfRegionAutre.setManaged(false);
        });

        cbRegion.valueProperty().addListener((obs, o, regSel) -> {
            boolean other = TunisiaGeo.REGION_OTHER.equals(regSel);
            tfRegionAutre.setVisible(other);
            tfRegionAutre.setManaged(other);
        });

        TextField tfPoint = new TextField();
        tfPoint.setPromptText("Point de rencontre (ex: devant le théâtre municipal)");

        // Map picker (point de rencontre)
        WebView mapView = new WebView();
        mapView.setPrefHeight(260);
        mapView.setMinHeight(240);
        WebEngine mapEngine = mapView.getEngine();
        mapEngine.setJavaScriptEnabled(true);
        final boolean[] mapReady = { false };
        final String[] lastMapQuery = { "" };
        PauseTransition mapDebounce = new PauseTransition(Duration.millis(450));

        Label mapHint = new Label("Cliquez sur la carte pour choisir un point précis.");
        mapHint.getStyleClass().add("hint");

        Label coordsLabel = new Label("Lat: —   Lng: —");
        coordsLabel.getStyleClass().add("hint");

        Runnable updateMapCenter = () -> {
            if (!mapReady[0]) return;
            String ville = safe(cbVille.getValue()).trim();
            if (ville.isEmpty()) return;

            String regionSel = cbRegion.getValue();
            String region = TunisiaGeo.REGION_OTHER.equals(regionSel)
                    ? safe(tfRegionAutre.getText()).trim()
                    : safe(regionSel).trim();

            String q = region.isEmpty() ? ville : (region + ", " + ville);
            q = q + ", Tunisie";

            if (q.equalsIgnoreCase(lastMapQuery[0])) return;
            lastMapQuery[0] = q;

            String js = "window.setSearchQuery && window.setSearchQuery('" + q
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    + "')";
            mapDebounce.stop();
            mapDebounce.setOnFinished(ev -> {
                try { mapEngine.executeScript(js); } catch (Exception ignored) {}
            });
            mapDebounce.playFromStart();
        };

        mapEngine.getLoadWorker().stateProperty().addListener((obs, o, st) -> {
            if (st == Worker.State.SUCCEEDED) {
                mapReady[0] = true;
                updateMapCenter.run();
            }
        });

        try {
            String base = getClass().getResource("/map/map_picker.html").toExternalForm();
            mapEngine.load(base);
        } catch (Exception ignored) {}

        mapEngine.titleProperty().addListener((obs, ot, nt) -> {
            if (nt == null || !nt.contains(",")) return;
            String[] mp = nt.split("\\|", 2);
            String coords = mp[0].trim();
            String addr = (mp.length == 2) ? mp[1].trim() : "";

            String[] co = coords.split(",");
            if (co.length == 2) coordsLabel.setText("Lat: " + co[0].trim() + "   Lng: " + co[1].trim());

            tfPoint.setText(addr.isBlank() ? coords : (addr + " (" + coords + ")"));

            // Sync ville/région from reverse-geocoded details (optional but makes map really dynamic)
            try {
                Object partsJsonObj = mapEngine.executeScript("window.getLastAddressParts && window.getLastAddressParts()");
                String partsJson = (partsJsonObj == null) ? "[]" : String.valueOf(partsJsonObj);
                List<String> parts = JsonStringArray.fromJson(partsJson);

                String state = (parts.size() > 0) ? safe(parts.get(0)).trim() : "";
                String city = (parts.size() > 1) ? safe(parts.get(1)).trim() : "";
                String district = (parts.size() > 2) ? safe(parts.get(2)).trim() : "";

                String villeCandidate = !state.isBlank() ? state : city;
                String matchedVille = TunisiaGeo.matchVilleForSelection(villeCandidate);
                if (matchedVille != null && !matchedVille.equals(cbVille.getValue())) {
                    cbVille.getSelectionModel().select(matchedVille);
                }

                if (!district.isBlank()) {
                    String currentVille = safe(cbVille.getValue()).trim();
                    if (!currentVille.isEmpty()) {
                        List<String> regs = regionsForVille(currentVille);
                        String found = regs.stream()
                                .filter(r -> r != null && r.equalsIgnoreCase(district))
                                .findFirst().orElse(null);
                        if (found != null) {
                            cbRegion.getSelectionModel().select(found);
                            tfRegionAutre.clear();
                            tfRegionAutre.setVisible(false);
                            tfRegionAutre.setManaged(false);
                        } else {
                            cbRegion.getSelectionModel().select(TunisiaGeo.REGION_OTHER);
                            tfRegionAutre.setVisible(true);
                            tfRegionAutre.setManaged(true);
                            tfRegionAutre.setText(district);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
        // ===== Sélection d'activité (catalogue) =====
        // On garde cbAct comme modèle interne (validation + sauvegarde) et on affiche une UI moderne (ListView).
        ComboBox<String> cbAct = new ComboBox<>(FXCollections.observableArrayList(SortieActivities.allActivitiesFlat()));
        cbAct.setVisible(false);
        cbAct.setManaged(false);

        TextField tfAutreAct = new TextField();
        tfAutreAct.setPromptText("Préciser l'activité");
        tfAutreAct.getStyleClass().add("activityOtherField");
        tfAutreAct.setVisible(false);
        tfAutreAct.setManaged(false);

        ListView<String> lvActCats = new ListView<>(FXCollections.observableArrayList(SortieActivities.categories()));
        lvActCats.getStyleClass().addAll("activityList", "activityCats");
        lvActCats.setPrefWidth(220);
        lvActCats.setMaxWidth(220);
        lvActCats.setPrefHeight(170);
        lvActCats.setFixedCellSize(38);

        ListView<String> lvActs = new ListView<>();
        lvActs.getStyleClass().addAll("activityList", "activityActs");
        lvActs.setPrefHeight(170);
        lvActs.setFixedCellSize(38);

        HBox activityPicker = new HBox(10, lvActCats, lvActs);
        activityPicker.getStyleClass().add("activityPicker");
        activityPicker.setAlignment(Pos.TOP_LEFT);

        lvActCats.getSelectionModel().selectedItemProperty().addListener((obs, o, cat) -> {
            lvActs.getItems().clear();
            if (cat == null) return;
            lvActs.setItems(FXCollections.observableArrayList(SortieActivities.activitiesForCategory(cat)));
            if (!lvActs.getItems().isEmpty()) lvActs.getSelectionModel().selectFirst();
        });

        lvActs.getSelectionModel().selectedItemProperty().addListener((obs, o, act) -> {
            if (act == null) return;
            cbAct.getSelectionModel().select(act);
            boolean other = SortieActivities.OTHER.equalsIgnoreCase(act);
            tfAutreAct.setVisible(other);
            tfAutreAct.setManaged(other);
            if (!other) tfAutreAct.clear();
        });

        // Valeurs par défaut
        if (!lvActCats.getItems().isEmpty()) {
            lvActCats.getSelectionModel().selectFirst();
        }

        DatePicker dpDate = new DatePicker();
        dpDate.setPromptText("Date");

        dpDate.setDayCellFactory(p -> new DateCell() {
        @Override
        public void updateItem(LocalDate item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) return;
            setDisable(item.isBefore(LocalDate.now()));
        }
    });

    Spinner<Integer> spHour = new Spinner<>(0, 23, 10);
    Spinner<Integer> spMin  = new Spinner<>(0, 59, 0);
        spHour.setEditable(true);
        spMin.setEditable(true);
        spHour.setPrefWidth(110);
        spMin.setPrefWidth(110);

    CheckBox cbNoBudget = new CheckBox("Aucun budget");
    TextField tfBudget  = new TextField();
        tfBudget.setPromptText("Budget max (TND)");

        cbNoBudget.selectedProperty().addListener((obs, o, n) -> {
        tfBudget.setDisable(Boolean.TRUE.equals(n));
        if (Boolean.TRUE.equals(n)) tfBudget.setText("0");
        else if ("0".equals(tfBudget.getText())) tfBudget.clear();
    });

    Spinner<Integer> spPlaces = new Spinner<>(1, 999, 5);
        spPlaces.setEditable(true);

    ComboBox<String> cbStatut = new ComboBox<>(
            FXCollections.observableArrayList("OUVERTE", "CLOTUREE", "ANNULEE", "TERMINEE"));
        cbStatut.getSelectionModel().select("OUVERTE");

    ImageView imgPrev = new ImageView();
        imgPrev.setFitWidth(420);
        imgPrev.setFitHeight(200);
        imgPrev.setPreserveRatio(false);
    Rectangle clipForm = new Rectangle(420, 200);
        clipForm.setArcWidth(24);
        clipForm.setArcHeight(24);
        imgPrev.setClip(clipForm);

    Label imgEmpty = new Label("Aucune image");
        imgEmpty.getStyleClass().add("imageEmpty");

    StackPane imgWrap = new StackPane(imgPrev, imgEmpty);
        imgWrap.getStyleClass().add("imageWrap");
        StackPane.setAlignment(imgEmpty, Pos.CENTER);

    Label imgPath = new Label("");
        imgPath.getStyleClass().add("hint");

    Button btnPickImg = new Button("Uploader image");
        btnPickImg.getStyleClass().add("btn-pill");

    final String[] pickedPath          = {null};
    final String[] lastPreviewImgPath  = {null};
    final Image[]  cachedPreviewImage  = {null};

    // ====== PREVIEW LIVE ======
    VBox previewCard = new VBox(10);
        previewCard.getStyleClass().add("previewCard");
        previewCard.setPrefWidth(380);
        previewCard.setMinWidth(300);
        previewCard.setMaxWidth(520);

    StackPane previewImgWrap = new StackPane();
        previewImgWrap.getStyleClass().add("previewImageWrap");

    ImageView previewIV = new ImageView();
        previewIV.setFitWidth(340);
        previewIV.setFitHeight(180);
        previewIV.setPreserveRatio(false);
        previewIV.setSmooth(true);
        previewIV.setCache(true);
        previewIV.setCacheHint(CacheHint.SPEED);
    Rectangle clipPrev = new Rectangle(340, 180);
        clipPrev.setArcWidth(22);
        clipPrev.setArcHeight(22);
        previewIV.setClip(clipPrev);

    Label previewImgEmpty = new Label("Aucune image");
        previewImgEmpty.getStyleClass().add("previewImageEmpty");
        previewImgWrap.getChildren().addAll(previewIV, previewImgEmpty);

    Label previewStatus = new Label("OUVERTE");
        previewStatus.getStyleClass().addAll("statusChip", "status-ouverte");
        StackPane.setAlignment(previewStatus, Pos.TOP_LEFT);
        StackPane.setMargin(previewStatus, new Insets(10, 0, 0, 10));
        previewImgWrap.getChildren().add(previewStatus);

    Label previewTitle     = new Label("Titre de la sortie");
        previewTitle.getStyleClass().add("previewTitle");
        previewTitle.setWrapText(true);

    Label previewMeta = new Label("Ville • Région • Activité");
        previewMeta.getStyleClass().add("previewMeta");
        previewMeta.setWrapText(true);

    Label previewLine = new Label("📅 —   •   💰 —   •   👥 —");
        previewLine.getStyleClass().add("previewLine");
        previewLine.setWrapText(true);

    Label previewMeet = new Label("🤝 —");
        previewMeet.getStyleClass().add("previewLine");
        previewMeet.setWrapText(true);

    Label previewDescTitle = new Label("Description");
        previewDescTitle.getStyleClass().add("previewSectionTitle");

    Label previewDesc = new Label("—");
        previewDesc.getStyleClass().add("previewDesc");
        previewDesc.setWrapText(true);

    Label liveHint = new Label("");
        liveHint.getStyleClass().add("liveHint");

        previewCard.getChildren().addAll(
                new Label("Aperçu en direct"),
    previewImgWrap, previewTitle, previewMeta,
    previewLine, previewMeet, previewDescTitle, previewDesc, liveHint
        );
        previewCard.getChildren().get(0).getStyleClass().add("previewHeader");

        previewCard.widthProperty().addListener((obs, o, w) -> {
        double ww = Math.max(280, w.doubleValue() - 24);
        previewIV.setFitWidth(ww);
        clipPrev.setWidth(ww);
    });

    Runnable applyPreviewImageIfChanged = () -> {
        String p = pickedPath[0];
        if (Objects.equals(p, lastPreviewImgPath[0])) return;
        lastPreviewImgPath[0] = p;
        cachedPreviewImage[0] = loadImageOrNull(p);
        previewIV.setImage(cachedPreviewImage[0]);
        boolean emptyImg = (cachedPreviewImage[0] == null);
        previewImgEmpty.setVisible(emptyImg);
        previewImgEmpty.setManaged(emptyImg);
    };

    Runnable updatePreviewNow = () -> {
        String t = safe(tfTitre.getText()).trim();
        previewTitle.setText(t.isEmpty() ? "Titre de la sortie" : t);

        String ville     = safe(cbVille.getValue());
        String regionSel = cbRegion.getValue();
        String region    = TunisiaGeo.REGION_OTHER.equals(regionSel)
                ? safe(tfRegionAutre.getText()).trim()
                : safe(regionSel);

        String actSel = safe(cbAct.getValue());
        String act    = "Autre".equalsIgnoreCase(actSel)
                ? safe(tfAutreAct.getText()).trim()
                : actSel;

        previewMeta.setText(
                (ville.isEmpty() ? "Ville" : ville) + " • " +
                        (region.isEmpty() ? "Région" : region) + " • " +
                        (act.isEmpty() ? "Activité" : act));

        LocalDate d  = dpDate.getValue();
        LocalDateTime dt = (d == null) ? null
                : LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue()));
        String when  = (dt == null) ? "—" : DT_FMT.format(dt);

        String budget;
        if (cbNoBudget.isSelected()) {
            budget = "Aucun budget";
        } else {
            String raw = safe(tfBudget.getText()).trim().replace(',', '.');
            if (raw.isEmpty()) budget = "—";
            else {
                try {
                    double v = Double.parseDouble(raw);
                    budget = (v <= 0) ? "Aucun budget" : (MONEY_FMT.format(v) + " TND max");
                } catch (Exception ex) { budget = "Budget invalide"; }
            }
        }

        previewLine.setText("📅 " + when + "   •   💰 " + budget
                + "   •   👥 " + spPlaces.getValue() + " places");

        String meet = safe(tfPoint.getText()).trim();
        previewMeet.setText("🤝 " + (meet.isEmpty() ? "—" : meet));

        String desc = safe(taDesc.getText()).trim();
        previewDesc.setText(desc.isEmpty() ? "—" : desc);

        String st = safe(cbStatut.getValue()).trim();
        if (st.isEmpty()) st = "OUVERTE";
        previewStatus.setText(st);
        previewStatus.getStyleClass().removeIf(c -> c.startsWith("status-"));
        previewStatus.getStyleClass().add("status-" + st.trim().toLowerCase());

        applyPreviewImageIfChanged.run();

        String validation = validateLive(tfTitre, cbVille, cbRegion, tfRegionAutre, tfPoint,
                cbAct, tfAutreAct, dpDate, spHour, spMin, cbNoBudget, tfBudget, cbStatut);
        liveHint.setText(validation);
        liveHint.setVisible(!validation.isEmpty());
        liveHint.setManaged(!validation.isEmpty());
    };

    PauseTransition debounce = new PauseTransition(Duration.millis(120));
    Runnable schedulePreview = () -> {
        debounce.stop();
        debounce.setOnFinished(ev -> updatePreviewNow.run());
        debounce.playFromStart();
    };

        btnPickImg.setOnAction(e -> {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir une image");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        File f = fc.showOpenDialog(dialog);
        if (f == null) return;
        try {
            String saved = UploadStore.saveSortieImage(f);
            pickedPath[0] = saved;
            imgPath.setText(shortPath(saved));
            Image im = loadImageOrNull(saved);
            imgPrev.setImage(im);
            boolean empty = (im == null);
            imgEmpty.setVisible(empty);
            imgEmpty.setManaged(empty);
            applyPreviewImageIfChanged.run();
            schedulePreview.run();
        } catch (Exception ex) {
            showError("Upload", "Impossible d'uploader l'image", safe(ex.getMessage()));
        }
    });

    VBox qBox = new VBox(8);
        qBox.getStyleClass().add("questionsBox");
    Button btnAddQ = new Button("+ Ajouter une question");
        btnAddQ.getStyleClass().add("ghostBtn");
        btnAddQ.setOnAction(e -> qBox.getChildren().add(buildQuestionRow("", schedulePreview)));

        if (isEdit) {
        tfTitre.setText(existing.getTitre());
        taDesc.setText(safe(existing.getDescription()));
        String villeToSelect = Optional.ofNullable(
                TunisiaGeo.matchVilleForSelection(existing.getVille())).orElse(existing.getVille());
        cbVille.getSelectionModel().select(villeToSelect);

        cbRegion.getItems().setAll(regionsForVille(villeToSelect));
        String reg = safe(existing.getLieuTexte());
        if (cbRegion.getItems().stream().anyMatch(x -> x.equalsIgnoreCase(reg))) {
            cbRegion.getSelectionModel().select(
                    cbRegion.getItems().stream().filter(x -> x.equalsIgnoreCase(reg))
                            .findFirst().orElse(null));
        } else {
            cbRegion.getSelectionModel().select(TunisiaGeo.REGION_OTHER);
            tfRegionAutre.setVisible(true);
            tfRegionAutre.setManaged(true);
            tfRegionAutre.setText(reg);
        }

        tfPoint.setText(existing.getPointRencontre());
        String act = safe(existing.getTypeActivite()).trim();
        String cat = SortieActivities.findCategoryForActivity(act);
        if (cat != null) {
            lvActCats.getSelectionModel().select(cat);
            String matched = SortieActivities.matchActivityInCategory(cat, act);
            if (matched != null) lvActs.getSelectionModel().select(matched);
            else if (!lvActs.getItems().isEmpty()) lvActs.getSelectionModel().selectFirst();
        } else {
            if (!lvActCats.getItems().isEmpty()) lvActCats.getSelectionModel().selectFirst();
            lvActs.getSelectionModel().select(SortieActivities.OTHER);
            tfAutreAct.setVisible(true);
            tfAutreAct.setManaged(true);
            tfAutreAct.setText(act);
        }

        if (existing.getDateSortie() != null) {
            dpDate.setValue(existing.getDateSortie().toLocalDate());
            spHour.getValueFactory().setValue(existing.getDateSortie().getHour());
            spMin.getValueFactory().setValue(existing.getDateSortie().getMinute());
        }

        if (existing.getBudgetMax() <= 0) {
            cbNoBudget.setSelected(true);
            tfBudget.setText("0");
            tfBudget.setDisable(true);
        } else {
            tfBudget.setText(String.valueOf(existing.getBudgetMax()));
        }

        spPlaces.getValueFactory().setValue(Math.max(1, existing.getNbPlaces()));
        cbStatut.getSelectionModel().select(
                safe(existing.getStatut()).isEmpty() ? "OUVERTE" : existing.getStatut());

        pickedPath[0] = existing.getImageUrl();
        imgPath.setText(shortPath(safe(existing.getImageUrl())));
        Image im = loadImageOrNull(existing.getImageUrl());
        imgPrev.setImage(im);
        boolean empty = (im == null);
        imgEmpty.setVisible(empty);
        imgEmpty.setManaged(empty);

        if (existing.getQuestions() != null) {
            for (String q : existing.getQuestions())
                qBox.getChildren().add(buildQuestionRow(q, schedulePreview));
        }
    }

        if (qBox.getChildren().isEmpty())
            qBox.getChildren().add(buildQuestionRow("", schedulePreview));

        tfTitre.textProperty().addListener((a,b,c)      -> schedulePreview.run());
        taDesc.textProperty().addListener((a,b,c)       -> schedulePreview.run());
        cbVille.valueProperty().addListener((a,b,c)     -> { schedulePreview.run(); updateMapCenter.run(); });
        cbRegion.valueProperty().addListener((a,b,c)    -> { schedulePreview.run(); updateMapCenter.run(); });
        tfRegionAutre.textProperty().addListener((a,b,c)-> { schedulePreview.run(); updateMapCenter.run(); });
        tfPoint.textProperty().addListener((a,b,c)      -> schedulePreview.run());
        cbAct.valueProperty().addListener((a,b,c)       -> schedulePreview.run());
        tfAutreAct.textProperty().addListener((a,b,c)   -> schedulePreview.run());
        dpDate.valueProperty().addListener((a,b,c)      -> schedulePreview.run());
        spHour.valueProperty().addListener((a,b,c)      -> schedulePreview.run());
        spMin.valueProperty().addListener((a,b,c)       -> schedulePreview.run());
        cbNoBudget.selectedProperty().addListener((a,b,c)-> schedulePreview.run());
        tfBudget.textProperty().addListener((a,b,c)     -> schedulePreview.run());
        spPlaces.valueProperty().addListener((a,b,c)    -> schedulePreview.run());
        cbStatut.valueProperty().addListener((a,b,c)    -> schedulePreview.run());

    Label headline = new Label(isEdit ? "Modifier une annonce" : "Créer une annonce");
        headline.getStyleClass().add("dialogTitle");

    GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
    ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(48);
    ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(52);
        grid.getColumnConstraints().addAll(c1, c2);

    int r = 0;
        grid.add(lab("Titre"), 0, r);             grid.add(tfTitre, 1, r++);
        grid.add(lab("Ville"), 0, r);             grid.add(cbVille, 1, r++);
        grid.add(lab("Lieu (région)"), 0, r);
        grid.add(new VBox(8, cbRegion, tfRegionAutre), 1, r++);
        grid.add(lab("Point de rencontre"), 0, r); grid.add(tfPoint, 1, r++);

    VBox mapBox = new VBox(8, mapHint, mapView, coordsLabel);
        grid.add(lab("Carte"), 0, r); grid.add(mapBox, 1, r++);
        grid.add(lab("Activité"), 0, r);
        grid.add(new VBox(8, activityPicker, tfAutreAct), 1, r++);
        grid.add(lab("Date + heure"), 0, r);
    HBox timeRow = new HBox(10, dpDate, new Label("Heure"), spHour, new Label("Min"), spMin);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(timeRow, 1, r++);
        grid.add(lab("Budget"), 0, r);
    HBox budgetRow = new HBox(10, cbNoBudget, tfBudget);
        budgetRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(budgetRow, 1, r++);
        grid.add(lab("Nombre de places"), 0, r);  grid.add(spPlaces, 1, r++);
        grid.add(lab("Statut"), 0, r);            grid.add(cbStatut, 1, r++);

    VBox imgBox = new VBox(10, imgWrap, new HBox(10, btnPickImg, imgPath));
        ((HBox) imgBox.getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);
    VBox descBox = new VBox(8, lab("Description"), taDesc);
    VBox qWrap   = new VBox(8, lab("Questions (optionnel)"), qBox, btnAddQ);
    VBox formContent = new VBox(14, grid, imgBox, descBox, qWrap);
        formContent.setPadding(new Insets(0, 6, 6, 0));

    ScrollPane formScroll = new ScrollPane(formContent);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        formScroll.getStyleClass().add("editorScroll");

    Button btnSave   = new Button(isEdit ? "Enregistrer" : "Créer");
        btnSave.getStyleClass().add("primaryBtn");
    Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("ghostBtn");
    HBox footer = new HBox(10, btnCancel, btnSave);
        footer.setAlignment(Pos.CENTER_RIGHT);
        btnCancel.setOnAction(e -> dialog.close());

        btnSave.setOnAction(e -> {
        try {
            String validation = validateLive(tfTitre, cbVille, cbRegion, tfRegionAutre, tfPoint,
                    cbAct, tfAutreAct, dpDate, spHour, spMin, cbNoBudget, tfBudget, cbStatut);
            if (!validation.isEmpty()) {
                showError("Validation", "Formulaire incomplet", validation);
                return;
            }

            AnnonceSortie an = isEdit ? existing : new AnnonceSortie();
            an.setUserId(currentUser.getId());
            an.setTitre(req(tfTitre.getText(), "Titre"));
            an.setDescription(emptyToNull(taDesc.getText()));
            an.setVille(req(cbVille.getValue(), "Ville"));

            String selectedRegion = cbRegion.getValue();
            if (selectedRegion == null || selectedRegion.trim().isEmpty())
                throw new IllegalArgumentException("Région obligatoire");
            an.setLieuTexte(TunisiaGeo.REGION_OTHER.equals(selectedRegion)
                    ? req(tfRegionAutre.getText(), "Région") : selectedRegion);

            an.setPointRencontre(req(tfPoint.getText(), "Point de rencontre"));

            String act = cbAct.getValue();
            if (act == null || act.trim().isEmpty())
                throw new IllegalArgumentException("Type d'activité obligatoire");
            an.setTypeActivite("Autre".equalsIgnoreCase(act)
                    ? req(tfAutreAct.getText(), "Type d'activité") : act);

            LocalDate d  = dpDate.getValue();
            LocalDateTime dt = LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue()));
            if (!dt.isAfter(LocalDateTime.now()))
                throw new IllegalArgumentException("Date + heure doit être dans le futur");
            an.setDateSortie(dt);

            an.setBudgetMax(parseBudget(tfBudget.getText(), cbNoBudget.isSelected()));
            an.setNbPlaces(spPlaces.getValue());
            an.setStatut(req(cbStatut.getValue(), "Statut"));
            an.setImageUrl(emptyToNull(pickedPath[0]));
            an.setQuestions(readQuestions(qBox));

            if (isEdit) service.update(an);
            else        service.add(an);

            dialog.close();
            loadData();

        } catch (Exception ex) {
            showError("Erreur", "Impossible d'enregistrer", safe(ex.getMessage()));
        }
    });

        updatePreviewNow.run();
        btnSave.setDisable(!liveHint.getText().isEmpty());
        liveHint.textProperty().addListener((obs, o, n) -> btnSave.setDisable(n != null && !n.isEmpty()));

    VBox shell = new VBox(12);
        shell.setPadding(new Insets(16));
        shell.getStyleClass().add("dialogRoot");

    SplitPane split = new SplitPane(formScroll, previewCard);
        split.setDividerPositions(0.64);
        split.getStyleClass().add("editorSplit");
        VBox.setVgrow(split, Priority.ALWAYS);

        shell.getChildren().addAll(headline, split, footer);

    Scene scene = new Scene(shell, 980, 820);
        scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.centerOnScreen();
        dialog.showAndWait();
}

private String validateLive(
        TextField tfTitre, ComboBox<String> cbVille, ComboBox<String> cbRegion,
        TextField tfRegionAutre, TextField tfPoint, ComboBox<String> cbAct,
        TextField tfAutreAct, DatePicker dpDate, Spinner<Integer> spHour,
        Spinner<Integer> spMin, CheckBox cbNoBudget, TextField tfBudget,
        ComboBox<String> cbStatut) {

    if (safe(tfTitre.getText()).trim().length() < 5) return "Titre trop court (min 5 caractères)";
    if (safe(cbVille.getValue()).trim().isEmpty())   return "Choisir une ville";

    String regSel = cbRegion.getValue();
    if (regSel == null || regSel.trim().isEmpty()) return "Choisir une région";
    if (TunisiaGeo.REGION_OTHER.equals(regSel) && safe(tfRegionAutre.getText()).trim().isEmpty())
        return "Écrire la région";

    if (safe(tfPoint.getText()).trim().length() < 3) return "Point de rencontre obligatoire";

    String actSel = safe(cbAct.getValue()).trim();
    if (actSel.isEmpty()) return "Choisir une activité";
    if ("Autre".equalsIgnoreCase(actSel) && safe(tfAutreAct.getText()).trim().isEmpty())
        return "Écrire le type d'activité";

    LocalDate d = dpDate.getValue();
    if (d == null) return "Choisir une date";
    if (!LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue()))
            .isAfter(LocalDateTime.now())) return "Date + heure doit être dans le futur";

    if (!cbNoBudget.isSelected()) {
        String raw = safe(tfBudget.getText()).trim().replace(',', '.');
        if (raw.isEmpty()) return "Budget vide ou cocher 'Aucun budget'";
        try {
            if (Double.parseDouble(raw) < 0) return "Budget invalide";
        } catch (Exception ex) { return "Budget invalide"; }
    }

    if (safe(cbStatut.getValue()).trim().isEmpty()) return "Choisir un statut";
    return "";
}

private HBox buildQuestionRow(String value, Runnable updatePreview) {
    TextField tf = new TextField(value == null ? "" : value);
    tf.setPromptText("Ex: Avez-vous une voiture ?");
    tf.getStyleClass().add("qField");

    Button rm = new Button("✕");
    rm.getStyleClass().add("qRemove");

    HBox row = new HBox(10, tf, rm);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(tf, Priority.ALWAYS);
    tf.textProperty().addListener((a,b,c) -> updatePreview.run());
    rm.setOnAction(e -> {
        VBox parent = (VBox) row.getParent();
        if (parent != null) parent.getChildren().remove(row);
        updatePreview.run();
    });
    return row;
}

private List<String> readQuestions(VBox qBox) {
    List<String> out = new ArrayList<>();
    for (Node n : qBox.getChildren()) {
        if (!(n instanceof HBox row)) continue;
        for (Node c : row.getChildren()) {
            if (c instanceof TextField tf) {
                String s = safe(tf.getText()).trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
    }
    return out;
}

private double parseBudget(String raw, boolean noBudget) {
    if (noBudget) return 0.0;
    String s = safe(raw).trim().replace(',', '.');
    if (s.isEmpty()) throw new IllegalArgumentException("Budget obligatoire ou cocher 'Aucun budget'");
    double v = Double.parseDouble(s);
    if (v < 0) throw new IllegalArgumentException("Budget invalide");
    return v;
}

private Label lab(String t) {
    Label l = new Label(t);
    l.getStyleClass().add("formLabel");
    return l;
}

private String req(String s, String label) {
    if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException(label + " obligatoire");
    return s.trim();
}

private String emptyToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
}

private boolean confirmDelete(String msg) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
    a.setTitle("Confirmation");
    a.setHeaderText("Suppression");
    a.setContentText(msg);
    Optional<ButtonType> r = a.showAndWait();
    return r.isPresent() && r.get() == ButtonType.OK;
}

private void showError(String title, String header, String content) {
    Alert a = new Alert(Alert.AlertType.ERROR);
    a.setTitle(title);
    a.setHeaderText(header);
    a.setContentText(content);
    a.showAndWait();
}

private void showInfo(String title, String content) {
    Alert a = new Alert(Alert.AlertType.INFORMATION);
    a.setTitle(title);
    a.setHeaderText(null);
    a.setContentText(content);
    a.showAndWait();
}

private String safe(String s) { return s == null ? "" : s; }

private String shortPath(String p) {
    if (p == null) return "";
    try {
        File f = new File(p);
        if (f.getName() != null && !f.getName().isEmpty()) return f.getName();
    } catch (Exception ignored) {}
    return p;
}

private Image loadImageOrFallback(String pathOrUrl) {
    try {
        if (pathOrUrl != null && !pathOrUrl.trim().isEmpty()) {
            String p = pathOrUrl.trim();
            File f = new File(p);
            if (p.startsWith("file:")) return new Image(p, true);
            if (f.exists()) return new Image(f.toURI().toString(), true);
            if (p.startsWith("http://") || p.startsWith("https://")) return new Image(p, true);
            if (p.startsWith("/")) {
                var u = getClass().getResource(p);
                if (u != null) return new Image(u.toExternalForm(), true);
            }
        }
    } catch (Exception ignored) {}
    return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), true);
}

private Image loadImageOrNull(String pathOrUrl) {
    try {
        if (pathOrUrl != null && !pathOrUrl.trim().isEmpty()) {
            String p = pathOrUrl.trim();
            File f = new File(p);
            if (p.startsWith("file:")) return new Image(p, true);
            if (f.exists()) return new Image(f.toURI().toString(), true);
            if (p.startsWith("http://") || p.startsWith("https://")) return new Image(p, true);
            if (p.startsWith("/")) {
                var u = getClass().getResource(p);
                if (u != null) return new Image(u.toExternalForm(), true);
            }
        }
    } catch (Exception ignored) {}
    return null;
}
}