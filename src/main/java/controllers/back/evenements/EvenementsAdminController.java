package controllers.back.evenements;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.evenements.Evenement;
import models.evenements.Inscription;
import models.evenements.Ticket;
import models.lieux.Lieu;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.ICalendarService;
import services.evenements.NotionCalendarService;
import services.evenements.InscriptionService;
import services.evenements.RecommendationService;
import services.evenements.TicketService;
import services.evenements.WeatherService;
import utils.Mydb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class EvenementsAdminController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final double CARD_MIN_W    = 300;
    private static final double CARD_TARGET_W = 320;

    @FXML private VBox panelEvents;
    @FXML private VBox panelInscriptions;
    @FXML private VBox panelTickets;
    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField        searchField;
    @FXML private Button           btnAdd;
    @FXML private ScrollPane       cardsScroll;
    @FXML private TilePane         cardsPane;
    @FXML private Label kpiTotal;
    @FXML private Label kpiOuverts;
    @FXML private Label kpiInscriptions;
    @FXML private Label breadcrumbEvent;
    @FXML private Label inscEventTitle;
    @FXML private Label inscEventMeta;
    @FXML private Label placesInfo;
    @FXML private VBox  inscriptionsBox;
    @FXML private Label breadcrumbInscription;
    @FXML private Label ticketInscInfo;
    @FXML private Label ticketEventInfo;   // ← affiche le paiement
    @FXML private VBox  ticketsBox;

    // Calendar panel
    @FXML private VBox panelCalendar;
    // Stats panel
    @FXML private VBox panelStats;
    @FXML private VBox statsContainer;
    @FXML private Label calMonthLabel;
    @FXML private VBox calendarContainer;
    @FXML private Button btnCalendar;
    @FXML private Button btnViewMonth, btnViewWeek, btnViewDay;
    @FXML private VBox calDetailPanel;
    @FXML private javafx.scene.control.ScrollPane calDetailScroll;
    @FXML private Label calNotionStatus;

    private final EvenementService   evenementService   = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final TicketService      ticketService      = new TicketService();
    private final RecommendationService recommendationService = new RecommendationService();
    private final WeatherService     weatherService     = new WeatherService();
    private final ICalendarService icsService = ICalendarService.getInstance();
    private final NotionCalendarService notionService = NotionCalendarService.getInstance();
    private List<Lieu> allLieux = List.of();
    private List<User> allUsers = List.of();

    private final ObservableList<Evenement> masterList   = FXCollections.observableArrayList();
    private final FilteredList<Evenement>  filteredList = new FilteredList<>(masterList, p -> true);

    private Evenement   currentEvent       = null;
    private Inscription currentInscription = null;
    private Node        selectedCard       = null;
    private double      lastViewportW      = -1;

    // Calendar state
    private YearMonth calCurrentMonth = YearMonth.now();
    private String calViewMode = "month";
    private Evenement calSelectedEvent = null;
    private static final Locale LOCALE_FR = Locale.FRENCH;

    // Calendar color palette (pastel bg + dark text)
    private static final String[][] CAL_COLORS = {
        {"#E8F0FE", "#1a4a7a"},  // blue (primary)
        {"#E8F0FE", "#1a73e8"},  // blue
        {"#E6F4EA", "#137333"},  // green
        {"#FFF8E1", "#E37400"},  // yellow
        {"#FFE2EC", "#C2185B"},  // rose
        {"#FFF0E1", "#C2410C"},  // orange
    };

    // ═══════════════════════════════════════════════════════════
    //  INITIALISATION
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // Load additional calendar stylesheet
        if (panelCalendar != null && panelCalendar.getScene() != null) {
            panelCalendar.getScene().getStylesheets().add(
                    getClass().getResource("/styles/back/evenements-admin.css").toExternalForm());
        }
        // Defer stylesheet loading if scene not ready yet
        if (panelCalendar != null) {
            panelCalendar.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    String css = getClass().getResource("/styles/back/evenements-admin.css").toExternalForm();
                    if (!newScene.getStylesheets().contains(css)) {
                        newScene.getStylesheets().add(css);
                    }
                }
            });
        }
        setupFilterCombo();
        setupSearchFilter();
        setupResponsiveTiles();
        allLieux = loadAllLieux();
        allUsers = loadAllUsers();
        loadData();
        showPanelEvents();

        // Auto-configure Notion cloud sync from environment variables
        if (!notionService.isConfigured()) {
            String notionToken = System.getenv("NOTION_API_TOKEN");
            String notionDbId  = System.getenv("NOTION_DB_ID");
            if (notionToken != null && notionDbId != null) {
                notionService.configure(notionToken, notionDbId);
                System.out.println("[Notion] Auto-configured from environment variables.");
            } else {
                System.out.println("[Notion] Set NOTION_API_TOKEN and NOTION_DB_ID env vars to enable sync.");
            }
        }
        // Test connection on startup and log DB properties for debugging
        new Thread(() -> {
            boolean ok = notionService.testConnection();
            javafx.application.Platform.runLater(() -> {
                if (calNotionStatus != null) {
                    calNotionStatus.setText(ok ? "☁ Notion connecté ✅" : "☁ Notion ❌ " + notionService.getLastError());
                }
            });
        }).start();
    }

    private void setupFilterCombo() {
        filterCombo.setItems(FXCollections.observableArrayList("Titre", "Statut", "Type"));
        filterCombo.getSelectionModel().select("Titre");
        filterCombo.valueProperty().addListener((obs, o, n) -> renderCards(filteredList));
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredList.setPredicate(ev -> matchesFilter(ev, newVal));
            renderCards(filteredList);
        });
    }

    private boolean matchesFilter(Evenement e, String q) {
        String query = (q == null) ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return true;
        return switch (filterCombo.getValue() == null ? "Titre" : filterCombo.getValue()) {
            case "Statut" -> contains(e.getStatut(), query);
            case "Type"   -> contains(e.getType(),   query);
            default       -> contains(e.getTitre(),  query);
        };
    }

    private boolean contains(String s, String q) {
        return s != null && s.toLowerCase().contains(q);
    }

    private void setupResponsiveTiles() {
        if (cardsScroll == null || cardsPane == null) return;
        cardsScroll.setFitToWidth(true);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        cardsScroll.viewportBoundsProperty().addListener((obs, oldB, b) -> {
            if (b == null) return;
            double w = b.getWidth();
            if (lastViewportW > 0 && Math.abs(w - lastViewportW) < 0.5) return;
            lastViewportW = w;
            double tileW = Math.max(CARD_MIN_W, (w - 16 - 36) / 3.0);
            cardsPane.setPrefTileWidth(tileW);
        });
        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) {
            double tileW = Math.max(CARD_MIN_W, (b.getWidth() - 16 - 36) / 3.0);
            cardsPane.setPrefTileWidth(tileW);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DONNÉES
    // ═══════════════════════════════════════════════════════════

    private void loadData() {
        try {
            masterList.clear();
            List<Evenement> events = evenementService.search(null, null, null);
            masterList.addAll(events);
            updateKpis(events);
            renderCards(filteredList);
            // Always refresh calendar so it's up-to-date when user switches to it
            if (calendarContainer != null) {
                renderCalendar();
            }
        } catch (Exception e) {
            showError("Erreur", "Chargement impossible", e.getMessage());
        }
    }

    private void updateKpis(List<Evenement> events) {
        if (kpiTotal != null) kpiTotal.setText(String.valueOf(events.size()));
        if (kpiOuverts != null) {
            long nb = events.stream()
                    .filter(e -> "OUVERT".equalsIgnoreCase(safeStr(e.getStatut()))).count();
            kpiOuverts.setText(String.valueOf(nb));
        }
        if (kpiInscriptions != null) {
            try {
                int total = events.stream()
                        .mapToInt(e -> inscriptionService.countByEvent(e.getId())).sum();
                kpiInscriptions.setText(String.valueOf(total));
            } catch (Exception ex) { kpiInscriptions.setText("—"); }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PANNEAU 1 — Cards événements
    // ═══════════════════════════════════════════════════════════

    private void renderCards(List<Evenement> events) {
        cardsPane.getChildren().clear();
        selectedCard = null;
        if (events.isEmpty()) {
            Label empty = new Label("Aucun événement trouvé.");
            empty.setStyle("-fx-text-fill:rgba(15,42,68,0.65);-fx-font-weight:800;");
            cardsPane.getChildren().add(empty);
            return;
        }
        for (Evenement e : events) cardsPane.getChildren().add(createEventCard(e));
    }

    private Node createEventCard(Evenement e) {
        VBox card = new VBox(8);
        card.getStyleClass().add("lieu-card");

        // ── Image avec statut chip overlay ──
        ImageView cardIV = new ImageView();
        cardIV.setFitWidth(CARD_TARGET_W - 24);
        cardIV.setFitHeight(160);
        cardIV.setPreserveRatio(false);
        cardIV.setSmooth(true);
        cardIV.setCache(true);
        cardIV.setCacheHint(CacheHint.SPEED);
        Rectangle clip = new Rectangle(CARD_TARGET_W - 24, 160);
        clip.setArcWidth(18); clip.setArcHeight(18);
        cardIV.setClip(clip);
        cardIV.setImage(loadImageOrFallback(e.getImageUrl()));

        // Bind image width to card width
        card.widthProperty().addListener((obs, o, w) -> {
            double iw = Math.max(200, w.doubleValue() - 24);
            cardIV.setFitWidth(iw);
            clip.setWidth(iw);
        });

        Label chipStatut = new Label(safeStr(e.getStatut()));
        chipStatut.getStyleClass().addAll("statusChip",
                "status-" + safeStr(e.getStatut()).toLowerCase());

        StackPane imgWrap = new StackPane(cardIV, chipStatut);
        imgWrap.getStyleClass().add("cardImageWrap");
        StackPane.setAlignment(chipStatut, Pos.TOP_LEFT);
        StackPane.setMargin(chipStatut, new Insets(8, 0, 0, 8));

        Label title = new Label(safeStr(e.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        Label meta = new Label(
                "📅 " + formatLDT(e.getDateDebut()) + "  →  " + formatLDT(e.getDateFin()));
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        Label details = new Label(
                "👥 " + e.getCapaciteMax() + " places   •   💰 " + e.getPrix() + " TND"
                + "   •   " + safeStr(e.getType()));
        details.getStyleClass().add("cardLine");
        details.setWrapText(true);

        String lieuName = "Sans lieu";
        if (e.getLieuId() != null) {
            lieuName = allLieux.stream()
                    .filter(l -> l.getId() == e.getLieuId())
                    .map(Lieu::getNom)
                    .findFirst().orElse("Lieu #" + e.getLieuId());
        }
        Label lieu = new Label("📍 " + lieuName);
        lieu.getStyleClass().add("cardLine");

        // ── Estimation météo de présence ──
        Label weatherEstLabel = new Label("⏳ Chargement…");
        weatherEstLabel.getStyleClass().add("adminWeatherRow");

        // Charger l'estimation en arrière-plan (coordonnées par défaut = Tunis)
        if (e.getDateDebut() != null) {
            double wLat = 36.8065;
            double wLon = 10.1815;

            if (e.getLieuId() != null) {
                Lieu lieuObj = allLieux.stream()
                        .filter(l -> l.getId() == e.getLieuId()).findFirst().orElse(null);
                if (lieuObj != null && lieuObj.getLatitude() != null && lieuObj.getLongitude() != null
                        && lieuObj.getLatitude() != 0 && lieuObj.getLongitude() != 0) {
                    wLat = lieuObj.getLatitude();
                    wLon = lieuObj.getLongitude();
                }
            }

            boolean isOutdoor = e.getType() != null &&
                    (e.getType().toLowerCase().contains("plein air")
                            || e.getType().toLowerCase().contains("outdoor")
                            || e.getType().toLowerCase().contains("ext")
                            || "PUBLIC".equalsIgnoreCase(e.getType()));

            final double finalLat = wLat;
            final double finalLon = wLon;
            new Thread(() -> {
                try {
                    WeatherService.WeatherResult wr = weatherService.getWeather(
                            finalLat, finalLon, e.getDateDebut(), isOutdoor);
                    if (wr != null) {
                        javafx.application.Platform.runLater(() -> {
                            weatherEstLabel.setText(wr.icon + "  " + wr.attendancePercent + "%");
                            if (wr.attendancePercent >= 75) {
                                weatherEstLabel.getStyleClass().add("adminEstGood");
                            } else if (wr.attendancePercent >= 50) {
                                weatherEstLabel.getStyleClass().add("adminEstCaution");
                            } else {
                                weatherEstLabel.getStyleClass().add("adminEstBad");
                            }
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> {
                            weatherEstLabel.setText("⛅  Estimation indisponible");
                            weatherEstLabel.getStyleClass().add("adminEstNeutral");
                        });
                    }
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        weatherEstLabel.setText("⛅  Estimation indisponible");
                        weatherEstLabel.getStyleClass().add("adminEstNeutral");
                    });
                }
            }).start();
        } else {
            weatherEstLabel.setText("⛅  Date inconnue");
            weatherEstLabel.getStyleClass().add("adminEstNeutral");
        }

        Button btnEdit = new Button("Modifier");
        Button btnDel  = new Button("Supprimer");
        btnEdit.getStyleClass().add("card-btn");
        btnDel.getStyleClass().addAll("card-btn", "danger");

        btnEdit.setOnAction(ev -> openEditor(e));
        btnDel.setOnAction(ev -> {
            if (confirmDelete("Supprimer l'événement « " + safeStr(e.getTitre()) + " » ?")) {
                try {
                    evenementService.delete(e.getId());

                    if (currentEvent != null && currentEvent.getId() == e.getId()) currentEvent = null;
                    loadData();
                } catch (Exception ex) { showError("Erreur", "Suppression impossible", ex.getMessage()); }
            }
        });

        HBox actions = new HBox(10, btnEdit, btnDel);
        actions.getStyleClass().add("card-actions");
        card.getChildren().addAll(imgWrap, title, meta, details, lieu, weatherEstLabel, actions);
        card.setOnMouseClicked(ev -> { selectCard(card); showPanelInscriptions(e); });
        return card;
    }

    private void selectCard(Node card) {
        if (selectedCard != null) selectedCard.getStyleClass().remove("selected");
        selectedCard = card;
        card.getStyleClass().add("selected");
    }

    // ═══════════════════════════════════════════════════════════
    //  DIALOG AJOUTER / MODIFIER événement
    // ═══════════════════════════════════════════════════════════

    @FXML public void onAjouterEvenement() { openEditor(null); }

    private void openEditor(Evenement existing) { openEditor(existing, null); }

    /** Open the event editor dialog. If presetDate is non-null and we're creating,
     *  the date pickers will be pre-filled with that date. */
    private void openEditor(Evenement existing, LocalDate presetDate) {
        boolean isEdit = (existing != null);
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Modifier Événement" : "Ajouter Événement");

        // ═══════════════════════════════════════
        //  CHAMPS DU FORMULAIRE
        // ═══════════════════════════════════════
        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de l'événement (3 à 100 caractères)");

        TextArea taDesc = new TextArea();
        taDesc.setPromptText("Description (max 500 caractères)");
        taDesc.setPrefRowCount(3);
        taDesc.setWrapText(true);

        // Limites de saisie
        tfTitre.textProperty().addListener((obs, o, n) -> { if (n.length() > 100) tfTitre.setText(o); });
        taDesc.textProperty().addListener((obs, o, n) -> { if (n.length() > 500) taDesc.setText(o); });

        // Date Début
        DatePicker dpDateDebut = new DatePicker();
        dpDateDebut.setPrefWidth(150);
        dpDateDebut.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;
                setDisable(item.isBefore(LocalDate.now()));
            }
        });
        Spinner<Integer> spHeureDebut = new Spinner<>(0, 23, 10);
        spHeureDebut.setPrefWidth(110); spHeureDebut.setEditable(true);
        Spinner<Integer> spMinuteDebut = new Spinner<>(0, 59, 0);
        spMinuteDebut.setPrefWidth(110); spMinuteDebut.setEditable(true);
        HBox hbDateDebut = new HBox(10, dpDateDebut, new Label("Heure"), spHeureDebut, new Label("Min"), spMinuteDebut);
        hbDateDebut.setAlignment(Pos.CENTER_LEFT);

        // Date Fin
        DatePicker dpDateFin = new DatePicker();
        dpDateFin.setPrefWidth(150);
        dpDateFin.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;
                setDisable(item.isBefore(LocalDate.now()));
            }
        });
        Spinner<Integer> spHeureFin = new Spinner<>(0, 23, 18);
        spHeureFin.setPrefWidth(110); spHeureFin.setEditable(true);
        Spinner<Integer> spMinuteFin = new Spinner<>(0, 59, 0);
        spMinuteFin.setPrefWidth(110); spMinuteFin.setEditable(true);
        HBox hbDateFin = new HBox(10, dpDateFin, new Label("Heure"), spHeureFin, new Label("Min"), spMinuteFin);
        hbDateFin.setAlignment(Pos.CENTER_LEFT);

        TextField tfCapacite = new TextField();
        tfCapacite.setPromptText("Entier entre 1 et 100 000");
        TextField tfPrix = new TextField();
        tfPrix.setPromptText("Prix en TND (ex: 25.50)");

        ComboBox<String> cbStatut = new ComboBox<>();
        cbStatut.setItems(FXCollections.observableArrayList("OUVERT", "FERME", "ANNULE"));
        cbStatut.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbType = new ComboBox<>();
        cbType.setItems(FXCollections.observableArrayList("PUBLIC", "PRIVE"));
        cbType.setMaxWidth(Double.MAX_VALUE);

        // ── Liste déroulante des lieux ──
        allLieux = loadAllLieux();
        ComboBox<Lieu> cbLieu = new ComboBox<>();
        cbLieu.getItems().add(null);            // option "Sans lieu"
        cbLieu.getItems().addAll(allLieux);
        cbLieu.setMaxWidth(Double.MAX_VALUE);
        cbLieu.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Lieu item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "— Sans lieu —" : item.getNom() + " (" + item.getVille() + ")");
            }
        });
        cbLieu.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Lieu item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setText(null); return; }
                setText(item == null ? "— Sans lieu —" : item.getNom() + " (" + item.getVille() + ")");
            }
        });

        // ── Lieu activé uniquement si type = PRIVE ──
        cbLieu.setDisable(true);  // désactivé par défaut (PUBLIC)
        cbType.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean prive = "PRIVE".equals(newVal);
            cbLieu.setDisable(!prive);
            if (!prive) {
                cbLieu.getSelectionModel().select(null);  // reset → "Sans lieu"
            }
        });

        // Numeric filters + max length
        tfCapacite.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*")) tfCapacite.setText(o);
            else if (n.length() > 6) tfCapacite.setText(o);
        });
        tfPrix.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d*\\.?\\d*")) tfPrix.setText(o);
            else if (n.length() > 10) tfPrix.setText(o);
        });

        // ═══════════════════════════════════════
        //  IMAGE UPLOAD
        // ═══════════════════════════════════════
        ImageView imgPrev = new ImageView();
        imgPrev.setFitWidth(420);
        imgPrev.setFitHeight(200);
        imgPrev.setPreserveRatio(false);
        Rectangle clipForm = new Rectangle(420, 200);
        clipForm.setArcWidth(24); clipForm.setArcHeight(24);
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

        final String[] pickedPath = {null};
        final String[] lastPreviewImagePath = {null};
        final Image[] cachedPreviewImage = {null};

        // ═══════════════════════════════════════
        //  PREVIEW LIVE (colonne droite)
        // ═══════════════════════════════════════
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
        clipPrev.setArcWidth(22); clipPrev.setArcHeight(22);
        previewIV.setClip(clipPrev);

        Label previewImgEmpty = new Label("Aucune image");
        previewImgEmpty.getStyleClass().add("previewImageEmpty");
        previewImgWrap.getChildren().addAll(previewIV, previewImgEmpty);

        Label previewStatus = new Label("OUVERT");
        previewStatus.getStyleClass().addAll("statusChip", "status-ouvert");
        StackPane.setAlignment(previewStatus, Pos.TOP_LEFT);
        StackPane.setMargin(previewStatus, new Insets(10, 0, 0, 10));
        previewImgWrap.getChildren().add(previewStatus);

        Label previewTitle = new Label("Titre de l'événement");
        previewTitle.getStyleClass().add("previewTitle");
        previewTitle.setWrapText(true);

        Label previewMeta = new Label("📅 —  →  —");
        previewMeta.getStyleClass().add("previewMeta");
        previewMeta.setWrapText(true);

        Label previewLine = new Label("👥 —   •   💰 —   •   Type");
        previewLine.getStyleClass().add("previewLine");
        previewLine.setWrapText(true);

        Label previewLieu = new Label("📍 —");
        previewLieu.getStyleClass().add("previewLine");
        previewLieu.setWrapText(true);

        Label previewDescTitle = new Label("Description");
        previewDescTitle.getStyleClass().add("previewSectionTitle");

        Label previewDesc = new Label("—");
        previewDesc.getStyleClass().add("previewDesc");
        previewDesc.setWrapText(true);

        Label liveHint = new Label("");
        liveHint.getStyleClass().add("liveHint");

        previewCard.getChildren().addAll(
                new Label("Aperçu en direct"),
                previewImgWrap,
                previewTitle,
                previewMeta,
                previewLine,
                previewLieu,
                previewDescTitle,
                previewDesc,
                liveHint
        );
        previewCard.getChildren().get(0).getStyleClass().add("previewHeader");

        previewCard.widthProperty().addListener((obs, o, w) -> {
            double ww = Math.max(280, w.doubleValue() - 24);
            previewIV.setFitWidth(ww);
            clipPrev.setWidth(ww);
        });

        Runnable applyPreviewImageIfChanged = () -> {
            String p = pickedPath[0];
            if (Objects.equals(p, lastPreviewImagePath[0])) return;
            lastPreviewImagePath[0] = p;
            cachedPreviewImage[0] = loadImageOrNull(p);
            previewIV.setImage(cachedPreviewImage[0]);
            boolean empty = (cachedPreviewImage[0] == null);
            previewImgEmpty.setVisible(empty);
            previewImgEmpty.setManaged(empty);
        };

        Runnable updatePreviewNow = () -> {
            // Titre
            String t = safeStr(tfTitre.getText()).trim();
            previewTitle.setText(t.isEmpty() ? "Titre de l'événement" : t);

            // Dates
            LocalDateTime dtDebut = null, dtFin = null;
            try {
                if (dpDateDebut.getValue() != null)
                    dtDebut = LocalDateTime.of(dpDateDebut.getValue(),
                            LocalTime.of(spHeureDebut.getValue(), spMinuteDebut.getValue()));
                if (dpDateFin.getValue() != null)
                    dtFin = LocalDateTime.of(dpDateFin.getValue(),
                            LocalTime.of(spHeureFin.getValue(), spMinuteFin.getValue()));
            } catch (Exception ignored) {}

            String when = (dtDebut == null ? "—" : DATE_FMT.format(dtDebut))
                    + "  →  " + (dtFin == null ? "—" : DATE_FMT.format(dtFin));
            previewMeta.setText("📅 " + when);

            // Capacité, prix, type
            String cap = safeStr(tfCapacite.getText()).trim();
            String prix = safeStr(tfPrix.getText()).trim();
            String type = safeStr(cbType.getValue());
            previewLine.setText("👥 " + (cap.isEmpty() ? "—" : cap) + " places   •   💰 "
                    + (prix.isEmpty() ? "—" : prix) + " TND   •   " + (type.isEmpty() ? "Type" : type));

            // Lieu
            Lieu selectedLieu = cbLieu.getValue();
            previewLieu.setText("📍 " + (selectedLieu == null ? "Sans lieu" : selectedLieu.getNom()));

            // Description
            String desc = safeStr(taDesc.getText()).trim();
            previewDesc.setText(desc.isEmpty() ? "—" : desc);

            // Statut chip
            String st = safeStr(cbStatut.getValue()).trim();
            if (st.isEmpty()) st = "OUVERT";
            previewStatus.setText(st);
            previewStatus.getStyleClass().removeIf(c -> c.startsWith("status-"));
            previewStatus.getStyleClass().add("status-" + st.toLowerCase());

            // Image
            applyPreviewImageIfChanged.run();

            // Validation live
            String validation = validateLive(tfTitre, dpDateDebut, spHeureDebut, spMinuteDebut,
                    dpDateFin, spHeureFin, spMinuteFin, tfCapacite, tfPrix, cbStatut, cbType);
            liveHint.setText(validation);
            liveHint.setVisible(!validation.isEmpty());
            liveHint.setManaged(!validation.isEmpty());
        };

        // Debounce preview updates
        PauseTransition debounce = new PauseTransition(Duration.millis(120));
        Runnable schedulePreview = () -> {
            debounce.stop();
            debounce.setOnFinished(ev -> updatePreviewNow.run());
            debounce.playFromStart();
        };

        // Image picker action
        btnPickImg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir une image");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif")
            );
            File f = fc.showOpenDialog(dialog);
            if (f == null) return;

            try {
                String saved = saveEventImage(f);
                pickedPath[0] = saved;
                imgPath.setText(new File(saved).getName());
                Image im = loadImageOrNull(saved);
                imgPrev.setImage(im);
                boolean empty = (im == null);
                imgEmpty.setVisible(empty);
                imgEmpty.setManaged(empty);
                applyPreviewImageIfChanged.run();
                schedulePreview.run();
            } catch (Exception ex) {
                showError("Upload", "Impossible d'uploader l'image", safeStr(ex.getMessage()));
            }
        });

        // ═══════════════════════════════════════
        //  PREFILL si mode édition
        // ═══════════════════════════════════════
        if (isEdit) {
            tfTitre.setText(safeStr(existing.getTitre()));
            taDesc.setText(safeStr(existing.getDescription()));
            if (existing.getDateDebut() != null) {
                dpDateDebut.setValue(existing.getDateDebut().toLocalDate());
                spHeureDebut.getValueFactory().setValue(existing.getDateDebut().getHour());
                spMinuteDebut.getValueFactory().setValue(existing.getDateDebut().getMinute());
            }
            if (existing.getDateFin() != null) {
                dpDateFin.setValue(existing.getDateFin().toLocalDate());
                spHeureFin.getValueFactory().setValue(existing.getDateFin().getHour());
                spMinuteFin.getValueFactory().setValue(existing.getDateFin().getMinute());
            }
            tfCapacite.setText(String.valueOf(existing.getCapaciteMax()));
            tfPrix.setText(String.valueOf(existing.getPrix()));
            cbStatut.getSelectionModel().select(safeStr(existing.getStatut()));
            cbType.getSelectionModel().select(safeStr(existing.getType()));
            if (existing.getLieuId() != null) {
                allLieux.stream().filter(l -> l.getId() == existing.getLieuId())
                        .findFirst().ifPresent(l -> cbLieu.getSelectionModel().select(l));
            } else {
                cbLieu.getSelectionModel().select(null);
            }

            pickedPath[0] = existing.getImageUrl();
            imgPath.setText(shortPath(safeStr(existing.getImageUrl())));
            Image im = loadImageOrNull(existing.getImageUrl());
            imgPrev.setImage(im);
            boolean emptyImg = (im == null);
            imgEmpty.setVisible(emptyImg);
            imgEmpty.setManaged(emptyImg);
        } else {
            cbStatut.getSelectionModel().select("OUVERT");
            cbType.getSelectionModel().select("PUBLIC");
            tfCapacite.setText("50");
            tfPrix.setText("0");
            LocalDate preset = (presetDate != null) ? presetDate : LocalDate.now();
            dpDateDebut.setValue(preset);
            spHeureDebut.getValueFactory().setValue(10);
            spMinuteDebut.getValueFactory().setValue(0);
            dpDateFin.setValue(preset);
            spHeureFin.getValueFactory().setValue(18);
            spMinuteFin.getValueFactory().setValue(0);
        }

        // ═══════════════════════════════════════
        //  LISTENERS live preview
        // ═══════════════════════════════════════
        tfTitre.textProperty().addListener((a,b,c) -> schedulePreview.run());
        taDesc.textProperty().addListener((a,b,c) -> schedulePreview.run());
        dpDateDebut.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spHeureDebut.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spMinuteDebut.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        dpDateFin.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spHeureFin.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spMinuteFin.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        tfCapacite.textProperty().addListener((a,b,c) -> schedulePreview.run());
        tfPrix.textProperty().addListener((a,b,c) -> schedulePreview.run());
        cbStatut.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        cbType.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        cbLieu.valueProperty().addListener((a,b,c) -> schedulePreview.run());

        // ═══════════════════════════════════════
        //  LAYOUT — formulaire gauche
        // ═══════════════════════════════════════
        Label headline = new Label(isEdit ? "Modifier un événement" : "Créer un événement");
        headline.getStyleClass().add("dialogTitle");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(38);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(62);
        grid.getColumnConstraints().addAll(c1, c2);

        int r = 0;
        grid.add(lab("Titre *"), 0, r);          grid.add(tfTitre, 1, r++);
        grid.add(lab("Date début *"), 0, r);      grid.add(hbDateDebut, 1, r++);
        grid.add(lab("Date fin *"), 0, r);        grid.add(hbDateFin, 1, r++);
        grid.add(lab("Capacité max *"), 0, r);    grid.add(tfCapacite, 1, r++);
        grid.add(lab("Prix (TND) *"), 0, r);      grid.add(tfPrix, 1, r++);
        grid.add(lab("Statut *"), 0, r);          grid.add(cbStatut, 1, r++);
        grid.add(lab("Type *"), 0, r);            grid.add(cbType, 1, r++);
        grid.add(lab("Lieu"), 0, r);              grid.add(cbLieu, 1, r++);

        // Image upload section
        Button btnGenerateAI = new Button("\u2728 Générer par IA");
        btnGenerateAI.getStyleClass().add("btn-pill");
        btnGenerateAI.setStyle("-fx-background-color: linear-gradient(to right, #8a2be2, #6a11cb);"
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        ProgressIndicator aiSpinner = new ProgressIndicator();
        aiSpinner.setPrefSize(18, 18);
        aiSpinner.setMaxSize(18, 18);
        aiSpinner.setVisible(false);
        aiSpinner.setManaged(false);

        Label aiStatusLabel = new Label("");
        aiStatusLabel.setStyle("-fx-text-fill: #8a2be2; -fx-font-size: 11px;");
        aiStatusLabel.setVisible(false);
        aiStatusLabel.setManaged(false);

        btnGenerateAI.setOnAction(e -> {
            String desc = safeStr(taDesc.getText()).trim();
            String titre = safeStr(tfTitre.getText()).trim();
            if (desc.isEmpty() && titre.isEmpty()) {
                showError("Génération IA", "Description manquante",
                        "Veuillez saisir un titre ou une description pour générer l'image.");
                return;
            }

            String searchText = desc.isEmpty() ? titre : desc;

            btnGenerateAI.setText("\u23F3 Génération...");
            btnGenerateAI.setDisable(true);
            aiSpinner.setVisible(true);
            aiSpinner.setManaged(true);
            aiStatusLabel.setText("Connexion aux serveurs IA...");
            aiStatusLabel.setVisible(true);
            aiStatusLabel.setManaged(true);

            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                return downloadAIImage(searchText);
            }).thenAccept(savedPath -> {
                javafx.application.Platform.runLater(() -> {
                    pickedPath[0] = savedPath;
                    imgPath.setText(new java.io.File(savedPath).getName());
                    Image im = loadImageOrNull(savedPath);
                    imgPrev.setImage(im);
                    boolean empty2 = (im == null);
                    imgEmpty.setVisible(empty2);
                    imgEmpty.setManaged(empty2);
                    applyPreviewImageIfChanged.run();
                    schedulePreview.run();

                    btnGenerateAI.setText("\u2728 Générer par IA");
                    btnGenerateAI.setDisable(false);
                    aiSpinner.setVisible(false);
                    aiSpinner.setManaged(false);
                    aiStatusLabel.setText("\u2705 Image générée !");
                    PauseTransition hideStatus = new PauseTransition(Duration.seconds(3));
                    hideStatus.setOnFinished(ev -> { aiStatusLabel.setVisible(false); aiStatusLabel.setManaged(false); });
                    hideStatus.play();
                });
            }).exceptionally(ex -> {
                javafx.application.Platform.runLater(() -> {
                    showError("Génération IA", "Échec de la génération",
                            "Aucun serveur d'images n'a pu répondre.\n" + ex.getMessage());
                    btnGenerateAI.setText("\u2728 Générer par IA");
                    btnGenerateAI.setDisable(false);
                    aiSpinner.setVisible(false);
                    aiSpinner.setManaged(false);
                    aiStatusLabel.setVisible(false);
                    aiStatusLabel.setManaged(false);
                });
                return null;
            });
        });

        HBox imgButtons = new HBox(10, btnPickImg, btnGenerateAI, aiSpinner, imgPath);
        imgButtons.setAlignment(Pos.CENTER_LEFT);
        VBox imgBox = new VBox(10, imgWrap, imgButtons, aiStatusLabel);

        // Description section
        VBox descBox = new VBox(8, lab("Description"), taDesc);

        VBox formContent = new VBox(14, grid, imgBox, descBox);
        formContent.setPadding(new Insets(0, 6, 6, 0));

        ScrollPane formScroll = new ScrollPane(formContent);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        formScroll.getStyleClass().add("editorScroll");

        // ═══════════════════════════════════════
        //  FOOTER
        // ═══════════════════════════════════════
        Button btnSave = new Button(isEdit ? "Enregistrer" : "Créer");
        btnSave.getStyleClass().add("primaryBtn");
        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("ghostBtn");
        HBox footer = new HBox(10, btnCancel, btnSave);
        footer.setAlignment(Pos.CENTER_RIGHT);
        btnCancel.setOnAction(ev -> dialog.close());

        btnSave.setOnAction(ev -> {
            String validation = validateLive(tfTitre, dpDateDebut, spHeureDebut, spMinuteDebut,
                    dpDateFin, spHeureFin, spMinuteFin, tfCapacite, tfPrix, cbStatut, cbType);
            if (!validation.isEmpty()) {
                showError("Validation", "Formulaire incomplet", validation);
                return;
            }

            try {
                String titre = textOf(tfTitre);
                LocalDateTime dateDebut = LocalDateTime.of(dpDateDebut.getValue(),
                        LocalTime.of(spHeureDebut.getValue(), spMinuteDebut.getValue()));
                LocalDateTime dateFin = LocalDateTime.of(dpDateFin.getValue(),
                        LocalTime.of(spHeureFin.getValue(), spMinuteFin.getValue()));
                int capacite = Integer.parseInt(textOf(tfCapacite));
                double prix = textOf(tfPrix).isEmpty() ? 0.0 : Double.parseDouble(textOf(tfPrix));
                Integer lieuId = cbLieu.getValue() != null ? cbLieu.getValue().getId() : null;

                if (isEdit) {
                    existing.setTitre(titre);
                    existing.setDescription(textOf(taDesc));
                    existing.setDateDebut(dateDebut);
                    existing.setDateFin(dateFin);
                    existing.setCapaciteMax(capacite);
                    existing.setPrix(prix);
                    existing.setStatut(cbStatut.getValue());
                    existing.setType(cbType.getValue());
                    existing.setLieuId(lieuId);
                    existing.setImageUrl(pickedPath[0] != null ? pickedPath[0] : "");
                    evenementService.update(existing);

                } else {
                    Evenement toAdd = new Evenement();
                    toAdd.setTitre(titre);
                    toAdd.setDescription(textOf(taDesc));
                    toAdd.setDateDebut(dateDebut);
                    toAdd.setDateFin(dateFin);
                    toAdd.setCapaciteMax(capacite);
                    toAdd.setPrix(prix);
                    toAdd.setStatut(cbStatut.getValue());
                    toAdd.setType(cbType.getValue());
                    toAdd.setLieuId(lieuId);
                    toAdd.setImageUrl(pickedPath[0] != null ? pickedPath[0] : "");
                    int newId = evenementService.add(toAdd);
                    toAdd.setId(newId);

                }
                loadData();
                dialog.close();
            } catch (IllegalArgumentException ex) {
                showError("Erreur", "Données invalides", ex.getMessage());
            } catch (Exception ex) {
                showError("Erreur", "Enregistrement impossible", ex.getMessage());
            }
        });

        // Initial preview
        updatePreviewNow.run();
        btnSave.setDisable(!liveHint.getText().isEmpty());
        liveHint.textProperty().addListener((obs, o, n) -> btnSave.setDisable(n != null && !n.isEmpty()));

        // ═══════════════════════════════════════
        //  SHELL — SplitPane 2 colonnes
        // ═══════════════════════════════════════
        VBox shell = new VBox(12);
        shell.setPadding(new Insets(16));
        shell.getStyleClass().add("dialogRoot");

        SplitPane split = new SplitPane(formScroll, previewCard);
        split.setDividerPositions(0.62);
        split.getStyleClass().add("editorSplit");
        VBox.setVgrow(split, Priority.ALWAYS);

        shell.getChildren().addAll(headline, split, footer);

        Scene scene = new Scene(shell, 980, 820);
        scene.getStylesheets().add(
                getClass().getResource("/styles/back/evenements-admin.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    // ── Validation live ─────────────────────────────────────────
    private String validateLive(TextField tfTitre,
                                DatePicker dpDateDebut, Spinner<Integer> spHD, Spinner<Integer> spMD,
                                DatePicker dpDateFin, Spinner<Integer> spHF, Spinner<Integer> spMF,
                                TextField tfCapacite, TextField tfPrix,
                                ComboBox<String> cbStatut, ComboBox<String> cbType) {
        // ── Titre ──
        String titre = safeStr(tfTitre.getText()).trim();
        if (titre.isEmpty()) return "Le titre est obligatoire";
        if (titre.length() < 3) return "Titre trop court (min 3 caractères)";
        if (titre.length() > 100) return "Titre trop long (max 100 caractères)";
        if (!titre.matches("[\\p{L}\\p{N}\\s'\\-–—.,!?:()&/]+")) return "Titre : caractères spéciaux non autorisés";

        // ── Dates ──
        if (dpDateDebut.getValue() == null) return "Sélectionnez une date de début";
        if (dpDateFin.getValue() == null) return "Sélectionnez une date de fin";

        try {
            LocalDateTime deb = LocalDateTime.of(dpDateDebut.getValue(), LocalTime.of(spHD.getValue(), spMD.getValue()));
            LocalDateTime fin = LocalDateTime.of(dpDateFin.getValue(), LocalTime.of(spHF.getValue(), spMF.getValue()));
            if (deb.isBefore(LocalDateTime.now().minusMinutes(5)))
                return "La date de début ne peut pas être dans le passé";
            if (!fin.isAfter(deb)) return "La date de fin doit être après la date de début";
            if (fin.isAfter(deb.plusYears(2))) return "Durée max : 2 ans";
        } catch (Exception ex) { return "Dates invalides"; }

        // ── Capacité ──
        String cap = safeStr(tfCapacite.getText()).trim();
        if (cap.isEmpty()) return "La capacité est obligatoire";
        try {
            int c = Integer.parseInt(cap);
            if (c <= 0) return "Capacité doit être > 0";
            if (c > 100_000) return "Capacité max 100 000";
        } catch (NumberFormatException ex) { return "Capacité invalide (entier attendu)"; }

        // ── Prix ──
        String prix = safeStr(tfPrix.getText()).trim();
        if (prix.isEmpty()) return "Le prix est obligatoire (0 si gratuit)";
        try {
            double p = Double.parseDouble(prix);
            if (p < 0) return "Le prix doit être ≥ 0";
            if (p > 99_999) return "Prix max 99 999 TND";
        } catch (NumberFormatException ex) { return "Prix invalide (nombre attendu)"; }

        // ── Statut / Type ──
        if (cbStatut.getValue() == null) return "Sélectionnez un statut";
        if (cbType.getValue() == null) return "Sélectionnez un type";

        return "";
    }

    // ── Sauvegarde image événement ──────────────────────────────
    private String saveEventImage(File source) throws IOException {
        Path uploadDir = Path.of(System.getProperty("user.home"), "uploads", "evenements");
        Files.createDirectories(uploadDir);
        String ext = "";
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) ext = name.substring(dot);
        String destName = UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = uploadDir.resolve(destName);
        Files.copy(source.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toAbsolutePath().toString();
    }

    // ── Génération d'image IA (multi-API avec fallback) ────────
    private String downloadAIImage(String description) {
        // Extraire les mots-clés pertinents de la description
        String keywords = extractKeywords(description);

        // ── Tentative 1 : Pollinations.ai (IA pure, gratuit, sans clé) ──
        try {
            String prompt = java.net.URLEncoder.encode(description, "UTF-8");
            String urlStr = "https://image.pollinations.ai/prompt/" + prompt + "?width=840&height=400&nologo=true";
            String result = downloadImageFromUrl(urlStr, "ai_pollinations_");
            if (result != null) return result;
        } catch (Exception ignored) { }

        // ── Tentative 2 : LoremFlickr (images Flickr par mots-clés, 100% gratuit) ──
        try {
            String encoded = java.net.URLEncoder.encode(keywords.replace(" ", ","), "UTF-8");
            String urlStr = "https://loremflickr.com/840/400/" + encoded;
            String result = downloadImageFromUrl(urlStr, "ai_flickr_");
            if (result != null) return result;
        } catch (Exception ignored) { }

        // ── Tentative 3 : Picsum (image aléatoire de qualité, dernier recours) ──
        try {
            String urlStr = "https://picsum.photos/840/400";
            String result = downloadImageFromUrl(urlStr, "ai_picsum_");
            if (result != null) return result;
        } catch (Exception ignored) { }

        throw new RuntimeException("Tous les serveurs d'images sont indisponibles.");
    }

    private String downloadImageFromUrl(String urlStr, String prefix) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int code = conn.getResponseCode();

            // Handle manual redirect (302/301)
            if (code == 301 || code == 302) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    conn.disconnect();
                    url = new java.net.URL(location);
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15_000);
                    conn.setReadTimeout(30_000);
                    conn.setRequestProperty("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    code = conn.getResponseCode();
                }
            }

            if (code == 200) {
                String contentType = conn.getContentType();
                if (contentType != null && contentType.startsWith("image")) {
                    java.io.InputStream in = conn.getInputStream();
                    Path uploadDir = Path.of(System.getProperty("user.home"), "uploads", "evenements");
                    Files.createDirectories(uploadDir);
                    String ext = ".jpg";
                    if (contentType.contains("png")) ext = ".png";
                    else if (contentType.contains("webp")) ext = ".webp";
                    String destName = prefix + UUID.randomUUID().toString().substring(0, 8) + ext;
                    Path dest = uploadDir.resolve(destName);
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    in.close();
                    conn.disconnect();

                    // Vérifier que le fichier n'est pas trop petit (< 5 Ko = probablement une erreur)
                    if (Files.size(dest) < 5_000) {
                        Files.deleteIfExists(dest);
                        return null;
                    }
                    return dest.toAbsolutePath().toString();
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // silently fail, will try next API
        }
        return null;
    }

    private String extractKeywords(String description) {
        // Supprimer les mots courants français pour garder les termes importants
        String[] stopWords = {"le", "la", "les", "un", "une", "des", "de", "du", "et",
                "en", "au", "aux", "ce", "cette", "ces", "mon", "ton", "son", "nous",
                "vous", "ils", "elle", "elles", "est", "sont", "sera", "pour", "par",
                "avec", "dans", "sur", "qui", "que", "quoi", "dont", "ou", "mais",
                "donc", "car", "ni", "plus", "moins", "très", "bien", "aussi", "tout",
                "tous", "toute", "toutes", "faire", "fait", "être", "avoir", "a",
                "il", "je", "tu", "nous", "pas", "ne", "se", "sa", "ses"};
        java.util.Set<String> stops = new java.util.HashSet<>(java.util.Arrays.asList(stopWords));

        String cleaned = description.toLowerCase()
                .replaceAll("[^a-zàâäéèêëïîôùûüÿçœæ\\s]", " ")
                .replaceAll("\\s+", " ").trim();

        StringBuilder keywords = new StringBuilder();
        int count = 0;
        for (String word : cleaned.split(" ")) {
            if (word.length() >= 3 && !stops.contains(word) && count < 5) {
                if (keywords.length() > 0) keywords.append(",");
                keywords.append(word);
                count++;
            }
        }
        return keywords.length() > 0 ? keywords.toString() : "event";
    }

    private Label lab(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("formLabel");
        return l;
    }

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════

    private void showPanel(VBox toShow) {
        for (VBox p : new VBox[]{panelEvents, panelInscriptions, panelTickets, panelCalendar, panelStats}) {
            if (p != null) { boolean show = (p == toShow); p.setVisible(show); p.setManaged(show); }
        }
    }

    private void showPanelEvents() { showPanel(panelEvents); }

    private void showPanelInscriptions(Evenement e) {
        this.currentEvent = e; this.currentInscription = null;
        breadcrumbEvent.setText(e.getTitre()); inscEventTitle.setText(e.getTitre());
        inscEventMeta.setText("📅 " + formatLDT(e.getDateDebut()) + "  →  " + formatLDT(e.getDateFin())
                + "   •   " + safeStr(e.getType()) + "   •   " + safeStr(e.getStatut()));
        showPanel(panelInscriptions); reloadInscriptions(); refreshPlacesInfo();
    }

    /**
     * ✅ CORRIGÉ — showPanelTickets
     * Ne lit plus le paiement depuis l'objet Inscription en mémoire (valeur périmée).
     * Le header sera mis à jour par refreshTicketsHeader() après chaque modification.
     */
    private void showPanelTickets(Inscription ins) {
        this.currentInscription = ins;
        String userName = resolveUserName(ins.getUserId());
        breadcrumbInscription.setText("Inscription #" + ins.getId() + " — " + userName);
        ticketInscInfo.setText("Inscription #" + ins.getId()
                + "  •  " + userName + "  •  " + safeStr(ins.getStatut()));
        ticketEventInfo.setText("Événement : " + (currentEvent != null ? currentEvent.getTitre() : "?")
                + "   •   Paiement : " + safeStr(ins.getPaiement()) + " TND");
        showPanel(panelTickets); reloadTickets();
    }

    @FXML public void onRetourEvents() { currentEvent = null; showPanelEvents(); }

    @FXML public void onRetourInscriptions() {
        currentInscription = null;
        if (currentEvent != null) showPanelInscriptions(currentEvent);
        else showPanelEvents();
    }

    // ═══════════════════════════════════════════════════════════
    //  PANNEAU 2 — Inscriptions
    // ═══════════════════════════════════════════════════════════

    private void reloadInscriptions() {
        inscriptionsBox.getChildren().clear();
        if (currentEvent == null) return;
        List<Inscription> list = inscriptionService.getByEventId(currentEvent.getId());
        if (list.isEmpty()) { inscriptionsBox.getChildren().add(emptyLabel("Aucune inscription.")); return; }
        for (Inscription ins : list) inscriptionsBox.getChildren().add(buildInscriptionRow(ins));
    }

    private Node buildInscriptionRow(Inscription ins) {
        VBox row = new VBox(10);
        row.getStyleClass().add("lieu-card");
        row.setPadding(new Insets(14, 18, 14, 18));

        // --- Header : user name + id ---
        String userName = resolveUserName(ins.getUserId());
        Label title = new Label(userName);
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size:15px;");
        Label idBadge = new Label("#" + ins.getUserId());
        idBadge.setStyle("-fx-background-color:rgba(15,42,68,0.08);-fx-text-fill:#0f2a44;"
                + "-fx-padding:2 8;-fx-background-radius:6;-fx-font-size:11px;-fx-font-weight:700;");

        // --- Status chip ---
        String statut = safeStr(ins.getStatut());
        Label statusChip = new Label(statut);
        statusChip.getStyleClass().add("statusChip");
        switch (statut.toUpperCase()) {
            case "CONFIRMEE" -> statusChip.getStyleClass().add("status-confirmee");
            case "ANNULEE"   -> statusChip.getStyleClass().add("status-annulee");
            default          -> statusChip.getStyleClass().add("status-en-attente");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, idBadge, spacer, statusChip);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- Meta line ---
        int nbTickets = ticketService.countByInscriptionId(ins.getId());
        String dateStr = ins.getDateCreation() != null
                ? ins.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
        Label meta = new Label("🎫  " + nbTickets + " ticket(s)   •   💰 " + safeStr(ins.getPaiement())
                + " TND   •   📅 " + dateStr);
        meta.getStyleClass().add("card-muted");

        // --- Actions ---
        Button btnConfirm = new Button("✔ Confirmer");
        Button btnAnnuler = new Button("✖ Annuler");
        Button btnTickets = new Button("🎫 Tickets →");
        Button btnDel     = new Button("Supprimer");
        btnConfirm.getStyleClass().add("card-btn");
        btnAnnuler.getStyleClass().add("card-btn");
        btnTickets.getStyleClass().add("card-btn");
        btnDel.getStyleClass().addAll("card-btn", "danger");
        btnConfirm.setOnAction(e -> { inscriptionService.updateStatut(ins.getId(), "CONFIRMEE"); reloadInscriptions(); refreshPlacesInfo(); updateKpis(masterList); });
        btnAnnuler.setOnAction(e -> { inscriptionService.updateStatut(ins.getId(), "ANNULEE");   reloadInscriptions(); refreshPlacesInfo(); updateKpis(masterList); });
        btnTickets.setOnAction(e -> showPanelTickets(ins));
        btnDel.setOnAction(e -> {
            if (!confirmDelete("Supprimer cette inscription ?")) return;
            inscriptionService.delete(ins.getId());
            reloadInscriptions(); refreshPlacesInfo(); updateKpis(masterList);
        });
        HBox actions = new HBox(10, btnConfirm, btnAnnuler, btnTickets, btnDel);
        actions.getStyleClass().add("card-actions");

        row.getChildren().addAll(header, meta, actions);
        return row;
    }

    @FXML
    public void onAjouterInscription() {
        if (currentEvent == null) { showWarning("Aucun événement sélectionné."); return; }
        allUsers = loadAllUsers();

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Ajouter une inscription");

        VBox root = new VBox(18);
        root.getStyleClass().add("dialogRoot");
        root.setPadding(new Insets(28, 32, 24, 32));
        root.setMaxWidth(460);

        Label titleLbl = new Label("Nouvelle inscription");
        titleLbl.getStyleClass().add("dialogTitle");

        Label eventInfo = new Label("Événement : " + currentEvent.getTitre()
                + "\nPrix par ticket : " + currentEvent.getPrix() + " TND");
        eventInfo.setStyle("-fx-text-fill:#475569;-fx-font-size:13px;");
        eventInfo.setWrapText(true);

        Label userLabel = new Label("Utilisateur");
        userLabel.setStyle("-fx-font-weight:700;-fx-text-fill:#0f2a44;-fx-font-size:13px;");

        ComboBox<User> cbUser = new ComboBox<>();
        cbUser.getItems().addAll(allUsers);
        cbUser.setMaxWidth(Double.MAX_VALUE);
        cbUser.setPromptText("— Sélectionner un utilisateur —");
        cbUser.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getNom() + " " + u.getPrenom() + "  (" + u.getEmail() + ")  #" + u.getId());
            }
        });
        cbUser.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getNom() + " " + u.getPrenom() + "  #" + u.getId());
            }
        });

        Label errLbl = new Label();
        errLbl.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;");
        errLbl.setVisible(false);
        errLbl.setManaged(false);

        Button btnOk = new Button("Ajouter");
        btnOk.getStyleClass().add("primaryBtn");
        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("ghostBtn");
        HBox btns = new HBox(12, btnCancel, btnOk);
        btns.setAlignment(Pos.CENTER_RIGHT);

        btnCancel.setOnAction(e -> dialog.close());
        btnOk.setOnAction(e -> {
            User sel = cbUser.getValue();
            if (sel == null) {
                errLbl.setText("⚠ Veuillez sélectionner un utilisateur.");
                errLbl.setVisible(true); errLbl.setManaged(true);
                return;
            }
            try {
                inscriptionService.addInscription(currentEvent.getId(), sel.getId(), 0.0f);
                reloadInscriptions(); refreshPlacesInfo(); updateKpis(masterList);
                dialog.close();
            } catch (IllegalStateException ex) { showWarning(ex.getMessage()); }
            catch (Exception ex) { showError("Erreur", "Inscription impossible", ex.getMessage()); }
        });

        root.getChildren().addAll(titleLbl, eventInfo, userLabel, cbUser, errLbl, btns);

        Scene sc = new Scene(root);
        sc.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/styles/back/evenements-admin.css")).toExternalForm());
        dialog.setScene(sc);
        dialog.showAndWait();
    }

    private void refreshPlacesInfo() {
        if (currentEvent == null) { placesInfo.setText(""); return; }
        // ✅ Places = capaciteMax − total tickets (pas inscriptions)
        int ticketsVendus = ticketService.countByEventId(currentEvent.getId());
        int max  = currentEvent.getCapaciteMax();
        int rest = Math.max(0, max - ticketsVendus);
        placesInfo.setText(ticketsVendus + " / " + max + " places  (libres : " + rest + ")");
        placesInfo.setStyle(rest == 0
                ? "-fx-text-fill:#dc2626;-fx-font-weight:bold;"
                : "-fx-text-fill:#15803d;-fx-font-weight:bold;");
    }

    // ═══════════════════════════════════════════════════════════
    //  PANNEAU 3 — Tickets
    // ═══════════════════════════════════════════════════════════

    /**
     * ✅ CORRIGÉ — reloadTickets
     * Affiche TOUS les tickets (getListByInscriptionId).
     * Après suppression d'un ticket : recalcule + rafraîchit le header immédiatement.
     */
    private void reloadTickets() {
        ticketsBox.getChildren().clear();
        if (currentInscription == null) return;

        List<Ticket> tickets = ticketService.getListByInscriptionId(currentInscription.getId());

        if (tickets.isEmpty()) {
            ticketsBox.getChildren().add(emptyLabel("Aucun ticket. Cliquez sur « Générer ticket » pour en ajouter."));
            return;
        }

        for (Ticket t : tickets) {
            VBox card = new VBox(8);
            card.getStyleClass().add("lieu-card");

            Label title = new Label("🎫 Ticket #" + t.getId());
            title.getStyleClass().add("card-title");

            String dateStr = t.getDate() != null
                    ? t.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
            Label sub = new Label("Inscription #" + t.getInscriptionId() + "   •   Date : " + dateStr);
            sub.getStyleClass().add("card-muted");

            Button btnDel = new Button("Supprimer ticket");
            btnDel.getStyleClass().addAll("card-btn", "danger");
            btnDel.setOnAction(e -> {
                if (!confirmDelete("Supprimer le ticket #" + t.getId() + " ?")) return;
                ticketService.delete(t.getId());
                // ✅ Recalcule paiement APRÈS suppression
                recalculerPaiement(currentInscription);
                // ✅ Rafraîchit l'affichage du paiement dans le header IMMÉDIATEMENT
                refreshTicketsHeader();
                reloadTickets();
                refreshPlacesInfo();
                reloadInscriptions();
            });

            HBox actions = new HBox(10, btnDel);
            actions.getStyleClass().add("card-actions");
            card.getChildren().addAll(title, sub, actions);
            ticketsBox.getChildren().add(card);
        }
    }

    /**
     * ✅ CORRIGÉ — onGenererTicket
     *
     * Après création du ticket :
     *   1. recalculerPaiement() → sauvegarde en base
     *   2. refreshTicketsHeader() → met à jour l'affichage du paiement IMMÉDIATEMENT
     *   3. reloadTickets() → affiche le nouveau ticket dans la liste
     *   4. refreshPlacesInfo() → met à jour les places disponibles
     *   5. reloadInscriptions() → met à jour la ligne inscription en arrière-plan
     */
    @FXML
    public void onGenererTicket() {
        if (currentInscription == null) { showWarning("Aucune inscription sélectionnée."); return; }
        if (currentEvent == null)        { showWarning("Événement introuvable."); return; }

        // Vérifie les places disponibles AVANT de créer
        int ticketsVendus = ticketService.countByEventId(currentEvent.getId());
        int max = currentEvent.getCapaciteMax();
        if (ticketsVendus >= max) {
            showWarning("Impossible : l'événement est complet (" + max + "/" + max + " places).");
            return;
        }

        try {
            // ✅ createForInscription() sans blocage "1 ticket max" (après fix SQL)
            ticketService.createForInscription(currentInscription.getId());

            // ✅ Recalcule paiement = prix × nb tickets → sauvegarde en base
            recalculerPaiement(currentInscription);

            // ✅ Met à jour le header du panneau 3 IMMÉDIATEMENT (sans changer de panneau)
            refreshTicketsHeader();

            reloadTickets();       // rafraîchit la liste des tickets
            refreshPlacesInfo();   // places = capaciteMax − total tickets
            reloadInscriptions();  // met à jour la ligne inscription en arrière-plan

        } catch (Exception ex) {
            showError("Erreur", "Génération impossible", ex.getMessage());
        }
    }

    /**
     * ✅ NOUVEAU — Rafraîchit le label paiement dans le header du panneau 3
     * sans changer de panneau ni recharger toute la page.
     *
     * Lit le nombre de tickets en temps réel depuis la DB pour calculer
     * le nouveau paiement et met à jour ticketEventInfo immédiatement.
     */
    private void refreshTicketsHeader() {
        if (currentInscription == null || currentEvent == null) return;

        // Recalcule le paiement en temps réel
        int nbTickets  = ticketService.countByInscriptionId(currentInscription.getId());
        float paiement = (float)(currentEvent.getPrix() * nbTickets);

        // ✅ Met à jour le label dans le panneau 3 → visible IMMÉDIATEMENT
        ticketEventInfo.setText(
                "Événement : " + currentEvent.getTitre()
                        + "   •   💰 Paiement : " + paiement + " TND"
                        + "   (" + nbTickets + " ticket(s) × " + currentEvent.getPrix() + " TND)"
        );
    }

    /**
     * Recalcule et sauvegarde le paiement en base.
     * paiement = prix_événement × nb_tickets_de_cette_inscription
     */
    private void recalculerPaiement(Inscription ins) {
        if (ins == null || currentEvent == null) return;
        int nbTickets  = ticketService.countByInscriptionId(ins.getId());
        float paiement = (float)(currentEvent.getPrix() * nbTickets);
        inscriptionService.updatePaiementFloat(ins.getId(), paiement);
    }

    // ═══════════════════════════════════════════════════════════
    //  CALENDRIER — Vue mensuelle des événements
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void onToggleCalendar() {
        calCurrentMonth = YearMonth.now();
        calViewMode = "month";
        updateViewToggle();
        renderCalendar();
        showPanel(panelCalendar);
        // Update Notion status indicator
        if (calNotionStatus != null) {
            calNotionStatus.setText(notionService.isConfigured() ? "☁ Notion connecté" : "☁ Notion —");
        }
    }

    @FXML
    public void onRetourFromCalendar() {
        showPanelEvents();
    }

    // ═══════════════════════════════════════════════════════════
    //  PANNEAU STATS — Intérêts des utilisateurs
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void onShowStats() {
        showPanel(panelStats);
        loadStatsData();
    }

    @FXML
    public void onRetourFromStats() {
        showPanelEvents();
    }

    private void loadStatsData() {
        if (statsContainer == null) return;
        statsContainer.getChildren().clear();

        // Loading message
        Label loadingLabel = new Label("🤖 Analyse des intérêts des utilisateurs en cours...");
        loadingLabel.setStyle("-fx-text-fill: #1a4a7a; -fx-font-size: 14px; -fx-font-weight: 800;");
        statsContainer.getChildren().add(loadingLabel);

        new Thread(() -> {
            try {
                // Analyser les intérêts de tous les users
                java.util.Map<String, Integer> globalInterests =
                        recommendationService.analyzeAllUsersInterests(allUsers);

                // Stats sur les inscriptions par événement
                java.util.Map<String, Integer> eventPopularity = new java.util.LinkedHashMap<>();
                for (Evenement ev : masterList) {
                    int count = inscriptionService.countByEvent(ev.getId());
                    if (count > 0) {
                        eventPopularity.put(truncateText(safeStr(ev.getTitre()), 25), count);
                    }
                }
                // Trier par popularité
                java.util.Map<String, Integer> sortedPopularity = eventPopularity.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(10)
                        .collect(java.util.stream.Collectors.toMap(
                                java.util.Map.Entry::getKey, java.util.Map.Entry::getValue,
                                (a, b) -> a, java.util.LinkedHashMap::new));

                // Stats générales
                int totalUsers = allUsers.size();
                int totalEvents = masterList.size();
                int totalInscriptions = masterList.stream()
                        .mapToInt(e -> inscriptionService.countByEvent(e.getId())).sum();
                long usersWithInscriptions = allUsers.stream()
                        .filter(u -> masterList.stream()
                                .anyMatch(ev -> inscriptionService.existsForUser(ev.getId(), u.getId())))
                        .count();
                double engagementRate = totalUsers > 0
                        ? (double) usersWithInscriptions / totalUsers * 100 : 0;

                javafx.application.Platform.runLater(() ->
                        renderStats(globalInterests, sortedPopularity,
                                totalUsers, totalEvents, totalInscriptions, engagementRate));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statsContainer.getChildren().clear();
                    Label err = new Label("❌ Erreur: " + e.getMessage());
                    err.setStyle("-fx-text-fill: #e53e3e; -fx-font-weight: 800;");
                    statsContainer.getChildren().add(err);
                });
            }
        }).start();
    }

    private void renderStats(java.util.Map<String, Integer> interests,
                             java.util.Map<String, Integer> popularity,
                             int totalUsers, int totalEvents,
                             int totalInscriptions, double engagementRate) {
        statsContainer.getChildren().clear();

        // ═══ KPI Cards ═══
        HBox kpiRow = new HBox(14);
        kpiRow.setAlignment(Pos.CENTER_LEFT);
        kpiRow.getChildren().addAll(
                buildStatKpi("👥", String.valueOf(totalUsers), "Utilisateurs"),
                buildStatKpi("🎉", String.valueOf(totalEvents), "Événements"),
                buildStatKpi("📝", String.valueOf(totalInscriptions), "Inscriptions"),
                buildStatKpi("📊", String.format("%.1f%%", engagementRate), "Taux d'engagement")
        );
        statsContainer.getChildren().add(kpiRow);

        // ═══ Section Intérêts ═══
        VBox interestsSection = new VBox(10);
        interestsSection.setPadding(new Insets(16, 0, 0, 0));

        Label interestsTitle = new Label("🎯 Centres d'intérêt déduits (analyse des inscriptions)");
        interestsTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #163a5c;");
        interestsSection.getChildren().add(interestsTitle);

        if (interests.isEmpty()) {
            Label noData = new Label("Pas encore assez de données d'inscriptions pour analyser les intérêts.");
            noData.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700; -fx-font-size: 12px;");
            interestsSection.getChildren().add(noData);
        } else {
            int maxVal = interests.values().stream().mapToInt(v -> v).max().orElse(1);
            for (var entry : interests.entrySet()) {
                HBox bar = buildBarRow(entry.getKey(), entry.getValue(), maxVal, "#1a4a7a");
                interestsSection.getChildren().add(bar);
            }
        }
        statsContainer.getChildren().add(interestsSection);

        // ═══ Section Popularité Événements ═══
        VBox popSection = new VBox(10);
        popSection.setPadding(new Insets(16, 0, 0, 0));

        Label popTitle = new Label("🔥 Événements les plus populaires");
        popTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #163a5c;");
        popSection.getChildren().add(popTitle);

        if (popularity.isEmpty()) {
            Label noData = new Label("Aucune inscription enregistrée.");
            noData.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: 700; -fx-font-size: 12px;");
            popSection.getChildren().add(noData);
        } else {
            int maxPop = popularity.values().stream().mapToInt(v -> v).max().orElse(1);
            for (var entry : popularity.entrySet()) {
                HBox bar = buildBarRow(entry.getKey(), entry.getValue(), maxPop, "#1a73e8");
                popSection.getChildren().add(bar);
            }
        }
        statsContainer.getChildren().add(popSection);

        // ═══ Info API ═══
        VBox apiInfo = new VBox(6);
        apiInfo.setPadding(new Insets(16, 0, 0, 0));
        Label apiTitle = new Label("⚙️ Configuration IA");
        apiTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 900; -fx-text-fill: #163a5c;");
        Label apiStatus = new Label(recommendationService.isApiKeyConfigured()
                ? "✅ API Gemini configurée — Recommandations IA actives"
                : "⚠️ Clé Gemini non configurée — Mode algorithme local actif\n"
                  + "Pour activer l'IA : modifiez GEMINI_API_KEY dans RecommendationService.java");
        apiStatus.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: "
                + (recommendationService.isApiKeyConfigured() ? "#137333" : "#E37400") + ";");
        apiStatus.setWrapText(true);
        apiInfo.getChildren().addAll(apiTitle, apiStatus);
        statsContainer.getChildren().add(apiInfo);
    }

    private VBox buildStatKpi(String icon, String value, String label) {
        VBox kpi = new VBox(4);
        kpi.setAlignment(Pos.CENTER);
        kpi.setStyle("-fx-background-color: white; -fx-background-radius: 14; "
                + "-fx-border-color: rgba(15,23,42,0.06); -fx-border-radius: 14; "
                + "-fx-padding: 14 20; -fx-pref-width: 170;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0.1, 0, 3);");
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 24px;");
        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #163a5c;");
        Label lblLbl = new Label(label);
        lblLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #94a3b8;");
        kpi.getChildren().addAll(iconLbl, valLbl, lblLbl);
        HBox.setHgrow(kpi, Priority.ALWAYS);
        return kpi;
    }

    private HBox buildBarRow(String label, int value, int max, String color) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));

        Label nameLbl = new Label(label);
        nameLbl.setMinWidth(140);
        nameLbl.setMaxWidth(140);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #163a5c;");

        double pct = max > 0 ? (double) value / max : 0;
        StackPane barWrap = new StackPane();
        barWrap.setMinHeight(22);
        barWrap.setMaxHeight(22);
        HBox.setHgrow(barWrap, Priority.ALWAYS);

        Region barBg = new Region();
        barBg.setStyle("-fx-background-color: rgba(15,23,42,0.05); -fx-background-radius: 6;");
        barBg.setMaxWidth(Double.MAX_VALUE);

        Region barFill = new Region();
        barFill.setMaxWidth(Double.MAX_VALUE);
        barFill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6; -fx-opacity: 0.85;");

        barWrap.getChildren().addAll(barBg, barFill);
        StackPane.setAlignment(barFill, Pos.CENTER_LEFT);

        // Bind fill width to percentage of container
        barWrap.widthProperty().addListener((obs, o, w) -> {
            barFill.setMaxWidth(Math.max(4, w.doubleValue() * pct));
        });

        Label valLbl = new Label(String.valueOf(value));
        valLbl.setMinWidth(35);
        valLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 900; -fx-text-fill: " + color + ";");

        row.getChildren().addAll(nameLbl, barWrap, valLbl);
        return row;
    }

    private String truncateText(String s, int max) {
        return (s == null || s.length() <= max) ? (s == null ? "" : s)
                : s.substring(0, max - 1) + "…";
    }

    // ═══════════════════════════════════════════════════════════
    //  CALENDRIER ICS — Export gratuit (RFC 5545)
    // ═══════════════════════════════════════════════════════════

    /**
     * Exporter TOUS les événements au format ICS.
     * L'utilisateur choisit le fichier de destination, puis le fichier
     * s'ouvre automatiquement dans son calendrier (Google, Outlook, Apple…).
     */
    @FXML
    public void onExportAllIcs() {
        List<Evenement> events = new ArrayList<>(masterList);
        if (events.isEmpty()) {
            showWarning("Aucun événement à exporter.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter tous les événements (.ics)");
        fc.setInitialFileName("fintokhrej_evenements.ics");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichier iCalendar (*.ics)", "*.ics"));
        Stage stage = (Stage) panelCalendar.getScene().getWindow();
        File file = fc.showSaveDialog(stage);

        if (file != null) {
            ICalendarService.ExportResult result = icsService.exportEvents(
                    events, lieuId -> resolveLieuName(lieuId), file);

            if (result.success) {
                showInfo("Export ICS",
                        "✅ " + result.exported + " événement(s) exporté(s)",
                        "Fichier : " + result.filePath
                        + "\n\nLe fichier va s'ouvrir dans votre calendrier…");
                // Ouvrir automatiquement dans l'app calendrier par défaut
                icsService.openWithDefaultApp(file);
            } else {
                showError("Export ICS", "Erreur d'export", result.error);
            }
        }
    }

    /**
     * Exporter les événements du MOIS affiché au format ICS.
     */
    @FXML
    public void onExportMonthIcs() {
        LocalDateTime monthStart = calCurrentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = calCurrentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Evenement> monthEvents = masterList.stream()
                .filter(e -> {
                    if (e.getDateDebut() == null) return false;
                    LocalDateTime d = e.getDateDebut();
                    LocalDateTime fin = e.getDateFin() != null ? e.getDateFin() : d;
                    return !(fin.isBefore(monthStart) || d.isAfter(monthEnd));
                })
                .collect(Collectors.toList());

        if (monthEvents.isEmpty()) {
            showWarning("Aucun événement trouvé pour "
                    + calCurrentMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL, LOCALE_FR)
                    + " " + calCurrentMonth.getYear() + ".");
            return;
        }

        String monthName = calCurrentMonth.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, LOCALE_FR);

        FileChooser fc = new FileChooser();
        fc.setTitle("Exporter les événements de " + monthName);
        fc.setInitialFileName("fintokhrej_" + monthName + "_" + calCurrentMonth.getYear() + ".ics");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichier iCalendar (*.ics)", "*.ics"));
        Stage stage = (Stage) panelCalendar.getScene().getWindow();
        File file = fc.showSaveDialog(stage);

        if (file != null) {
            ICalendarService.ExportResult result = icsService.exportEvents(
                    monthEvents, lieuId -> resolveLieuName(lieuId), file);

            if (result.success) {
                showInfo("Export ICS — " + monthName,
                        "✅ " + result.exported + " événement(s) exporté(s)",
                        "Fichier : " + result.filePath
                        + "\n\nLe fichier va s'ouvrir dans votre calendrier…");
                icsService.openWithDefaultApp(file);
            } else {
                showError("Export ICS", "Erreur d'export", result.error);
            }
        }
    }

    /**
     * Exporter un événement UNIQUE en .ics et l'ouvrir immédiatement
     * dans le calendrier par défaut du système.
     */
    private void exportSingleEventIcs(Evenement ev) {
        try {
            // Créer un fichier temporaire .ics
            String safeName = safeStr(ev.getTitre()).replaceAll("[^a-zA-Z0-9àâéèêëîïôùûüÀÂÉÈÊËÎÏÔÙÛÜ ]", "_");
            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "fintokhrej_ics");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            File icsFile = new File(tmpDir, safeName + ".ics");

            String lieuName = resolveLieuName(ev.getLieuId());
            boolean ok = icsService.exportEvent(ev, lieuName, icsFile);

            if (ok) {
                icsService.openWithDefaultApp(icsFile);
            } else {
                showError("Export ICS", "Erreur",
                        "Impossible d'exporter l'événement « " + safeStr(ev.getTitre()) + " »");
            }
        } catch (Exception e) {
            showError("Export ICS", "Erreur", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  NOTION API — Synchronisation gratuite vers le cloud
    // ═══════════════════════════════════════════════════════════

    /**
     * Ouvre un dialog pour configurer la connexion Notion
     * (token API + ID base de données).
     */
    @FXML
    public void onConfigureNotion() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("⚙️ Configurer Notion Calendar");

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setMaxWidth(560);
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("🔗 Connexion à Notion (100% gratuit)");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label instructions = new Label(
                "1. Créez un compte gratuit sur notion.so\n"
                + "2. Allez sur notion.so/my-integrations\n"
                + "3. Créez une intégration → copiez le Token\n"
                + "4. Créez une base de données dans Notion\n"
                + "5. Partagez-la avec votre intégration\n"
                + "6. Copiez l'ID de la base depuis l'URL");
        instructions.setWrapText(true);
        instructions.setStyle("-fx-font-size: 13px; -fx-text-fill: #5f6368; -fx-line-spacing: 2;");

        Label lblToken = new Label("🔑 Token API (commence par ntn_...)");
        lblToken.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField tfToken = new TextField();
        tfToken.setPromptText("ntn_xxxxxxxxxxxxxxxxxxxx");
        tfToken.setStyle("-fx-font-size: 13px; -fx-padding: 8;");
        if (notionService.getApiToken() != null) tfToken.setText(notionService.getApiToken());

        Label lblDb = new Label("📋 ID de la base de données (32 caractères)");
        lblDb.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        TextField tfDb = new TextField();
        tfDb.setPromptText("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        tfDb.setStyle("-fx-font-size: 13px; -fx-padding: 8;");
        if (notionService.getDatabaseId() != null) tfDb.setText(notionService.getDatabaseId());

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 13px;");

        Button btnTest = new Button("🔍 Tester la connexion");
        btnTest.setStyle("-fx-background-color: #4285F4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 8; -fx-cursor: hand;");

        Button btnSave = new Button("✅ Enregistrer");
        btnSave.setStyle("-fx-background-color: #34A853; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 8; -fx-cursor: hand;");

        Button btnCancel = new Button("Annuler");
        btnCancel.setStyle("-fx-background-color: #f1f3f4; -fx-text-fill: #3c4043; -fx-padding: 8 20; -fx-background-radius: 8; -fx-cursor: hand;");

        btnTest.setOnAction(ev -> {
            String token = tfToken.getText().trim();
            String dbId = tfDb.getText().trim();
            if (token.isEmpty() || dbId.isEmpty()) {
                statusLabel.setText("❌ Veuillez remplir les deux champs.");
                statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #EA4335;");
                return;
            }
            notionService.configure(token, dbId);
            statusLabel.setText("⏳ Test en cours...");
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5f6368;");

            javafx.concurrent.Task<Boolean> testTask = new javafx.concurrent.Task<>() {
                @Override protected Boolean call() { return notionService.testConnection(); }
            };
            testTask.setOnSucceeded(e -> {
                if (testTask.getValue()) {
                    statusLabel.setText("✅ Connexion réussie ! Base de données trouvée.");
                    statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #34A853; -fx-font-weight: bold;");
                } else {
                    statusLabel.setText("❌ Échec: " + notionService.getLastError());
                    statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #EA4335;");
                }
            });
            testTask.setOnFailed(e -> {
                statusLabel.setText("❌ Erreur: " + testTask.getException().getMessage());
                statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #EA4335;");
            });
            new Thread(testTask).start();
        });

        btnSave.setOnAction(ev -> {
            String token = tfToken.getText().trim();
            String dbId = tfDb.getText().trim();
            notionService.configure(token, dbId);
            dialog.close();

            // Sauvegarder dans un fichier local pour ne pas re-saisir
            saveNotionConfig(token, dbId);

            showInfo("Notion configuré", "✅ Configuration enregistrée",
                    "Vous pouvez maintenant synchroniser vos événements vers Notion.");
        });

        btnCancel.setOnAction(ev -> dialog.close());

        HBox buttons = new HBox(10, btnTest, btnSave, btnCancel);
        buttons.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(title, instructions, lblToken, tfToken, lblDb, tfDb, statusLabel, buttons);

        Scene scene = new Scene(root, 540, 480);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    /**
     * Synchroniser TOUS les événements vers Notion.
     */
    @FXML
    public void onSyncToNotion() {
        // Charger la config sauvegardée si pas encore configuré
        if (!notionService.isConfigured()) {
            loadNotionConfig();
        }

        if (!notionService.isConfigured()) {
            showWarning("Notion n'est pas configuré.\n"
                    + "Cliquez sur ⚙️ Configurer pour entrer votre token et l'ID de la base.");
            return;
        }

        List<Evenement> events = new ArrayList<>(masterList);
        if (events.isEmpty()) {
            showWarning("Aucun événement à synchroniser.");
            return;
        }

        // Confirmation
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Synchronisation Notion");
        confirm.setHeaderText("Synchroniser " + events.size() + " événement(s) vers Notion ?");
        confirm.setContentText("Les événements seront synchronisés intelligemment :\n"
                + "• Nouveaux → créés\n"
                + "• Modifiés → mis à jour\n"
                + "• Supprimés → retirés de Notion\n"
                + "Aucun doublon ne sera créé.");
        if (!confirm.showAndWait().map(r -> r == ButtonType.OK).orElse(false)) return;

        // Show a progress alert
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle("Synchronisation en cours...");
        progressAlert.setHeaderText("⏳ Synchronisation vers Notion...");
        progressAlert.setContentText("Veuillez patienter...");
        progressAlert.getButtonTypes().setAll(ButtonType.CANCEL);
        progressAlert.show();

        // ALL Notion work happens in the background thread
        Thread syncThread = new Thread(() -> {
            try {
                // Step 1: Test connection + prepare DB schema
                System.out.println("[Notion] === SYNC THREAD START ===");
                boolean connected = notionService.testConnection();
                if (!connected) {
                    String err = notionService.getLastError();
                    javafx.application.Platform.runLater(() -> {
                        progressAlert.close();
                        showError("Notion", "❌ Connexion impossible",
                                "Erreur : " + err + "\n\nVérifiez le token et l'ID de la base.");
                    });
                    return;
                }

                // Step 2: Sync
                System.out.println("[Notion] === Connection OK, starting sync ===");
                NotionCalendarService.SyncResult result = notionService.syncAll(events, lieuId -> resolveLieuName(lieuId));
                System.out.println("[Notion] === SYNC THREAD DONE: " + result + " ===");

                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();
                    StringBuilder msg = new StringBuilder();
                    if (result.created > 0)
                        msg.append("✅ ").append(result.created).append(" événement(s) créé(s)\n");
                    if (result.updated > 0)
                        msg.append("✏️ ").append(result.updated).append(" événement(s) mis à jour\n");
                    if (result.deleted > 0)
                        msg.append("🗑️ ").append(result.deleted).append(" orphelin(s) supprimé(s) de Notion\n");
                    if (result.created == 0 && result.updated == 0 && result.deleted == 0 && result.failed == 0)
                        msg.append("✅ Tout est déjà à jour — aucun changement\n");
                    if (result.failed > 0) {
                        msg.append("\n❌ ").append(result.failed).append(" échec(s)\n");
                        if (!result.errors.isEmpty()) {
                            msg.append("\nDétails des erreurs:\n");
                            for (int i = 0; i < Math.min(result.errors.size(), 10); i++) {
                                msg.append("• ").append(result.errors.get(i)).append("\n");
                            }
                        }
                    }
                    msg.append("\n📋 Retrouvez vos événements sur notion.so");
                    showInfo("Synchronisation Notion", result.toString(), msg.toString());
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("[Notion] === SYNC THREAD EXCEPTION: " + ex.getMessage() + " ===");
                javafx.application.Platform.runLater(() -> {
                    progressAlert.close();
                    showError("Notion", "❌ Erreur de synchronisation",
                            "Exception: " + ex.getClass().getSimpleName() + "\n" + ex.getMessage());
                });
            }
        });
        syncThread.setDaemon(true);
        syncThread.start();
    }
    /**
     * Vider la base Notion (archiver toutes les pages).
     */
    @FXML
    public void onClearNotion() {
        if (!notionService.isConfigured()) {
            loadNotionConfig();
        }
        if (!notionService.isConfigured()) {
            showWarning("Notion n'est pas configuré.");
            return;
        }

        if (!confirmDelete("Vider la base Notion ?\nTous les événements dans Notion seront archivés.")) {
            return;
        }

        javafx.concurrent.Task<Integer> clearTask = new javafx.concurrent.Task<>() {
            @Override protected Integer call() { return notionService.clearAll(); }
        };
        clearTask.setOnSucceeded(e -> {
            showInfo("Notion", "Base vidée",
                    "🗑️ " + clearTask.getValue() + " événement(s) archivé(s) dans Notion.");
        });
        clearTask.setOnFailed(e -> showError("Notion", "Erreur", "Impossible de vider la base."));
        new Thread(clearTask).start();
    }

    // -- Persistence locale de la config Notion --

    private void saveNotionConfig(String token, String dbId) {
        try {
            java.io.File dir = new java.io.File(System.getProperty("user.home"), ".fintokhrej");
            if (!dir.exists()) dir.mkdirs();
            java.io.File configFile = new java.io.File(dir, "notion_config.txt");
            java.nio.file.Files.writeString(configFile.toPath(), token + "\n" + dbId);
            System.out.println("[Notion] Config sauvegardée dans " + configFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[Notion] Erreur sauvegarde config: " + e.getMessage());
        }
    }

    private void loadNotionConfig() {
        try {
            java.io.File configFile = new java.io.File(
                    System.getProperty("user.home") + "/.fintokhrej/notion_config.txt");
            if (configFile.exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath());
                if (lines.size() >= 2) {
                    notionService.configure(lines.get(0), lines.get(1));
                    System.out.println("[Notion] Config chargée depuis " + configFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.err.println("[Notion] Erreur chargement config: " + e.getMessage());
        }
    }

    private void showInfo(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(header); alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void onPrevMonth() {
        calCurrentMonth = calCurrentMonth.minusMonths(1);
        renderCalendar();
    }

    @FXML
    public void onNextMonth() {
        calCurrentMonth = calCurrentMonth.plusMonths(1);
        renderCalendar();
    }

    @FXML
    public void onCalendarToday() {
        calCurrentMonth = YearMonth.now();
        renderCalendar();
    }

    private void renderCalendar() {
        if (calendarContainer == null) return;
        calendarContainer.getChildren().clear();

        // Update month label
        String monthName = calCurrentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE_FR);
        monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
        calMonthLabel.setText(monthName + " " + calCurrentMonth.getYear());

        // Assign stable color to each event by id
        java.util.Map<Integer, Integer> eventColorMap = new java.util.HashMap<>();
        int colorIdx = 0;
        for (Evenement ev : masterList) {
            if (!eventColorMap.containsKey(ev.getId())) {
                eventColorMap.put(ev.getId(), colorIdx % CAL_COLORS.length);
                colorIdx++;
            }
        }

        switch (calViewMode) {
            case "week":  renderWeekView(eventColorMap); break;
            case "day":   renderDayView(eventColorMap); break;
            default:      renderMonthView(eventColorMap); break;
        }
    }

    // ── View toggle handlers ──

    @FXML
    public void onViewMonth() {
        calViewMode = "month";
        updateViewToggle();
        renderCalendar();
    }

    @FXML
    public void onViewWeek() {
        calViewMode = "week";
        updateViewToggle();
        renderCalendar();
    }

    @FXML
    public void onViewDay() {
        calViewMode = "day";
        updateViewToggle();
        renderCalendar();
    }

    private void updateViewToggle() {
        if (btnViewMonth == null) return;
        btnViewMonth.getStyleClass().remove("cal-toggle-active");
        btnViewWeek.getStyleClass().remove("cal-toggle-active");
        btnViewDay.getStyleClass().remove("cal-toggle-active");
        switch (calViewMode) {
            case "month": btnViewMonth.getStyleClass().add("cal-toggle-active"); break;
            case "week":  btnViewWeek.getStyleClass().add("cal-toggle-active"); break;
            case "day":   btnViewDay.getStyleClass().add("cal-toggle-active"); break;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MONTH VIEW — Modern Pink Grid
    // ═══════════════════════════════════════════════════════════

    private void renderMonthView(java.util.Map<Integer, Integer> eventColorMap) {
        // Day headers
        GridPane header = new GridPane();
        header.getStyleClass().add("cal-m-header");
        String[] dayAbbr = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            header.getColumnConstraints().add(cc);
            Label lbl = new Label(dayAbbr[i]);
            lbl.getStyleClass().add(i == 6 ? "cal-m-day-hdr-sun" : "cal-m-day-hdr");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            header.add(lbl, i, 0);
        }
        calendarContainer.getChildren().add(header);

        // Grid
        GridPane grid = new GridPane();
        grid.getStyleClass().add("cal-m-grid");
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        LocalDate firstOfMonth = calCurrentMonth.atDay(1);
        int offset = firstOfMonth.getDayOfWeek().getValue() - 1;
        int daysInMonth = calCurrentMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        // Collect month events
        LocalDateTime monthStart = calCurrentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = calCurrentMonth.atEndOfMonth().atTime(23, 59, 59);
        List<Evenement> monthEvents = masterList.stream()
                .filter(e -> {
                    if (e.getDateDebut() == null) return false;
                    LocalDateTime d = e.getDateDebut();
                    LocalDateTime fin = e.getDateFin() != null ? e.getDateFin() : d;
                    return !(fin.isBefore(monthStart) || d.isAfter(monthEnd));
                })
                .collect(Collectors.toList());

        int totalCells = offset + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int cellIdx = row * 7 + col;
                int dayNum = cellIdx - offset + 1;

                VBox cell = new VBox(2);
                cell.getStyleClass().add("cal-m-cell");
                cell.setMinHeight(110);
                cell.setPrefHeight(120);

                boolean isCurrent = dayNum >= 1 && dayNum <= daysInMonth;
                LocalDate cellDate;
                int displayDay;

                if (dayNum < 1) {
                    YearMonth prev = calCurrentMonth.minusMonths(1);
                    displayDay = prev.lengthOfMonth() + dayNum;
                    cellDate = prev.atDay(displayDay);
                    cell.getStyleClass().add("cal-m-cell-outside");
                } else if (dayNum > daysInMonth) {
                    displayDay = dayNum - daysInMonth;
                    cellDate = calCurrentMonth.plusMonths(1).atDay(displayDay);
                    cell.getStyleClass().add("cal-m-cell-outside");
                } else {
                    displayDay = dayNum;
                    cellDate = calCurrentMonth.atDay(dayNum);
                }

                if (col == 6) cell.getStyleClass().add("cal-m-cell-sun");

                // Day number
                HBox dayRow = new HBox();
                dayRow.setAlignment(Pos.CENTER_LEFT);
                dayRow.setPadding(new Insets(6, 8, 2, 8));

                Label numLabel = new Label(String.valueOf(displayDay));
                if (isCurrent && cellDate.equals(today)) {
                    numLabel.getStyleClass().add("cal-m-today");
                    cell.getStyleClass().add("cal-m-cell-today");
                } else {
                    numLabel.getStyleClass().add(isCurrent ? "cal-m-day-num" : "cal-m-day-num-muted");
                }
                dayRow.getChildren().add(numLabel);

                // "+" add button for current-month days
                if (isCurrent) {
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    final LocalDate addDate = cellDate;
                    Button btnAddEv = new Button("+");
                    btnAddEv.setStyle("-fx-background-color: #1a4a7a; -fx-text-fill: white; " +
                            "-fx-font-size: 11px; -fx-font-weight: 900; -fx-background-radius: 999; " +
                            "-fx-min-width: 22; -fx-min-height: 22; -fx-max-width: 22; -fx-max-height: 22; " +
                            "-fx-padding: 0; -fx-cursor: hand; -fx-opacity: 0.0;");
                    btnAddEv.setOnAction(ev -> openEditor(null, addDate));
                    // Show on hover
                    cell.setOnMouseEntered(me -> btnAddEv.setStyle(btnAddEv.getStyle().replace("-fx-opacity: 0.0", "-fx-opacity: 1.0")));
                    cell.setOnMouseExited(me -> btnAddEv.setStyle(btnAddEv.getStyle().replace("-fx-opacity: 1.0", "-fx-opacity: 0.0")));
                    dayRow.getChildren().addAll(spacer, btnAddEv);
                }

                cell.getChildren().add(dayRow);

                // Events for this day
                if (isCurrent) {
                    final LocalDate fDate = cellDate;
                    List<Evenement> dayEvs = monthEvents.stream()
                            .filter(e -> {
                                LocalDate s = e.getDateDebut().toLocalDate();
                                LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                                return !fDate.isBefore(s) && !fDate.isAfter(en);
                            })
                            .collect(Collectors.toList());

                    VBox evBox = new VBox(2);
                    evBox.setPadding(new Insets(0, 4, 4, 4));
                    int maxShow = 3;

                    for (int idx = 0; idx < Math.min(dayEvs.size(), maxShow); idx++) {
                        Evenement ev = dayEvs.get(idx);
                        int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                        HBox chip = buildEventChip(ev, ci);
                        evBox.getChildren().add(chip);
                    }

                    if (dayEvs.size() > maxShow) {
                        Label more = new Label("+" + (dayEvs.size() - maxShow) + " autres");
                        more.getStyleClass().add("cal-m-more");
                        final List<Evenement> allDayEvs = dayEvs;
                        more.setOnMouseClicked(me -> {
                            me.consume();
                            showDayEventsPopup(fDate, allDayEvs);
                        });
                        evBox.getChildren().add(more);
                    }

                    cell.getChildren().add(evBox);
                    VBox.setVgrow(evBox, Priority.ALWAYS);

                    if (!dayEvs.isEmpty()) {
                        final List<Evenement> allDayEvs = dayEvs;
                        cell.setOnMouseClicked(me -> {
                            if (me.getTarget() == cell || me.getTarget() instanceof Region) {
                                showDayEventsPopup(fDate, allDayEvs);
                            }
                        });
                    } else {
                        // Empty day → click opens the creation form with this date
                        cell.setStyle(cell.getStyle() + "-fx-cursor: hand;");
                        cell.setOnMouseClicked(me -> openEditor(null, fDate));
                    }
                }

                grid.add(cell, col, row);
            }
        }
        calendarContainer.getChildren().add(grid);
    }

    /** Build a colorful event chip with time + title */
    private HBox buildEventChip(Evenement ev, int colorIndex) {
        String bgColor = CAL_COLORS[colorIndex % CAL_COLORS.length][0];
        String textColor = CAL_COLORS[colorIndex % CAL_COLORS.length][1];

        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chip, Priority.ALWAYS);
        chip.setStyle("-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: 6; -fx-padding: 3 8; -fx-cursor: hand;");

        // Time label
        String timeStr = ev.getDateDebut() != null
                ? ev.getDateDebut().format(DateTimeFormatter.ofPattern("HH:mm"))
                : "";
        Label timeLbl = new Label(timeStr);
        timeLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");

        // Title label
        Label titleLbl = new Label(truncate(safeStr(ev.getTitre()), 12));
        titleLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: " + textColor + ";");
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        chip.getChildren().addAll(timeLbl, titleLbl);

        // Tooltip
        String tp = "📌 " + safeStr(ev.getTitre())
                + "\n📅 " + formatLDT(ev.getDateDebut()) + " → " + formatLDT(ev.getDateFin())
                + "\n📍 " + resolveLieuName(ev.getLieuId())
                + "\n💰 " + ev.getPrix() + " TND  •  " + safeStr(ev.getStatut());
        Tooltip tooltip = new Tooltip(tp);
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setStyle("-fx-font-size: 12px; -fx-font-family: 'Segoe UI'; -fx-background-radius: 8;");
        Tooltip.install(chip, tooltip);

        // Click → show detail panel
        chip.setOnMouseClicked(me -> {
            me.consume();
            showEventDetail(ev, colorIndex);
        });

        return chip;
    }

    // ═══════════════════════════════════════════════════════════
    //  WEEK VIEW — List of days for current week
    // ═══════════════════════════════════════════════════════════

    private void renderWeekView(java.util.Map<Integer, Integer> eventColorMap) {
        // Determine the week containing the 1st of current month (or today)
        LocalDate ref = calCurrentMonth.atDay(Math.min(LocalDate.now().getDayOfMonth(), calCurrentMonth.lengthOfMonth()));
        if (!ref.getMonth().equals(calCurrentMonth.getMonth())) ref = calCurrentMonth.atDay(1);
        LocalDate weekStart = ref.with(DayOfWeek.MONDAY);

        VBox weekContainer = new VBox(0);
        weekContainer.getStyleClass().add("cal-w-container");

        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            final LocalDate fDay = day;
            boolean isToday = day.equals(LocalDate.now());

            VBox dayBox = new VBox(6);
            dayBox.getStyleClass().add("cal-w-day");
            if (isToday) dayBox.getStyleClass().add("cal-w-day-today");
            dayBox.setPadding(new Insets(12, 16, 12, 16));

            // Day header row
            HBox dayHeader = new HBox(10);
            dayHeader.setAlignment(Pos.CENTER_LEFT);
            String dayName = day.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_FR);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            Label dayTitle = new Label(dayName + " " + day.getDayOfMonth());
            dayTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: " + (isToday ? "#1a4a7a" : "#1e293b") + ";");
            if (isToday) {
                Label todayBadge = new Label("Aujourd'hui");
                todayBadge.setStyle("-fx-background-color: #1a4a7a; -fx-text-fill: white; " +
                        "-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 8; -fx-background-radius: 999;");
                dayHeader.getChildren().addAll(dayTitle, todayBadge);
            } else {
                dayHeader.getChildren().add(dayTitle);
            }
            // "+" add button
            Region wSpacer = new Region();
            HBox.setHgrow(wSpacer, Priority.ALWAYS);
            Button wBtnAdd = new Button("+");
            wBtnAdd.setStyle("-fx-background-color: #1a4a7a; -fx-text-fill: white; " +
                    "-fx-font-size: 13px; -fx-font-weight: 900; -fx-background-radius: 999; " +
                    "-fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; " +
                    "-fx-padding: 0; -fx-cursor: hand;");
            wBtnAdd.setOnAction(ev -> openEditor(null, fDay));
            dayHeader.getChildren().addAll(wSpacer, wBtnAdd);
            dayBox.getChildren().add(dayHeader);

            // Events for this day
            List<Evenement> dayEvs = masterList.stream()
                    .filter(e -> {
                        if (e.getDateDebut() == null) return false;
                        LocalDate s = e.getDateDebut().toLocalDate();
                        LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                        return !fDay.isBefore(s) && !fDay.isAfter(en);
                    })
                    .collect(Collectors.toList());

            if (dayEvs.isEmpty()) {
                Label noEv = new Label("Aucun événement — cliquez pour ajouter");
                noEv.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic; -fx-cursor: hand;");
                noEv.setOnMouseClicked(me -> openEditor(null, fDay));
                dayBox.getChildren().add(noEv);
            } else {
                for (Evenement ev : dayEvs) {
                    int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                    HBox card = buildWeekEventCard(ev, ci);
                    dayBox.getChildren().add(card);
                }
            }

            weekContainer.getChildren().add(dayBox);
        }
        calendarContainer.getChildren().add(weekContainer);
    }

    private HBox buildWeekEventCard(Evenement ev, int colorIndex) {
        String bgColor = CAL_COLORS[colorIndex % CAL_COLORS.length][0];
        String textColor = CAL_COLORS[colorIndex % CAL_COLORS.length][1];

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10; " +
                "-fx-padding: 10 16; -fx-cursor: hand;");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);

        // Color bar
        Region colorBar = new Region();
        colorBar.setMinWidth(4);
        colorBar.setPrefWidth(4);
        colorBar.setMinHeight(36);
        colorBar.setStyle("-fx-background-color: " + textColor + "; -fx-background-radius: 2;");

        // Info
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(safeStr(ev.getTitre()));
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");
        String timeRange = formatLDT(ev.getDateDebut()) + " → " + formatLDT(ev.getDateFin());
        Label time = new Label("🕐 " + timeRange);
        time.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.8;");
        Label loc = new Label("📍 " + resolveLieuName(ev.getLieuId()));
        loc.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.7;");
        info.getChildren().addAll(title, time, loc);

        // Status chip
        Label statusChip = new Label(safeStr(ev.getStatut()));
        statusChip.setStyle("-fx-background-color: rgba(255,255,255,0.6); -fx-background-radius: 999; " +
                "-fx-padding: 3 10; -fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");

        card.getChildren().addAll(colorBar, info, statusChip);

        card.setOnMouseClicked(me -> {
            me.consume();
            showEventDetail(ev, colorIndex);
        });

        return card;
    }

    // ═══════════════════════════════════════════════════════════
    //  DAY VIEW — Detailed timeline for a single day
    // ═══════════════════════════════════════════════════════════

    private void renderDayView(java.util.Map<Integer, Integer> eventColorMap) {
        LocalDate today = LocalDate.now();
        LocalDate day = calCurrentMonth.atDay(
                Math.min(today.getDayOfMonth(), calCurrentMonth.lengthOfMonth()));
        if (!day.getMonth().equals(calCurrentMonth.getMonth())) day = calCurrentMonth.atDay(1);
        final LocalDate fDay = day;

        VBox dayContainer = new VBox(0);
        dayContainer.getStyleClass().add("cal-d-container");
        dayContainer.setPadding(new Insets(16));

        // Day title + add button
        String dayName = day.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_FR);
        dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
        Label dayTitle = new Label(dayName + " " + day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", LOCALE_FR)));
        dayTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #1e293b;");
        HBox dayTitleRow = new HBox(12);
        dayTitleRow.setAlignment(Pos.CENTER_LEFT);
        dayTitleRow.setPadding(new Insets(0, 0, 16, 0));
        Region dSpacer = new Region();
        HBox.setHgrow(dSpacer, Priority.ALWAYS);
        Button dBtnAdd = new Button("+ Ajouter");
        dBtnAdd.setStyle("-fx-background-color: #1a4a7a; -fx-text-fill: white; " +
                "-fx-font-size: 13px; -fx-font-weight: 800; -fx-background-radius: 8; " +
                "-fx-padding: 6 16; -fx-cursor: hand;");
        dBtnAdd.setOnAction(ev -> openEditor(null, fDay));
        dayTitleRow.getChildren().addAll(dayTitle, dSpacer, dBtnAdd);
        dayContainer.getChildren().add(dayTitleRow);

        // Events for this day
        List<Evenement> dayEvs = masterList.stream()
                .filter(e -> {
                    if (e.getDateDebut() == null) return false;
                    LocalDate s = e.getDateDebut().toLocalDate();
                    LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                    return !fDay.isBefore(s) && !fDay.isAfter(en);
                })
                .sorted((a, b) -> {
                    if (a.getDateDebut() == null || b.getDateDebut() == null) return 0;
                    return a.getDateDebut().compareTo(b.getDateDebut());
                })
                .collect(Collectors.toList());

        if (dayEvs.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(60));
            empty.setStyle("-fx-cursor: hand;");
            Label emptyIcon = new Label("📅");
            emptyIcon.setStyle("-fx-font-size: 48px;");
            Label emptyText = new Label("Aucun événement — cliquez pour ajouter");
            emptyText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 16px; -fx-font-weight: 700;");
            empty.getChildren().addAll(emptyIcon, emptyText);
            empty.setOnMouseClicked(me -> openEditor(null, fDay));
            dayContainer.getChildren().add(empty);
        } else {
            // Timeline
            for (int i = 0; i < dayEvs.size(); i++) {
                Evenement ev = dayEvs.get(i);
                int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                String bgColor = CAL_COLORS[ci % CAL_COLORS.length][0];
                String textColor = CAL_COLORS[ci % CAL_COLORS.length][1];

                HBox timeline = new HBox(16);
                timeline.setAlignment(Pos.TOP_LEFT);
                timeline.setPadding(new Insets(0, 0, 12, 0));

                // Time column
                VBox timeCol = new VBox(2);
                timeCol.setMinWidth(70);
                timeCol.setAlignment(Pos.TOP_RIGHT);
                String startTime = ev.getDateDebut() != null
                        ? ev.getDateDebut().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";
                String endTime = ev.getDateFin() != null
                        ? ev.getDateFin().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";
                Label tStart = new Label(startTime);
                tStart.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #1e293b;");
                Label tEnd = new Label(endTime);
                tEnd.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                timeCol.getChildren().addAll(tStart, tEnd);

                // Dot + line
                VBox dotCol = new VBox();
                dotCol.setAlignment(Pos.TOP_CENTER);
                dotCol.setMinWidth(20);
                Region dot = new Region();
                dot.setMinSize(12, 12);
                dot.setMaxSize(12, 12);
                dot.setStyle("-fx-background-color: " + textColor + "; -fx-background-radius: 999;");
                Region line = new Region();
                line.setMinWidth(2);
                line.setPrefWidth(2);
                line.setMinHeight(40);
                line.setStyle("-fx-background-color: " + (i < dayEvs.size() - 1 ? "rgba(0,0,0,0.08)" : "transparent") + ";");
                VBox.setVgrow(line, Priority.ALWAYS);
                dotCol.getChildren().addAll(dot, line);

                // Card
                VBox card = new VBox(6);
                HBox.setHgrow(card, Priority.ALWAYS);
                card.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12; -fx-padding: 14 18; -fx-cursor: hand;");

                Label cardTitle = new Label(safeStr(ev.getTitre()));
                cardTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");
                cardTitle.setWrapText(true);

                Label cardLoc = new Label("📍 " + resolveLieuName(ev.getLieuId()));
                cardLoc.setStyle("-fx-font-size: 12px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.8;");

                int nbInsc = 0;
                try { nbInsc = inscriptionService.countByEvent(ev.getId()); } catch (Exception ignored) {}
                Label cardInfo = new Label("👥 " + nbInsc + "/" + ev.getCapaciteMax() + " inscrit(s)  •  💰 " + ev.getPrix() + " TND");
                cardInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.7;");

                Label cardStatus = new Label(safeStr(ev.getStatut()) + "  •  " + safeStr(ev.getType()));
                cardStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.6;");

                card.getChildren().addAll(cardTitle, cardLoc, cardInfo, cardStatus);
                card.setOnMouseClicked(me -> {
                    me.consume();
                    showEventDetail(ev, ci);
                });

                timeline.getChildren().addAll(timeCol, dotCol, card);
                dayContainer.getChildren().add(timeline);
            }
        }

        calendarContainer.getChildren().add(dayContainer);
    }

    // ═══════════════════════════════════════════════════════════
    //  EVENT DETAIL — Side panel
    // ═══════════════════════════════════════════════════════════

    private void showEventDetail(Evenement ev, int colorIndex) {
        if (calDetailPanel == null) return;
        calSelectedEvent = ev;
        calDetailPanel.getChildren().clear();
        calDetailPanel.setVisible(true);
        calDetailPanel.setManaged(true);
        if (calDetailScroll != null) { calDetailScroll.setVisible(true); calDetailScroll.setManaged(true); }

        String accentColor = CAL_COLORS[colorIndex % CAL_COLORS.length][1];
        String bgColor = CAL_COLORS[colorIndex % CAL_COLORS.length][0];

        // Close button
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        Button btnClose = new Button("✕");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 16px; " +
                "-fx-cursor: hand; -fx-padding: 4 8;");
        btnClose.setOnAction(e -> hideEventDetail());
        topRow.getChildren().add(btnClose);
        calDetailPanel.getChildren().add(topRow);

        // Color accent header
        VBox headerBox = new VBox(6);
        headerBox.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12; -fx-padding: 16;");
        Label evTitle = new Label(safeStr(ev.getTitre()));
        evTitle.setWrapText(true);
        evTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: " + accentColor + ";");
        Label statusChip = new Label(safeStr(ev.getStatut()));
        statusChip.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-padding: 3 10; " +
                "-fx-background-radius: 999; -fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + accentColor + ";");
        headerBox.getChildren().addAll(evTitle, statusChip);
        calDetailPanel.getChildren().add(headerBox);

        // Separator
        Region sep1 = new Region();
        sep1.setMinHeight(12);
        calDetailPanel.getChildren().add(sep1);

        // Info rows
        addDetailRow(calDetailPanel, "📅 Début", formatLDT(ev.getDateDebut()));
        addDetailRow(calDetailPanel, "📅 Fin", formatLDT(ev.getDateFin()));
        addDetailRow(calDetailPanel, "📍 Lieu", resolveLieuName(ev.getLieuId()));
        addDetailRow(calDetailPanel, "🎭 Type", safeStr(ev.getType()));
        addDetailRow(calDetailPanel, "💰 Prix", ev.getPrix() + " TND");
        addDetailRow(calDetailPanel, "👥 Capacité", String.valueOf(ev.getCapaciteMax()));

        int nbInsc = 0;
        try { nbInsc = inscriptionService.countByEvent(ev.getId()); } catch (Exception ignored) {}
        addDetailRow(calDetailPanel, "📝 Inscrits", String.valueOf(nbInsc));

        // Description
        String desc = safeStr(ev.getDescription());
        if (!desc.isEmpty()) {
            Region sep2 = new Region();
            sep2.setMinHeight(8);
            calDetailPanel.getChildren().add(sep2);
            Label descTitle = new Label("Description");
            descTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #94a3b8; -fx-padding: 0 0 4 0;");
            Label descText = new Label(truncate(desc, 200));
            descText.setWrapText(true);
            descText.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
            calDetailPanel.getChildren().addAll(descTitle, descText);
        }

        // Action buttons
        Region sep3 = new Region();
        sep3.setMinHeight(16);
        calDetailPanel.getChildren().add(sep3);

        Button btnInsc = new Button("📝 Voir inscriptions");
        btnInsc.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: white; " +
                "-fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 8 16; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        btnInsc.setMaxWidth(Double.MAX_VALUE);
        btnInsc.setOnAction(e -> {
            hideEventDetail();
            showPanelInscriptions(ev);
        });

        Button btnEdit = new Button("✏ Modifier");
        btnEdit.setStyle("-fx-background-color: rgba(0,0,0,0.06); -fx-text-fill: #475569; " +
                "-fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 8 16; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        btnEdit.setMaxWidth(Double.MAX_VALUE);
        btnEdit.setOnAction(e -> {
            hideEventDetail();
            openEditor(ev);
        });

        Button btnIcs = new Button("📤 Exporter .ics");
        btnIcs.setStyle("-fx-background-color: rgba(0,0,0,0.06); -fx-text-fill: #475569; " +
                "-fx-font-weight: 800; -fx-font-size: 12px; -fx-padding: 8 16; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        btnIcs.setMaxWidth(Double.MAX_VALUE);
        btnIcs.setOnAction(e -> exportSingleEventIcs(ev));

        VBox btns = new VBox(8, btnInsc, btnEdit, btnIcs);
        calDetailPanel.getChildren().add(btns);
    }

    private void addDetailRow(VBox container, String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #94a3b8; -fx-min-width: 80;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #1e293b;");
        val.setWrapText(true);
        HBox.setHgrow(val, Priority.ALWAYS);
        row.getChildren().addAll(lbl, val);
        container.getChildren().add(row);
    }

    private void hideEventDetail() {
        if (calDetailPanel != null) {
            calDetailPanel.setVisible(false);
            calDetailPanel.setManaged(false);
            calDetailPanel.getChildren().clear();
        }
        if (calDetailScroll != null) {
            calDetailScroll.setVisible(false);
            calDetailScroll.setManaged(false);
        }
        calSelectedEvent = null;
    }

    /** Popup showing all events for a specific day */
    private void showDayEventsPopup(LocalDate date, List<Evenement> events) {
        Stage popup = new Stage();
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.setTitle("Événements du " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        VBox root = new VBox(12);
        root.getStyleClass().add("dialogRoot");
        root.setPadding(new Insets(20, 24, 16, 24));
        root.setMaxWidth(560);

        Label title = new Label("📅 " + date.format(DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", LOCALE_FR)));
        title.getStyleClass().add("dialogTitle");
        title.setStyle("-fx-font-size:18px;");

        Label countLbl = new Label(events.size() + " événement(s)");
        countLbl.getStyleClass().add("card-muted");

        VBox eventsList = new VBox(10);
        for (Evenement ev : events) {
            VBox card = new VBox(6);
            card.getStyleClass().add("lieu-card");
            card.setPadding(new Insets(12, 14, 12, 14));

            // Status chip
            Label statusChip = new Label(safeStr(ev.getStatut()));
            statusChip.getStyleClass().addAll("statusChip",
                    "status-" + safeStr(ev.getStatut()).toLowerCase());

            Label evTitle = new Label(safeStr(ev.getTitre()));
            evTitle.getStyleClass().add("card-title");
            evTitle.setWrapText(true);

            HBox titleRow = new HBox(10, evTitle, new Region(), statusChip);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleRow.getChildren().get(1), Priority.ALWAYS);

            Label evTime = new Label("🕐 " + formatLDT(ev.getDateDebut()) + " → " + formatLDT(ev.getDateFin()));
            evTime.getStyleClass().add("card-muted");

            Label evDetails = new Label("📍 " + resolveLieuName(ev.getLieuId())
                    + "   •   👥 " + ev.getCapaciteMax() + " places"
                    + "   •   💰 " + ev.getPrix() + " TND"
                    + "   •   " + safeStr(ev.getType()));
            evDetails.getStyleClass().add("cardLine");
            evDetails.setWrapText(true);

            // Description preview
            String desc = safeStr(ev.getDescription());
            if (!desc.isEmpty()) {
                Label descLabel = new Label(truncate(desc, 120));
                descLabel.getStyleClass().add("card-muted");
                descLabel.setWrapText(true);
                descLabel.setStyle("-fx-font-style:italic;");
                card.getChildren().addAll(titleRow, evTime, evDetails, descLabel);
            } else {
                card.getChildren().addAll(titleRow, evTime, evDetails);
            }

            // Inscriptions count
            try {
                int nbInsc = inscriptionService.countByEvent(ev.getId());
                Label inscLabel = new Label("📝 " + nbInsc + " inscription(s)");
                inscLabel.getStyleClass().add("card-muted");
                card.getChildren().add(inscLabel);
            } catch (Exception ignored) {}

            // Button to open inscriptions panel
            Button btnOpen = new Button("Voir inscriptions →");
            btnOpen.getStyleClass().add("card-btn");
            btnOpen.setOnAction(e -> {
                popup.close();
                showPanelInscriptions(ev);
            });

            Button btnEdit = new Button("Modifier");
            btnEdit.getStyleClass().add("card-btn");
            btnEdit.setOnAction(e -> {
                popup.close();
                openEditor(ev);
            });

            HBox actions = new HBox(10, btnOpen, btnEdit);
            actions.getStyleClass().add("card-actions");
            card.getChildren().add(actions);

            eventsList.getChildren().add(card);
        }

        ScrollPane sp = new ScrollPane(eventsList);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setMaxHeight(400);
        sp.getStyleClass().add("cards-scroll");

        Button btnClose = new Button("Fermer");
        btnClose.getStyleClass().add("ghostBtn");
        btnClose.setOnAction(e -> popup.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, countLbl, sp, footer);

        Scene sc = new Scene(root, 540, 500);
        sc.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/styles/back/evenements-admin.css")).toExternalForm());
        popup.setScene(sc);
        popup.setResizable(true);
        popup.centerOnScreen();
        popup.showAndWait();
    }

    private String resolveLieuName(Integer lieuId) {
        if (lieuId == null) return "Sans lieu";
        return allLieux.stream()
                .filter(l -> l.getId() == lieuId)
                .map(Lieu::getNom)
                .findFirst().orElse("Lieu #" + lieuId);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private Label emptyLabel(String msg) {
        Label l = new Label(msg);
        l.setStyle("-fx-text-fill:rgba(15,42,68,0.65);-fx-font-weight:800;-fx-padding:20;");
        return l;
    }

    private String formatLDT(LocalDateTime ldt) {
        return ldt != null ? ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
    }

    private static String safeStr(Object o) { return o == null ? "" : String.valueOf(o); }

    private String textOf(TextInputControl tf) {
        return tf == null ? "" : Optional.ofNullable(tf.getText()).orElse("").trim();
    }

    // ── Image helpers ──────────────────────────────────────────
    private Image loadImageOrFallback(String path) {
        Image img = loadImageOrNull(path);
        if (img != null) return img;
        try { return new Image(getClass().getResourceAsStream("/images/demo/hero/hero.jpg")); }
        catch (Exception ignored) { return null; }
    }

    private Image loadImageOrNull(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            File f = new File(path);
            if (f.exists()) return new Image(f.toURI().toString(), true);
        } catch (Exception ignored) {}
        try {
            if (path.startsWith("http")) return new Image(path, true);
            var res = getClass().getResourceAsStream(path.startsWith("/") ? path : "/" + path);
            if (res != null) return new Image(res);
        } catch (Exception ignored) {}
        return null;
    }

    private String shortPath(String path) {
        if (path == null || path.isBlank()) return "";
        int i = path.lastIndexOf('/');
        int j = path.lastIndexOf('\\');
        int sep = Math.max(i, j);
        return sep >= 0 ? path.substring(sep + 1) : path;
    }

    private boolean confirmDelete(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation"); alert.setHeaderText(null); alert.setContentText(message);
        return alert.showAndWait().map(r -> r == ButtonType.OK).orElse(false);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention"); alert.setHeaderText(null); alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title); alert.setHeaderText(header); alert.setContentText(details);
        alert.showAndWait();
    }

    // ── Chargement des lieux directement depuis la BDD ──────────
    private String resolveUserName(int userId) {
        return allUsers.stream()
                .filter(u -> u.getId() == userId)
                .findFirst()
                .map(u -> u.getNom() + " " + u.getPrenom())
                .orElse("User #" + userId);
    }

    private List<User> loadAllUsers() {
        List<User> users = new java.util.ArrayList<>();
        String sql = "SELECT id, nom, prenom, email, role FROM user ORDER BY nom, prenom";
        try {
            Connection cnx = Mydb.getInstance().getConnection();
            PreparedStatement ps = cnx.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setNom(rs.getString("nom"));
                u.setPrenom(rs.getString("prenom"));
                u.setEmail(rs.getString("email"));
                u.setRole(rs.getString("role"));
                users.add(u);
            }
            rs.close(); ps.close();
        } catch (Exception e) {
            System.err.println("loadAllUsers error: " + e.getMessage());
            e.printStackTrace();
        }
        return users;
    }

    private List<Lieu> loadAllLieux() {
        List<Lieu> lieux = new java.util.ArrayList<>();
        String sql = "SELECT id, nom, ville, adresse, categorie, type, latitude, longitude FROM lieu ORDER BY nom";
        try {
            Connection cnx = Mydb.getInstance().getConnection();
            PreparedStatement ps = cnx.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Lieu l = new Lieu();
                l.setId(rs.getInt("id"));
                l.setNom(rs.getString("nom"));
                l.setVille(rs.getString("ville"));
                l.setAdresse(rs.getString("adresse"));
                l.setCategorie(rs.getString("categorie"));
                l.setType(rs.getString("type"));
                double lat = rs.getDouble("latitude");
                l.setLatitude(rs.wasNull() ? null : lat);
                double lon = rs.getDouble("longitude");
                l.setLongitude(rs.wasNull() ? null : lon);
                lieux.add(l);
            }
            rs.close(); ps.close();
        } catch (Exception e) {
            System.err.println("loadAllLieux error: " + e.getMessage());
            e.printStackTrace();
        }
        return lieux;
    }
}