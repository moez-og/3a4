package controllers.front.lieux;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import models.lieux.Lieu;
import services.lieux.LieuService;
import utils.ui.ShellNavigator;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LieuxController {

    @FXML private HBox villesChips;
    @FXML private HBox categoriesChips;

    @FXML private TextField searchField;
    @FXML private FlowPane cardsPane;

    @FXML private Label countLabel;
    @FXML private Label stateLabel;

    private final LieuService lieuService = new LieuService();

    private ShellNavigator navigator;

    private List<Lieu> all = new ArrayList<>();
    private String selectedVille = null;
    private String selectedCategorie = null;

    // Pour retrouver un chip rapidement (preset)
    private final Map<String, Button> villeBtnByKey = new HashMap<>();
    private final Map<String, Button> catBtnByKey = new HashMap<>();

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        loadData();
        wireSearch();
    }

    /**
     * ✅ Preset appelé depuis l’accueil:
     * - ville : "Tunis"
     * - cat   : "CAFE"
     * - q     : "cafe"
     */
    public void applyPreset(String ville, String cat, String q) {
        // Recherche
        if (searchField != null) {
            searchField.setText(q == null ? "" : q.trim());
        }

        // Ville
        if (ville != null && !ville.trim().isEmpty()) {
            String key = norm(ville);
            Button b = villeBtnByKey.get(key);
            if (b != null) {
                selectedVille = b.getText();
                markSelected(villesChips, b);
            } else {
                // si la ville n’existe pas dans les chips -> on fait juste la recherche textuelle
                selectedVille = null;
                villesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
                if (searchField != null && (q == null || q.isBlank())) {
                    searchField.setText(ville.trim());
                }
            }
        } else {
            selectedVille = null;
            villesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        }

        // Catégorie
        if (cat != null && !cat.trim().isEmpty()) {
            String key = norm(cat);
            Button b = catBtnByKey.get(key);
            if (b != null) {
                selectedCategorie = b.getText();
                markSelected(categoriesChips, b);
            } else {
                selectedCategorie = null;
                categoriesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
                if (searchField != null && (q == null || q.isBlank())) {
                    searchField.setText(cat.trim());
                }
            }
        } else {
            selectedCategorie = null;
            categoriesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        }

        applyFilters();
    }

    private void loadData() {
        try {
            all = lieuService.getAll();

            buildVilleChips(lieuService.getDistinctVilles());
            buildCategorieChips(lieuService.getDistinctCategories());

            applyFilters();

        } catch (Exception e) {
            showState("Erreur chargement lieux: " + safe(e.getMessage()));
        }
    }

    private void wireSearch() {
        if (searchField == null) return;
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
    }

    /* ===================== ACTIONS (FXML) ===================== */

    @FXML
    public void resetFilters() {
        selectedVille = null;
        selectedCategorie = null;

        villesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        categoriesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));

        if (searchField != null) searchField.setText("");
        applyFilters();
    }

    @FXML
    public void selectAllVilles() {
        selectedVille = null;
        villesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        applyFilters();
    }

    @FXML
    public void selectAllCategories() {
        selectedCategorie = null;
        categoriesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        applyFilters();
    }

    /* ===================== CHIPS ===================== */

    private void buildVilleChips(List<String> villes) {
        villesChips.getChildren().clear();
        villeBtnByKey.clear();

        for (String v : villes) {
            Button b = new Button(v);
            b.getStyleClass().addAll("chip");
            b.setOnAction(e -> {
                selectedVille = v;
                markSelected(villesChips, b);
                applyFilters();
            });
            villesChips.getChildren().add(b);
            villeBtnByKey.put(norm(v), b);
        }
    }

    private void buildCategorieChips(List<String> cats) {
        categoriesChips.getChildren().clear();
        catBtnByKey.clear();

        for (String c : cats) {
            Button b = new Button(c);
            b.getStyleClass().addAll("chip");
            b.setOnAction(e -> {
                selectedCategorie = c;
                markSelected(categoriesChips, b);
                applyFilters();
            });
            categoriesChips.getChildren().add(b);
            catBtnByKey.put(norm(c), b);
        }
    }

    private void markSelected(HBox row, Button selectedBtn) {
        row.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        selectedBtn.getStyleClass().add("chipSelected");
    }

    /* ===================== FILTER + RENDER ===================== */

    private void applyFilters() {
        String q = safe(searchField == null ? "" : searchField.getText()).trim().toLowerCase(Locale.ROOT);

        List<Lieu> filtered = new ArrayList<>();
        for (Lieu l : all) {
            if (selectedVille != null && !selectedVille.equalsIgnoreCase(safe(l.getVille()))) continue;
            if (selectedCategorie != null && !selectedCategorie.equalsIgnoreCase(safe(l.getCategorie()))) continue;

            if (!q.isEmpty()) {
                String blob = (safe(l.getNom()) + " " + safe(l.getVille()) + " " + safe(l.getCategorie()) + " " + safe(l.getAdresse()))
                        .toLowerCase(Locale.ROOT);
                if (!blob.contains(q)) continue;
            }

            filtered.add(l);
        }

        renderCards(filtered);
    }

    private void renderCards(List<Lieu> lieux) {
        cardsPane.getChildren().clear();

        if (countLabel != null) countLabel.setText(lieux.size() + " lieu(x)");

        if (lieux.isEmpty()) {
            showState("Aucun lieu trouvé avec ces filtres.");
            return;
        }

        hideState();

        for (Lieu l : lieux) {
            cardsPane.getChildren().add(buildCard(l));
        }
    }

    private VBox buildCard(Lieu l) {
        VBox card = new VBox(10);
        card.getStyleClass().add("lieuCard");
        card.setPrefWidth(320);

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");

        ImageView iv = new ImageView();
        iv.setFitWidth(320);
        iv.setFitHeight(170);
        iv.setPreserveRatio(false);

        Rectangle clip = new Rectangle(320, 170);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        iv.setClip(clip);

        iv.setImage(loadImageOrFallback(l.getImageUrl()));
        imgWrap.getChildren().add(iv);

        Label name = new Label(safe(l.getNom()));
        name.getStyleClass().add("cardTitle");

        Label meta = new Label(safe(l.getVille()) + " • " + safe(l.getCategorie()));
        meta.getStyleClass().add("cardMeta");

        Label addr = new Label(safe(l.getAdresse()));
        addr.getStyleClass().add("cardAddr");
        addr.setWrapText(true);

        HBox actions = new HBox(10);
        Button details = new Button("Voir détails");
        details.getStyleClass().add("primaryBtn");
        details.setOnAction(e -> openDetailsPage(l));

        Button maps = new Button("Maps");
        maps.getStyleClass().add("ghostBtn");
        maps.setOnAction(e -> openMaps(l));

        actions.getChildren().addAll(details, maps);

        card.getChildren().addAll(imgWrap, name, meta, addr, actions);

        card.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) openDetailsPage(l);
        });

        return card;
    }

    private void openDetailsPage(Lieu l) {
        if (navigator == null) return;
        navigator.navigate("lieu-details:" + l.getId());
    }

    private void openMaps(Lieu l) {
        try {
            String url;
            if (l.getLatitude() != null && l.getLongitude() != null) {
                url = "https://www.google.com/maps/search/?api=1&query=" + l.getLatitude() + "," + l.getLongitude();
            } else {
                String q = URLEncoder.encode(safe(l.getNom()) + " " + safe(l.getVille()), StandardCharsets.UTF_8);
                url = "https://www.google.com/maps/search/?api=1&query=" + q;
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {}
    }

    private Image loadImageOrFallback(String raw) {
        String path = safe(raw).trim();

        try {
            if (!path.isEmpty()) {
                if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
                    return new Image(path, true);
                }
                if (path.startsWith("/")) {
                    URL u = getClass().getResource(path);
                    if (u != null) return new Image(u.toExternalForm(), true);
                }
            }
        } catch (Exception ignored) {}

        URL fallback = getClass().getResource("/images/demo/hero/hero.jpg");
        return fallback == null ? null : new Image(fallback.toExternalForm(), true);
    }

    private void showState(String msg) {
        if (stateLabel == null) return;
        stateLabel.setText(msg);
        stateLabel.setVisible(true);
        stateLabel.setManaged(true);
    }

    private void hideState() {
        if (stateLabel == null) return;
        stateLabel.setVisible(false);
        stateLabel.setManaged(false);
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }
}