package controllers.back.sorties;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
import models.sorties.AnnonceSortie;
import models.users.User;
import services.sorties.AnnonceSortieService;
import utils.files.UploadStore;
import utils.geo.TunisiaGeo;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SortiesAdminController {

    @FXML private ScrollPane cardsScroll;
    @FXML private TilePane cardsPane;

    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField searchField;
    @FXML private Button btnAdd;

    @FXML private Label kpiTotal;
    @FXML private Label kpiOuvertes;
    @FXML private Label kpiAvenir;

    private final AnnonceSortieService service = new AnnonceSortieService();
    private final ObservableList<AnnonceSortie> masterList = FXCollections.observableArrayList();
    private final FilteredList<AnnonceSortie> filteredList = new FilteredList<>(masterList, p -> true);

    private User currentUser;

    private Node selectedCard = null;
    private double lastViewportW = -1;

    // Responsive card sizing
    private static final double CARD_MIN_W = 320;     // min width before we drop columns
    private static final double CARD_TARGET_W = 340;  // comfortable width on normal screens

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    // Villes Tunisie: via TunisiaGeo (tolère accents et variantes)
    private static final List<String> ACTIVITY_PRESETS = List.of(
            "Marche", "Footing", "Pique-nique", "Sortie café", "Restaurant", "Randonnée", "Autre"
    );

    // Régions / délégations : toutes les villes (Tunisie complète)
    private List<String> regionsForVille(String ville) {
        return TunisiaGeo.regionsForVille(ville);
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
    }

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupSearchFilter();
        setupButtons();
        setupResponsiveTiles();
        loadData();
    }

    private void setupFilterCombo() {
        filterCombo.setItems(FXCollections.observableArrayList("Titre", "Ville", "Activité", "Statut"));
        filterCombo.getSelectionModel().select("Titre");
        filterCombo.valueProperty().addListener((obs, o, n) -> renderCards(filteredList));
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, o, n) -> {
            filteredList.setPredicate(a -> matchesFilter(a, n));
            renderCards(filteredList);
        });
    }

    private boolean matchesFilter(AnnonceSortie a, String q) {
        String query = (q == null) ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return true;

        String mode = filterCombo.getValue() == null ? "Titre" : filterCombo.getValue();

        return switch (mode) {
            case "Ville" -> contains(a.getVille(), query) || contains(a.getLieuTexte(), query);
            case "Activité" -> contains(a.getTypeActivite(), query);
            case "Statut" -> contains(a.getStatut(), query);
            default -> contains(a.getTitre(), query) || contains(a.getDescription(), query);
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
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS); // écran stable

        // Safe defaults (avoid weird initial layout before viewport is known)
        cardsPane.setPrefColumns(1);
        cardsPane.setPrefTileWidth(CARD_TARGET_W);

        cardsScroll.viewportBoundsProperty().addListener((obs, oldB, b) -> {
            if (b == null) return;
            double viewportW = b.getWidth();
            if (lastViewportW > 0 && Math.abs(viewportW - lastViewportW) < 0.5) return;
            lastViewportW = viewportW;

            applyResponsiveGrid(viewportW);
        });

        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) {
            applyResponsiveGrid(b.getWidth());
        }
    }

    /**
     * Fixe le problème "cartes superposées" quand la fenêtre est petite:
     * - calcule le nombre de colonnes qui FIT réellement dans le viewport
     * - impose une largeur de tuile cohérente (>= CARD_MIN_W)
     */
    private void applyResponsiveGrid(double viewportW) {
        // TilePane padding via CSS: .cards-pane { -fx-padding: 6; }
        double padding = 12; // left + right
        double gap = cardsPane.getHgap() > 0 ? cardsPane.getHgap() : 14;
        double available = Math.max(0, viewportW - padding);

        int cols = (int) Math.floor((available + gap) / (CARD_MIN_W + gap));
        cols = Math.max(1, Math.min(4, cols));

        double tileW = (available - gap * (cols - 1)) / cols;
        if (tileW < CARD_MIN_W) {
            cols = 1;
            tileW = available;
        }

        cardsPane.setPrefColumns(cols);
        cardsPane.setPrefTileWidth(tileW);
    }

    private void loadData() {
        try {
            masterList.clear();
            List<AnnonceSortie> all = service.getAll();
            masterList.addAll(all);

            updateKpis(all);
            renderCards(filteredList);

        } catch (Exception e) {
            showError("Erreur", "Chargement des sorties impossible", safe(e.getMessage()));
        }
    }

    private void updateKpis(List<AnnonceSortie> all) {
        if (kpiTotal != null) kpiTotal.setText(String.valueOf(all.size()));

        long opened = all.stream().filter(a -> "OUVERTE".equalsIgnoreCase(safe(a.getStatut()))).count();
        if (kpiOuvertes != null) kpiOuvertes.setText(String.valueOf(opened));

        LocalDateTime now = LocalDateTime.now();
        long futur = all.stream().filter(a -> a.getDateSortie() != null && a.getDateSortie().isAfter(now)).count();
        if (kpiAvenir != null) kpiAvenir.setText(String.valueOf(futur));
    }

    private void renderCards(List<AnnonceSortie> list) {
        cardsPane.getChildren().clear();
        selectedCard = null;

        for (AnnonceSortie a : list) {
            cardsPane.getChildren().add(createCard(a));
        }

        if (list.isEmpty()) {
            Label empty = new Label("Aucune annonce trouvée.");
            empty.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 800;");
            cardsPane.getChildren().add(empty);
        }
    }

    private Node createCard(AnnonceSortie a) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortie-card");

        // IMPORTANT: make the card truly resizable; otherwise TilePane will overflow and look "superposed"
        card.setMinWidth(CARD_MIN_W);
        card.prefWidthProperty().bind(cardsPane.prefTileWidthProperty());
        card.maxWidthProperty().bind(cardsPane.prefTileWidthProperty());

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");
        imgWrap.setMaxWidth(Double.MAX_VALUE);

        ImageView iv = new ImageView();
        // bind to container width => responsive
        iv.fitWidthProperty().bind(imgWrap.widthProperty());
        iv.setFitHeight(170);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);

        Rectangle clip = new Rectangle(CARD_TARGET_W, 170);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(iv.fitWidthProperty());
        iv.setClip(clip);

        iv.setImage(loadImageOrFallback(a.getImageUrl()));
        imgWrap.getChildren().add(iv);

        Label statut = new Label(safe(a.getStatut()));
        statut.getStyleClass().addAll("statusChip", "status-" + safe(a.getStatut()).toLowerCase());
        StackPane.setAlignment(statut, Pos.TOP_LEFT);
        StackPane.setMargin(statut, new Insets(10, 0, 0, 10));
        imgWrap.getChildren().add(statut);

        Label title = new Label(safe(a.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        Label meta = new Label(
                safe(a.getVille()) + " • " + safe(a.getLieuTexte()) + " • " + safe(a.getTypeActivite())
        );
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        String when = a.getDateSortie() == null ? "" : DT_FMT.format(a.getDateSortie());
        String budget = a.getBudgetMax() <= 0 ? "Aucun budget" : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");
        Label line = new Label("📅 " + when + "   •   💰 " + budget + "   •   👥 " + a.getNbPlaces() + " places");
        line.getStyleClass().add("cardLine");
        line.setWrapText(true);

        Label meet = new Label("🤝 " + safe(a.getPointRencontre()));
        meet.getStyleClass().add("cardLine");
        meet.setWrapText(true);

        Button quickEdit = new Button("Modifier");
        Button quickDelete = new Button("Supprimer");
        quickEdit.getStyleClass().add("card-btn");
        quickDelete.getStyleClass().addAll("card-btn", "danger");

        quickEdit.setOnAction(e -> openEditor(a));
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

        HBox actions = new HBox(10, quickEdit, quickDelete);
        actions.getStyleClass().add("card-actions");

        card.getChildren().addAll(imgWrap, title, meta, line, meet, actions);

        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> selectCard(card));
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2) openEditor(a);
        });

        return card;
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

        // --- Champs ---
        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de la sortie");

        TextArea taDesc = new TextArea();
        taDesc.setPromptText("Description (optionnel)");
        taDesc.setPrefRowCount(3);

        ComboBox<String> cbVille = new ComboBox<>(FXCollections.observableArrayList(TunisiaGeo.villes()));
        cbVille.setPromptText("Ville");

        // Région dépendante de la ville
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

        ComboBox<String> cbAct = new ComboBox<>(FXCollections.observableArrayList(ACTIVITY_PRESETS));
        cbAct.setPromptText("Type d'activité");

        TextField tfAutreAct = new TextField();
        tfAutreAct.setPromptText("Écrire le type d'activité");
        tfAutreAct.setVisible(false);
        tfAutreAct.setManaged(false);

        cbAct.valueProperty().addListener((obs, o, n) -> {
            boolean other = "Autre".equalsIgnoreCase(String.valueOf(n));
            tfAutreAct.setVisible(other);
            tfAutreAct.setManaged(other);
        });

        DatePicker dpDate = new DatePicker();
        dpDate.setPromptText("Date");

        // Pas de date dans le passé
        dpDate.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;
                setDisable(item.isBefore(LocalDate.now()));
            }
        });

        Spinner<Integer> spHour = new Spinner<>(0, 23, 10);
        Spinner<Integer> spMin = new Spinner<>(0, 59, 0);
        spHour.setEditable(true);
        spMin.setEditable(true);
        spHour.setPrefWidth(110);
        spMin.setPrefWidth(110);

        CheckBox cbNoBudget = new CheckBox("Aucun budget");
        TextField tfBudget = new TextField();
        tfBudget.setPromptText("Budget max (TND)");

        cbNoBudget.selectedProperty().addListener((obs, o, n) -> {
            tfBudget.setDisable(Boolean.TRUE.equals(n));
            if (Boolean.TRUE.equals(n)) tfBudget.setText("0");
            else if ("0".equals(tfBudget.getText())) tfBudget.clear();
        });

        Spinner<Integer> spPlaces = new Spinner<>(1, 999, 5);
        spPlaces.setEditable(true);

        ComboBox<String> cbStatut = new ComboBox<>(FXCollections.observableArrayList("OUVERTE", "CLOTUREE", "ANNULEE"));
        cbStatut.getSelectionModel().select("OUVERTE");

        // --- Image upload + preview (vide par défaut) ---
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

        final String[] pickedPath = {null};

        // Preview image cache (évite rechargements lents)
        final String[] lastPreviewImagePath = {null};
        final Image[] cachedPreviewImage = {null};

        // ====== PREVIEW LIVE (colonne droite) ======
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

        Label previewTitle = new Label("Titre de la sortie");
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
                previewImgWrap,
                previewTitle,
                previewMeta,
                previewLine,
                previewMeet,
                previewDescTitle,
                previewDesc,
                liveHint
        );
        previewCard.getChildren().get(0).getStyleClass().add("previewHeader");

        // Ajuste largeur preview image selon la colonne
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

            boolean emptyImg = (cachedPreviewImage[0] == null);
            previewImgEmpty.setVisible(emptyImg);
            previewImgEmpty.setManaged(emptyImg);
        };

        Runnable updatePreviewNow = () -> {
            String t = safe(tfTitre.getText()).trim();
            previewTitle.setText(t.isEmpty() ? "Titre de la sortie" : t);

            String ville = safe(cbVille.getValue());
            String regionSel = cbRegion.getValue();
            String region = "";
            if (TunisiaGeo.REGION_OTHER.equals(regionSel)) region = safe(tfRegionAutre.getText()).trim();
            else region = safe(regionSel);

            String actSel = safe(cbAct.getValue());
            String act = "Autre".equalsIgnoreCase(actSel) ? safe(tfAutreAct.getText()).trim() : actSel;

            String meta = (ville.isEmpty() ? "Ville" : ville)
                    + " • " + (region.isEmpty() ? "Région" : region)
                    + " • " + (act.isEmpty() ? "Activité" : act);
            previewMeta.setText(meta);

            // date + heure
            LocalDate d = dpDate.getValue();
            LocalDateTime dt = null;
            if (d != null) {
                dt = LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue()));
            }
            String when = (dt == null) ? "—" : DT_FMT.format(dt);

            // budget
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
                    } catch (Exception ex) {
                        budget = "Budget invalide";
                    }
                }
            }

            int places = spPlaces.getValue();
            previewLine.setText("📅 " + when + "   •   💰 " + budget + "   •   👥 " + places + " places");

            String meet = safe(tfPoint.getText()).trim();
            previewMeet.setText("🤝 " + (meet.isEmpty() ? "—" : meet));

            String desc = safe(taDesc.getText()).trim();
            previewDesc.setText(desc.isEmpty() ? "—" : desc);

            // statut style
            String st = safe(cbStatut.getValue()).trim();
            if (st.isEmpty()) st = "OUVERTE";
            previewStatus.setText(st);

            previewStatus.getStyleClass().removeIf(c -> c.startsWith("status-"));
            previewStatus.getStyleClass().add("status-" + st.toLowerCase());

            // image (vide par défaut) — optimisé
            applyPreviewImageIfChanged.run();

            // validation live (désactiver save si invalide)
            String validation = validateLive(tfTitre, cbVille, cbRegion, tfRegionAutre, tfPoint,
                    cbAct, tfAutreAct, dpDate, spHour, spMin, cbNoBudget, tfBudget, cbStatut);

            liveHint.setText(validation);
            liveHint.setVisible(!validation.isEmpty());
            liveHint.setManaged(!validation.isEmpty());
        };

        // Debounce: évite un re-layout à chaque touche (le preview est costaud)
        PauseTransition debounce = new PauseTransition(Duration.millis(120));
        Runnable schedulePreview = () -> {
            debounce.stop();
            debounce.setOnFinished(ev -> updatePreviewNow.run());
            debounce.playFromStart();
        };

        btnPickImg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir une image");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp")
            );
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

        // --- Questions dynamiques ---
        VBox qBox = new VBox(8);
        qBox.getStyleClass().add("questionsBox");
        Button btnAddQ = new Button("+ Ajouter une question");
        btnAddQ.getStyleClass().add("ghostBtn");
        btnAddQ.setOnAction(e -> qBox.getChildren().add(buildQuestionRow("", schedulePreview)));

        // --- Prefill si edit ---
        if (isEdit) {
            tfTitre.setText(existing.getTitre());
            taDesc.setText(safe(existing.getDescription()));
            String villeToSelect = Optional.ofNullable(TunisiaGeo.matchVilleForSelection(existing.getVille())).orElse(existing.getVille());
            cbVille.getSelectionModel().select(villeToSelect);

            cbRegion.getItems().setAll(regionsForVille(villeToSelect));
            String reg = safe(existing.getLieuTexte());
            if (cbRegion.getItems().stream().anyMatch(x -> x.equalsIgnoreCase(reg))) {
                cbRegion.getSelectionModel().select(cbRegion.getItems().stream()
                        .filter(x -> x.equalsIgnoreCase(reg)).findFirst().orElse(null));
            } else {
                cbRegion.getSelectionModel().select(TunisiaGeo.REGION_OTHER);
                tfRegionAutre.setVisible(true);
                tfRegionAutre.setManaged(true);
                tfRegionAutre.setText(reg);
            }

            tfPoint.setText(existing.getPointRencontre());

            String act = safe(existing.getTypeActivite());
            String preset = ACTIVITY_PRESETS.stream().filter(p -> p.equalsIgnoreCase(act)).findFirst().orElse(null);
            if (preset != null) {
                cbAct.getSelectionModel().select(preset);
            } else {
                cbAct.getSelectionModel().select("Autre");
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
            cbStatut.getSelectionModel().select(safe(existing.getStatut()).isEmpty() ? "OUVERTE" : existing.getStatut());

            pickedPath[0] = existing.getImageUrl();
            imgPath.setText(shortPath(safe(existing.getImageUrl())));
            Image im = loadImageOrNull(existing.getImageUrl());
            imgPrev.setImage(im);
            boolean empty = (im == null);
            imgEmpty.setVisible(empty);
            imgEmpty.setManaged(empty);

            if (existing.getQuestions() != null) {
                for (String q : existing.getQuestions()) {
                    qBox.getChildren().add(buildQuestionRow(q, schedulePreview));
                }
            }
        }

        if (qBox.getChildren().isEmpty()) {
            qBox.getChildren().add(buildQuestionRow("", schedulePreview));
        }

        // --- Listeners preview live ---
        tfTitre.textProperty().addListener((a,b,c) -> schedulePreview.run());
        taDesc.textProperty().addListener((a,b,c) -> schedulePreview.run());
        cbVille.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        cbRegion.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        tfRegionAutre.textProperty().addListener((a,b,c) -> schedulePreview.run());
        tfPoint.textProperty().addListener((a,b,c) -> schedulePreview.run());
        cbAct.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        tfAutreAct.textProperty().addListener((a,b,c) -> schedulePreview.run());
        dpDate.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spHour.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        spMin.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        cbNoBudget.selectedProperty().addListener((a,b,c) -> schedulePreview.run());
        tfBudget.textProperty().addListener((a,b,c) -> schedulePreview.run());
        spPlaces.valueProperty().addListener((a,b,c) -> schedulePreview.run());
        cbStatut.valueProperty().addListener((a,b,c) -> schedulePreview.run());

        // --- Layout form ---
        Label headline = new Label(isEdit ? "Modifier une annonce" : "Créer une annonce");
        headline.getStyleClass().add("dialogTitle");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(48);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(52);
        grid.getColumnConstraints().addAll(c1, c2);

        int r = 0;
        grid.add(lab("Titre"), 0, r);
        grid.add(tfTitre, 1, r++);

        grid.add(lab("Ville"), 0, r);
        grid.add(cbVille, 1, r++);

        grid.add(lab("Lieu (région)"), 0, r);
        VBox regionWrap = new VBox(8, cbRegion, tfRegionAutre);
        grid.add(regionWrap, 1, r++);

        grid.add(lab("Point de rencontre"), 0, r);
        grid.add(tfPoint, 1, r++);

        grid.add(lab("Activité"), 0, r);
        VBox actWrap = new VBox(8, cbAct, tfAutreAct);
        grid.add(actWrap, 1, r++);

        grid.add(lab("Date + heure"), 0, r);
        HBox timeRow = new HBox(10, dpDate, new Label("Heure"), spHour, new Label("Min"), spMin);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(timeRow, 1, r++);

        grid.add(lab("Budget"), 0, r);
        HBox budgetRow = new HBox(10, cbNoBudget, tfBudget);
        budgetRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(budgetRow, 1, r++);

        grid.add(lab("Nombre de places"), 0, r);
        grid.add(spPlaces, 1, r++);

        grid.add(lab("Statut"), 0, r);
        grid.add(cbStatut, 1, r++);

        VBox imgBox = new VBox(10, imgWrap, new HBox(10, btnPickImg, imgPath));
        ((HBox) imgBox.getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);

        VBox descBox = new VBox(8, lab("Description"), taDesc);
        VBox qWrap = new VBox(8, lab("Questions (optionnel)"), qBox, btnAddQ);

        VBox formContent = new VBox(14, grid, imgBox, descBox, qWrap);
        formContent.setPadding(new Insets(0, 6, 6, 0));

        ScrollPane formScroll = new ScrollPane(formContent);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        formScroll.getStyleClass().add("editorScroll");

        // --- Footer ---
        Button btnSave = new Button(isEdit ? "Enregistrer" : "Créer");
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

                AnnonceSortie a = isEdit ? existing : new AnnonceSortie();
                a.setUserId(currentUser.getId());

                a.setTitre(req(tfTitre.getText(), "Titre"));
                a.setDescription(emptyToNull(taDesc.getText()));
                a.setVille(req(cbVille.getValue(), "Ville"));

                String selectedRegion = cbRegion.getValue();
                if (selectedRegion == null || selectedRegion.trim().isEmpty()) {
                    throw new IllegalArgumentException("Région obligatoire");
                }
                if (TunisiaGeo.REGION_OTHER.equals(selectedRegion)) {
                    a.setLieuTexte(req(tfRegionAutre.getText(), "Région"));
                } else {
                    a.setLieuTexte(selectedRegion);
                }

                a.setPointRencontre(req(tfPoint.getText(), "Point de rencontre"));

                String act = cbAct.getValue();
                if (act == null || act.trim().isEmpty()) throw new IllegalArgumentException("Type d'activité obligatoire");
                if ("Autre".equalsIgnoreCase(act)) {
                    a.setTypeActivite(req(tfAutreAct.getText(), "Type d'activité"));
                } else {
                    a.setTypeActivite(act);
                }

                LocalDate d = dpDate.getValue();
                int hh = spHour.getValue();
                int mm = spMin.getValue();
                LocalDateTime dt = LocalDateTime.of(d, LocalTime.of(hh, mm));
                if (!dt.isAfter(LocalDateTime.now())) {
                    throw new IllegalArgumentException("Date + heure doit être dans le futur");
                }
                a.setDateSortie(dt);

                double budget = parseBudget(tfBudget.getText(), cbNoBudget.isSelected());
                a.setBudgetMax(budget);
                a.setNbPlaces(spPlaces.getValue());
                a.setStatut(req(cbStatut.getValue(), "Statut"));

                a.setImageUrl(emptyToNull(pickedPath[0]));
                a.setQuestions(readQuestions(qBox));

                if (isEdit) service.update(a);
                else service.add(a);

                dialog.close();
                loadData();

            } catch (Exception ex) {
                showError("Erreur", "Impossible d'enregistrer", safe(ex.getMessage()));
            }
        });

        // disable save tant que c'est invalide
        updatePreviewNow.run();
        btnSave.setDisable(!liveHint.getText().isEmpty());
        liveHint.textProperty().addListener((obs, o, n) -> btnSave.setDisable(n != null && !n.isEmpty()));

        // --- Shell dialog (2 colonnes) ---
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
            TextField tfTitre,
            ComboBox<String> cbVille,
            ComboBox<String> cbRegion,
            TextField tfRegionAutre,
            TextField tfPoint,
            ComboBox<String> cbAct,
            TextField tfAutreAct,
            DatePicker dpDate,
            Spinner<Integer> spHour,
            Spinner<Integer> spMin,
            CheckBox cbNoBudget,
            TextField tfBudget,
            ComboBox<String> cbStatut
    ) {
        String titre = safe(tfTitre.getText()).trim();
        if (titre.length() < 5) return "Titre trop court (min 5 caractères)";

        String ville = safe(cbVille.getValue()).trim();
        if (ville.isEmpty()) return "Choisir une ville";

        String regSel = cbRegion.getValue();
        if (regSel == null || regSel.trim().isEmpty()) return "Choisir une région";
        if (TunisiaGeo.REGION_OTHER.equals(regSel)) {
            if (safe(tfRegionAutre.getText()).trim().isEmpty()) return "Écrire la région";
        }

        if (safe(tfPoint.getText()).trim().length() < 3) return "Point de rencontre obligatoire";

        String actSel = safe(cbAct.getValue()).trim();
        if (actSel.isEmpty()) return "Choisir une activité";
        if ("Autre".equalsIgnoreCase(actSel) && safe(tfAutreAct.getText()).trim().isEmpty()) return "Écrire le type d'activité";

        LocalDate d = dpDate.getValue();
        if (d == null) return "Choisir une date";
        LocalDateTime dt = LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue()));
        if (!dt.isAfter(LocalDateTime.now())) return "Date + heure doit être dans le futur";

        if (!cbNoBudget.isSelected()) {
            String raw = safe(tfBudget.getText()).trim().replace(',', '.');
            if (raw.isEmpty()) return "Budget vide ou cocher 'Aucun budget'";
            try {
                double v = Double.parseDouble(raw);
                if (v < 0) return "Budget invalide";
            } catch (Exception ex) {
                return "Budget invalide";
            }
        }

        String st = safe(cbStatut.getValue()).trim();
        if (st.isEmpty()) return "Choisir un statut";

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

    private String safe(String s) {
        return s == null ? "" : s;
    }

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

        // fallback carte liste
        return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), true);
    }

    // Pour l'éditeur: image vide par défaut.
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