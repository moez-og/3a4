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
import models.users.User;
import services.offres.CodePromoService;
import services.offres.OffreService;

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

    @FXML private ComboBox<String> modeCombo;
    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField searchField;
    @FXML private Button btnAdd;

    @FXML private javafx.scene.control.ScrollPane cardsScrollOffres;
    @FXML private javafx.scene.control.ScrollPane cardsScrollCodes;
    @FXML private TilePane cardsPaneOffres;
    @FXML private TilePane cardsPaneCodes;

    private final OffreService offreService = new OffreService();
    private final CodePromoService codePromoService = new CodePromoService();

    private final ObservableList<Offre> offresList = FXCollections.observableArrayList();
    private final ObservableList<CodePromo> codesPromoList = FXCollections.observableArrayList();
    private final ObservableList<Lieu> lieuxList = FXCollections.observableArrayList();
    private final Map<Integer, String> lieuxLabelById = new HashMap<>();

    private User currentUser;
    private Node selectedCard;
    private Offre selectedOffre;
    private CodePromo selectedPromo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
        modeCombo.setItems(FXCollections.observableArrayList("Offres", "Codes promo"));
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

    private void reloadAll() {
        loadOffres();
        loadCodesPromo();
        updateKpis();
        render();
    }

    private void updateKpis() {
        if (kpiTotalOffres != null) kpiTotalOffres.setText(String.valueOf(offresList.size()));
        long actives = offresList.stream().filter(o -> "active".equalsIgnoreCase(safe(o.getStatut()).trim())).count();
        if (kpiActives != null) kpiActives.setText(String.valueOf(actives));
        if (kpiCodes != null) kpiCodes.setText(String.valueOf(codesPromoList.size()));
    }

    private void showMode(String mode) {
        boolean showOffres = !"Codes promo".equals(mode);
        cardsScrollOffres.setVisible(showOffres);
        cardsScrollOffres.setManaged(showOffres);
        cardsScrollCodes.setVisible(!showOffres);
        cardsScrollCodes.setManaged(!showOffres);
        btnAdd.setText(showOffres ? "Ajouter" : "Ajouter");
    }

    private void render() {
        String mode = safe(modeCombo.getValue());
        if ("Codes promo".equals(mode)) {
            renderPromoCards();
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

        Button del = new Button("Supprimer");
        del.getStyleClass().addAll("card-btn", "danger");
        del.setOnAction(e -> deletePromo(promo));

        HBox actions = new HBox(10, edit, del);
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

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnCancel, sp, btnSave);
        footer.setAlignment(Pos.CENTER_RIGHT);
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
        ComboBox<String> cbStatut = new ComboBox<>(FXCollections.observableArrayList("ACTIF", "EXPIRE", "DESACTIVE"));
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
