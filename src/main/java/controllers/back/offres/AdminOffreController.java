package controllers.back.offres;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import models.lieux.Lieu;
import models.offres.CodePromo;
import models.offres.Offre;
import models.offres.ReservationOffre;
import models.users.User;
import services.offres.CodePromoService;
import services.offres.OffreService;
import services.offres.ReservationOffreService;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminOffreController {

    @FXML private Label kpiTotalOffres;
    @FXML private Label kpiActives;
    @FXML private Label kpiCodes;
    @FXML private Label kpiReservations;

    @FXML private ComboBox<String> modeCombo;
    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField searchField;
    @FXML private Button btnAdd;
    @FXML private Button btnScanQr;

    @FXML private javafx.scene.control.ScrollPane cardsScrollOffres;
    @FXML private javafx.scene.control.ScrollPane cardsScrollCodes;
    @FXML private javafx.scene.control.ScrollPane cardsScrollReservations;
    @FXML private TilePane cardsPaneOffres;
    @FXML private TilePane cardsPaneCodes;
    @FXML private TilePane cardsPaneReservations;

    private final OffreService offreService = new OffreService();
    private final CodePromoService codePromoService = new CodePromoService();
    private final ReservationOffreService reservationService = new ReservationOffreService();

    private final ObservableList<Offre> offresList = FXCollections.observableArrayList();
    private final ObservableList<CodePromo> codesPromoList = FXCollections.observableArrayList();
    private final ObservableList<ReservationOffre> reservationsList = FXCollections.observableArrayList();
    private final ObservableList<Lieu> lieuxList = FXCollections.observableArrayList();
    private final Map<Integer, String> lieuxLabelById = new HashMap<>();

    private User currentUser;
    private Node selectedCard;
    private Offre selectedOffre;
    private CodePromo selectedPromo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** ⚠  Remplace cette URL par l'URL Webhook de ton workflow n8n. */
    private static final String N8N_WEBHOOK_URL = "https://fourtiahmed.app.n8n.cloud/webhook-test/edff844c-8e3b-48c5-ad5e-50b88471dcb5";

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    @FXML
    public void initialize() {
        setupModeCombo();
        setupFilterCombo();
        setupSearch();
        setupAddButton();

        loadLieux();
        reloadAll();
        showMode("Offres");
    }

    private void setupModeCombo() {
        modeCombo.setItems(FXCollections.observableArrayList("Offres", "Codes promo", "Réservations"));
        modeCombo.getSelectionModel().select("Offres");
        modeCombo.valueProperty().addListener((o, ov, nv) -> {
            showMode(nv);
            setupFilterCombo();
            render();
        });
    }

    private void setupFilterCombo() {
        String mode = safe(modeCombo.getValue());
        if ("Codes promo".equals(mode)) {
            filterCombo.setItems(FXCollections.observableArrayList("Offre", "Utilisateur", "Statut"));
            filterCombo.getSelectionModel().select("Offre");
            btnAdd.setDisable(true);
        } else if ("Réservations".equals(mode)) {
            filterCombo.setItems(FXCollections.observableArrayList("Utilisateur", "Offre", "Lieu", "Statut"));
            filterCombo.getSelectionModel().select("Statut");
            btnAdd.setDisable(true);
        } else {
            filterCombo.setItems(FXCollections.observableArrayList("Titre", "Type", "Statut"));
            filterCombo.getSelectionModel().select("Titre");
            btnAdd.setDisable(false);
        }
    }

    private void setupSearch() {
        searchField.textProperty().addListener((o, ov, nv) -> render());
        filterCombo.valueProperty().addListener((o, ov, nv) -> render());
    }

    private void setupAddButton() {
        btnAdd.setOnAction(e -> {
            if (!"Offres".equals(safe(modeCombo.getValue()))) return;
            openOffreEditor(null);
        });
        btnScanQr.setOnAction(e -> openScanQrDialog());
    }

    private void loadLieux() {
        try {
            lieuxList.setAll(offreService.obtenirTousLesLieux());
            lieuxLabelById.clear();
            for (Lieu lieu : lieuxList) {
                lieuxLabelById.put(lieu.getId(), lieu.toString());
            }
        } catch (SQLException e) {
            // If the DB schema doesn't allow linking offers to lieux, this might still work; keep the screen usable.
            lieuxList.clear();
            lieuxLabelById.clear();
        }
    }

    private void loadOffres() {
        try {
            offresList.setAll(offreService.obtenirToutes());
        } catch (SQLException e) {
            showError("Offres", "Impossible de charger les offres: " + e.getMessage());
        }
    }

    private void loadCodesPromo() {
        try {
            codesPromoList.setAll(codePromoService.obtenirTousCodesPromo());
        } catch (SQLException e) {
            showError("Codes promo", "Impossible de charger les codes promo: " + e.getMessage());
        }
    }

    private void loadReservations() {
        try {
            reservationsList.setAll(reservationService.toutesLesReservations());
        } catch (SQLException e) {
            showError("Réservations", "Impossible de charger les réservations: " + e.getMessage());
        }
    }

    private void reloadAll() {
        loadOffres();
        loadCodesPromo();
        loadReservations();
        updateKpis();
        render();
    }

    private void updateKpis() {
        if (kpiTotalOffres != null) kpiTotalOffres.setText(String.valueOf(offresList.size()));
        long actives = offresList.stream().filter(o -> "active".equalsIgnoreCase(safe(o.getStatut()).trim())).count();
        if (kpiActives != null) kpiActives.setText(String.valueOf(actives));
        if (kpiCodes != null) kpiCodes.setText(String.valueOf(codesPromoList.size()));
        long enAttente = reservationsList.stream().filter(r -> "EN_ATTENTE".equalsIgnoreCase(safe(r.getStatut()).trim())).count();
        if (kpiReservations != null) kpiReservations.setText(String.valueOf(enAttente));
    }

    private void showMode(String mode) {
        boolean showOffres = "Offres".equals(mode);
        boolean showCodes  = "Codes promo".equals(mode);
        boolean showReserv = "Réservations".equals(mode);

        cardsScrollOffres.setVisible(showOffres);
        cardsScrollOffres.setManaged(showOffres);
        cardsScrollCodes.setVisible(showCodes);
        cardsScrollCodes.setManaged(showCodes);
        cardsScrollReservations.setVisible(showReserv);
        cardsScrollReservations.setManaged(showReserv);

        btnAdd.setVisible(showOffres);
        btnAdd.setManaged(showOffres);
        btnScanQr.setVisible(showCodes);
        btnScanQr.setManaged(showCodes);
    }

    private void render() {
        String mode = safe(modeCombo.getValue());
        if ("Codes promo".equals(mode)) {
            renderPromoCards();
        } else if ("Réservations".equals(mode)) {
            renderReservationCards();
        } else {
            renderOffreCards();
        }
    }

    private void renderOffreCards() {
        cardsPaneOffres.getChildren().clear();
        selectedOffre = null;
        selectedPromo = null;
        selectedCard = null;

        List<Offre> filtered = offresList.stream().filter(this::matchesOffreFilter).toList();
        for (Offre offre : filtered) {
            cardsPaneOffres.getChildren().add(createOffreCard(offre));
        }

        if (cardsPaneOffres.getChildren().isEmpty()) {
            Label empty = new Label("Aucune offre trouvée.");
            empty.getStyleClass().add("cardLine");
            VBox wrap = new VBox(empty);
            wrap.setPadding(new Insets(10));
            cardsPaneOffres.getChildren().add(wrap);
        }
    }

    private boolean matchesOffreFilter(Offre o) {
        String q = safe(searchField.getText()).trim().toLowerCase();
        if (q.isEmpty()) return true;

        String mode = safe(filterCombo.getValue());
        return switch (mode) {
            case "Type" -> safe(o.getType()).toLowerCase().contains(q);
            case "Statut" -> safe(o.getStatut()).toLowerCase().contains(q);
            default -> safe(o.getTitre()).toLowerCase().contains(q);
        };
    }

    private Node createOffreCard(Offre offre) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortie-card");
        card.setMinWidth(360);

        Label title = new Label(safe(offre.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        Label chip = new Label(safe(offre.getStatut()).isBlank() ? "-" : offre.getStatut());
        chip.getStyleClass().addAll("statusChip", mapOffreStatutClass(offre.getStatut()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, chip);
        header.setAlignment(Pos.TOP_LEFT);

        String lieuLabel = resolveLieuLabel(offre);
        Label meta = new Label(safe(offre.getType()) + " · " + formatPercent(offre.getPourcentage()) + " · " + lieuLabel);
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        Label dates = new Label("Validité: " + formatDate(offre.getDate_debut()) + " → " + formatDate(offre.getDate_fin()));
        dates.getStyleClass().add("cardLine");

        String desc = abbreviate(safe(offre.getDescription()).trim().replace("\n", " "), 140);
        Label description = new Label(desc.isBlank() ? "" : desc);
        description.getStyleClass().add("cardLine");
        description.setWrapText(true);
        description.setVisible(!desc.isBlank());
        description.setManaged(!desc.isBlank());

        Button edit = new Button("Modifier");
        edit.getStyleClass().add("card-btn");
        edit.setOnAction(e -> openOffreEditor(offre));

        Button del = new Button("Supprimer");
        del.getStyleClass().addAll("card-btn", "danger");
        del.setOnAction(e -> deleteOffre(offre));

        HBox actions = new HBox(10, edit, del);
        actions.getStyleClass().add("card-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, meta, dates, description, actions);
        card.setOnMouseClicked(e -> selectCard(card, offre, null));
        return card;
    }

    private void renderPromoCards() {
        cardsPaneCodes.getChildren().clear();
        selectedOffre = null;
        selectedPromo = null;
        selectedCard = null;

        List<CodePromo> filtered = codesPromoList.stream().filter(this::matchesPromoFilter).toList();
        for (CodePromo promo : filtered) {
            cardsPaneCodes.getChildren().add(createPromoCard(promo));
        }

        if (cardsPaneCodes.getChildren().isEmpty()) {
            Label empty = new Label("Aucun code promo trouvé.");
            empty.getStyleClass().add("cardLine");
            VBox wrap = new VBox(empty);
            wrap.setPadding(new Insets(10));
            cardsPaneCodes.getChildren().add(wrap);
        }
    }

    private boolean matchesPromoFilter(CodePromo p) {
        String q = safe(searchField.getText()).trim().toLowerCase();
        if (q.isEmpty()) return true;

        String mode = safe(filterCombo.getValue());
        return switch (mode) {
            case "Utilisateur" -> String.valueOf(p.getUser_id()).contains(q);
            case "Statut" -> safe(p.getStatut()).toLowerCase().contains(q);
            default -> String.valueOf(p.getOffre_id()).contains(q);
        };
    }

    private Node createPromoCard(CodePromo promo) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortie-card");
        card.setMinWidth(360);

        Label title = new Label("Code #" + promo.getId());
        title.getStyleClass().add("cardTitle");

        Label chip = new Label(safe(promo.getStatut()).isBlank() ? "-" : promo.getStatut());
        chip.getStyleClass().addAll("statusChip", mapPromoStatutClass(promo.getStatut()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, chip);
        header.setAlignment(Pos.TOP_LEFT);

        Label meta = new Label("Offre #" + promo.getOffre_id() + " · User #" + (promo.getUser_id() > 0 ? promo.getUser_id() : "-") );
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        Label dates = new Label("Généré: " + formatDate(promo.getDate_generation()) + " · Expire: " + formatDate(promo.getDate_expiration()));
        dates.getStyleClass().add("cardLine");

        String qr = abbreviate(safe(promo.getQr_image_url()).trim(), 90);
        Label qrLbl = new Label(qr.isBlank() ? "" : ("QR: " + qr));
        qrLbl.getStyleClass().add("cardLine");
        qrLbl.setWrapText(true);
        qrLbl.setVisible(!qr.isBlank());
        qrLbl.setManaged(!qr.isBlank());

        Button edit = new Button("Modifier");
        edit.getStyleClass().add("card-btn");
        edit.setOnAction(e -> openPromoEditor(promo));

        Button markUsed = new Button("Marquer utilisé");
        markUsed.getStyleClass().add("card-btn");
        boolean alreadyUsed = "UTILISÉ".equalsIgnoreCase(safe(promo.getStatut()).trim());
        markUsed.setDisable(alreadyUsed);
        markUsed.setOnAction(e -> {
            if (!confirm("Utiliser code promo", "Marquer le code promo #" + promo.getId() + " comme utilisé ?")) return;
            try {
                codePromoService.marquerUtilise(promo.getId());
                reloadAll();
            } catch (Exception ex) {
                showError("Codes promo", safe(ex.getMessage()));
            }
        });

        Button del = new Button("Supprimer");
        del.getStyleClass().addAll("card-btn", "danger");
        del.setOnAction(e -> deletePromo(promo));

        HBox actions = new HBox(10, edit, markUsed, del);
        actions.getStyleClass().add("card-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, meta, dates, qrLbl, actions);
        card.setOnMouseClicked(e -> selectCard(card, null, promo));
        return card;
    }

    private void selectCard(Node card, Offre offre, CodePromo promo) {
        if (selectedCard != null) {
            selectedCard.getStyleClass().remove("selected");
        }
        selectedCard = card;
        selectedCard.getStyleClass().add("selected");
        selectedOffre = offre;
        selectedPromo = promo;
    }

    private void deleteOffre(Offre offre) {
        if (offre == null) return;
        if (!confirm("Suppression", "Supprimer l'offre: " + safe(offre.getTitre()) + " ?")) return;
        try {
            offreService.supprimer(offre.getId());
            reloadAll();
        } catch (Exception ex) {
            showError("Offres", safe(ex.getMessage()));
        }
    }

    private void deletePromo(CodePromo promo) {
        if (promo == null) return;
        if (!confirm("Suppression", "Supprimer le code promo #" + promo.getId() + " ?")) return;
        try {
            codePromoService.supprimerCodePromo(promo.getId());
            reloadAll();
        } catch (Exception ex) {
            showError("Codes promo", safe(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RÉSERVATIONS
    // ═══════════════════════════════════════════════════════════════

    private void renderReservationCards() {
        cardsPaneReservations.getChildren().clear();
        selectedCard = null;

        List<ReservationOffre> filtered = reservationsList.stream()
                .filter(this::matchesReservFilter).toList();

        for (ReservationOffre r : filtered) {
            cardsPaneReservations.getChildren().add(createReservationCard(r));
        }

        if (cardsPaneReservations.getChildren().isEmpty()) {
            Label empty = new Label("Aucune réservation trouvée.");
            empty.getStyleClass().add("cardLine");
            VBox wrap = new VBox(empty);
            wrap.setPadding(new Insets(10));
            cardsPaneReservations.getChildren().add(wrap);
        }
    }

    private boolean matchesReservFilter(ReservationOffre r) {
        String q = safe(searchField.getText()).trim().toLowerCase();
        if (q.isEmpty()) return true;
        String col = safe(filterCombo.getValue());
        return switch (col) {
            case "Utilisateur" -> String.valueOf(r.getUserId()).contains(q);
            case "Offre"       -> String.valueOf(r.getOffreId()).contains(q);
            case "Lieu"        -> String.valueOf(r.getLieuId()).contains(q);
            default            -> safe(r.getStatut()).toLowerCase().contains(q);
        };
    }

    private Node createReservationCard(ReservationOffre r) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortie-card");
        card.setMinWidth(360);

        // ── En-tête id + statut ──────────────────────────────────
        Label title = new Label("Réservation #" + r.getId());
        title.getStyleClass().add("cardTitle");

        String statut = safe(r.getStatut()).trim();
        Label chip = new Label(statut.isEmpty() ? "-" : statut);
        chip.getStyleClass().addAll("statusChip", mapReservStatutClass(statut));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, spacer, chip);
        header.setAlignment(Pos.TOP_LEFT);

        // ── Infos ─────────────────────────────────────────────────
        String lieuLabel = lieuxLabelById.getOrDefault(r.getLieuId(), "Lieu #" + r.getLieuId());
        Label meta = new Label(
                "Offre #" + r.getOffreId()
                + "  ·  User #" + r.getUserId()
                + "  ·  " + lieuLabel);
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        Label dates = new Label(
                "Date : " + formatDate(r.getDateReservation())
                + "  ·  " + r.getNombrePersonnes() + " personne(s)");
        dates.getStyleClass().add("cardLine");

        String note = abbreviate(safe(r.getNote()).trim(), 100);
        Label noteLbl = new Label(note.isBlank() ? "" : "Note : " + note);
        noteLbl.getStyleClass().add("cardLine");
        noteLbl.setWrapText(true);
        noteLbl.setVisible(!note.isBlank());
        noteLbl.setManaged(!note.isBlank());

        // ── Boutons Accepter / Refuser ────────────────────────────
        boolean isEnAttente = "EN_ATTENTE".equalsIgnoreCase(statut);

        Button btnAccepter = new Button("✔  Accepter");
        btnAccepter.getStyleClass().add("card-btn");
        btnAccepter.setStyle("-fx-text-fill: #27ae60;");
        btnAccepter.setDisable(!isEnAttente);
        btnAccepter.setOnAction(e -> changerStatutReservation(r, "CONFIRMÉE"));

        Button btnRefuser = new Button("✖  Refuser");
        btnRefuser.getStyleClass().addAll("card-btn", "danger");
        btnRefuser.setDisable(!isEnAttente);
        btnRefuser.setOnAction(e -> changerStatutReservation(r, "REFUSÉE"));

        HBox actions = new HBox(10, btnAccepter, btnRefuser);
        actions.getStyleClass().add("card-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(header, meta, dates, noteLbl, actions);
        card.setOnMouseClicked(e -> selectCard(card, null, null));
        return card;
    }

    private void changerStatutReservation(ReservationOffre r, String nouveauStatut) {
        String libelle = "CONFIRMÉE".equals(nouveauStatut) ? "accepter" : "refuser";
        if (!confirm("Réservation", "Voulez-vous " + libelle + " la réservation #" + r.getId() + " ?"))
            return;
        try {
            reservationService.changerStatut(r.getId(), nouveauStatut);
            reloadAll();
        } catch (Exception ex) {
            showError("Réservations", safe(ex.getMessage()));
        }
    }

    private String mapReservStatutClass(String statut) {
        String s = safe(statut).trim().toLowerCase();
        return switch (s) {
            case "confirmée" -> "status-ouverte";
            case "refusée", "annulée" -> "status-annulee";
            default -> "status-cloturee"; // EN_ATTENTE
        };
    }

    private void openOffreEditor(Offre existing) {
        if (!offreService.supportsLieuAssociation()) {
            showError("Schéma BD", "La table des offres ne contient aucune colonne lieu (ex: lieu_id).\n\nAjoute la colonne en base pour gérer les offres côté admin.");
            return;
        }

        boolean isEdit = existing != null;
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Modifier Offre" : "Ajouter Offre");

        ComboBox<Lieu> cbLieu = new ComboBox<>(lieuxList);
        cbLieu.setPromptText("-- Choisir un lieu --");
        cbLieu.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbStatut = new ComboBox<>(FXCollections.observableArrayList("active", "inactive", "expirée"));
        cbStatut.setMaxWidth(Double.MAX_VALUE);

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre");

        TextField tfType = new TextField();
        tfType.setPromptText("Type (ex: Happy Hour)");

        TextField tfPourcentage = new TextField();
        tfPourcentage.setPromptText("Pourcentage (0-100)");

        DatePicker dpDebut = new DatePicker();
        DatePicker dpFin = new DatePicker();

        TextArea taDesc = new TextArea();
        taDesc.setPromptText("Description");
        taDesc.setPrefRowCount(4);

        if (isEdit) {
            cbLieu.getSelectionModel().select(lieuxList.stream().filter(l -> l.getId() == existing.getLieu_id()).findFirst().orElse(null));
            cbStatut.setValue(safe(existing.getStatut()).isBlank() ? "active" : existing.getStatut());
            tfTitre.setText(safe(existing.getTitre()));
            tfType.setText(safe(existing.getType()));
            tfPourcentage.setText(String.valueOf(existing.getPourcentage()));
            dpDebut.setValue(existing.getDate_debut() == null ? null : existing.getDate_debut().toLocalDate());
            dpFin.setValue(existing.getDate_fin() == null ? null : existing.getDate_fin().toLocalDate());
            taDesc.setText(safe(existing.getDescription()));
        } else {
            cbStatut.setValue("active");
        }

        Label headline = new Label(isEdit ? "Modifier une offre" : "Ajouter une offre");
        headline.getStyleClass().add("dialogTitle");

        VBox form = new VBox(10,
                row("Lieu", cbLieu),
                row("Statut", cbStatut),
                row("Titre", tfTitre),
                row("Type", tfType),
                row("Pourcentage", tfPourcentage),
                row("Date début", dpDebut),
                row("Date fin", dpFin),
                row("Description", taDesc)
        );
        form.setPadding(new Insets(0, 8, 8, 8));

        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("card-btn");
        btnCancel.setOnAction(e -> dialog.close());

        Button btnSave = new Button(isEdit ? "Enregistrer" : "Créer");
        btnSave.getStyleClass().add("card-btn");
        btnSave.setOnAction(e -> {
            try {
                Offre toSave = buildOffre(existing, cbLieu, cbStatut, tfTitre, tfType, tfPourcentage, dpDebut, dpFin, taDesc);
                if (isEdit) {
                    offreService.modifier(toSave);
                } else {
                    offreService.ajouter(toSave);
                }
                dialog.close();
                reloadAll();
            } catch (Exception ex) {
                showError("Offres", safe(ex.getMessage()));
            }
        });

        // ── Status label shown next to the button ──────────────
        Label webhookStatus = new Label();
        webhookStatus.setStyle("-fx-font-weight: 800; -fx-font-size: 11.5px;");
        webhookStatus.setWrapText(true);

        Button btnAnalyse = new Button("📊 Analyser offres");
        btnAnalyse.getStyleClass().add("card-btn-analyse");
        btnAnalyse.setOnAction(e -> {
            // ── Collect form fields ──────────────────────────────
            String lieu    = cbLieu.getValue() != null ? cbLieu.getValue().toString() : "";
            String statut  = safe(cbStatut.getValue());
            String titre   = tfTitre.getText().trim();
            String type    = tfType.getText().trim();
            String pct     = tfPourcentage.getText().trim();
            String debut   = dpDebut.getValue() != null ? dpDebut.getValue().toString() : "";
            String fin     = dpFin.getValue()   != null ? dpFin.getValue().toString()   : "";
            String desc    = taDesc.getText().trim();

            // ── Build JSON payload ───────────────────────────────
            String json = "{"
                    + "\"lieu\":\""        + escJson(lieu)   + "\","
                    + "\"statut\":\""      + escJson(statut) + "\","
                    + "\"titre\":\""       + escJson(titre)  + "\","
                    + "\"type\":\""        + escJson(type)   + "\","
                    + "\"pourcentage\":\"" + escJson(pct)    + "\","
                    + "\"date_debut\":\""  + escJson(debut)  + "\","
                    + "\"date_fin\":\""    + escJson(fin)    + "\","
                    + "\"description\":\"" + escJson(desc)   + "\""
                    + "}";

            // ── Send asynchronously so the UI stays responsive ───
            btnAnalyse.setDisable(true);
            webhookStatus.setText("Envoi en cours…");
            webhookStatus.setStyle("-fx-text-fill: #555; -fx-font-weight: 800; -fx-font-size: 11.5px;");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(N8N_WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> javafx.application.Platform.runLater(() -> {
                        btnAnalyse.setDisable(false);
                        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            webhookStatus.setText("✔ Analyse reçue");
                            webhookStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: 800; -fx-font-size: 11.5px;");
                            try {
                                String body = resp.body().trim();
                                JsonObject result;
                                // Handle both array wrapper and plain object
                                if (body.startsWith("[")) {
                                    result = JsonParser.parseString(body)
                                            .getAsJsonArray().get(0).getAsJsonObject();
                                } else {
                                    result = JsonParser.parseString(body).getAsJsonObject();
                                }
                                // If the result still has a nested "output" key (raw Anthropic shape),
                                // drill down: output[0].content[0].text
                                if (result.has("output")) {
                                    JsonElement text = result
                                            .getAsJsonArray("output").get(0).getAsJsonObject()
                                            .getAsJsonArray("content").get(0).getAsJsonObject()
                                            .get("text");
                                    result = text.isJsonObject()
                                            ? text.getAsJsonObject()
                                            : JsonParser.parseString(text.getAsString()).getAsJsonObject();
                                }
                                showAnalysisDialog(result, tfTitre, tfType, tfPourcentage, taDesc,
                                        () -> btnAnalyse.fire());
                            } catch (Exception parseEx) {
                                showError("Analyse", "Réponse invalide de n8n:\n" + resp.body());
                            }
                        } else {
                            webhookStatus.setText("⚠ Erreur HTTP " + resp.statusCode());
                            webhookStatus.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: 800; -fx-font-size: 11.5px;");
                        }
                    }))
                    .exceptionally(ex -> {
                        javafx.application.Platform.runLater(() -> {
                            btnAnalyse.setDisable(false);
                            webhookStatus.setText("✗ " + ex.getCause().getMessage());
                            webhookStatus.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: 800; -fx-font-size: 11.5px;");
                        });
                        return null;
                    });
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnCancel, webhookStatus, sp, btnAnalyse, btnSave);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        VBox content = new VBox(12, headline, form);
        content.setPadding(new Insets(0));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("editorScroll");

        BorderPane shell = new BorderPane();
        shell.setCenter(scroll);
        shell.setBottom(footer);
        shell.setPadding(new Insets(16));
        shell.getStyleClass().add("dialogRoot");

        Scene scene = new Scene(shell, 640, 720);
        scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private Offre buildOffre(Offre existing,
                             ComboBox<Lieu> cbLieu,
                             ComboBox<String> cbStatut,
                             TextField tfTitre,
                             TextField tfType,
                             TextField tfPourcentage,
                             DatePicker dpDebut,
                             DatePicker dpFin,
                             TextArea taDesc) {
        Lieu lieu = cbLieu.getValue();
        if (lieu == null) throw new IllegalArgumentException("Le lieu est obligatoire.");

        String titre = safe(tfTitre.getText()).trim();
        if (titre.isEmpty()) throw new IllegalArgumentException("Le titre est obligatoire.");

        String type = safe(tfType.getText()).trim();
        if (type.isEmpty()) throw new IllegalArgumentException("Le type est obligatoire.");

        float pourcentage;
        try {
            pourcentage = Float.parseFloat(safe(tfPourcentage.getText()).trim().replace(',', '.'));
        } catch (Exception e) {
            throw new IllegalArgumentException("Pourcentage invalide.");
        }

        LocalDate debut = dpDebut.getValue();
        LocalDate fin = dpFin.getValue();
        if (debut == null || fin == null) throw new IllegalArgumentException("Dates début/fin obligatoires.");

        String statut = safe(cbStatut.getValue()).trim();
        if (statut.isEmpty()) statut = "active";

        Offre offre = new Offre();
        if (existing != null) {
            offre.setId(existing.getId());
            offre.setUser_id(existing.getUser_id());
            offre.setEvent_id(existing.getEvent_id());
        } else {
            if (currentUser == null || currentUser.getId() <= 0) {
                throw new IllegalArgumentException("Impossible d'identifier l'utilisateur connecté.");
            }
            offre.setUser_id(currentUser.getId());
            offre.setEvent_id(0);
        }
        offre.setLieu_id(lieu.getId());
        offre.setTitre(titre);
        offre.setType(type);
        offre.setPourcentage(pourcentage);
        offre.setDate_debut(Date.valueOf(debut));
        offre.setDate_fin(Date.valueOf(fin));
        offre.setStatut(statut);
        offre.setDescription(safe(taDesc.getText()).trim());
        return offre;
    }

    private void openPromoEditor(CodePromo existing) {
        if (existing == null) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Modifier Code Promo");

        TextField tfOffreId = new TextField(String.valueOf(existing.getOffre_id()));
        TextField tfUserId = new TextField(existing.getUser_id() > 0 ? String.valueOf(existing.getUser_id()) : "");
        TextField tfQr = new TextField(safe(existing.getQr_image_url()));
        DatePicker dpGen = new DatePicker(existing.getDate_generation() == null ? null : existing.getDate_generation().toLocalDate());
        DatePicker dpExp = new DatePicker(existing.getDate_expiration() == null ? null : existing.getDate_expiration().toLocalDate());
        ComboBox<String> cbStatut = new ComboBox<>(FXCollections.observableArrayList("ACTIF", "UTILISÉ", "EXPIRE", "DESACTIVE"));
        cbStatut.setValue(safe(existing.getStatut()).isBlank() ? "ACTIF" : existing.getStatut());

        Label headline = new Label("Modifier un code promo");
        headline.getStyleClass().add("dialogTitle");

        VBox form = new VBox(10,
                row("Offre ID", tfOffreId),
                row("User ID", tfUserId),
                row("Statut", cbStatut),
                row("Date Gén.", dpGen),
                row("Date Exp.", dpExp),
                row("QR URL", tfQr)
        );
        form.setPadding(new Insets(0, 8, 8, 8));

        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("card-btn");
        btnCancel.setOnAction(e -> dialog.close());

        Button btnSave = new Button("Enregistrer");
        btnSave.getStyleClass().add("card-btn");
        btnSave.setOnAction(e -> {
            try {
                CodePromo updated = new CodePromo();
                updated.setId(existing.getId());
                updated.setOffre_id(parsePositiveInt(tfOffreId.getText(), "ID offre invalide."));
                updated.setUser_id(parseOptionalInt(tfUserId.getText()));
                updated.setStatut(safe(cbStatut.getValue()).trim());

                LocalDate gen = dpGen.getValue();
                LocalDate exp = dpExp.getValue();
                if (gen == null || exp == null) throw new IllegalArgumentException("Dates génération/expiration obligatoires.");
                updated.setDate_generation(Date.valueOf(gen));
                updated.setDate_expiration(Date.valueOf(exp));
                updated.setQr_image_url(safe(tfQr.getText()).trim());

                codePromoService.modifierCodePromo(updated);
                dialog.close();
                reloadAll();
            } catch (Exception ex) {
                showError("Codes promo", safe(ex.getMessage()));
            }
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnCancel, sp, btnSave);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        VBox content = new VBox(12, headline, form);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("editorScroll");

        BorderPane shell = new BorderPane();
        shell.setCenter(scroll);
        shell.setBottom(footer);
        shell.setPadding(new Insets(16));
        shell.getStyleClass().add("dialogRoot");

        Scene scene = new Scene(shell, 600, 540);
        scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private Node row(String label, Node field) {
        Label l = new Label(label);
        l.getStyleClass().add("cardLine");
        VBox wrap = new VBox(6, l, field);
        VBox.setVgrow(field, Priority.NEVER);
        if (field instanceof TextArea ta) {
            VBox.setVgrow(ta, Priority.ALWAYS);
        }
        return wrap;
    }

    private boolean confirm(String title, String message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(title);
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        Optional<ButtonType> choice = confirm.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.OK;
    }

    private String resolveLieuLabel(Offre offre) {
        if (offre == null) return "Sans lieu";
        if (offre.getLieu_id() <= 0) return "Sans lieu";
        return lieuxLabelById.getOrDefault(offre.getLieu_id(), "Lieu #" + offre.getLieu_id());
    }

    private String mapOffreStatutClass(String statut) {
        String s = safe(statut).trim().toLowerCase();
        return switch (s) {
            case "active" -> "status-ouverte";
            case "expirée", "expiree" -> "status-annulee";
            default -> "status-cloturee";
        };
    }

    private String mapPromoStatutClass(String statut) {
        String s = safe(statut).trim().toLowerCase();
        return switch (s) {
            case "actif" -> "status-ouverte";
            case "utilisé", "utilise" -> "status-cloturee";
            case "expire" , "expiré", "expirée", "expiree" -> "status-annulee";
            default -> "status-cloturee";
        };
    }

    private String formatPercent(float p) {
        if (Math.floor(p) == p) return ((int) p) + "%";
        return p + "%";
    }

    private String formatDate(Date d) {
        if (d == null) return "-";
        return d.toLocalDate().format(DATE_FMT);
    }

    private int parsePositiveInt(String txt, String errMessage) {
        try {
            int val = Integer.parseInt(safe(txt).trim());
            if (val <= 0) throw new NumberFormatException();
            return val;
        } catch (Exception e) {
            throw new IllegalArgumentException(errMessage);
        }
    }

    private int parseOptionalInt(String txt) {
        String v = safe(txt).trim();
        if (v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("ID utilisateur invalide.");
        }
    }

    private String abbreviate(String value, int max) {
        String v = safe(value);
        if (v.length() <= max) return v;
        return v.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** Escapes a string for safe embedding inside a JSON string literal. */
    private String escJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ─────────────────────────────────────────────────────────────────
    //  ANALYSE MARKETING – dialog de résultats
    // ─────────────────────────────────────────────────────────────────
    private void showAnalysisDialog(JsonObject result,
                                    TextField tfTitre,
                                    TextField tfType,
                                    TextField tfPourcentage,
                                    TextArea  taDesc,
                                    Runnable  onRerun) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("📊 Analyse Marketing IA");

        // ── helpers ──
        java.util.function.Function<String, String> str = key -> {
            try { return result.has(key) && !result.get(key).isJsonNull()
                    ? result.get(key).getAsString() : "—"; } catch(Exception e){ return "—"; }
        };
        java.util.function.Function<String, Integer> num = key -> {
            try { return result.has(key) ? result.get(key).getAsInt() : 0; } catch(Exception e){ return 0; }
        };
        java.util.function.Function<String, List<String>> arr = key -> {
            List<String> list = new java.util.ArrayList<>();
            try { if(result.has(key)) {
                JsonArray a = result.getAsJsonArray(key);
                for(JsonElement el : a) list.add(el.getAsString());
            }} catch(Exception ignored){}
            return list;
        };

        // ── Score ──────────────────────────────────────────────
        int score = num.apply("score");
        String scoreColor = score >= 75 ? "#16a34a" : score >= 50 ? "#d97706" : "#dc2626";
        Label lblScore = new Label(score + " / 100");
        lblScore.setStyle("-fx-font-size: 42px; -fx-font-weight: 900; -fx-text-fill: " + scoreColor + ";");
        Label lblEval = new Label(str.apply("evaluation"));
        lblEval.setStyle("-fx-text-fill: #555; -fx-font-size: 13px;");
        lblEval.setWrapText(true);

        // ── Score bar ──────────────────────────────────────────
        javafx.scene.layout.StackPane barBg = new javafx.scene.layout.StackPane();
        barBg.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 999; -fx-pref-height: 10; -fx-min-height: 10;");
        barBg.setMaxWidth(Double.MAX_VALUE);
        javafx.scene.layout.HBox barFill = new javafx.scene.layout.HBox();
        barFill.setStyle("-fx-background-color: " + scoreColor + "; -fx-background-radius: 999; -fx-pref-height: 10; -fx-min-height: 10;");
        barFill.setPrefWidth(4.0 * score); // 400px max at score=100
        javafx.scene.layout.StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
        barBg.getChildren().add(barFill);

        VBox scoreBox = new VBox(4, lblScore, lblEval, barBg);
        scoreBox.setStyle("-fx-background-color: #f8faff; -fx-background-radius: 14; -fx-padding: 14; -fx-border-color: #e2e8f0; -fx-border-radius: 14;");

        // ── Section builder ────────────────────────────────────
        java.util.function.BiFunction<String, List<String>, VBox> listSection = (title, items) -> {
            VBox box = new VBox(6);
            box.setStyle("-fx-background-color: #f8faff; -fx-background-radius: 12; -fx-padding: 12; -fx-border-color: #e2e8f0; -fx-border-radius: 12;");
            Label lbl = new Label(title);
            lbl.setStyle("-fx-font-weight: 900; -fx-font-size: 13px; -fx-text-fill: #0f2a44;");
            box.getChildren().add(lbl);
            if (items.isEmpty()) {
                Label none = new Label("—");
                none.setStyle("-fx-text-fill: #888;");
                box.getChildren().add(none);
            } else {
                for (String item : items) {
                    Label li = new Label("• " + item);
                    li.setWrapText(true);
                    li.setStyle("-fx-text-fill: #334155; -fx-font-size: 12.5px;");
                    box.getChildren().add(li);
                }
            }
            return box;
        };

        VBox weakBox  = listSection.apply("⚠  Points faibles",           arr.apply("points_faibles"));
        VBox amelBox  = listSection.apply("💡 Améliorations conseillées", arr.apply("ameliorations"));

        // ── Offre optimisée ────────────────────────────────────
        JsonObject opt = result.has("offre_optimisee") && result.get("offre_optimisee").isJsonObject()
                ? result.getAsJsonObject("offre_optimisee") : new JsonObject();
        String optTitre = opt.has("titre")       ? opt.get("titre").getAsString()       : "";
        String optDesc  = opt.has("description") ? opt.get("description").getAsString() : "";
        String optPct   = opt.has("pourcentage_suggere") ? opt.get("pourcentage_suggere").getAsString() : "";

        VBox optBox = new VBox(6);
        optBox.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 12; -fx-padding: 12; -fx-border-color: #bbf7d0; -fx-border-radius: 12;");
        Label optTitle = new Label("✨ Version optimisée suggérée");
        optTitle.setStyle("-fx-font-weight: 900; -fx-font-size: 13px; -fx-text-fill: #15803d;");
        Label optTitreL = new Label("Titre : " + optTitre);
        optTitreL.setWrapText(true);
        optTitreL.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #166534;");
        Label optPctL = new Label("Réduction suggérée : " + optPct + "%");
        optPctL.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #166534;");
        Label optDescL = new Label("Description : " + optDesc);
        optDescL.setWrapText(true);
        optDescL.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #166534;");
        Button btnApply = new Button("✔ Appliquer ces suggestions");
        btnApply.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: 900; -fx-background-radius: 10; -fx-padding: 8 14; -fx-cursor: hand;");
        btnApply.setOnAction(ev -> {
            if (!optTitre.isEmpty()) tfTitre.setText(optTitre);
            if (!optPct.isEmpty())   tfPourcentage.setText(optPct);
            if (!optDesc.isEmpty())  taDesc.setText(optDesc);
            dlg.close();
        });
        optBox.getChildren().addAll(optTitle, optTitreL, optPctL, optDescL, btnApply);

        // ── Diffusion ──────────────────────────────────────────
        JsonObject diff = result.has("diffusion") && result.get("diffusion").isJsonObject()
                ? result.getAsJsonObject("diffusion") : new JsonObject();
        List<String> canaux = new java.util.ArrayList<>();
        if (diff.has("canaux")) for(JsonElement c : diff.getAsJsonArray("canaux")) canaux.add(c.getAsString());
        String timing = diff.has("timing")        ? diff.get("timing").getAsString()        : "—";
        String cible  = diff.has("public_cible")  ? diff.get("public_cible").getAsString()  : "—";

        VBox diffBox = new VBox(6);
        diffBox.setStyle("-fx-background-color: #eff6ff; -fx-background-radius: 12; -fx-padding: 12; -fx-border-color: #bfdbfe; -fx-border-radius: 12;");
        Label diffTitle = new Label("📣 Recommandations de diffusion");
        diffTitle.setStyle("-fx-font-weight: 900; -fx-font-size: 13px; -fx-text-fill: #1d4ed8;");
        Label lblCanaux  = new Label("Canaux : " + String.join(", ", canaux));
        lblCanaux.setWrapText(true); lblCanaux.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1e3a8a;");
        Label lblTiming  = new Label("⏰ Timing optimal : " + timing);
        lblTiming.setWrapText(true); lblTiming.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1e3a8a;");
        Label lblCible   = new Label("🎯 Public cible : " + cible);
        lblCible.setWrapText(true);  lblCible.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1e3a8a;");
        diffBox.getChildren().addAll(diffTitle, lblCanaux, lblTiming, lblCible);

        // ── Footer buttons ─────────────────────────────────────
        Button btnRerun = new Button("🔄 Relancer l'analyse");
        btnRerun.setStyle("-fx-background-color: linear-gradient(to right,#6d28d9,#7c3aed); -fx-text-fill: white; -fx-font-weight: 900; -fx-background-radius: 10; -fx-padding: 9 16; -fx-cursor: hand;");
        btnRerun.setOnAction(ev -> { dlg.close(); onRerun.run(); });

        Button btnClose = new Button("Fermer");
        btnClose.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-weight: 900; -fx-background-radius: 10; -fx-padding: 9 16; -fx-cursor: hand;");
        btnClose.setOnAction(ev -> dlg.close());

        Region fsp = new Region(); HBox.setHgrow(fsp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnClose, fsp, btnRerun);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(14, 0, 0, 0));

        // ── Layout ─────────────────────────────────────────────
        Label header = new Label("📊 Analyse Marketing IA");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #0f2a44;");

        VBox body = new VBox(12, header, scoreBox, weakBox, amelBox, optBox, diffBox, footer);
        body.setPadding(new Insets(20));

        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(sp, 560, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        dlg.setScene(scene);
        dlg.setResizable(true);
        dlg.centerOnScreen();
        dlg.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Ouvre un dialogue permettant au restaurateur de saisir/coller le contenu
     * scanné du QR code (format: OFFRE=X|USER=Y|TS=Z).
     * Le code promo correspondant est recherché puis marqué UTILISÉ.
     */
    private void openScanQrDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Valider un code promo");

        // ── Titre ──────────────────────────────────────────────
        Label headline = new Label("Validation code promo");
        headline.getStyleClass().add("dialogTitle");

        // ── Saisie du numéro ───────────────────────────────────
        Label hint = new Label("Entrez le numéro donné par le scan du QR code :");
        hint.getStyleClass().add("cardLine");

        TextField tfCode = new TextField();
        tfCode.setPromptText("Numéro du code promo (ex: 42)");
        tfCode.setMaxWidth(Double.MAX_VALUE);

        Button btnRechercher = new Button("Rechercher");
        btnRechercher.getStyleClass().add("card-btn");
        btnRechercher.setDefaultButton(true);

        HBox searchRow = new HBox(10, tfCode, btnRechercher);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfCode, Priority.ALWAYS);

        // ── Zone d'information sur le code trouvé ──────────────
        VBox infoBox = new VBox(8);
        infoBox.setVisible(false);
        infoBox.setManaged(false);
        infoBox.setStyle("-fx-background-color: #f0f4ff; -fx-background-radius: 8; -fx-padding: 12;");

        Label lblBadge   = new Label();
        Label lblOffre   = new Label();
        Label lblUser    = new Label();
        Label lblDates   = new Label();
        Label lblStatut  = new Label();
        for (Label l : new Label[]{lblBadge, lblOffre, lblUser, lblDates, lblStatut}) {
            l.getStyleClass().add("cardLine");
            l.setWrapText(true);
        }
        lblBadge.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        infoBox.getChildren().addAll(lblBadge, lblOffre, lblUser, lblDates, lblStatut);

        // ── Message de résultat ────────────────────────────────
        Label resultLabel = new Label();
        resultLabel.setWrapText(true);
        resultLabel.getStyleClass().add("cardMeta");
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);

        // ── Boutons du bas ──────────────────────────────────────
        Button btnCancel  = new Button("Fermer");
        btnCancel.getStyleClass().add("card-btn");
        btnCancel.setOnAction(e -> dialog.close());

        Button btnConfirm = new Button("✔  Confirmer l'utilisation");
        btnConfirm.getStyleClass().add("card-btn");
        btnConfirm.setVisible(false);
        btnConfirm.setManaged(false);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnCancel, sp, btnConfirm);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        // ── Conteneur principal ────────────────────────────────
        VBox content = new VBox(14, headline, hint, searchRow, infoBox, resultLabel, footer);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("dialogRoot");

        // ── Wrapper pour codePromo trouvé ─────────────────────
        final CodePromo[] foundPromo = {null};

        // ── Action Rechercher ──────────────────────────────────
        Runnable doSearch = () -> {
            String raw = safe(tfCode.getText()).trim();
            // reset état
            infoBox.setVisible(false);
            infoBox.setManaged(false);
            resultLabel.setVisible(false);
            resultLabel.setManaged(false);
            btnConfirm.setVisible(false);
            btnConfirm.setManaged(false);
            foundPromo[0] = null;

            if (raw.isEmpty()) {
                resultLabel.setText("⚠  Entrez le numéro du code promo.");
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                dialog.sizeToScene();
                return;
            }

            String decoded;
            try { decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8); }
            catch (Exception ex) { decoded = raw; }

            try {
                CodePromo promo = null;

                if (decoded.matches("\\d+")) {
                    // Format actuel : simple ID
                    int id = Integer.parseInt(decoded);
                    promo = codePromoService.trouverParId(id);
                    if (promo == null) {
                        resultLabel.setText("✗  Aucun code promo trouvé avec le numéro " + id + ".");
                        resultLabel.setVisible(true);
                        resultLabel.setManaged(true);
                        dialog.sizeToScene();
                        return;
                    }
                } else {
                    // Rétrocompat OFFRE=X|USER=Y
                    int offreId = 0, userId = 0;
                    for (String part : decoded.split("\\|")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            switch (kv[0].trim().toUpperCase()) {
                                case "OFFRE" -> offreId = Integer.parseInt(kv[1].trim());
                                case "USER"  -> userId  = Integer.parseInt(kv[1].trim());
                            }
                        }
                    }
                    if (offreId <= 0) {
                        resultLabel.setText("⚠  Format non reconnu. Entrez le numéro donné par le scanner.");
                        resultLabel.setVisible(true);
                        resultLabel.setManaged(true);
                        dialog.sizeToScene();
                        return;
                    }
                    promo = codePromoService.trouverCodePromoActif(offreId, userId);
                    if (promo == null) {
                        resultLabel.setText("✗  Aucun code promo trouvé pour cette offre/utilisateur.");
                        resultLabel.setVisible(true);
                        resultLabel.setManaged(true);
                        dialog.sizeToScene();
                        return;
                    }
                }

                // Afficher les détails
                foundPromo[0] = promo;
                String statut = safe(promo.getStatut()).trim();
                boolean dejUtilise = "UTILISÉ".equalsIgnoreCase(statut);
                java.sql.Date today = new java.sql.Date(System.currentTimeMillis());
                boolean expire = promo.getDate_expiration() != null && promo.getDate_expiration().before(today);

                lblBadge.setText("Code promo  #" + promo.getId());
                lblOffre.setText("Offre :  #" + promo.getOffre_id());
                lblUser.setText("Utilisateur :  " + (promo.getUser_id() > 0 ? "#" + promo.getUser_id() : "—"));
                lblDates.setText("Généré : " + formatDate(promo.getDate_generation())
                        + "    Expire : " + formatDate(promo.getDate_expiration()));
                lblStatut.setText("Statut :  " + (statut.isEmpty() ? "—" : statut));

                infoBox.setVisible(true);
                infoBox.setManaged(true);

                if (dejUtilise) {
                    resultLabel.setText("⚠  Ce code promo a déjà été utilisé.");
                    resultLabel.setVisible(true);
                    resultLabel.setManaged(true);
                } else if (expire) {
                    resultLabel.setText("⚠  Ce code promo est expiré (" + formatDate(promo.getDate_expiration()) + ").");
                    resultLabel.setVisible(true);
                    resultLabel.setManaged(true);
                } else {
                    btnConfirm.setVisible(true);
                    btnConfirm.setManaged(true);
                }
                dialog.sizeToScene();

            } catch (NumberFormatException ex) {
                resultLabel.setText("⚠  Numéro invalide.");
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                dialog.sizeToScene();
            } catch (SQLException ex) {
                resultLabel.setText("✗  Erreur BD : " + safe(ex.getMessage()));
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                dialog.sizeToScene();
            }
        };

        btnRechercher.setOnAction(e -> doSearch.run());
        tfCode.setOnAction(e -> doSearch.run());

        // ── Action Confirmer ───────────────────────────────────
        btnConfirm.setOnAction(e -> {
            CodePromo promo = foundPromo[0];
            if (promo == null) return;
            try {
                codePromoService.marquerUtilise(promo.getId());
                infoBox.setVisible(false);
                infoBox.setManaged(false);
                btnConfirm.setVisible(false);
                btnConfirm.setManaged(false);
                resultLabel.setText("✔  Code promo #" + promo.getId() + " marqué comme UTILISÉ avec succès.");
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                tfCode.clear();
                foundPromo[0] = null;
                reloadAll();
                dialog.sizeToScene();
            } catch (SQLException ex) {
                resultLabel.setText("✗  Erreur BD : " + safe(ex.getMessage()));
                resultLabel.setVisible(true);
                resultLabel.setManaged(true);
                dialog.sizeToScene();
            }
        });

        Scene scene = new Scene(content, 480, 220);
        scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }
}
