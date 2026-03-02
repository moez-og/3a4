package controllers.front.sorties;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.concurrent.Worker;
import javafx.util.Duration;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.users.User;
import services.sorties.ParticipationSortieService;
import services.sorties.AnnonceSortieService;
import utils.files.UploadStore;
import utils.geo.TunisiaGeo;
import utils.json.JsonStringArray;
import utils.ui.AutoCompleteComboBox;
import utils.ui.ShellNavigator;
import utils.sorties.SortieActivities;

import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class SortiesController {
    private static final int TITLE_MAX = 60;
    private static final int PAGE_SIZE = 6;

    private double lastPaneW = -1;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> villeFilter;
    @FXML private ComboBox<String> actFilter;
    @FXML private ComboBox<String> statutFilter;
    @FXML private ToggleButton toggleMine;
    @FXML private ToggleButton toggleOpenOnly;

    @FXML private TilePane cardsPane;
    @FXML private Label countLabel;
    @FXML private Label stateLabel;
    @FXML private Button btnCreate;

    @FXML private ScrollPane sortiesScroll;
    @FXML private HBox paginationBar;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Label pageLabel;
    private final AnnonceSortieService service = new AnnonceSortieService();

    private final ObservableList<AnnonceSortie> master = FXCollections.observableArrayList();
    private final FilteredList<AnnonceSortie> filtered = new FilteredList<>(master, x -> true);

    private User currentUser;

    @SuppressWarnings("unused")
    private Stage primaryStage;
    private ShellNavigator navigator;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setNavigator(ShellNavigator nav) {
        this.navigator = nav;
    }
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    private int currentPage = 0;
    private int totalPages = 1;
    private List<AnnonceSortie> lastFilteredList = List.of();

    public void setCurrentUser(User u) {
        this.currentUser = u;
    }

    @FXML
    public void initialize() {
        setupFilters();
        setupSearch();
        setupCardsPaneLayout();
        btnCreate.setOnAction(e -> createAnnonce());
        load();
    }

    private void setupCardsPaneLayout() {
        if (cardsPane == null) return;

        cardsPane.setAlignment(Pos.TOP_CENTER);
        cardsPane.setPrefColumns(3);
        // IMPORTANT: prefColumns is not a strict constraint; enforce 3 columns by sizing tiles to the
        // actual TilePane width (already accounts for parent padding), and subtract TilePane insets.
        cardsPane.setPrefTileWidth(320);

        cardsPane.widthProperty().addListener((obs, o, n) -> {
            double paneW = n == null ? 0 : n.doubleValue();
            if (paneW <= 0) return;
            if (lastPaneW > 0 && Math.abs(paneW - lastPaneW) < 0.5) return;
            lastPaneW = paneW;
            applyFixedGridFromPaneWidth(paneW);
        });

        Platform.runLater(() -> {
            double paneW = cardsPane.getWidth();
            if (paneW > 0) applyFixedGridFromPaneWidth(paneW);
        });
    }

    private void applyFixedGridFromPaneWidth(double paneW) {
        if (paneW <= 0 || cardsPane == null) return;

        Insets in = cardsPane.getInsets();
        double insets = (in == null) ? 0 : (in.getLeft() + in.getRight());
        double gap = cardsPane.getHgap() > 0 ? cardsPane.getHgap() : 14;
        double available = Math.max(0, paneW - insets);

        int cols = 3;
        // Use floor + a tiny safety margin to avoid rounding that can drop to 2 columns.
        double tileW = Math.floor((available - gap * (cols - 1)) / cols);
        tileW = Math.max(0, tileW - 1);

        cardsPane.setPrefColumns(cols);
        cardsPane.setPrefTileWidth(tileW);
    }

    private void setupFilters() {
        villeFilter.setItems(FXCollections.observableArrayList(withAll(TunisiaGeo.villes())));
        actFilter.setItems(FXCollections.observableArrayList(withAll(SortieActivities.activitiesForFilter())));
        statutFilter.setItems(FXCollections.observableArrayList(withAll(List.of("OUVERTE","CLOTUREE","ANNULEE","TERMINEE"))));

        villeFilter.getSelectionModel().selectFirst();
        actFilter.getSelectionModel().selectFirst();
        statutFilter.getSelectionModel().selectFirst();

        villeFilter.valueProperty().addListener((a,b,c) -> applyFilters());
        actFilter.valueProperty().addListener((a,b,c) -> applyFilters());
        statutFilter.valueProperty().addListener((a,b,c) -> applyFilters());
        toggleMine.selectedProperty().addListener((a,b,c) -> applyFilters());
        toggleOpenOnly.selectedProperty().addListener((a,b,c) -> applyFilters());
    }

    private void setupSearch() {
        searchField.textProperty().addListener((a,b,c) -> applyFilters());
    }

    @FXML
    private void resetFilters() {
        villeFilter.getSelectionModel().selectFirst();
        actFilter.getSelectionModel().selectFirst();
        statutFilter.getSelectionModel().selectFirst();
        toggleMine.setSelected(false);
        toggleOpenOnly.setSelected(false);
        searchField.clear();
        applyFilters();
    }

    private void load() {
        try {
            master.clear();
            master.addAll(service.getAll());
            applyFilters();
        } catch (Exception e) {
            showError("Erreur", "Chargement impossible", safe(e.getMessage()));
        }
    }

    private void applyFilters() {
        String q = safe(searchField.getText()).trim().toLowerCase();
        String ville = safe(villeFilter.getValue());
        String act = safe(actFilter.getValue());
        String st = safe(statutFilter.getValue());

        boolean mine = toggleMine.isSelected();
        boolean openOnly = toggleOpenOnly.isSelected();

        filtered.setPredicate(a -> {
            if (a == null) return false;

            if (!q.isEmpty()) {
                boolean ok =
                        contains(a.getTitre(), q) ||
                                contains(a.getVille(), q) ||
                                contains(a.getLieuTexte(), q) ||
                                contains(a.getTypeActivite(), q) ||
                                contains(a.getPointRencontre(), q) ||
                                contains(a.getDescription(), q);
                if (!ok) return false;
            }

            if (!"Tous".equals(ville) && !safe(a.getVille()).equalsIgnoreCase(ville)) return false;
            if (!"Tous".equals(act) && !safe(a.getTypeActivite()).equalsIgnoreCase(act)) return false;
            if (!"Tous".equals(st) && !safe(a.getStatut()).equalsIgnoreCase(st)) return false;

            if (openOnly && !"OUVERTE".equalsIgnoreCase(safe(a.getStatut()))) return false;

            if (mine) {
                if (currentUser == null) return false;
                if (a.getUserId() != currentUser.getId()) return false;
            }

            return true;
        });

        currentPage = 0;

        render();
    }

    private void render() {
        List<AnnonceSortie> list = new ArrayList<>(filtered);
        lastFilteredList = list;

        countLabel.setText(list.size() + " annonce(s)");
        if (list.isEmpty()) {
            cardsPane.getChildren().clear();
            stateLabel.setText("Aucune annonce trouvée.");
            stateLabel.setVisible(true);
            stateLabel.setManaged(true);
            setPaginationVisible(false);
            return;
        }

        stateLabel.setVisible(false);
        stateLabel.setManaged(false);
        renderCurrentPage();
    }

    private void renderCurrentPage() {
        List<AnnonceSortie> list = (lastFilteredList == null) ? List.of() : lastFilteredList;

        totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) PAGE_SIZE));
        if (currentPage < 0) currentPage = 0;
        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        int from = currentPage * PAGE_SIZE;
        int to = Math.min(list.size(), from + PAGE_SIZE);

        cardsPane.getChildren().clear();
        for (int i = from; i < to; i++) {
            cardsPane.getChildren().add(buildCard(list.get(i)));
        }

        boolean showPager = list.size() > PAGE_SIZE;
        setPaginationVisible(showPager);
        if (showPager) {
            if (pageLabel != null) pageLabel.setText("Page " + (currentPage + 1) + "/" + totalPages);
            if (btnPrevPage != null) btnPrevPage.setDisable(currentPage == 0);
            if (btnNextPage != null) btnNextPage.setDisable(currentPage >= totalPages - 1);
        }

        if (sortiesScroll != null) {
            sortiesScroll.setVvalue(0);
        }
    }

    private void setPaginationVisible(boolean visible) {
        if (paginationBar == null) return;
        paginationBar.setVisible(visible);
        paginationBar.setManaged(visible);
    }

    @FXML
    private void prevPage() {
        if (currentPage <= 0) return;
        currentPage--;
        renderCurrentPage();
    }

    @FXML
    private void nextPage() {
        if (currentPage >= totalPages - 1) return;
        currentPage++;
        renderCurrentPage();
    }

    private Node buildCard(AnnonceSortie a) {
        VBox card = new VBox(10);
        card.getStyleClass().add("sortieCard");
        // IMPORTANT: allow shrinking; fixed 3-columns requires cards to be resizable.
        card.setMinWidth(0);
        card.prefWidthProperty().bind(cardsPane.prefTileWidthProperty());
        card.maxWidthProperty().bind(cardsPane.prefTileWidthProperty());

        // Image (fallback local)
        ImageView iv = new ImageView();
        iv.setFitHeight(170);
        iv.setPreserveRatio(false);

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");
        imgWrap.setMaxWidth(Double.MAX_VALUE);

        // Bind image width to its container => responsive
        iv.fitWidthProperty().bind(imgWrap.widthProperty());
        iv.setSmooth(true);

        Rectangle clip = new Rectangle(0, 170);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(iv.fitWidthProperty());
        iv.setClip(clip);
        iv.setImage(loadImageOrFallback(a.getImageUrl()));

        imgWrap.getChildren().add(iv);

        Label chip = new Label(safe(a.getStatut()));
        chip.getStyleClass().addAll("statusChip", "status-" + safe(a.getStatut()).toLowerCase());
        StackPane.setAlignment(chip, Pos.TOP_LEFT);
        StackPane.setMargin(chip, new Insets(10,0,0,10));
        imgWrap.getChildren().add(chip);

        Label title = new Label(safe(a.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        Label meta = new Label(safe(a.getVille()) + " • " + safe(a.getLieuTexte()) + " • " + safe(a.getTypeActivite()));
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        String when = (a.getDateSortie() == null) ? "—" : DT_FMT.format(a.getDateSortie());
        String budget = (a.getBudgetMax() <= 0) ? "Aucun budget" : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");

        Label line = new Label("📅 " + when + "   •   💰 " + budget + "   •   👥 " + a.getNbPlaces() + " places");
        line.getStyleClass().add("cardLine");
        line.setWrapText(true);

        HBox actions = new HBox(10);
        actions.getStyleClass().add("cardActions");

        boolean isOwner = (currentUser != null && a.getUserId() == currentUser.getId());

        if (!isOwner) {
            Button btnDetails = new Button("Détails");
            btnDetails.getStyleClass().add("ghostBtn");
            btnDetails.setOnAction(e -> showDetails(a));
            actions.getChildren().add(btnDetails);
        }
        if (isOwner) {
            Button btnEdit = new Button("Modifier");
            btnEdit.getStyleClass().add("primaryBtn");
            btnEdit.setOnAction(e -> openEditor(a));

            Button btnDel = new Button("Supprimer");
            btnDel.getStyleClass().add("dangerBtn");
            btnDel.setOnAction(e -> {
                if (confirm("Supprimer", "Supprimer cette annonce ?")) {
                    try {
                        service.delete(a.getId());
                        load();
                    } catch (Exception ex) {
                        showError("Erreur", "Suppression impossible", safe(ex.getMessage()));
                    }
                }
            });

            actions.getChildren().addAll(btnEdit, btnDel);
        }

        // Ouvrir les détails en cliquant sur la carte (sauf sur les boutons)
        card.setOnMouseClicked(ev -> {
            Node t = (ev.getTarget() instanceof Node) ? (Node) ev.getTarget() : null;
            if (t != null && isInsideButton(t)) return;
            showDetails(a);
        });

        card.getChildren().addAll(imgWrap, title, meta, line, actions);
        return card;
    }

    @FXML
    private void createAnnonce() {
        openEditor(null);
    }

    /**
     * Helper for navigation-driven edit: opens the editor for an annonce id.
     */
    public void openEditorByAnnonceId(int annonceId) {
        if (annonceId <= 0) return;
        try {
            AnnonceSortie a = service.getById(annonceId);
            if (a == null) {
                showError("Introuvable", "Annonce introuvable", "ID: " + annonceId);
                return;
            }
            openEditor(a);
        } catch (Exception ex) {
            showError("Erreur", "Chargement impossible", safe(ex.getMessage()));
        }
    }

    // ✅ Formulaire PRO front: pas de preview à droite + photo stable
    private void openEditor(AnnonceSortie existing) {
        boolean isEdit = existing != null;

        // ⚠️ Validation visuelle uniquement après tentative d'enregistrement
        final boolean[] showValidation = { false };

        if (currentUser == null) {
            showError("Session", "Utilisateur non défini", "Le front n'a pas injecté currentUser.");
            return;
        }

        // Editeur uniquement pour le propriétaire
        if (isEdit && existing.getUserId() != currentUser.getId()) {
            showError("Accès", "Action interdite", "Modification réservée au créateur de l'annonce.");
            return;
        }

        Stage dialog = new Stage();
        // ✅ Fix Windows: une modale SANS owner peut s'ouvrir sans focus => impossible d'écrire/scroll.
        javafx.stage.Window ownerWindow = null;
        try {
            if (cardsPane != null && cardsPane.getScene() != null) ownerWindow = cardsPane.getScene().getWindow();
        } catch (Exception ignored) {}
        try {
            if (ownerWindow == null && btnCreate != null && btnCreate.getScene() != null) ownerWindow = btnCreate.getScene().getWindow();
        } catch (Exception ignored) {}
        try {
            if (ownerWindow == null && searchField != null && searchField.getScene() != null) ownerWindow = searchField.getScene().getWindow();
        } catch (Exception ignored) {}

        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }
        dialog.setTitle(isEdit ? "Modifier annonce" : "Créer annonce");

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de la sortie");

        // Limite de caractères (pro) + compteur
        Label titleCount = new Label("0/" + TITLE_MAX);
        titleCount.getStyleClass().add("fieldCount");
        UnaryOperator<TextFormatter.Change> titleFilter = ch -> {
            String next = ch.getControlNewText();
            if (next != null && next.length() > TITLE_MAX) return null;
            return ch;
        };
        tfTitre.setTextFormatter(new TextFormatter<>(titleFilter));

        ComboBox<String> cbVille = new ComboBox<>(FXCollections.observableArrayList(TunisiaGeo.villes()));
        cbVille.setPromptText("Ville");

        ComboBox<String> cbRegion = new ComboBox<>();
        cbRegion.setPromptText("Région / quartier");

        // Auto-complete (type-to-search)
        AutoCompleteComboBox.install(cbVille);
        AutoCompleteComboBox.install(cbRegion);

        TextField tfRegionAutre = new TextField();
        tfRegionAutre.setPromptText("Écrire la région / quartier");
        tfRegionAutre.setVisible(false);
        tfRegionAutre.setManaged(false);

        cbVille.valueProperty().addListener((obs, o, villeSel) -> {
            cbRegion.getItems().setAll(TunisiaGeo.regionsForVille(villeSel));
            AutoCompleteComboBox.refreshOriginalItems(cbRegion);
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

        Label mapHint = new Label("Cliquez sur la carte pour choisir un point précis.");
        mapHint.getStyleClass().add("hint");

        Label coordsLabel = new Label("Lat: —   Lng: —");
        coordsLabel.getStyleClass().add("hint");

        WebEngine mapEngine = mapView.getEngine();
        mapEngine.setJavaScriptEnabled(true);
        final boolean[] mapReady = { false };

        PauseTransition mapDebounce = new PauseTransition(Duration.millis(450));
        final String[] lastMapQuery = { "" };

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
            // Initial center is based on current ville/region (if already selected)
            String initVille = safe(cbVille.getValue()).trim();
            String initRegionSel = safe(cbRegion.getValue()).trim();
            String initRegion = TunisiaGeo.REGION_OTHER.equals(initRegionSel)
                    ? safe(tfRegionAutre.getText()).trim()
                    : initRegionSel;
            String initQ = initVille.isEmpty() ? "" : ((initRegion.isEmpty() ? initVille : (initRegion + ", " + initVille)) + ", Tunisie");
            if (!initQ.isBlank()) {
                mapEngine.load(base + "?q=" + java.net.URLEncoder.encode(initQ, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                mapEngine.load(base);
            }
        } catch (Exception ex) {
            // Map not critical; keep form usable
        }

        mapEngine.titleProperty().addListener((obs, ot, nt) -> {
            if (nt == null || !nt.contains(",")) return;
            String[] mp = nt.split("\\|", 2);
            String coords = mp[0].trim();
            String addr = (mp.length == 2) ? mp[1].trim() : "";

            String[] co = coords.split(",");
            if (co.length == 2) {
                coordsLabel.setText("Lat: " + co[0].trim() + "   Lng: " + co[1].trim());
            }

            if (!addr.isBlank()) tfPoint.setText(addr + " (" + coords + ")");
            else tfPoint.setText(coords);

            // Sync ville/région from reverse-geocoded details (robust, optional)
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

                // Region: match against regions list for selected ville
                if (!district.isBlank()) {
                    String currentVille = safe(cbVille.getValue()).trim();
                    if (!currentVille.isEmpty()) {
                        List<String> regs = TunisiaGeo.regionsForVille(currentVille);
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
        @Override public void updateItem(LocalDate item, boolean empty) {
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

    // Budget: saisie clean (chiffres + , .) + longueur
    UnaryOperator<TextFormatter.Change> budgetFilter = ch -> {
        String t = ch.getControlNewText();
        if (t == null) return ch;
        if (t.length() > 10) return null;
        if (!t.matches("[0-9]*([.,][0-9]{0,2})?")) return null;
        return ch;
    };
        tfBudget.setTextFormatter(new TextFormatter<>(budgetFilter));

        cbNoBudget.selectedProperty().addListener((obs, o, n) -> {
        tfBudget.setDisable(Boolean.TRUE.equals(n));
        if (Boolean.TRUE.equals(n)) tfBudget.setText("0");
        else if ("0".equals(tfBudget.getText())) tfBudget.clear();
    });

    Spinner<Integer> spPlaces = new Spinner<>(1, 999, 5);
        spPlaces.setEditable(true);

    ComboBox<String> cbStatut = new ComboBox<>(FXCollections.observableArrayList("OUVERTE", "CLOTUREE", "ANNULEE", "TERMINEE"));
        cbStatut.getSelectionModel().select("OUVERTE");

    TextArea taDesc = new TextArea();
        taDesc.setPromptText("Description (optionnel)");
        taDesc.setPrefRowCount(3);

    // ===== Image STABLE (ne bouge pas pendant la saisie) =====
    ImageView imgPrev = new ImageView();
        imgPrev.setFitWidth(520);
        imgPrev.setFitHeight(220);
        imgPrev.setPreserveRatio(false);
        imgPrev.setSmooth(true);
        imgPrev.setCache(true);
        imgPrev.setCacheHint(CacheHint.SPEED);

    Rectangle clip = new Rectangle(520, 220);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        imgPrev.setClip(clip);

    Label imgEmpty = new Label("Aucune image");
        imgEmpty.getStyleClass().add("imageEmpty");

    StackPane imgWrap = new StackPane(imgPrev, imgEmpty);
        imgWrap.getStyleClass().add("imageWrap");
        imgWrap.setMinHeight(220);
        imgWrap.setPrefHeight(220);
        imgWrap.setMaxHeight(220);

    Label imgPath = new Label("");
        imgPath.getStyleClass().add("hint");

    Button btnPickImg = new Button("Uploader image");
        btnPickImg.getStyleClass().add("primaryBtn");

    final String[] pickedPath = { null };
    final String[] lastAppliedPath = { null };

    Runnable applyImageIfChanged = () -> {
        String p = pickedPath[0];
        if (p == null ? lastAppliedPath[0] == null : p.equals(lastAppliedPath[0])) return;
        lastAppliedPath[0] = p;

        Image im = loadImageOrNull(p);
        imgPrev.setImage(im);

        boolean empty = (im == null);
        imgEmpty.setVisible(empty);
        imgEmpty.setManaged(empty);

        imgPath.setText(shortName(p));
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
            applyImageIfChanged.run();
        } catch (Exception ex) {
            showError("Upload", "Impossible d'uploader l'image", safe(ex.getMessage()));
        }
    });

    // Questions dynamiques
    VBox qBox = new VBox(8);
        qBox.getStyleClass().add("questionsBox");

    Button btnAddQ = new Button("+ Ajouter une question");
        btnAddQ.getStyleClass().add("ghostBtn");
        btnAddQ.setOnAction(e -> qBox.getChildren().add(buildQuestionRow("")));

    // Prefill edit
        if (isEdit) {
        tfTitre.setText(existing.getTitre());
        titleCount.setText(Math.min(safe(existing.getTitre()).length(), TITLE_MAX) + "/" + TITLE_MAX);
        String villeToSelect = Optional.ofNullable(TunisiaGeo.matchVilleForSelection(existing.getVille())).orElse(existing.getVille());
        cbVille.getSelectionModel().select(villeToSelect);

        cbRegion.getItems().setAll(TunisiaGeo.regionsForVille(villeToSelect));
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
        String act = safe(existing.getTypeActivite()).trim();
        String cat = SortieActivities.findCategoryForActivity(act);
        if (cat != null) {
            lvActCats.getSelectionModel().select(cat);
            String matched = SortieActivities.matchActivityInCategory(cat, act);
            if (matched != null) lvActs.getSelectionModel().select(matched);
            else if (!lvActs.getItems().isEmpty()) lvActs.getSelectionModel().selectFirst();
        } else {
            // Activité hors catalogue => Autre
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
        } else tfBudget.setText(String.valueOf(existing.getBudgetMax()));

        spPlaces.getValueFactory().setValue(Math.max(1, existing.getNbPlaces()));
        cbStatut.getSelectionModel().select(safe(existing.getStatut()).isEmpty() ? "OUVERTE" : existing.getStatut());

        taDesc.setText(safe(existing.getDescription()));

        pickedPath[0] = existing.getImageUrl();
        applyImageIfChanged.run();

        if (existing.getQuestions() != null) {
            for (String q : existing.getQuestions()) qBox.getChildren().add(buildQuestionRow(q));
        }
    } else {
        // image vide par défaut
        applyImageIfChanged.run();

        // valeurs par défaut "pro" (évite date invalide)
        dpDate.setValue(LocalDate.now().plusDays(1));
        spHour.getValueFactory().setValue(10);
        spMin.getValueFactory().setValue(0);
    }

        if (qBox.getChildren().isEmpty()) qBox.getChildren().add(buildQuestionRow(""));

    Label errorLive = new Label("");
        errorLive.getStyleClass().add("liveHint");
        errorLive.setVisible(false);
        errorLive.setManaged(false);

    // Progress (effet SaaS)
    ProgressBar progress = new ProgressBar(0);
        progress.getStyleClass().add("formProgress");
        progress.setMaxWidth(Double.MAX_VALUE);

    Label progressTxt = new Label("0% complété");
        progressTxt.getStyleClass().add("formProgressText");

    HBox progressRow = new HBox(10, progress, progressTxt);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progress, Priority.ALWAYS);

    Button btnSave = new Button(isEdit ? "Enregistrer" : "Créer");
        btnSave.getStyleClass().add("primaryBtn");
    Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("ghostBtn");

    Runnable validateLive = () -> {
        // compteur titre
        titleCount.setText(Math.min(safe(tfTitre.getText()).length(), TITLE_MAX) + "/" + TITLE_MAX);

        // règles
        boolean badTitre = safe(tfTitre.getText()).trim().length() < 5;
        boolean badVille = safe(cbVille.getValue()).trim().isEmpty();

        String regSel = cbRegion.getValue();
        boolean badRegion = (regSel == null || regSel.trim().isEmpty())
                || (TunisiaGeo.REGION_OTHER.equals(regSel) && safe(tfRegionAutre.getText()).trim().isEmpty());

        boolean badPoint = safe(tfPoint.getText()).trim().length() < 3;

        String actSel = safe(cbAct.getValue()).trim();
        boolean badAct = actSel.isEmpty() || ("Autre".equalsIgnoreCase(actSel) && safe(tfAutreAct.getText()).trim().isEmpty());

        LocalDate d = dpDate.getValue();
        boolean badDate = (d == null)
                || !LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue())).isAfter(LocalDateTime.now());

        boolean badBudget;
        if (cbNoBudget.isSelected()) {
            badBudget = false;
        } else {
            String raw = safe(tfBudget.getText()).trim();
            badBudget = raw.isEmpty();
            if (!badBudget) {
                try {
                    double v = Double.parseDouble(raw.replace(',', '.'));
                    badBudget = v < 0;
                } catch (Exception ex) {
                    badBudget = true;
                }
            }
        }

        boolean badStatut = safe(cbStatut.getValue()).trim().isEmpty();

        // message live
        String msg = validateFront(tfTitre, cbVille, cbRegion, tfRegionAutre, tfPoint, cbAct, tfAutreAct,
                dpDate, spHour, spMin, cbNoBudget, tfBudget, cbStatut);

        boolean ok = msg.isEmpty();

        // ✅ IMPORTANT: pas de rouge / pas de message tant que l'utilisateur n'a pas cliqué "Enregistrer"
        if (showValidation[0]) {
            setInvalid(tfTitre, badTitre);
            setInvalid(cbVille, badVille);
            setInvalid(cbRegion, badRegion);
            setInvalid(tfRegionAutre, TunisiaGeo.REGION_OTHER.equals(regSel)
                    && safe(tfRegionAutre.getText()).trim().isEmpty());
            setInvalid(tfPoint, badPoint);
            setInvalid(cbAct, badAct);
            setInvalid(lvActCats, badAct);
            setInvalid(lvActs, badAct);
            setInvalid(tfAutreAct, "Autre".equalsIgnoreCase(actSel)
                    && safe(tfAutreAct.getText()).trim().isEmpty());
            setInvalid(dpDate, badDate);
            setInvalid(tfBudget, badBudget);
            setInvalid(cbStatut, badStatut);

            errorLive.setText(msg);
            errorLive.setVisible(!ok);
            errorLive.setManaged(!ok);
        } else {
            // Nettoyer les styles si on n'affiche pas encore la validation
            setInvalid(tfTitre, false);
            setInvalid(cbVille, false);
            setInvalid(cbRegion, false);
            setInvalid(tfRegionAutre, false);
            setInvalid(tfPoint, false);
            setInvalid(cbAct, false);
            setInvalid(lvActCats, false);
            setInvalid(lvActs, false);
            setInvalid(tfAutreAct, false);
            setInvalid(dpDate, false);
            setInvalid(tfBudget, false);
            setInvalid(cbStatut, false);

            errorLive.setVisible(false);
            errorLive.setManaged(false);
        }

        // progress
        int total = 7;
        int done = 0;
        if (!badTitre) done++;
        if (!badVille) done++;
        if (!badRegion) done++;
        if (!badPoint) done++;
        if (!badAct) done++;
        if (!badDate) done++;
        if (!badBudget) done++;

        double p = Math.max(0, Math.min(1, done / (double) total));
        progress.setProgress(p);
        progressTxt.setText(Math.round(p * 100) + "% complété");

        // ✅ photo reste stable: on applique seulement si path change
        applyImageIfChanged.run();
    };

    // listeners validation live
        tfTitre.textProperty().addListener((a,b,c) -> validateLive.run());
        cbVille.valueProperty().addListener((a,b,c) -> { validateLive.run(); updateMapCenter.run(); });
        cbRegion.valueProperty().addListener((a,b,c) -> { validateLive.run(); updateMapCenter.run(); });
        tfRegionAutre.textProperty().addListener((a,b,c) -> { validateLive.run(); updateMapCenter.run(); });
        tfPoint.textProperty().addListener((a,b,c) -> validateLive.run());
        cbAct.valueProperty().addListener((a,b,c) -> validateLive.run());
        tfAutreAct.textProperty().addListener((a,b,c) -> validateLive.run());
        dpDate.valueProperty().addListener((a,b,c) -> validateLive.run());
        spHour.valueProperty().addListener((a,b,c) -> validateLive.run());
        spMin.valueProperty().addListener((a,b,c) -> validateLive.run());
        cbNoBudget.selectedProperty().addListener((a,b,c) -> validateLive.run());
        tfBudget.textProperty().addListener((a,b,c) -> validateLive.run());
        spPlaces.valueProperty().addListener((a,b,c) -> validateLive.run());
        cbStatut.valueProperty().addListener((a,b,c) -> validateLive.run());

        btnCancel.setOnAction(e -> dialog.close());

        btnSave.setOnAction(e -> {
        try {
            String msg = validateFront(tfTitre, cbVille, cbRegion, tfRegionAutre, tfPoint, cbAct, tfAutreAct,
                    dpDate, spHour, spMin, cbNoBudget, tfBudget, cbStatut);
            if (!msg.isEmpty()) {
                // Activer la validation visuelle à partir de maintenant
                showValidation[0] = true;
                validateLive.run();
                return;
            }

            AnnonceSortie a = isEdit ? existing : new AnnonceSortie();
            a.setUserId(currentUser.getId());

            a.setTitre(tfTitre.getText().trim());
            a.setVille(cbVille.getValue());

            String regSel = cbRegion.getValue();
            if (TunisiaGeo.REGION_OTHER.equals(regSel)) a.setLieuTexte(tfRegionAutre.getText().trim());
            else a.setLieuTexte(regSel);

            a.setPointRencontre(tfPoint.getText().trim());

            String actSel = cbAct.getValue();
            if ("Autre".equalsIgnoreCase(actSel)) a.setTypeActivite(tfAutreAct.getText().trim());
            else a.setTypeActivite(actSel);

            LocalDateTime dt = LocalDateTime.of(dpDate.getValue(), LocalTime.of(spHour.getValue(), spMin.getValue()));
            a.setDateSortie(dt);

            double budget = cbNoBudget.isSelected() ? 0.0 : Double.parseDouble(tfBudget.getText().trim().replace(',', '.'));
            a.setBudgetMax(budget);

            a.setNbPlaces(spPlaces.getValue());
            a.setStatut(cbStatut.getValue());

            a.setImageUrl(pickedPath[0] == null || pickedPath[0].trim().isEmpty() ? null : pickedPath[0]);
            a.setDescription(taDesc.getText() == null || taDesc.getText().trim().isEmpty() ? null : taDesc.getText().trim());
            a.setQuestions(readQuestions(qBox));

            if (isEdit) service.update(a);
            else service.add(a);

            dialog.close();
            load();

        } catch (Exception ex) {
            showError("Erreur", "Enregistrement impossible", safe(ex.getMessage()));
        }
    });

    // Layout
    GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

    int r = 0;
    HBox titreRow = new HBox(10, tfTitre, titleCount);
        titreRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfTitre, Priority.ALWAYS);
        titleCount.setMinWidth(Region.USE_PREF_SIZE);
        grid.add(lab("Titre"), 0, r); grid.add(titreRow, 1, r++);
        grid.add(lab("Ville"), 0, r); grid.add(cbVille, 1, r++);

    VBox regionWrap = new VBox(8, cbRegion, tfRegionAutre);
        grid.add(lab("Lieu (région)"), 0, r); grid.add(regionWrap, 1, r++);

        grid.add(lab("Point de rencontre"), 0, r); grid.add(tfPoint, 1, r++);

    VBox mapBox = new VBox(8, mapHint, mapView, coordsLabel);
        grid.add(lab("Carte"), 0, r); grid.add(mapBox, 1, r++);

    VBox actWrap = new VBox(8, activityPicker, tfAutreAct);
        grid.add(lab("Activité"), 0, r); grid.add(actWrap, 1, r++);

    HBox timeRow = new HBox(10, dpDate, new Label("Heure"), spHour, new Label("Min"), spMin);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(lab("Date + heure"), 0, r); grid.add(timeRow, 1, r++);

    HBox budgetRow = new HBox(10, cbNoBudget, tfBudget);
        budgetRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(lab("Budget"), 0, r); grid.add(budgetRow, 1, r++);

        grid.add(lab("Nombre de places"), 0, r); grid.add(spPlaces, 1, r++);
        grid.add(lab("Statut"), 0, r); grid.add(cbStatut, 1, r++);

    VBox imgBox = new VBox(10, imgWrap, new HBox(10, btnPickImg, imgPath));
        ((HBox) imgBox.getChildren().get(1)).setAlignment(Pos.CENTER_LEFT);

    Label header = new Label(isEdit ? "Modifier une annonce" : "Créer une annonce");
        header.getStyleClass().add("dialogTitle");

    Label sub = new Label("Les champs manquants deviennent rouges uniquement lors de l'enregistrement.");
        sub.getStyleClass().add("dialogSubtitle");

    VBox sectionInfos = new VBox(10, new Label("Informations"), grid);
        sectionInfos.getStyleClass().addAll("sectionCard", "section");
        ((Label) sectionInfos.getChildren().get(0)).getStyleClass().add("sectionTitle");

    VBox sectionMedia = new VBox(10, new Label("Image"), imgBox);
        sectionMedia.getStyleClass().addAll("sectionCard", "section");
        ((Label) sectionMedia.getChildren().get(0)).getStyleClass().add("sectionTitle");

    VBox sectionDesc = new VBox(10, new Label("Description"), taDesc);
        sectionDesc.getStyleClass().addAll("sectionCard", "section");
        ((Label) sectionDesc.getChildren().get(0)).getStyleClass().add("sectionTitle");

    VBox sectionQ = new VBox(10, new Label("Questions (optionnel)"), qBox, btnAddQ);
        sectionQ.getStyleClass().addAll("sectionCard", "section");
        ((Label) sectionQ.getChildren().get(0)).getStyleClass().add("sectionTitle");

    VBox root = new VBox(14,
            header,
            sub,
            progressRow,
            errorLive,
            sectionInfos,
            sectionMedia,
            sectionDesc,
            sectionQ
    );
        root.getStyleClass().add("dialogRoot");
        root.setPadding(new Insets(16));

    HBox footer = new HBox(10, btnCancel, btnSave);
        footer.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(footer);

    ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPannable(true);
        sp.getStyleClass().add("editorScroll");

    Scene scene = new Scene(sp, 900, 880);

    // ✅ Fix scroll "bloqué" : certains contrôles consomment la molette.
    // On force un pas de scroll stable (souris/trackpad) pour éviter l'impression de "ne scroll pas".
        sp.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, ev -> {
        if (ev == null) return;
        if (ev.isControlDown() || ev.isAltDown() || ev.isShiftDown()) return;
        double dy = ev.getDeltaY();
        if (Math.abs(dy) < 0.01) {
            // certains trackpads envoient 0 ici mais utilisent textDeltaY
            dy = ev.getTextDeltaY() * ev.getMultiplierY();
        }
        if (Math.abs(dy) < 0.01) return;

        double step = dy / 400.0; // sensibilité stable
        double next = sp.getVvalue() - step;
        if (next < 0) next = 0;
        if (next > 1) next = 1;
        sp.setVvalue(next);
        ev.consume();
    });

        scene.getStylesheets().add(getClass().getResource("/styles/sorties/sorties-front.css").toExternalForm());
        dialog.setScene(scene);

        validateLive.run();

    // ✅ Fix "je ne peux rien écrire" : forcer le focus une fois la fenêtre affichée.
        dialog.setOnShown(e -> {
        tfTitre.requestFocus();
        tfTitre.selectAll();
        dialog.requestFocus();
        dialog.toFront();
    });

        dialog.showAndWait();
}

private String validateFront(
        TextField tfTitre, ComboBox<String> cbVille, ComboBox<String> cbRegion, TextField tfRegionAutre,
        TextField tfPoint, ComboBox<String> cbAct, TextField tfAutreAct,
        DatePicker dpDate, Spinner<Integer> spHour, Spinner<Integer> spMin,
        CheckBox cbNoBudget, TextField tfBudget, ComboBox<String> cbStatut
) {
    String titre = safe(tfTitre.getText()).trim();
    if (titre.length() < 5) return "Titre trop court (min 5 caractères)";
    if (titre.length() > TITLE_MAX) return "Titre trop long (max " + TITLE_MAX + " caractères)";

    String ville = safe(cbVille.getValue()).trim();
    if (ville.isEmpty()) return "Choisir une ville";

    String regSel = cbRegion.getValue();
    if (regSel == null || regSel.trim().isEmpty()) return "Choisir une région";
    if (TunisiaGeo.REGION_OTHER.equals(regSel) && safe(tfRegionAutre.getText()).trim().isEmpty()) return "Écrire la région";

    if (safe(tfPoint.getText()).trim().length() < 3) return "Point de rencontre obligatoire";

    String act = safe(cbAct.getValue()).trim();
    if (act.isEmpty()) return "Choisir une activité";
    if ("Autre".equalsIgnoreCase(act) && safe(tfAutreAct.getText()).trim().isEmpty()) return "Écrire le type d'activité";

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

    if (safe(cbStatut.getValue()).trim().isEmpty()) return "Choisir un statut";
    return "";
}

private HBox buildQuestionRow(String value) {
    TextField tf = new TextField(value == null ? "" : value);
    tf.setPromptText("Ex: Avez-vous une voiture ?");
    tf.getStyleClass().add("qField");

    Button rm = new Button("✕");
    rm.getStyleClass().add("qRemove");

    HBox row = new HBox(10, tf, rm);
    row.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(tf, Priority.ALWAYS);

    rm.setOnAction(e -> {
        VBox parent = (VBox) row.getParent();
        if (parent != null) parent.getChildren().remove(row);
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


@SuppressWarnings("unused")
private void openParticipationDialog(AnnonceSortie a) {
    if (a == null) return;

    if (currentUser == null) {
        showError("Session", "Utilisateur non défini", "Connexion requise pour participer.");
        return;
    }

    if (a.getUserId() == currentUser.getId()) {
        showError("Participation", "Action impossible", "Participation non disponible sur ta propre annonce.");
        return;
    }

    if (!"OUVERTE".equalsIgnoreCase(safe(a.getStatut()))) {
        showError("Participation", "Annonce non ouverte", "Cette sortie n'accepte plus de demandes.");
        return;
    }

    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/front/sorties/ParticipationDialogView.fxml"));
        Parent root = loader.load();

        ParticipationDialogController c = loader.getController();

        Stage pStage = new Stage();
        pStage.initModality(Modality.APPLICATION_MODAL);

        if (cardsPane != null && cardsPane.getScene() != null) {
            pStage.initOwner(cardsPane.getScene().getWindow());
        }

        pStage.setTitle("Participation");
        Scene sc = new Scene(root);
        pStage.setScene(sc);

        c.setDialogStage(pStage);
        c.setContext(currentUser, a);
        c.setOnSuccess(this::load);

        pStage.showAndWait();

    } catch (Exception ex) {
        showError("Erreur", "Participation", safe(ex.getMessage()));
    }
}

private void showDetails(AnnonceSortie a) {
    if (a == null) return;

    // Prefer in-shell navigation (responsive page) instead of a fixed modal dialog.
    if (navigator != null) {
        navigator.navigate("sortie-details:" + a.getId());
        return;
    }

    boolean isOwner = (currentUser != null && a.getUserId() == currentUser.getId());

    ParticipationSortieService participationService = new ParticipationSortieService();
    services.users.UserService usersService = new services.users.UserService();

    // Récupérer le nom du créateur si possible (optionnel)
    String createdBy = "Utilisateur #" + a.getUserId();
    try {
        models.users.User u = usersService.trouverParId(a.getUserId());
        String fn = fullName(u);
        if (!fn.isBlank()) createdBy = fn;
    } catch (Exception ignored) { }

    Stage dialog = new Stage();
    // IMPORTANT: éviter APPLICATION_MODAL sans owner (peut bloquer focus/scroll des modales suivantes)
    javafx.stage.Window ownerWindow = null;
    try {
        if (cardsPane != null && cardsPane.getScene() != null) ownerWindow = cardsPane.getScene().getWindow();
    } catch (Exception ignored) {}
    try {
        if (ownerWindow == null && btnCreate != null && btnCreate.getScene() != null) ownerWindow = btnCreate.getScene().getWindow();
    } catch (Exception ignored) {}

    if (ownerWindow != null) {
        dialog.initOwner(ownerWindow);
        dialog.initModality(Modality.WINDOW_MODAL);
    } else {
        dialog.initModality(Modality.APPLICATION_MODAL);
    }
    dialog.setTitle("Détails de la sortie");
    dialog.setResizable(false);

    // ===== HERO (image + overlay + titre) =====
    ImageView heroImg = new ImageView(loadImageOrFallback(a.getImageUrl()));
    heroImg.setFitWidth(940);
    heroImg.setFitHeight(320);
    heroImg.setPreserveRatio(false);
    heroImg.setSmooth(true);
    heroImg.setCache(true);
    heroImg.setCacheHint(CacheHint.SPEED);

    Rectangle heroClip = new Rectangle(940, 320);
    heroClip.setArcWidth(26);
    heroClip.setArcHeight(26);
    heroImg.setClip(heroClip);

    Region overlay = new Region();
    overlay.getStyleClass().add("detailsHeroOverlay");
    overlay.setPrefSize(940, 320);

    Label status = new Label(safe(a.getStatut()));
    status.getStyleClass().addAll("statusChip", "detailsStatusChip", "status-" + safe(a.getStatut()).toLowerCase());

    Label title = new Label(safe(a.getTitre()));
    title.getStyleClass().add("detailsTitle");
    title.setWrapText(true);

    Label meta = new Label(safe(a.getVille()) + " • " + safe(a.getLieuTexte()) + " • " + safe(a.getTypeActivite()));
    meta.getStyleClass().add("detailsMeta");
    meta.setWrapText(true);

    VBox heroText = new VBox(8, status, title, meta);
    heroText.getStyleClass().add("detailsHeroText");

    Button btnX = new Button("✕");
    btnX.getStyleClass().add("detailsCloseBtn");
    btnX.setOnAction(e -> dialog.close());

    StackPane hero = new StackPane(heroImg, overlay, heroText, btnX);
    hero.getStyleClass().add("detailsHero");
    StackPane.setAlignment(heroText, Pos.BOTTOM_LEFT);
    StackPane.setMargin(heroText, new Insets(0, 0, 16, 16));
    StackPane.setAlignment(btnX, Pos.TOP_RIGHT);
    StackPane.setMargin(btnX, new Insets(12, 12, 0, 0));

    // ===== INFO =====
    String when = (a.getDateSortie() == null) ? "—" : DT_FMT.format(a.getDateSortie());
    String budget = (a.getBudgetMax() <= 0) ? "Aucun budget" : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");
    String places = a.getNbPlaces() + " place(s)";
    String point = safe(a.getPointRencontre()).isBlank() ? "—" : safe(a.getPointRencontre());

    VBox infoLeft = detailsCard(
            "Informations",
            detailsItem("📍", "Ville", safeOrDash(a.getVille())),
            detailsItem("🧭", "Région / quartier", safeOrDash(a.getLieuTexte())),
            detailsItem("📌", "Point de rencontre", point)
    );

    VBox infoRight = detailsCard(
            "Organisation",
            detailsItem("📅", "Date & heure", when),
            detailsItem("💰", "Budget", budget),
            detailsItem("👥", "Places", places),
            detailsItem("🧑‍💼", "Créée par", createdBy)
    );

    HBox infoRow = new HBox(14, infoLeft, infoRight);
    infoRow.getStyleClass().add("detailsInfoRow");
    HBox.setHgrow(infoLeft, Priority.ALWAYS);
    HBox.setHgrow(infoRight, Priority.ALWAYS);

    // ===== DESCRIPTION =====
    String descTxt = safe(a.getDescription()).trim();
    if (descTxt.isEmpty()) descTxt = "Aucune description.";

    Label desc = new Label(descTxt);
    desc.getStyleClass().add("detailsParagraph");
    desc.setWrapText(true);

    VBox descCard = detailsCard("Description", desc);

    // ===== QUESTIONS =====
    FlowPane chips = new FlowPane();
    chips.setHgap(10);
    chips.setVgap(10);
    chips.getStyleClass().add("detailsChips");

    if (a.getQuestions() != null && !a.getQuestions().isEmpty()) {
        for (String q : a.getQuestions()) {
            String qq = safe(q).trim();
            if (qq.isEmpty()) continue;
            Label chip = new Label("❓ " + qq);
            chip.getStyleClass().add("detailsChip");
            chips.getChildren().add(chip);
        }
    } else {
        Label empty = new Label("Aucune question.");
        empty.getStyleClass().add("detailsMuted");
        chips.getChildren().add(empty);
    }

    VBox qCard = detailsCard("Questions", chips);

    // ===== PARTICIPANTS ACCEPTÉS =====
    FlowPane acceptedChips = new FlowPane();
    acceptedChips.setHgap(10);
    acceptedChips.setVgap(10);
    acceptedChips.getStyleClass().add("detailsChips");

    Label acceptedEmpty = new Label("Aucun participant accepté.");
    acceptedEmpty.getStyleClass().add("detailsMuted");

    try {
        List<ParticipationSortie> parts = participationService.getByAnnonce(a.getId());
        if (parts != null) {
            for (ParticipationSortie p : parts) {
                if (!isAcceptedStatus(p)) continue;

                String nm = "Utilisateur #" + p.getUserId();
                try {
                    models.users.User u = usersService.trouverParId(p.getUserId());
                    String fn = fullName(u);
                    if (!fn.isBlank()) nm = fn;
                } catch (Exception ignored) {}

                String placesTxt = p.getNbPlaces() > 1 ? (" (" + p.getNbPlaces() + " places)") : "";
                Label chip = new Label("👤 " + nm + placesTxt);
                chip.getStyleClass().add("detailsChip");
                acceptedChips.getChildren().add(chip);
            }
        }
    } catch (Exception ignored) {}

    Node acceptedBody = acceptedChips.getChildren().isEmpty() ? acceptedEmpty : acceptedChips;
    VBox acceptedCard = detailsCard("Participants acceptés", acceptedBody);

    // ===== ACTIONS =====
    Button btnClose = new Button("Fermer");
    btnClose.getStyleClass().add("ghostBtn");
    btnClose.setOnAction(e -> dialog.close());

    HBox actions = new HBox(10);
    actions.getStyleClass().add("detailsActions");
    actions.setAlignment(Pos.CENTER_RIGHT);

    if (isOwner) {
        Button btnEdit = new Button("Modifier");
        btnEdit.getStyleClass().add("primaryBtn");
        btnEdit.setOnAction(e -> {
            dialog.close();
            // attendre la fermeture complète pour éviter une modale "sans focus"
            Platform.runLater(() -> openEditor(a));
        });

        Button btnRequests = new Button("Demandes");
        btnRequests.getStyleClass().add("ghostBtn");
        btnRequests.setOnAction(e -> openRequestsDialog(a, dialog, participationService, usersService, acceptedChips, acceptedEmpty));

        Button btnDel = new Button("Supprimer");
        btnDel.getStyleClass().add("dangerBtn");
        btnDel.setOnAction(e -> {
            if (confirm("Supprimer", "Supprimer cette annonce ?")) {
                try {
                    service.delete(a.getId());
                    dialog.close();
                    load();
                } catch (Exception ex) {
                    showError("Erreur", "Suppression impossible", safe(ex.getMessage()));
                }
            }
        });

        actions.getChildren().addAll(btnClose, btnRequests, btnEdit, btnDel);
    } else {
        Button btnParticiper = new Button();
        btnParticiper.getStyleClass().add("primaryBtn");

        boolean canParticipate = currentUser != null
                && "OUVERTE".equalsIgnoreCase(safe(a.getStatut()))
                && a.getUserId() != currentUser.getId();

        ParticipationSortie existing = null;

        if (canParticipate) {
            try {
                existing = participationService.getByAnnonceAndUser(a.getId(), currentUser.getId());
            } catch (Exception ignored) { }
        }

        if (!canParticipate) {
            btnParticiper.setText(currentUser == null ? "Connexion requise" : "Annonce non ouverte");
            btnParticiper.setDisable(true);
        } else {
            btnParticiper.setText(existing != null ? "Gérer participation" : "Participer");
            btnParticiper.setOnAction(ev -> {
                try {
                    var url = getClass().getResource("/fxml/front/sorties/ParticipationDialogView.fxml");
                    if (url == null) throw new IllegalStateException("FXML introuvable: /fxml/front/sorties/ParticipationDialogView.fxml");

                    FXMLLoader loader = new FXMLLoader(url);
                    Parent root = loader.load();

                    ParticipationDialogController c = loader.getController();

                    Stage pStage = new Stage();
                    pStage.initModality(Modality.APPLICATION_MODAL);
                    pStage.initOwner(dialog);
                    pStage.setTitle("Participation");
                    pStage.setResizable(false);

                    Scene sc = new Scene(root);
                    pStage.setScene(sc);

                    c.setDialogStage(pStage);
                    c.setContext(currentUser, a);
                    c.setOnSuccess(() -> {
                        try {
                            ParticipationSortie ex2 = participationService.getByAnnonceAndUser(a.getId(), currentUser.getId());
                            btnParticiper.setText(ex2 == null ? "Participer" : "Gérer participation");
                            btnParticiper.setDisable(false);
                        } catch (Exception ignored) {
                            btnParticiper.setText("Gérer participation");
                            btnParticiper.setDisable(false);
                        }
                    });

                    pStage.showAndWait();

                } catch (Exception ex) {
                    showError("Erreur", "Participation impossible", safe(ex.getMessage()));
                }
            });
        }

        actions.getChildren().addAll(btnClose, btnParticiper);
    }

    VBox content = new VBox(14, hero, infoRow, descCard, qCard, acceptedCard, actions);
    content.getStyleClass().add("detailsRoot");
    content.setPadding(new Insets(16));

    ScrollPane sp = new ScrollPane(content);
    sp.setFitToWidth(true);
    sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    sp.getStyleClass().add("detailsScroll");

    Scene scene = new Scene(sp, 980, 900);
    scene.getStylesheets().add(getClass().getResource("/styles/sorties/sorties-front.css").toExternalForm());
    scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
        if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close();
    });

    dialog.setScene(scene);
    dialog.showAndWait();
}

private void openRequestsDialog(
        AnnonceSortie annonce,
        Stage owner,
        ParticipationSortieService participationService,
        services.users.UserService usersService,
        FlowPane acceptedChips,
        Label acceptedEmpty
) {
    if (annonce == null || participationService == null) return;

    List<ParticipationSortie> parts;
    try {
        parts = participationService.getByAnnonce(annonce.getId());
    } catch (Exception ex) {
        showError("Erreur", "Chargement impossible", safe(ex.getMessage()));
        return;
    }

    List<ParticipationSortie> pending = (parts == null) ? List.of() : parts.stream()
            .filter(p -> p != null && "EN_ATTENTE".equalsIgnoreCase(safe(p.getStatut()).trim()))
            .toList();

    Stage dialog = new Stage();
    dialog.initModality(Modality.APPLICATION_MODAL);
    if (owner != null) dialog.initOwner(owner);
    dialog.setTitle("Demandes de participation");

    BorderPane root = new BorderPane();
    root.getStyleClass().add("participRoot");
    root.setPadding(new Insets(14));

    VBox header = new VBox(4);
    header.getStyleClass().add("participHeader");

    Label title = new Label("Demandes de participation");
    title.getStyleClass().add("participTitle");

    int totalPending = pending.size();
    final String subTxt = safe(annonce.getTitre()).isBlank() ? "" : "• " + safe(annonce.getTitre());
    Label sub = new Label(totalPending + " en attente " + subTxt);
    sub.getStyleClass().add("participSub");
    sub.setUserData(totalPending);
    header.getChildren().addAll(title, sub);
    root.setTop(header);
    BorderPane.setMargin(header, new Insets(0, 0, 12, 0));

    VBox list = new VBox(10);
    Label section = new Label("Demandes en attente");
    section.getStyleClass().add("sectionTitle");
    list.getChildren().add(section);

    Label empty = new Label("Aucune demande en attente.");
    empty.getStyleClass().add("hintText");

    Runnable decPending = () -> {
        try {
            int current = (sub.getUserData() instanceof Integer)
                    ? (Integer) sub.getUserData()
                    : Integer.parseInt(String.valueOf(sub.getUserData()));
            int next = Math.max(0, current - 1);
            sub.setUserData(next);
            sub.setText(next + " en attente " + subTxt);
            if (next == 0 && !list.getChildren().contains(empty)) list.getChildren().add(empty);
        } catch (Exception ignored) {}
    };

    if (pending.isEmpty()) {
        list.getChildren().add(empty);
    } else {
        for (ParticipationSortie p : pending) {
            list.getChildren().add(requestRow(annonce, p, participationService, usersService, acceptedChips, acceptedEmpty, decPending));
        }
    }

    ScrollPane sp = new ScrollPane(list);
    sp.setFitToWidth(true);
    sp.getStyleClass().add("participScroll");
    root.setCenter(sp);

    Button close = new Button("Fermer");
    close.getStyleClass().add("ghostBtn");
    close.setOnAction(e -> dialog.close());
    HBox footer = new HBox(close);
    footer.setAlignment(Pos.CENTER_RIGHT);
    footer.setPadding(new Insets(12, 0, 0, 0));
    root.setBottom(footer);

    Scene scene = new Scene(root, 720, 620);
    try {
        var url = getClass().getResource("/styles/sorties/participation-dialog.css");
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    } catch (Exception ignored) {}

    dialog.setScene(scene);
    dialog.showAndWait();
}

private Node requestRow(
        AnnonceSortie annonce,
        ParticipationSortie p,
        ParticipationSortieService participationService,
        services.users.UserService usersService,
        FlowPane acceptedChips,
        Label acceptedEmpty,
        Runnable onResolved
) {
    VBox box = new VBox(8);
    box.getStyleClass().add("participCard");

    final String displayName = resolveUserName(usersService, p.getUserId());

    Label title = new Label(displayName);
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
            int accepted = participationService.countAcceptedPlaces(annonce.getId());
            int remaining = Math.max(0, annonce.getNbPlaces() - accepted);
            if (p.getNbPlaces() > remaining) {
                showError("Places insuffisantes", "Impossible d'accepter",
                        "Places restantes: " + remaining + ". Demandées: " + p.getNbPlaces());
                return;
            }

            participationService.updateStatus(p.getId(), "CONFIRMEE");

            // update UI in details dialog
            if (acceptedChips != null) {
                String placesTxt = p.getNbPlaces() > 1 ? (" (" + p.getNbPlaces() + " places)") : "";
                Label chip = new Label("👤 " + displayName + placesTxt);
                chip.getStyleClass().add("detailsChip");
                acceptedChips.getChildren().add(chip);
            }
            if (acceptedEmpty != null) {
                acceptedEmpty.setVisible(false);
                acceptedEmpty.setManaged(false);
            }

            showInfo("Acceptée", "La demande a été acceptée.");
            box.setDisable(true);
            box.setOpacity(0.55);
            if (onResolved != null) onResolved.run();
            load();
        } catch (Exception ex) {
            showError("Erreur", "Action impossible", safe(ex.getMessage()));
        }
    });

    refuse.setOnAction(e -> {
        try {
            participationService.updateStatus(p.getId(), "REFUSEE");
            showInfo("Refusée", "La demande a été refusée.");
            box.setDisable(true);
            box.setOpacity(0.55);
            if (onResolved != null) onResolved.run();
            load();
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

private String resolveUserName(services.users.UserService usersService, int userId) {
    String nm = "Utilisateur #" + userId;
    if (usersService == null) return nm;
    try {
        models.users.User u = usersService.trouverParId(userId);
        String fn = fullName(u);
        if (!fn.isBlank()) return fn;
    } catch (Exception ignored) {}
    return nm;
}

private boolean isAcceptedStatus(ParticipationSortie p) {
    if (p == null) return false;
    String s = safe(p.getStatut()).trim().toUpperCase();
    return s.equals("CONFIRMEE") || s.equals("ACCEPTEE");
}

private VBox detailsCard(String title, Node... body) {
    Label t = new Label(title);
    t.getStyleClass().add("detailsSectionTitle");

    VBox box = new VBox(10);
    box.getStyleClass().add("detailsCard");
    box.getChildren().add(t);
    box.getChildren().addAll(body);

    return box;
}

private Node detailsItem(String icon, String label, String value) {
    Label ic = new Label(icon);
    ic.getStyleClass().add("detailsIcon");

    Label l1 = new Label(label);
    l1.getStyleClass().add("detailsItemLabel");

    Label l2 = new Label(value == null || value.isBlank() ? "—" : value);
    l2.getStyleClass().add("detailsItemValue");
    l2.setWrapText(true);

    VBox txt = new VBox(2, l1, l2);
    HBox.setHgrow(txt, Priority.ALWAYS);

    HBox row = new HBox(10, ic, txt);
    row.getStyleClass().add("detailsItem");
    row.setAlignment(Pos.TOP_LEFT);

    return row;
}

private String safeOrDash(String s) {
    String x = safe(s).trim();
    return x.isEmpty() ? "—" : x;
}

private String fullName(User u) {
    if (u == null) return "";
    String p = safe(u.getPrenom()).trim();
    String n = safe(u.getNom()).trim();
    String out = (p + " " + n).trim();
    return out;
}

private boolean confirm(String title, String msg) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION);
    a.setTitle(title);
    a.setHeaderText(title);
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

private boolean isInsideButton(Node n) {
    Node cur = n;
    while (cur != null) {
        if (cur instanceof ButtonBase) return true;
        cur = cur.getParent();
    }
    return false;
}

private boolean contains(String s, String q) {
    return s != null && s.toLowerCase().contains(q);
}

private List<String> withAll(List<String> list) {
    ArrayList<String> out = new ArrayList<>();
    out.add("Tous");
    out.addAll(list);
    return out;
}

private String safe(String s) { return s == null ? "" : s; }

private void setInvalid(Control c, boolean invalid) {
    if (c == null) return;
    c.getStyleClass().remove("invalid");
    if (invalid) c.getStyleClass().add("invalid");
}

private Label lab(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("formLabel");
    return l;
}
private String shortName(String p) {
    if (p == null) return "";
    try {
        File f = new File(p);
        return f.getName();
    } catch (Exception ignored) {
        return p;
    }
}

private Image loadImageOrFallback(String pathOrUrl) {
    Image im = loadImageOrNull(pathOrUrl);
    if (im != null) return im;
    return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), true);
}

private Image loadImageOrNull(String pathOrUrl) {
    try {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return null;
        String p = pathOrUrl.trim();
        File f = new File(p);
        if (p.startsWith("file:")) return new Image(p, true);
        if (f.exists()) return new Image(f.toURI().toString(), true);
        if (p.startsWith("http://") || p.startsWith("https://")) return new Image(p, true);
        if (p.startsWith("/")) {
            var u = getClass().getResource(p);
            if (u != null) return new Image(u.toExternalForm(), true);
        }
    } catch (Exception ignored) {}
    return null;
}
}