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
import services.evenements.InscriptionService;
import services.evenements.TicketService;
import utils.Mydb;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private final EvenementService   evenementService   = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final TicketService      ticketService      = new TicketService();
    private List<Lieu> allLieux = List.of();
    private List<User> allUsers = List.of();

    private final ObservableList<Evenement> masterList   = FXCollections.observableArrayList();
    private final FilteredList<Evenement>  filteredList = new FilteredList<>(masterList, p -> true);

    private Evenement   currentEvent       = null;
    private Inscription currentInscription = null;
    private Node        selectedCard       = null;
    private double      lastViewportW      = -1;

    // ═══════════════════════════════════════════════════════════
    //  INITIALISATION
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupSearchFilter();
        setupResponsiveTiles();
        allLieux = loadAllLieux();
        allUsers = loadAllUsers();
        loadData();
        showPanelEvents();
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
            double tileW = Math.max(CARD_MIN_W, (w - 12 - 28) / 3.0);
            cardsPane.setPrefTileWidth(tileW);
        });
        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) {
            double tileW = Math.max(CARD_MIN_W, (b.getWidth() - 12 - 28) / 3.0);
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
        card.getChildren().addAll(imgWrap, title, meta, details, lieu, actions);
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

    private void openEditor(Evenement existing) {
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
            dpDateDebut.setValue(LocalDate.now());
            spHeureDebut.getValueFactory().setValue(10);
            spMinuteDebut.getValueFactory().setValue(0);
            dpDateFin.setValue(LocalDate.now());
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
                    evenementService.add(toAdd);
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
        for (VBox p : new VBox[]{panelEvents, panelInscriptions, panelTickets}) {
            boolean show = (p == toShow); p.setVisible(show); p.setManaged(show);
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
        String sql = "SELECT id, nom, ville, adresse, categorie, type FROM lieu ORDER BY nom";
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