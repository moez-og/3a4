package controllers.front.lieux;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import models.lieux.Lieu;
import models.users.User;
import controllers.front.shell.FrontDashboardController;
import services.lieux.FavoriLieuService;
import services.lieux.LieuService;
import utils.ui.FrontOfferContext;
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
    @FXML private HBox budgetChips;

    @FXML private TextField searchField;
    @FXML private FlowPane cardsPane;

    @FXML private Label countLabel;
    @FXML private Label stateLabel;

    private final LieuService lieuService = new LieuService();
    private final FavoriLieuService favoriService = new FavoriLieuService();

    private ShellNavigator navigator;
    private User currentUser;

    private List<Lieu> all = new ArrayList<>();
    private String selectedVille = null;
    private String selectedCategorie = null;
    private String selectedBudget = null;  // "gratuit" | "low" | "mid" | "high"

    // Pour retrouver un chip rapidement (preset)
    private final Map<String, Button> villeBtnByKey = new HashMap<>();
    private final Map<String, Button> catBtnByKey = new HashMap<>();

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        // Reconstruire les cartes maintenant que l'utilisateur est connu
        // (les cœurs favoris nécessitent currentUser pour être affichés)
        if (cardsPane != null) {
            applyFilters();
        }
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
            buildBudgetChips();

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
        selectedBudget = null;

        villesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        categoriesChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        if (budgetChips != null) budgetChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));

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

    @FXML
    public void selectAllBudgets() {
        selectedBudget = null;
        if (budgetChips != null) budgetChips.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        applyFilters();
    }

    /* ===================== CHIPS ===================== */

    private void buildBudgetChips() {
        if (budgetChips == null) return;
        budgetChips.getChildren().clear();

        // Tranches fixes
        String[][] tranches = {
            { "gratuit",  "🆓  Gratuit",     "= 0 TND"       },
            { "low",      "💚  Petit budget", "< 20 TND"      },
            { "mid",      "🟡  Moyen",        "20 – 80 TND"   },
            { "high",     "🔴  Premium",      "> 80 TND"      }
        };

        for (String[] t : tranches) {
            String key   = t[0];
            String label = t[1];
            String hint  = t[2];

            Button btn = new Button(label + "  " + hint);
            btn.getStyleClass().addAll("chip");
            btn.setOnAction(e -> {
                selectedBudget = key;
                markSelected(budgetChips, btn);
                applyFilters();
            });
            budgetChips.getChildren().add(btn);
        }
    }

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

            // Filtre budget
            if (selectedBudget != null) {
                double budget = budgetMoyen(l);
                boolean pass = switch (selectedBudget) {
                    case "gratuit" -> budget == 0;
                    case "low"     -> budget > 0 && budget < 20;
                    case "mid"     -> budget >= 20 && budget <= 80;
                    case "high"    -> budget > 80;
                    default        -> true;
                };
                if (!pass) continue;
            }

            if (!q.isEmpty()) {
                String blob = (safe(l.getNom()) + " " + safe(l.getVille()) + " " + safe(l.getCategorie()) + " " + safe(l.getAdresse()))
                        .toLowerCase(Locale.ROOT);
                if (!blob.contains(q)) continue;
            }

            filtered.add(l);
        }

        renderCards(filtered);
    }

    /** Calcule un budget moyen depuis budgetMin/Max du lieu */
    private double budgetMoyen(Lieu l) {
        Double min = l.getBudgetMin();
        Double max = l.getBudgetMax();
        if (min == null && max == null) return 0;
        if (min != null && max != null) return (min + max) / 2.0;
        if (min != null) return min;
        return max;
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

        // Bouton favori — design cercle blanc avec cœur
        if (currentUser != null) {
            boolean[] isFav = { favoriService.isFavori(currentUser.getId(), l.getId()) };

            // Style helpers
            java.util.function.Supplier<String> activeStyle = () ->
                "-fx-background-color: white;" +
                "-fx-background-radius: 999;" +
                "-fx-border-radius: 999;" +
                "-fx-min-width: 38; -fx-max-width: 38;" +
                "-fx-min-height: 38; -fx-max-height: 38;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;" +
                "-fx-font-size: 17px;" +
                "-fx-text-fill: #e11d48;" +
                "-fx-alignment: center;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 8, 0, 0, 2);";

            java.util.function.Supplier<String> inactiveStyle = () ->
                "-fx-background-color: white;" +
                "-fx-background-radius: 999;" +
                "-fx-border-radius: 999;" +
                "-fx-min-width: 38; -fx-max-width: 38;" +
                "-fx-min-height: 38; -fx-max-height: 38;" +
                "-fx-padding: 0;" +
                "-fx-cursor: hand;" +
                "-fx-font-size: 17px;" +
                "-fx-text-fill: #e11d48;" +
                "-fx-alignment: center;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 2);";

            // Utilise ♡ (outline) ou ♥ (plein) selon l'état
            Button heart = new Button(isFav[0] ? "♥" : "♡");
            heart.setStyle(isFav[0] ? activeStyle.get() : inactiveStyle.get());

            // Hover : légère mise en avant
            heart.setOnMouseEntered(e -> heart.setStyle(
                (isFav[0] ? activeStyle.get() : inactiveStyle.get())
                    .replace("rgba(0,0,0,0.18)", "rgba(0,0,0,0.30)")
                    .replace("rgba(0,0,0,0.22)", "rgba(0,0,0,0.32)")
                    + "-fx-scale-x: 1.12; -fx-scale-y: 1.12;"
            ));
            heart.setOnMouseExited(e ->
                heart.setStyle(isFav[0] ? activeStyle.get() : inactiveStyle.get())
            );

            heart.setOnAction(e -> {
                isFav[0] = favoriService.toggle(currentUser.getId(), l.getId());
                heart.setText(isFav[0] ? "♥" : "♡");
                heart.setStyle(isFav[0] ? activeStyle.get() : inactiveStyle.get());
            });

            heart.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> ev.consume());
            StackPane.setAlignment(heart, Pos.TOP_RIGHT);
            StackPane.setMargin(heart, new Insets(10));
            imgWrap.getChildren().add(heart);
        }

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

        Button offres = new Button("Voir offres");
        offres.getStyleClass().add("ghostBtn");
        offres.setOnAction(e -> openOffresForLieu(l));

        Button maps = new Button("Maps");
        maps.getStyleClass().add("ghostBtn");
        maps.setOnAction(e -> openMaps(l));

        // Empêche les boutons de déclencher le double-clic de la carte
        for (Button b : new Button[]{details, offres, maps}) {
            b.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> ev.consume());
        }

        actions.getChildren().addAll(details, offres, maps);

        card.getChildren().addAll(imgWrap, name, meta, addr, actions);

        card.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) openDetailsPage(l);
        });

        return card;
    }

    private void openOffresForLieu(Lieu l) {
        if (navigator == null || l == null) return;
        FrontOfferContext.setSelectedLieuId(l.getId());
        navigator.navigate(FrontDashboardController.ROUTE_OFFRES);
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