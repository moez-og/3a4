package controllers.back.lieux;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.lieux.Lieu;
import services.lieux.LieuService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LieuxAdminController {

    @FXML private ScrollPane cardsScroll;
    @FXML private TilePane cardsPane;

    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField searchField;
    @FXML private Button btnAdd;

    @FXML private Label kpiTotal;
    @FXML private Label kpiPublic;

    private final LieuService lieuService = new LieuService();

    private final ObservableList<Lieu> masterList = FXCollections.observableArrayList();
    private final FilteredList<Lieu> filteredList = new FilteredList<>(masterList, p -> true);

    private Lieu selectedLieu = null;
    private Node selectedCard = null;

    // Anti-jitter: garde la dernière largeur de viewport pour éviter les boucles de relayout
    private double lastViewportW = -1;

    public LieuxAdminController() throws SQLException {
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
        filterCombo.setItems(FXCollections.observableArrayList("Nom", "Ville", "Catégorie", "Type"));
        filterCombo.getSelectionModel().select("Nom");
        filterCombo.valueProperty().addListener((obs, oldV, newV) -> renderCards(filteredList));
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredList.setPredicate(lieu -> matchesFilter(lieu, newVal));
            renderCards(filteredList);
        });
    }

    private boolean matchesFilter(Lieu lieu, String q) {
        String query = (q == null) ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return true;

        String mode = filterCombo.getValue() == null ? "Nom" : filterCombo.getValue();

        return switch (mode) {
            case "Ville" -> contains(lieu.getVille(), query);
            case "Catégorie" -> contains(lieu.getCategorie(), query);
            case "Type" -> contains(lieu.getType(), query);
            default -> contains(lieu.getNom(), query);
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

        // IMPORTANT: le "tremblement" vient presque toujours d'une boucle:
        // largeur viewport -> recalcul tiles -> hauteur contenu change -> scrollbar apparaît/disparaît -> largeur change...
        // 1) on stabilise la largeur en gardant la scrollbar verticale toujours présente
        // 2) on évite de forcer la prefWidth du TilePane (fitToWidth le gère déjà)
        cardsScroll.setFitToWidth(true);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        cardsScroll.viewportBoundsProperty().addListener((obs, oldB, b) -> {
            if (b == null) return;
            double viewportW = b.getWidth();

            // Ignore les micro-variations (arrondis) pour éviter des relayouts en boucle
            if (lastViewportW > 0 && Math.abs(viewportW - lastViewportW) < 0.5) return;
            lastViewportW = viewportW;

            double panePadding = 12; // cardsPane padding (6 + 6)
            double gap = 14;

            // 3 colonnes => 2 gaps
            double tileW = (viewportW - panePadding - (2 * gap)) / 3.0;
            tileW = Math.max(260, tileW);

            cardsPane.setPrefTileWidth(tileW);
            // Ne pas forcer la largeur: fitToWidth gère le contenu, sinon on déclenche des relayouts inutiles
        });

        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) {
            double viewportW = b.getWidth();
            double tileW = Math.max(260, (viewportW - 12 - 28) / 3.0);
            cardsPane.setPrefTileWidth(tileW);
        }
    }

    private void loadData() {
        try {
            masterList.clear();
            List<Lieu> lieux = lieuService.getAll();
            masterList.addAll(lieux);

            updateKpis(lieux);
            renderCards(filteredList);
        } catch (Exception e) {
            showError("Erreur", "Chargement des lieux impossible", e.getMessage());
        }
    }

    private void updateKpis(List<Lieu> lieux) {
        if (kpiTotal != null) kpiTotal.setText(String.valueOf(lieux.size()));
        long publicCount = lieux.stream().filter(l -> "PUBLIC".equalsIgnoreCase(safe(l.getType()))).count();
        if (kpiPublic != null) kpiPublic.setText(String.valueOf(publicCount));
    }

    private void renderCards(List<Lieu> lieux) {
        cardsPane.getChildren().clear();
        selectedLieu = null;
        selectedCard = null;

        for (Lieu lieu : lieux) {
            Node card = createLieuCard(lieu);
            cardsPane.getChildren().add(card);
        }

        if (lieux.isEmpty()) {
            Label empty = new Label("Aucun lieu trouvé.");
            empty.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 800;");
            cardsPane.getChildren().add(empty);
        }
    }

    private Node createLieuCard(Lieu lieu) {
        VBox card = new VBox(8);
        card.getStyleClass().add("lieu-card");

        Label title = new Label(safe(lieu.getNom()));
        title.getStyleClass().add("card-title");

        String metaText = safe(lieu.getVille()) + " • " + safe(lieu.getCategorie()) + " • " + safe(lieu.getType());
        Label meta = new Label(metaText.trim());
        meta.getStyleClass().add("card-meta");

        Label address = new Label(safe(lieu.getAdresse()));
        address.getStyleClass().add("card-muted");
        address.setWrapText(true);

        Label idChip = new Label("ID: " + lieu.getId() + " | Offre: " + lieu.getIdOffre());
        idChip.getStyleClass().add("card-chip");

        Button quickEdit = new Button("Modifier");
        Button quickDelete = new Button("Supprimer");

        quickEdit.getStyleClass().add("card-btn");
        quickDelete.getStyleClass().addAll("card-btn", "danger");

        quickEdit.setOnAction(e -> openEditor(lieu));
        quickDelete.setOnAction(e -> {
            if (confirmDelete("Supprimer le lieu: " + safe(lieu.getNom()) + " ?")) {
                try {
                    lieuService.delete(lieu.getId());
                    loadData();
                } catch (Exception ex) {
                    showError("Erreur", "Suppression impossible", ex.getMessage());
                }
            }
        });

        HBox actions = new HBox(10, quickEdit, quickDelete);
        actions.getStyleClass().add("card-actions");

        card.getChildren().addAll(title, meta, address, idChip, actions);

        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> selectCard(card, lieu));
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2) openEditor(lieu);
        });

        return card;
    }

    private void selectCard(Node card, Lieu lieu) {
        if (selectedCard != null) selectedCard.getStyleClass().remove("selected");
        selectedCard = card;
        selectedLieu = lieu;
        card.getStyleClass().add("selected");
    }

    // ===== Editor (CRUD) + Map Picker =====

    private void openEditor(Lieu existing) {
        boolean isEdit = existing != null;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Modifier Lieu" : "Ajouter Lieu");

        // ----- Controls -----
        TextField tfIdOffre = new TextField();
        TextField tfNom = new TextField();
        TextField tfVille = new TextField();
        TextField tfAdresse = new TextField();

        ComboBox<String> cbCategorie = new ComboBox<>();
        List<String> cats = lieuService.getDistinctCategories();
        if (cats == null || cats.isEmpty()) {
            cbCategorie.setItems(FXCollections.observableArrayList(
                    "CAFE", "RESTO", "HOTEL", "MUSEE", "PARK", "SHOP", "AUTRE"
            ));
        } else {
            cbCategorie.setItems(FXCollections.observableArrayList(cats));
        }

        ComboBox<String> cbType = new ComboBox<>();
        cbType.setItems(FXCollections.observableArrayList("PUBLIC", "PRIVE"));

        TextField tfLatitude = new TextField();
        TextField tfLongitude = new TextField();
        tfLatitude.setEditable(false);
        tfLongitude.setEditable(false);
        tfLatitude.setPromptText("Clique sur la map...");
        tfLongitude.setPromptText("Clique sur la map...");

        TextField tfImageUrl = new TextField();
        TextField tfTelephone = new TextField();
        TextField tfSiteWeb = new TextField();
        TextField tfInstagram = new TextField();
        TextArea taDescription = new TextArea();
        taDescription.setPrefRowCount(3);

        TextField tfBudgetMin = new TextField();
        TextField tfBudgetMax = new TextField();

        // ----- Prefill edit -----
        if (isEdit) {
            tfIdOffre.setText(String.valueOf(existing.getIdOffre()));
            tfNom.setText(safe(existing.getNom()));
            tfVille.setText(safe(existing.getVille()));
            tfAdresse.setText(safe(existing.getAdresse()));

            cbCategorie.getSelectionModel().select(safe(existing.getCategorie()));
            cbType.getSelectionModel().select(safe(existing.getType()).isBlank() ? "PUBLIC" : existing.getType());

            tfLatitude.setText(existing.getLatitude() == null ? "" : String.valueOf(existing.getLatitude()));
            tfLongitude.setText(existing.getLongitude() == null ? "" : String.valueOf(existing.getLongitude()));

            tfImageUrl.setText(safe(existing.getImageUrl()));
            tfTelephone.setText(safe(existing.getTelephone()));
            tfSiteWeb.setText(safe(existing.getSiteWeb()));
            tfInstagram.setText(safe(existing.getInstagram()));
            taDescription.setText(safe(existing.getDescription()));

            tfBudgetMin.setText(existing.getBudgetMin() == null ? "" : String.valueOf(existing.getBudgetMin()));
            tfBudgetMax.setText(existing.getBudgetMax() == null ? "" : String.valueOf(existing.getBudgetMax()));
        } else {
            if (!cbCategorie.getItems().isEmpty()) cbCategorie.getSelectionModel().selectFirst();
            cbType.getSelectionModel().select("PUBLIC");
            tfIdOffre.setText("0");
        }

        // ----- Map (WebView) -----
        WebView webView = new WebView();
        webView.setPrefSize(520, 320);

        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);

        String url = getClass().getResource("/map/map_picker.html").toExternalForm();
        engine.load(url);

        // On écoute le title: "lat,lng|address"
        engine.titleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) return;
            if (!newT.contains(",")) return; // ignore "Sélecteur..."

            String[] mainParts = newT.split("\\|", 2);
            String coords = mainParts[0];

            String[] parts = coords.split(",");
            if (parts.length != 2) return;

            String lat = parts[0].trim();
            String lng = parts[1].trim();
            tfLatitude.setText(lat);
            tfLongitude.setText(lng);

            if (mainParts.length == 2) {
                String fullAddress = mainParts[1].trim();
                if (!fullAddress.isBlank()) {
                    tfAdresse.setText(fullAddress);
                    String ville = extractCityFromDisplayName(fullAddress);
                    if (ville != null && !ville.isBlank()) tfVille.setText(ville);
                }
            }
        });

        engine.getLoadWorker().stateProperty().addListener((obs, oldS, newS) -> {
            if (newS == Worker.State.SUCCEEDED) {
                // Map chargée
            }
        });

        // ----- Layout -----
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));

        int r = 0;

        grid.add(new Label("ID Offre (optionnel):"), 0, r);
        grid.add(tfIdOffre, 1, r++);

        grid.add(new Label("Nom:"), 0, r);
        grid.add(tfNom, 1, r++);

        grid.add(new Label("Ville:"), 0, r);
        grid.add(tfVille, 1, r++);

        grid.add(new Label("Adresse:"), 0, r);
        grid.add(tfAdresse, 1, r++);

        grid.add(new Label("Catégorie:"), 0, r);
        grid.add(cbCategorie, 1, r++);

        grid.add(new Label("Type:"), 0, r);
        grid.add(cbType, 1, r++);

        grid.add(new Label("Map (clic pour lat/lng):"), 0, r);
        grid.add(webView, 1, r++);

        grid.add(new Label("Latitude:"), 0, r);
        grid.add(tfLatitude, 1, r++);

        grid.add(new Label("Longitude:"), 0, r);
        grid.add(tfLongitude, 1, r++);

        grid.add(new Label("Image URL:"), 0, r);
        grid.add(tfImageUrl, 1, r++);

        grid.add(new Label("Téléphone:"), 0, r);
        grid.add(tfTelephone, 1, r++);

        grid.add(new Label("Site web:"), 0, r);
        grid.add(tfSiteWeb, 1, r++);

        grid.add(new Label("Instagram:"), 0, r);
        grid.add(tfInstagram, 1, r++);

        grid.add(new Label("Description:"), 0, r);
        grid.add(taDescription, 1, r++);

        grid.add(new Label("Budget min:"), 0, r);
        grid.add(tfBudgetMin, 1, r++);

        grid.add(new Label("Budget max:"), 0, r);
        grid.add(tfBudgetMax, 1, r++);

        Button btnSave = new Button("Enregistrer");
        Button btnCancel = new Button("Annuler");
        HBox actions = new HBox(10, btnSave, btnCancel);
        actions.setAlignment(Pos.CENTER_RIGHT);
        grid.add(actions, 1, r);

        btnCancel.setOnAction(e -> dialog.close());

        btnSave.setOnAction(e -> {
            String nom = textOf(tfNom);
            String ville = textOf(tfVille);
            String adresse = textOf(tfAdresse);
            String categorie = cbCategorie.getValue();
            String type = cbType.getValue();

            if (nom.isEmpty() || ville.isEmpty() || adresse.isEmpty() || categorie == null || type == null) {
                showWarning("Veuillez renseigner Nom, Ville, Adresse, Catégorie et Type.");
                return;
            }

            if (tfLatitude.getText().isBlank() || tfLongitude.getText().isBlank()) {
                showWarning("Veuillez choisir la position sur la map (latitude/longitude).");
                return;
            }

            int idOffre = parseIntOrZero(textOf(tfIdOffre));

            Double lat;
            Double lon;
            try {
                lat = Double.valueOf(tfLatitude.getText().trim());
                lon = Double.valueOf(tfLongitude.getText().trim());
            } catch (NumberFormatException ex) {
                showWarning("Latitude/Longitude invalides.");
                return;
            }

            Double budgetMin = parseDoubleOrNull(textOf(tfBudgetMin));
            Double budgetMax = parseDoubleOrNull(textOf(tfBudgetMax));

            try {
                if (isEdit) {
                    existing.setIdOffre(idOffre);
                    existing.setNom(nom);
                    existing.setVille(ville);
                    existing.setAdresse(adresse);
                    existing.setCategorie(categorie);
                    existing.setType(type);
                    existing.setLatitude(lat);
                    existing.setLongitude(lon);

                    existing.setImageUrl(textOf(tfImageUrl));
                    existing.setTelephone(textOf(tfTelephone));
                    existing.setSiteWeb(textOf(tfSiteWeb));
                    existing.setInstagram(textOf(tfInstagram));
                    existing.setDescription(textOf(taDescription));

                    existing.setBudgetMin(budgetMin);
                    existing.setBudgetMax(budgetMax);

                    lieuService.update(existing);
                } else {
                    Lieu toAdd = new Lieu();
                    toAdd.setIdOffre(idOffre);
                    toAdd.setNom(nom);
                    toAdd.setVille(ville);
                    toAdd.setAdresse(adresse);
                    toAdd.setCategorie(categorie);
                    toAdd.setType(type);
                    toAdd.setLatitude(lat);
                    toAdd.setLongitude(lon);

                    toAdd.setImageUrl(textOf(tfImageUrl));
                    toAdd.setTelephone(textOf(tfTelephone));
                    toAdd.setSiteWeb(textOf(tfSiteWeb));
                    toAdd.setInstagram(textOf(tfInstagram));
                    toAdd.setDescription(textOf(taDescription));

                    toAdd.setBudgetMin(budgetMin);
                    toAdd.setBudgetMax(budgetMax);

                    lieuService.add(toAdd);
                }

                loadData();
                dialog.close();

            } catch (Exception ex) {
                showError("Erreur", "Enregistrement impossible", ex.getMessage());
            }
        });

        Scene scene = new Scene(grid, 860, 820);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    // ===== Helpers =====

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String textOf(TextInputControl tf) {
        return tf == null ? "" : Optional.ofNullable(tf.getText()).orElse("").trim();
    }

    private int parseIntOrZero(String s) {
        try {
            if (s == null || s.isBlank()) return 0;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private Double parseDoubleOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean confirmDelete(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().map(result -> result == ButtonType.OK).orElse(false);
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attention");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }

    /**
     * Heuristic: extract city from a display_name returned by Nominatim/geocoder.
     * Example: "Place X, Paris, Île-de-France, France" -> "Paris"
     */
    private String extractCityFromDisplayName(String displayName) {
        if (displayName == null) return null;
        String[] parts = displayName.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();

        if (parts.length == 0) return null;
        if (parts.length == 1) return parts[0];

        if (parts.length == 2) {
            return parts[0].matches(".*\\d.*") ? parts[1] : parts[0];
        }

        return parts[1];
    }
}