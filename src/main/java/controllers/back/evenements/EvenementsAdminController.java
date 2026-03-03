package controllers.back.evenements;

// ══════════════════════════════════════════════════════════════════
//  CORRECTIFS À APPLIQUER dans EvenementsAdminController.java
//
//  PROBLÈME 1 → résolu par fix_ticket_unique.sql (côté DB)
//               "Duplicate entry for key 'uk_ticket_inscription'"
//
//  PROBLÈME 2 → résolu ici : paiement ne s'affiche pas immédiatement
//
//  Remplace UNIQUEMENT ces 3 méthodes dans ton controller existant :
//    1. showPanelTickets()
//    2. onGenererTicket()
//    3. reloadTickets()           (supprime aussi dans btnDel)
//  Et AJOUTE la nouvelle méthode refreshTicketsHeader()
// ══════════════════════════════════════════════════════════════════

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.evenements.Evenement;
import models.evenements.Inscription;
import models.evenements.Ticket;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import services.evenements.TicketService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public class EvenementsAdminController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            cardsPane.setPrefTileWidth(Math.max(260, (w - 12 - 28) / 3.0));
        });
        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0)
            cardsPane.setPrefTileWidth(Math.max(260, (b.getWidth() - 12 - 28) / 3.0));
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

        Label title = new Label(safeStr(e.getTitre()));
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "📅 " + formatLDT(e.getDateDebut()) + "  →  " + formatLDT(e.getDateFin())
                        + "\n👥 " + e.getCapaciteMax() + " places   •   💰 " + e.getPrix() + " TND");
        meta.getStyleClass().add("card-meta");
        meta.setWrapText(true);

        HBox chips = new HBox(8);
        Label chipStatut = new Label(safeStr(e.getStatut()));
        chipStatut.getStyleClass().add("card-chip");
        chipStatut.setStyle(chipStatutStyle(e.getStatut()));
        Label chipType = new Label(safeStr(e.getType()));
        chipType.getStyleClass().add("card-chip");
        chips.getChildren().addAll(chipStatut, chipType);

        Label lieu = new Label("📍 " + (e.getLieuId() != null ? "Lieu #" + e.getLieuId() : "Sans lieu"));
        lieu.getStyleClass().add("card-muted");

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
        card.getChildren().addAll(title, meta, chips, lieu, actions);
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

        TextField tfTitre     = new TextField(); tfTitre.setPromptText("Min. 3 caractères");
        TextArea  taDesc      = new TextArea();  taDesc.setPromptText("Description (optionnelle)"); taDesc.setPrefRowCount(3);

        // Date Début avec DatePicker + temps
        DatePicker dpDateDebut = new DatePicker(); dpDateDebut.setPrefWidth(150);
        Spinner<Integer> spHeureDebut = new Spinner<>(0, 23, 10); spHeureDebut.setPrefWidth(60); spHeureDebut.setEditable(true);
        Spinner<Integer> spMinuteDebut = new Spinner<>(0, 59, 0); spMinuteDebut.setPrefWidth(60); spMinuteDebut.setEditable(true);
        HBox hbDateDebut = new HBox(10, dpDateDebut, new Label("Heure:"), spHeureDebut, new Label("Min:"), spMinuteDebut);
        hbDateDebut.setAlignment(Pos.CENTER_LEFT);

        // Date Fin avec DatePicker + temps
        DatePicker dpDateFin = new DatePicker(); dpDateFin.setPrefWidth(150);
        Spinner<Integer> spHeureFin = new Spinner<>(0, 23, 18); spHeureFin.setPrefWidth(60); spHeureFin.setEditable(true);
        Spinner<Integer> spMinuteFin = new Spinner<>(0, 59, 0); spMinuteFin.setPrefWidth(60); spMinuteFin.setEditable(true);
        HBox hbDateFin = new HBox(10, dpDateFin, new Label("Heure:"), spHeureFin, new Label("Min:"), spMinuteFin);
        hbDateFin.setAlignment(Pos.CENTER_LEFT);

        TextField tfCapacite  = new TextField(); tfCapacite.setPromptText("Entier > 0");
        TextField tfPrix      = new TextField(); tfPrix.setPromptText("Décimal ≥ 0  ex: 25.5");
        ComboBox<String> cbStatut = new ComboBox<>();
        cbStatut.setItems(FXCollections.observableArrayList("OUVERT", "FERME", "ANNULE"));
        cbStatut.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> cbType = new ComboBox<>();
        cbType.setItems(FXCollections.observableArrayList("PUBLIC", "PRIVE"));
        cbType.setMaxWidth(Double.MAX_VALUE);
        TextField tfLieuId   = new TextField(); tfLieuId.setPromptText("ID lieu (optionnel)");
        TextField tfImageUrl  = new TextField(); tfImageUrl.setPromptText("URL image (optionnel)");
        Label lblErreur = new Label();
        lblErreur.setStyle("-fx-text-fill:#dc2626;-fx-font-weight:bold;-fx-font-size:12px;");
        lblErreur.setWrapText(true);

        tfCapacite.textProperty().addListener((obs, o, n) -> { if (!n.matches("\\d*")) tfCapacite.setText(o); });
        tfPrix.textProperty().addListener((obs, o, n)     -> { if (!n.matches("\\d*\\.?\\d*")) tfPrix.setText(o); });
        tfLieuId.textProperty().addListener((obs, o, n)   -> { if (!n.matches("\\d*")) tfLieuId.setText(o); });

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
            tfLieuId.setText(existing.getLieuId() != null ? String.valueOf(existing.getLieuId()) : "");
            tfImageUrl.setText(safeStr(existing.getImageUrl()));
        } else {
            cbStatut.getSelectionModel().select("OUVERT");
            cbType.getSelectionModel().select("PUBLIC");
            tfCapacite.setText("50"); tfPrix.setText("0");
            // Initialiser les dates par défaut pour un nouvel événement
            dpDateDebut.setValue(LocalDate.now());
            spHeureDebut.getValueFactory().setValue(10);
            spMinuteDebut.getValueFactory().setValue(0);
            dpDateFin.setValue(LocalDate.now());
            spHeureFin.getValueFactory().setValue(18);
            spMinuteFin.getValueFactory().setValue(0);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(20));
        int r = 0;
        addRow(grid, r++, "Titre *",        tfTitre);
        addRow(grid, r++, "Description",    taDesc);
        addRow(grid, r++, "Date début *",   hbDateDebut);
        addRow(grid, r++, "Date fin *",     hbDateFin);
        addRow(grid, r++, "Capacité max *", tfCapacite);
        addRow(grid, r++, "Prix (TND) *",   tfPrix);
        addRow(grid, r++, "Statut *",       cbStatut);
        addRow(grid, r++, "Type *",         cbType);
        addRow(grid, r++, "ID Lieu",        tfLieuId);
        addRow(grid, r++, "Image URL",      tfImageUrl);
        grid.add(lblErreur, 0, r, 2, 1); r++;

        Button btnSave = new Button("Enregistrer");
        Button btnCancel = new Button("Annuler");
        btnSave.setStyle("-fx-background-color:#1e3a5f;-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:8;-fx-padding:8 20;");
        btnCancel.setStyle("-fx-background-radius:8;-fx-padding:8 16;");
        HBox actionsBox = new HBox(10, btnSave, btnCancel);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        grid.add(actionsBox, 0, r, 2, 1);
        btnCancel.setOnAction(ev -> dialog.close());

        btnSave.setOnAction(ev -> {
            lblErreur.setText("");
            String titre = textOf(tfTitre);
            if (titre.isEmpty())    { showFieldError(lblErreur, tfTitre,    "Le titre est obligatoire."); return; }
            if (titre.length() < 3) { showFieldError(lblErreur, tfTitre,    "Titre : min 3 caractères."); return; }

            // Validation et récupération des dates
            LocalDateTime dateDebut;
            try {
                if (dpDateDebut.getValue() == null) { showFieldError(lblErreur, null, "Sélectionnez une date de début."); return; }
                int heure = spHeureDebut.getValue() != null ? spHeureDebut.getValue() : 0;
                int minute = spMinuteDebut.getValue() != null ? spMinuteDebut.getValue() : 0;
                dateDebut = LocalDateTime.of(dpDateDebut.getValue(), LocalTime.of(heure, minute));
            } catch (Exception ex) { showFieldError(lblErreur, null, "Date début invalide."); return; }

            LocalDateTime dateFin;
            try {
                if (dpDateFin.getValue() == null) { showFieldError(lblErreur, null, "Sélectionnez une date de fin."); return; }
                int heure = spHeureFin.getValue() != null ? spHeureFin.getValue() : 0;
                int minute = spMinuteFin.getValue() != null ? spMinuteFin.getValue() : 0;
                dateFin = LocalDateTime.of(dpDateFin.getValue(), LocalTime.of(heure, minute));
            } catch (Exception ex) { showFieldError(lblErreur, null, "Date fin invalide."); return; }

            if (!dateFin.isAfter(dateDebut)) { showFieldError(lblErreur, null, "Date fin doit être après la date début."); return; }
            int capacite;
            try { capacite = Integer.parseInt(textOf(tfCapacite)); if (capacite <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { showFieldError(lblErreur, tfCapacite, "Capacité : entier > 0."); return; }
            if (capacite > 100_000) { showFieldError(lblErreur, tfCapacite, "Capacité max 100 000."); return; }
            double prix;
            try { String s = textOf(tfPrix); prix = s.isEmpty() ? 0.0 : Double.parseDouble(s); if (prix < 0) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { showFieldError(lblErreur, tfPrix, "Prix : nombre ≥ 0"); return; }
            if (cbStatut.getValue() == null) { lblErreur.setText("❌ Sélectionnez un statut."); return; }
            if (cbType.getValue()   == null) { lblErreur.setText("❌ Sélectionnez un type.");   return; }
            Integer lieuId = null;
            String lieuStr = textOf(tfLieuId);
            if (!lieuStr.isEmpty()) {
                try { lieuId = Integer.parseInt(lieuStr); if (lieuId <= 0) throw new NumberFormatException(); }
                catch (NumberFormatException ex) { showFieldError(lblErreur, tfLieuId, "ID Lieu : entier > 0 ou laisser vide."); return; }
            }
            try {
                if (isEdit) {
                    existing.setTitre(titre);        existing.setDescription(textOf(taDesc));
                    existing.setDateDebut(dateDebut); existing.setDateFin(dateFin);
                    existing.setCapaciteMax(capacite); existing.setPrix(prix);
                    existing.setStatut(cbStatut.getValue()); existing.setType(cbType.getValue());
                    existing.setLieuId(lieuId);      existing.setImageUrl(textOf(tfImageUrl));
                    evenementService.update(existing);
                } else {
                    Evenement toAdd = new Evenement();
                    toAdd.setTitre(titre);        toAdd.setDescription(textOf(taDesc));
                    toAdd.setDateDebut(dateDebut); toAdd.setDateFin(dateFin);
                    toAdd.setCapaciteMax(capacite); toAdd.setPrix(prix);
                    toAdd.setStatut(cbStatut.getValue()); toAdd.setType(cbType.getValue());
                    toAdd.setLieuId(lieuId);      toAdd.setImageUrl(textOf(tfImageUrl));
                    evenementService.add(toAdd);
                }
                loadData(); dialog.close();
            } catch (IllegalArgumentException ex) { lblErreur.setText("❌ " + ex.getMessage()); }
            catch (Exception ex) { showError("Erreur", "Enregistrement impossible", ex.getMessage()); }
        });

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true); sp.setStyle("-fx-background-color:white;-fx-background:white;");
        dialog.setScene(new Scene(sp, 580, 680));
        dialog.centerOnScreen(); dialog.showAndWait();
    }

    private void showFieldError(Label lbl, Control field, String msg) {
        lbl.setText("❌ " + msg); if (field != null) field.requestFocus();
    }

    private void addRow(GridPane grid, int row, String labelText, Node control) {
        Label lbl = new Label(labelText + " :"); lbl.setStyle("-fx-font-weight:bold;-fx-text-fill:#1e3a5f;");
        grid.add(lbl, 0, row); grid.add(control, 1, row); GridPane.setHgrow(control, Priority.ALWAYS);
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
        breadcrumbInscription.setText("Inscription #" + ins.getId() + " — User #" + ins.getUserId());
        ticketInscInfo.setText("Inscription #" + ins.getId()
                + "  •  User #" + ins.getUserId() + "  •  " + safeStr(ins.getStatut()));
        // ✅ On affiche le paiement initial depuis l'objet
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
        VBox row = new VBox(8);
        row.getStyleClass().add("lieu-card");
        int nbTickets = ticketService.countByInscriptionId(ins.getId());
        Label title = new Label("👤 User #" + ins.getUserId()
                + "   •   Statut : " + safeStr(ins.getStatut())
                + "   •   🎫 " + nbTickets + " ticket(s)");
        title.getStyleClass().add("card-title");
        String dateStr = ins.getDateCreation() != null
                ? ins.getDateCreation().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
        Label sub = new Label("💰 Paiement : " + safeStr(ins.getPaiement()) + " TND"
                + "   •   Créé le : " + dateStr);
        sub.getStyleClass().add("card-muted");
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
        row.getChildren().addAll(title, sub, actions);
        return row;
    }

    @FXML
    public void onAjouterInscription() {
        if (currentEvent == null) { showWarning("Aucun événement sélectionné."); return; }
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Ajouter inscription");
        d.setHeaderText("Événement : " + currentEvent.getTitre()
                + "\nPrix par ticket : " + currentEvent.getPrix() + " TND");
        d.setContentText("User ID (entier > 0) :");
        d.showAndWait().ifPresent(val -> {
            int userId;
            try { userId = Integer.parseInt(val.trim()); if (userId <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException ex) { showWarning("User ID invalide."); return; }
            try {
                inscriptionService.addInscription(currentEvent.getId(), userId, 0.0f);
                reloadInscriptions(); refreshPlacesInfo(); updateKpis(masterList);
            } catch (IllegalStateException ex) { showWarning(ex.getMessage()); }
            catch (Exception ex) { showError("Erreur", "Inscription impossible", ex.getMessage()); }
        });
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

    private String chipStatutStyle(String statut) {
        if (statut == null) return "";
        return switch (statut.toUpperCase()) {
            case "OUVERT" -> "-fx-background-color:rgba(34,197,94,0.18);-fx-text-fill:#15803d;";
            case "FERME"  -> "-fx-background-color:rgba(251,146,60,0.18);-fx-text-fill:#c2410c;";
            case "ANNULE" -> "-fx-background-color:rgba(220,38,38,0.18);-fx-text-fill:#b91c1c;";
            default       -> "";
        };
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
}