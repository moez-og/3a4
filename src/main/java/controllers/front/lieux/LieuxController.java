package controllers.front.lieux;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
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
    @FXML private VBox rootVBox;   // fx:id du VBox racine de la page
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
        animatePageIn();
    }

    /** Animation d'entrée de la page : sections en cascade du haut */
    private void animatePageIn() {
        if (rootVBox == null) return;
        var sections = rootVBox.getChildren();
        for (int i = 0; i < sections.size(); i++) {
            var node = sections.get(i);
            node.setOpacity(0);
            node.setTranslateY(18);
            int delay = i * 70;
            PauseTransition p = new PauseTransition(Duration.millis(delay));
            p.setOnFinished(e -> {
                FadeTransition ft = new FadeTransition(Duration.millis(320), node);
                ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition tt = new TranslateTransition(Duration.millis(320), node);
                tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(ft, tt).play();
            });
            p.play();
        }
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

        String[][] tranches = {
            { "gratuit",  "🆓  Gratuit",      "= 0 TND"      },
            { "low",      "💚  Petit budget",  "< 20 TND"     },
            { "mid",      "🟡  Moyen",         "20–80 TND"    },
            { "high",     "🔴  Premium",       "> 80 TND"     }
        };

        for (int i = 0; i < tranches.length; i++) {
            String key   = tranches[i][0];
            String label = tranches[i][1];
            String hint  = tranches[i][2];

            Button btn = new Button(label + "  " + hint);
            btn.getStyleClass().add("chip");
            btn.setOpacity(0);
            btn.setTranslateY(8);
            btn.setOnAction(e -> {
                selectedBudget = key;
                markSelected(budgetChips, btn);
                applyFilters();
            });
            budgetChips.getChildren().add(btn);
            animateChipIn(btn, i * 40);
        }
    }

    private void buildVilleChips(List<String> villes) {
        villesChips.getChildren().clear();
        villeBtnByKey.clear();

        for (int i = 0; i < villes.size(); i++) {
            String v = villes.get(i);
            Button b = new Button(v);
            b.getStyleClass().add("chip");
            b.setOpacity(0);
            b.setTranslateY(8);
            b.setOnAction(e -> {
                selectedVille = v;
                markSelected(villesChips, b);
                applyFilters();
            });
            villesChips.getChildren().add(b);
            villeBtnByKey.put(norm(v), b);
            animateChipIn(b, i * 30);
        }
    }

    private void buildCategorieChips(List<String> cats) {
        categoriesChips.getChildren().clear();
        catBtnByKey.clear();

        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            Button b = new Button(c);
            b.getStyleClass().add("chip");
            b.setOpacity(0);
            b.setTranslateY(8);
            b.setOnAction(e -> {
                selectedCategorie = c;
                markSelected(categoriesChips, b);
                applyFilters();
            });
            categoriesChips.getChildren().add(b);
            catBtnByKey.put(norm(c), b);
            animateChipIn(b, i * 30);
        }
    }

    /** Entrée animée d'un chip : fade + slide depuis le bas */
    private void animateChipIn(javafx.scene.Node node, int delayMs) {
        PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
        pause.setOnFinished(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(220), node);
            ft.setFromValue(0); ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(220), node);
            tt.setFromY(8); tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, tt).play();
        });
        pause.play();
    }

    private void markSelected(HBox row, Button selectedBtn) {
        row.getChildren().forEach(n -> n.getStyleClass().remove("chipSelected"));
        selectedBtn.getStyleClass().add("chipSelected");
        // Flash de confirmation sur le chip sélectionné
        ScaleTransition st = new ScaleTransition(Duration.millis(100), selectedBtn);
        st.setToX(1.10); st.setToY(1.10);
        ScaleTransition back = new ScaleTransition(Duration.millis(120), selectedBtn);
        back.setToX(1.0); back.setToY(1.0);
        back.setInterpolator(Interpolator.EASE_OUT);
        st.setOnFinished(e -> back.play());
        st.play();
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
        // ── Fade out les anciennes cartes ──────────────────────────
        if (!cardsPane.getChildren().isEmpty()) {
            FadeTransition ftOut = new FadeTransition(Duration.millis(140), cardsPane);
            ftOut.setFromValue(1.0);
            ftOut.setToValue(0.0);
            ftOut.setOnFinished(e -> populateCards(lieux));
            ftOut.play();
        } else {
            populateCards(lieux);
        }
    }

    private void populateCards(List<Lieu> lieux) {
        cardsPane.getChildren().clear();
        cardsPane.setOpacity(1.0);

        if (countLabel != null) countLabel.setText(lieux.size() + " lieu(x)");

        if (lieux.isEmpty()) {
            showState("Aucun lieu trouvé avec ces filtres.");
            return;
        }
        hideState();

        // Construire toutes les cartes invisibles
        List<VBox> cards = new ArrayList<>();
        for (Lieu l : lieux) {
            VBox card = buildCard(l);
            card.setOpacity(0);
            card.setTranslateY(24);
            card.setScaleX(0.94);
            card.setScaleY(0.94);
            cardsPane.getChildren().add(card);
            cards.add(card);
        }

        // Entrée en cascade : chaque carte apparaît avec un décalage de 45ms
        for (int i = 0; i < cards.size(); i++) {
            VBox card = cards.get(i);
            int delayMs = i * 45;

            PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
            pause.setOnFinished(ev -> {
                // Fade + translateY + scale simultanés
                FadeTransition ft = new FadeTransition(Duration.millis(280), card);
                ft.setFromValue(0); ft.setToValue(1);
                ft.setInterpolator(Interpolator.EASE_OUT);

                TranslateTransition tt = new TranslateTransition(Duration.millis(280), card);
                tt.setFromY(24); tt.setToY(0);
                tt.setInterpolator(Interpolator.EASE_OUT);

                ScaleTransition st = new ScaleTransition(Duration.millis(280), card);
                st.setFromX(0.94); st.setToX(1.0);
                st.setFromY(0.94); st.setToY(1.0);
                st.setInterpolator(Interpolator.EASE_OUT);

                new ParallelTransition(ft, tt, st).play();
            });
            pause.play();
        }
    }

    private VBox buildCard(Lieu l) {
        VBox card = new VBox(0);
        card.getStyleClass().add("lieuCard");
        card.setPrefWidth(310);

        // ── IMAGE avec overlay gradient ──────────────────────────
        StackPane imgWrap = new StackPane();

        ImageView iv = new ImageView();
        iv.setFitWidth(310);
        iv.setFitHeight(175);
        iv.setPreserveRatio(false);

        Rectangle clip = new Rectangle(310, 175);
        clip.setArcWidth(22); clip.setArcHeight(22);
        iv.setClip(clip);
        iv.setImage(loadImageOrFallback(l.getImageUrl()));

        // Overlay gradient sombre en bas de l'image
        Rectangle overlay = new Rectangle(310, 175);
        overlay.setArcWidth(22); overlay.setArcHeight(22);
        overlay.setFill(javafx.scene.paint.Color.TRANSPARENT);

        imgWrap.getChildren().addAll(iv, overlay);

        // Badge catégorie en haut à gauche
        String cat = safe(l.getCategorie());
        if (!cat.isBlank()) {
            Label catBadge = new Label(cat);
            catBadge.setStyle(
                "-fx-background-color: rgba(11,37,80,0.72);" +
                "-fx-background-radius: 999;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 10px;" +
                "-fx-font-weight: 900;" +
                "-fx-padding: 4 10;"
            );
            StackPane.setAlignment(catBadge, Pos.TOP_LEFT);
            StackPane.setMargin(catBadge, new Insets(10));
            imgWrap.getChildren().add(catBadge);
        }

        // Bouton favori (cœur)
        if (currentUser != null) {
            boolean[] isFav = { favoriService.isFavori(currentUser.getId(), l.getId()) };

            Button heart = new Button(isFav[0] ? "♥" : "♡");
            applyHeartStyle(heart, isFav[0]);

            // Hover avec scale animé
            heart.setOnMouseEntered(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), heart);
                st.setToX(1.18); st.setToY(1.18); st.play();
            });
            heart.setOnMouseExited(e -> {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), heart);
                st.setToX(1.0); st.setToY(1.0); st.play();
            });

            heart.setOnAction(e -> {
                isFav[0] = favoriService.toggle(currentUser.getId(), l.getId());
                heart.setText(isFav[0] ? "♥" : "♡");
                applyHeartStyle(heart, isFav[0]);

                // Animation "pulse" au clic
                ScaleTransition pulse = new ScaleTransition(Duration.millis(80), heart);
                pulse.setToX(1.30); pulse.setToY(1.30);
                ScaleTransition back = new ScaleTransition(Duration.millis(150), heart);
                back.setToX(1.0); back.setToY(1.0);
                back.setInterpolator(Interpolator.EASE_OUT);
                pulse.setOnFinished(ev -> back.play());
                pulse.play();
            });

            heart.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> ev.consume());
            StackPane.setAlignment(heart, Pos.TOP_RIGHT);
            StackPane.setMargin(heart, new Insets(10));
            imgWrap.getChildren().add(heart);
        }

        // ── CORPS ────────────────────────────────────────────────
        VBox body = new VBox(6);
        body.getStyleClass().add("cardBody");
        body.setPadding(new Insets(12, 14, 14, 14));

        Label name = new Label(safe(l.getNom()));
        name.getStyleClass().add("cardTitle");
        name.setWrapText(false);

        // Ligne ville + budget
        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label meta = new Label("📍 " + safe(l.getVille()));
        meta.getStyleClass().add("cardMeta");

        // Badge budget si disponible
        String budgetStr = formatBudgetCard(l.getBudgetMin(), l.getBudgetMax());
        if (!budgetStr.isEmpty()) {
            Label budgetBadge = new Label(budgetStr);
            budgetBadge.getStyleClass().add("cardBudgetBadge");
            metaRow.getChildren().addAll(meta, budgetBadge);
        } else {
            metaRow.getChildren().add(meta);
        }

        Label addr = new Label(safe(l.getAdresse()));
        addr.getStyleClass().add("cardAddr");
        addr.setWrapText(false);
        addr.setMaxWidth(280);

        // Actions
        HBox actions = new HBox(8);
        actions.setPadding(new Insets(6, 0, 0, 0));

        Button details = new Button("Voir détails");
        details.getStyleClass().add("primaryBtn");
        details.setOnAction(e -> openDetailsPage(l));

        Button maps = new Button("🗺");
        maps.getStyleClass().add("ghostBtn");
        maps.setPrefWidth(36); maps.setPrefHeight(36);
        maps.setStyle("-fx-padding: 6; -fx-background-radius: 10; -fx-border-radius: 10;");
        maps.setOnAction(e -> openMaps(l));
        Tooltip.install(maps, new Tooltip("Ouvrir dans Maps"));

        Button offres = new Button("🏷");
        offres.getStyleClass().add("ghostBtn");
        offres.setPrefWidth(36); offres.setPrefHeight(36);
        offres.setStyle("-fx-padding: 6; -fx-background-radius: 10; -fx-border-radius: 10;");
        offres.setOnAction(e -> openOffresForLieu(l));
        Tooltip.install(offres, new Tooltip("Voir les offres"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actions.getChildren().addAll(details, spacer, maps, offres);

        for (Button b : new Button[]{details, maps, offres})
            b.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> ev.consume());

        body.getChildren().addAll(name, metaRow, addr, actions);
        card.getChildren().addAll(imgWrap, body);

        // ── HOVER : élévation + bordure or ─────────────────────
        DropShadow shadowNormal = new DropShadow(18, 0, 6, Color.rgb(11, 37, 80, 0.09));
        DropShadow shadowHover  = new DropShadow(32, 0, 14, Color.rgb(11, 37, 80, 0.22));

        card.setEffect(shadowNormal);

        card.setOnMouseEntered(e -> {
            // Élévation avec timeline
            Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(180),
                    new KeyValue(card.translateYProperty(), -6, Interpolator.EASE_OUT)
                )
            );
            tl.play();
            card.setEffect(shadowHover);
            // Legère teinte dorée sur l'overlay
            overlay.setFill(javafx.scene.paint.Color.rgb(212, 175, 55, 0.07));
        });

        card.setOnMouseExited(e -> {
            Timeline tl = new Timeline(
                new KeyFrame(Duration.millis(180),
                    new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT)
                )
            );
            tl.play();
            card.setEffect(shadowNormal);
            overlay.setFill(javafx.scene.paint.Color.TRANSPARENT);
        });

        card.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2) openDetailsPage(l);
        });

        return card;
    }

    /** Style du bouton cœur favori */
    private void applyHeartStyle(Button heart, boolean active) {
        heart.setStyle(
            "-fx-background-color: " + (active ? "rgba(225,29,72,0.92)" : "white") + ";" +
            "-fx-background-radius: 999;" +
            "-fx-min-width: 36; -fx-max-width: 36;" +
            "-fx-min-height: 36; -fx-max-height: 36;" +
            "-fx-padding: 0;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 16px;" +
            "-fx-text-fill: " + (active ? "white" : "#e11d48") + ";" +
            "-fx-alignment: center;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 8, 0, 0, 2);"
        );
    }

    /** Formate le budget pour le badge de la carte */
    private String formatBudgetCard(Double min, Double max) {
        if (min == null && max == null) return "";
        if (min != null && min == 0 && (max == null || max == 0)) return "🆓 Gratuit";
        if (min != null && max != null) return String.format("%.0f–%.0f TND", min, max);
        if (min != null) return String.format("≥ %.0f TND", min);
        return String.format("≤ %.0f TND", max);
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