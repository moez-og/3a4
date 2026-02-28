package controllers.front.lieux;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.lieux.Lieu;
import models.users.User;
import services.lieux.EvaluationLieuService;
import services.lieux.FavoriLieuService;
import services.lieux.LieuService;
import utils.ui.ShellNavigator;
import utils.ui.ViewPaths;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LieuxController {

    /* ── FXML ─────────────────────────────────────── */
    @FXML private HBox      rootHBox;
    @FXML private VBox      leftPanel;
    @FXML private VBox      rightPanel;
    @FXML private StackPane detailContainer;
    @FXML private Label     rightPanelTitle;

    // Toolbar
    @FXML private HBox      activeFiltersBar;
    @FXML private TextField searchField;
    @FXML private Button    toggleFiltersBtn;
    @FXML private Label     countLabel;

    // Filters panel (collapsible)
    @FXML private VBox      filtersPanel;
    @FXML private HBox      villesChips;
    @FXML private HBox      categoriesChips;
    @FXML private HBox      budgetChips;

    // Cards
    @FXML private FlowPane  cardsPane;
    @FXML private Label     stateLabel;
    @FXML private ScrollPane cardsScroll;

    /* ── Services ─────────────────────────────────── */
    private final LieuService           lieuService   = new LieuService();
    private final FavoriLieuService     favoriService = new FavoriLieuService();
    private final EvaluationLieuService evalService   = new EvaluationLieuService();

    /* ── State ────────────────────────────────────── */
    private ShellNavigator navigator;
    private User            currentUser;
    private List<Lieu>      all         = new ArrayList<>();
    private String          selVille    = null;
    private String          selCat      = null;
    private String          selBudget   = null;
    private Lieu            selLieu     = null;
    private boolean         splitOpen   = false;
    private boolean         filtersOpen = false;
    private boolean         layoutReady = false;

    private final Map<String, Button>  villeMap = new HashMap<>();
    private final Map<String, Button>  catMap   = new HashMap<>();
    private final Map<Integer, VBox>   cardMap  = new HashMap<>();

    /* Ratios split */
    private static final double LEFT_RATIO  = 0.42;
    private static final double RIGHT_RATIO = 0.58;
    private Timeline splitAnim   = null;
    private Timeline filterAnim  = null;
    private double   filterFullH = -1;  // hauteur mesurée du filtersPanel

    /* ══════════════════════════════════════════════
       INIT
    ══════════════════════════════════════════════ */
    public void setNavigator(ShellNavigator n) { this.navigator = n; }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        if (cardsPane != null) applyFilters();
    }

    @FXML
    private void initialize() {
        // Stratégie pleine-largeur : bind au premier layout pass
        rootHBox.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            if (w <= 0) return;
            if (!layoutReady) {
                layoutReady = true;
                leftPanel.setPrefWidth(w);
                leftPanel.setMaxWidth(Double.MAX_VALUE);
                rightPanel.setPrefWidth(0);
                rightPanel.setMaxWidth(0);
            } else if (!splitOpen) {
                leftPanel.setPrefWidth(w);
                leftPanel.setMaxWidth(Double.MAX_VALUE);
            } else {
                leftPanel.setPrefWidth(w * LEFT_RATIO);
                leftPanel.setMaxWidth(w * LEFT_RATIO);
                rightPanel.setPrefWidth(w * RIGHT_RATIO);
                rightPanel.setMaxWidth(w * RIGHT_RATIO);
            }
        });

        loadData();
        wireSearch();
        animatePageIn();
    }

    private void animatePageIn() {
        if (leftPanel == null) return;
        leftPanel.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(280), leftPanel);
        ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT); ft.play();
    }

    /* ══════════════════════════════════════════════
       TOGGLE FILTRES (expand / collapse)
    ══════════════════════════════════════════════ */
    @FXML
    public void toggleFilters() {
        if (filtersOpen) collapseFilters();
        else             expandFilters();
    }

    private void expandFilters() {
        filtersOpen = true;
        toggleFiltersBtn.getStyleClass().remove("lxToggleFiltersBtnActive");
        toggleFiltersBtn.getStyleClass().add("lxToggleFiltersBtnActive");
        toggleFiltersBtn.setText("✕  Filtres");

        filtersPanel.setManaged(true);
        filtersPanel.setVisible(true);

        // Mesurer la hauteur naturelle si pas encore connue
        if (filterFullH < 0) {
            filtersPanel.setOpacity(0);
            // Laisser le layout calculer une fois, puis animer
            Platform.runLater(() -> {
                filterFullH = filtersPanel.prefHeight(-1);
                doExpandAnim();
            });
        } else {
            doExpandAnim();
        }
    }

    private void doExpandAnim() {
        if (filterAnim != null) filterAnim.stop();
        filtersPanel.setMaxHeight(0);
        filtersPanel.setOpacity(0);

        filterAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(filtersPanel.maxHeightProperty(), 0, Interpolator.EASE_BOTH),
                new KeyValue(filtersPanel.opacityProperty(),   0, Interpolator.EASE_OUT)
            ),
            new KeyFrame(Duration.millis(260),
                new KeyValue(filtersPanel.maxHeightProperty(), filterFullH + 40, Interpolator.EASE_BOTH),
                new KeyValue(filtersPanel.opacityProperty(),   1.0, Interpolator.EASE_OUT)
            )
        );
        filterAnim.setOnFinished(e -> filtersPanel.setMaxHeight(Double.MAX_VALUE));
        filterAnim.play();
    }

    private void collapseFilters() {
        filtersOpen = false;
        toggleFiltersBtn.getStyleClass().remove("lxToggleFiltersBtnActive");
        toggleFiltersBtn.setText("⊞  Filtres");

        double curH = filtersPanel.getHeight();
        if (filterAnim != null) filterAnim.stop();

        filterAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(filtersPanel.maxHeightProperty(), curH, Interpolator.EASE_BOTH),
                new KeyValue(filtersPanel.opacityProperty(),   1.0,  Interpolator.EASE_IN)
            ),
            new KeyFrame(Duration.millis(220),
                new KeyValue(filtersPanel.maxHeightProperty(), 0,   Interpolator.EASE_BOTH),
                new KeyValue(filtersPanel.opacityProperty(),   0,   Interpolator.EASE_IN)
            )
        );
        filterAnim.setOnFinished(e -> {
            filtersPanel.setManaged(false);
            filtersPanel.setVisible(false);
        });
        filterAnim.play();
    }

    /* ══════════════════════════════════════════════
       PRESET
    ══════════════════════════════════════════════ */
    public void applyPreset(String ville, String cat, String q) {
        if (searchField != null) searchField.setText(q == null ? "" : q.trim());
        if (ville != null && !ville.trim().isEmpty()) {
            Button b = villeMap.get(norm(ville));
            if (b != null) { selVille = b.getText(); markChip(villesChips, b); }
        }
        if (cat != null && !cat.trim().isEmpty()) {
            Button b = catMap.get(norm(cat));
            if (b != null) { selCat = b.getText(); markChip(categoriesChips, b); }
        }
        applyFilters();
    }

    /* ══════════════════════════════════════════════
       DONNÉES
    ══════════════════════════════════════════════ */
    private void loadData() {
        try {
            all = lieuService.getAll();
            buildVilleChips(lieuService.getDistinctVilles());
            buildCatChips(lieuService.getDistinctCategories());
            buildBudgetChips();
            applyFilters();
        } catch (Exception e) {
            showState("Erreur : " + safe(e.getMessage()));
        }
    }

    private void wireSearch() {
        if (searchField != null)
            searchField.textProperty().addListener((obs, o, n) -> applyFilters());
    }

    /* ══════════════════════════════════════════════
       FILTER ACTIONS
    ══════════════════════════════════════════════ */
    @FXML public void resetFilters() {
        selVille = selCat = selBudget = null;
        if (searchField != null) searchField.setText("");
        clearChips(villesChips); clearChips(categoriesChips);
        if (budgetChips != null) clearChips(budgetChips);
        refreshActiveFiltersBar();
        applyFilters();
    }
    @FXML public void selectAllVilles()     { selVille  = null; clearChips(villesChips);     refreshActiveFiltersBar(); applyFilters(); }
    @FXML public void selectAllCategories() { selCat    = null; clearChips(categoriesChips); refreshActiveFiltersBar(); applyFilters(); }
    @FXML public void selectAllBudgets()    { selBudget = null; if(budgetChips!=null) clearChips(budgetChips); refreshActiveFiltersBar(); applyFilters(); }

    private void clearChips(HBox row) {
        row.getChildren().forEach(n -> n.getStyleClass().remove("lxChipActive"));
    }

    /* ══════════════════════════════════════════════
       BUILD CHIPS
    ══════════════════════════════════════════════ */
    private void buildVilleChips(List<String> villes) {
        villesChips.getChildren().clear(); villeMap.clear();
        for (int i = 0; i < villes.size(); i++) {
            String v = villes.get(i);
            Button b = chip(v);
            b.setOnAction(e -> { selVille = v; markChip(villesChips, b); refreshActiveFiltersBar(); applyFilters(); });
            villesChips.getChildren().add(b);
            villeMap.put(norm(v), b);
            fadeInChip(b, i * 25);
        }
    }

    private void buildCatChips(List<String> cats) {
        categoriesChips.getChildren().clear(); catMap.clear();
        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            long cnt = all.stream().filter(l -> c.equalsIgnoreCase(safe(l.getCategorie()))).count();
            Button b = chip(c + (cnt > 0 ? "  " + cnt : ""));
            b.setOnAction(e -> { selCat = c; markChip(categoriesChips, b); refreshActiveFiltersBar(); applyFilters(); });
            categoriesChips.getChildren().add(b);
            catMap.put(norm(c), b);
            fadeInChip(b, i * 25);
        }
    }

    private void buildBudgetChips() {
        if (budgetChips == null) return;
        budgetChips.getChildren().clear();
        String[][] t = {
            {"gratuit","🆓 Gratuit"},
            {"low",    "💚 < 20 TND"},
            {"mid",    "🟡 20–80 TND"},
            {"high",   "🔴 > 80 TND"}
        };
        for (int i = 0; i < t.length; i++) {
            String key = t[i][0], lbl = t[i][1];
            Button b = chip(lbl);
            b.setOnAction(e -> { selBudget = key; markChip(budgetChips, b); refreshActiveFiltersBar(); applyFilters(); });
            budgetChips.getChildren().add(b);
            fadeInChip(b, i * 30);
        }
    }

    private Button chip(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("lxChip");
        b.setOpacity(0);
        return b;
    }

    private void fadeInChip(Node n, int delayMs) {
        PauseTransition p = new PauseTransition(Duration.millis(delayMs));
        p.setOnFinished(e -> {
            FadeTransition ft = new FadeTransition(Duration.millis(190), n);
            ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT); ft.play();
        });
        p.play();
    }

    private void markChip(HBox row, Button b) {
        clearChips(row);
        b.getStyleClass().add("lxChipActive");
        ScaleTransition s = new ScaleTransition(Duration.millis(85), b);
        s.setToX(1.07); s.setToY(1.07);
        ScaleTransition bk = new ScaleTransition(Duration.millis(105), b);
        bk.setToX(1.0); bk.setToY(1.0); bk.setInterpolator(Interpolator.EASE_OUT);
        s.setOnFinished(ev -> bk.play()); s.play();
    }

    /* ══════════════════════════════════════════════
       ACTIVE FILTERS BAR (chips inline dans toolbar)
    ══════════════════════════════════════════════ */
    private void refreshActiveFiltersBar() {
        if (activeFiltersBar == null) return;
        activeFiltersBar.getChildren().clear();

        if (selVille != null)    addActiveChip("📍 " + selVille,  () -> { selVille  = null; clearChips(villesChips);     refreshActiveFiltersBar(); applyFilters(); });
        if (selCat != null)      addActiveChip("⚙ "  + selCat,    () -> { selCat    = null; clearChips(categoriesChips); refreshActiveFiltersBar(); applyFilters(); });
        if (selBudget != null)   addActiveChip("💰 " + budgetLabel(selBudget), () -> { selBudget = null; if(budgetChips!=null) clearChips(budgetChips); refreshActiveFiltersBar(); applyFilters(); });
    }

    private void addActiveChip(String text, Runnable onRemove) {
        Button chip = new Button(text + "  ✕");
        chip.getStyleClass().add("lxActiveFilterChip");
        chip.setOpacity(0);
        chip.setOnAction(e -> { e.consume(); onRemove.run(); });
        activeFiltersBar.getChildren().add(chip);
        // Fade in
        FadeTransition ft = new FadeTransition(Duration.millis(160), chip);
        ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT); ft.play();
    }

    private String budgetLabel(String key) {
        return switch (key) {
            case "gratuit" -> "Gratuit";
            case "low"     -> "< 20 TND";
            case "mid"     -> "20–80 TND";
            case "high"    -> "> 80 TND";
            default -> key;
        };
    }

    /* ══════════════════════════════════════════════
       FILTER + RENDER
    ══════════════════════════════════════════════ */
    private void applyFilters() {
        String q = safe(searchField == null ? "" : searchField.getText()).trim().toLowerCase(Locale.ROOT);
        List<Lieu> out = new ArrayList<>();
        for (Lieu l : all) {
            if (selVille  != null && !selVille.equalsIgnoreCase(safe(l.getVille())))   continue;
            if (selCat    != null && !selCat.equalsIgnoreCase(safe(l.getCategorie()))) continue;
            if (selBudget != null) {
                double bud = budgetMoyen(l);
                boolean ok = switch (selBudget) {
                    case "gratuit" -> bud == 0;
                    case "low"     -> bud > 0 && bud < 20;
                    case "mid"     -> bud >= 20 && bud <= 80;
                    case "high"    -> bud > 80;
                    default -> true;
                };
                if (!ok) continue;
            }
            if (!q.isEmpty()) {
                String blob = (safe(l.getNom())+" "+safe(l.getVille())+" "+safe(l.getCategorie())).toLowerCase(Locale.ROOT);
                if (!blob.contains(q)) continue;
            }
            out.add(l);
        }
        renderCards(out);
    }

    private double budgetMoyen(Lieu l) {
        Double mn = l.getBudgetMin(), mx = l.getBudgetMax();
        if (mn == null && mx == null) return 0;
        if (mn != null && mx != null) return (mn + mx) / 2.0;
        return mn != null ? mn : mx;
    }

    /* ══════════════════════════════════════════════
       RENDER CARDS (grille)
    ══════════════════════════════════════════════ */
    private void renderCards(List<Lieu> lieux) {
        if (countLabel != null) countLabel.setText(lieux.size() + " lieu(x)");
        if (lieux.isEmpty()) { showState("Aucun lieu trouvé."); return; }
        hideState();

        FadeTransition out = new FadeTransition(Duration.millis(90), cardsPane);
        out.setToValue(0);
        out.setOnFinished(e -> {
            cardsPane.getChildren().clear();
            cardMap.clear();
            cardsPane.setOpacity(1);

            // Largeur de card adaptée selon split
            double cardW = splitOpen ? 240 : 290;

            for (int i = 0; i < lieux.size(); i++) {
                VBox card = buildCard(lieux.get(i), cardW);
                card.setOpacity(0);
                card.setTranslateY(16);
                cardsPane.getChildren().add(card);
                int d = i * 40;
                PauseTransition pause = new PauseTransition(Duration.millis(d));
                pause.setOnFinished(ev -> {
                    FadeTransition ft = new FadeTransition(Duration.millis(240), card);
                    ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT);
                    TranslateTransition tt = new TranslateTransition(Duration.millis(240), card);
                    tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(ft, tt).play();
                });
                pause.play();
            }
        });
        out.play();
    }

    /* ══════════════════════════════════════════════
       BUILD CARD (style image 3)
    ══════════════════════════════════════════════ */
    private VBox buildCard(Lieu l, double cardW) {
        VBox card = new VBox(0);
        card.getStyleClass().add("lxCard");
        card.setPrefWidth(cardW);
        card.setMaxWidth(cardW);

        // ── Image
        StackPane imgWrap = new StackPane();
        ImageView iv = new ImageView();
        iv.setFitWidth(cardW);
        iv.setFitHeight(splitOpen ? 140 : 175);
        iv.setPreserveRatio(false);
        Rectangle clip = new Rectangle(cardW, splitOpen ? 140 : 175);
        clip.setArcWidth(18); clip.setArcHeight(18);
        iv.setClip(clip);
        iv.setImage(loadImg(l.getImageUrl()));

        // Badge catégorie sur l'image
        String cat = safe(l.getCategorie());
        if (!cat.isBlank()) {
            Label catBadge = new Label(cat);
            catBadge.setStyle(
                "-fx-background-color: rgba(11,37,80,0.72);" +
                "-fx-background-radius: 999;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 9.5px;" +
                "-fx-font-weight: 900;" +
                "-fx-padding: 3 10;"
            );
            StackPane.setAlignment(catBadge, Pos.TOP_LEFT);
            StackPane.setMargin(catBadge, new Insets(9));
            imgWrap.getChildren().addAll(iv, catBadge);
        } else {
            imgWrap.getChildren().add(iv);
        }

        // Favori
        if (currentUser != null) {
            boolean[] fav = { favoriService.isFavori(currentUser.getId(), l.getId()) };
            Button heart = new Button(fav[0] ? "♥" : "♡");
            heart.getStyleClass().add("lxHeartBtn");
            heartStyle(heart, fav[0]);
            heart.setOnAction(e -> {
                e.consume();
                fav[0] = favoriService.toggle(currentUser.getId(), l.getId());
                heart.setText(fav[0] ? "♥" : "♡"); heartStyle(heart, fav[0]);
                ScaleTransition pulse = new ScaleTransition(Duration.millis(80), heart);
                pulse.setToX(1.28); pulse.setToY(1.28);
                ScaleTransition bk = new ScaleTransition(Duration.millis(120), heart);
                bk.setToX(1.0); bk.setToY(1.0); bk.setInterpolator(Interpolator.EASE_OUT);
                pulse.setOnFinished(ev -> bk.play()); pulse.play();
            });
            StackPane.setAlignment(heart, Pos.TOP_RIGHT);
            StackPane.setMargin(heart, new Insets(9));
            imgWrap.getChildren().add(heart);
        }

        // ── Corps
        VBox body = new VBox(5);
        body.setPadding(new Insets(12, 14, 14, 14));

        Label nomLbl = new Label(safe(l.getNom()));
        nomLbl.getStyleClass().add("lxCardTitle");

        // Ligne ville + budget
        HBox metaRow = new HBox(7); metaRow.setAlignment(Pos.CENTER_LEFT);
        Label villeLbl = new Label("📍 " + safe(l.getVille()));
        villeLbl.getStyleClass().add("lxCardMeta");
        metaRow.getChildren().add(villeLbl);

        String budStr = fmtBudget(l.getBudgetMin(), l.getBudgetMax());
        if (!budStr.isEmpty()) {
            Label budBadge = new Label(budStr);
            budBadge.getStyleClass().add("lxCardBudgetBadge");
            metaRow.getChildren().add(budBadge);
        }

        // Adresse
        Label addrLbl = new Label(safe(l.getAdresse()));
        addrLbl.getStyleClass().add("lxCardAddr");
        addrLbl.setMaxWidth(cardW - 28);

        // Note
        Label noteLbl = new Label();
        try {
            double avg = evalService.avgNote(l.getId());
            int cnt = evalService.countByLieuId(l.getId());
            if (cnt > 0) {
                noteLbl.setText(String.format("★ %.1f  (%d avis)", avg, cnt));
                noteLbl.setStyle("-fx-text-fill: #d4af37; -fx-font-size: 10.5px; -fx-font-weight: 900;");
            }
        } catch (Exception ignored) {}

        // Actions
        HBox actionsRow = new HBox(7);
        actionsRow.setPadding(new Insets(6, 0, 0, 0));
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button detBtn = new Button("Voir détails");
        detBtn.getStyleClass().add("lxCardPrimaryBtn");
        detBtn.setOnAction(e -> { e.consume(); openFullscreen(l); });

        Button mapsBtn = new Button("🗺 Maps");
        mapsBtn.getStyleClass().add("lxCardGhostBtn");
        mapsBtn.setOnAction(e -> { e.consume(); openMaps(l); });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        actionsRow.getChildren().addAll(detBtn, mapsBtn);

        body.getChildren().addAll(nomLbl, metaRow);
        if (!safe(l.getAdresse()).isBlank()) body.getChildren().add(addrLbl);
        if (noteLbl.getText() != null && !noteLbl.getText().isEmpty()) body.getChildren().add(noteLbl);
        body.getChildren().add(actionsRow);

        card.getChildren().addAll(imgWrap, body);
        cardMap.put(l.getId(), card);

        // ── Hover animation
        DropShadow shadowHover = new DropShadow(26, 0, 8, Color.rgb(11, 37, 80, 0.18));
        DropShadow shadowNorm  = new DropShadow(16, 0, 5, Color.rgb(11, 37, 80, 0.10));
        card.setEffect(shadowNorm);

        card.setOnMouseEntered(ev -> {
            if (selLieu == null || selLieu.getId() != l.getId()) {
                Timeline tl = new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(card.translateYProperty(), -5, Interpolator.EASE_OUT)));
                tl.play();
                card.setEffect(shadowHover);
            }
        });
        card.setOnMouseExited(ev -> {
            if (selLieu == null || selLieu.getId() != l.getId()) {
                Timeline tl = new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT)));
                tl.play();
                card.setEffect(shadowNorm);
            }
        });

        // ── Clic → split
        card.setOnMouseClicked(e -> selectLieu(l, card));

        return card;
    }

    /* ══════════════════════════════════════════════
       SPLIT VIEW
    ══════════════════════════════════════════════ */
    private void selectLieu(Lieu l, VBox card) {
        // Désélectionner ancien
        if (selLieu != null && selLieu.getId() != l.getId()) {
            VBox prev = cardMap.get(selLieu.getId());
            if (prev != null) {
                prev.getStyleClass().remove("lxCardSelected");
                prev.setTranslateY(0);
            }
        }

        // Reclique même → fermer
        if (selLieu != null && selLieu.getId() == l.getId() && splitOpen) {
            closePanelPublic();
            return;
        }

        selLieu = l;
        card.getStyleClass().remove("lxCardSelected");
        card.getStyleClass().add("lxCardSelected");

        if (rightPanelTitle != null) rightPanelTitle.setText(safe(l.getNom()));

        if (!splitOpen) openPanel(l);
        else            loadDetail(l);
    }

    /* ── Ouvre panel droit ─────────────────────── */
    private void openPanel(Lieu l) {
        if (splitAnim != null) splitAnim.stop();
        splitOpen = true;

        double totalW = rootHBox.getWidth();
        if (totalW <= 0) totalW = 1100;
        double targetLeft  = totalW * LEFT_RATIO;
        double targetRight = totalW * RIGHT_RATIO;

        rightPanel.setPrefWidth(0); rightPanel.setMaxWidth(0);
        rightPanel.setOpacity(0);
        rightPanel.setManaged(true); rightPanel.setVisible(true);

        // Charger le contenu
        PauseTransition ld = new PauseTransition(Duration.millis(70));
        ld.setOnFinished(e -> loadDetail(l)); ld.play();

        splitAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(leftPanel.prefWidthProperty(),  totalW,      Interpolator.EASE_BOTH),
                new KeyValue(leftPanel.maxWidthProperty(),   totalW,      Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.prefWidthProperty(), 0,           Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.maxWidthProperty(),  0,           Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.opacityProperty(),   0,           Interpolator.EASE_OUT)
            ),
            new KeyFrame(Duration.millis(340),
                new KeyValue(leftPanel.prefWidthProperty(),  targetLeft,  Interpolator.EASE_BOTH),
                new KeyValue(leftPanel.maxWidthProperty(),   targetLeft,  Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.prefWidthProperty(), targetRight, Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.maxWidthProperty(),  targetRight, Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.opacityProperty(),   1.0,         Interpolator.EASE_OUT)
            )
        );
        // Re-render cards en taille réduite après l'ouverture du split
        splitAnim.setOnFinished(e -> applyFilters());
        splitAnim.play();
    }

    /* ── Ferme panel droit (bouton ✕ dans header) ── */
    @FXML
    public void closePanel() { closePanelInternal(); }
    public void closePanelPublic() { closePanelInternal(); }

    private void closePanelInternal() {
        if (splitAnim != null) splitAnim.stop();
        splitOpen = false;

        // Désélectionner
        if (selLieu != null) {
            VBox card = cardMap.get(selLieu.getId());
            if (card != null) { card.getStyleClass().remove("lxCardSelected"); card.setTranslateY(0); }
            selLieu = null;
        }

        double totalW = rootHBox.getWidth();
        if (totalW <= 0) totalW = 1100;
        double fromL = leftPanel.getPrefWidth();
        double fromR = rightPanel.getPrefWidth();

        splitAnim = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(leftPanel.prefWidthProperty(),  fromL,  Interpolator.EASE_BOTH),
                new KeyValue(leftPanel.maxWidthProperty(),   fromL,  Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.prefWidthProperty(), fromR,  Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.maxWidthProperty(),  fromR,  Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.opacityProperty(),   1.0,    Interpolator.EASE_IN)
            ),
            new KeyFrame(Duration.millis(300),
                new KeyValue(leftPanel.prefWidthProperty(),  totalW, Interpolator.EASE_BOTH),
                new KeyValue(leftPanel.maxWidthProperty(),   totalW, Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.prefWidthProperty(), 0,      Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.maxWidthProperty(),  0,      Interpolator.EASE_BOTH),
                new KeyValue(rightPanel.opacityProperty(),   0,      Interpolator.EASE_IN)
            )
        );
        splitAnim.setOnFinished(e -> {
            rightPanel.setVisible(false); rightPanel.setManaged(false);
            rightPanel.setPrefWidth(0);   rightPanel.setMaxWidth(0);
            leftPanel.setMaxWidth(Double.MAX_VALUE);
            detailContainer.getChildren().clear();
            // Re-render cards en taille normale
            applyFilters();
        });
        splitAnim.play();
    }

    /* ── Ouvre fullscreen depuis bouton panel droit ── */
    @FXML
    public void openCurrentFullscreen() {
        if (selLieu != null) openFullscreen(selLieu);
    }

    /* ── Charge le détail inline ─────────────────── */
    private void loadDetail(Lieu l) {
        try {
            URL url = getClass().getResource(ViewPaths.FRONT_LIEU_DETAILS);
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Node view = loader.load();

            LieuDetailsController ctrl = loader.getController();
            ctrl.setNavigator(navigator);
            ctrl.setCurrentUser(currentUser);
            ctrl.setOnBackCallback(() -> Platform.runLater(this::closePanelInternal));
            ctrl.setLieuId(l.getId());

            detailContainer.getChildren().setAll(view);
            view.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), view);
            ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT); ft.play();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /* ══════════════════════════════════════════════
       NAVIGATION
    ══════════════════════════════════════════════ */
    private void openFullscreen(Lieu l) {
        if (navigator != null) navigator.navigate("lieu-details:" + l.getId());
    }

    private void openMaps(Lieu l) {
        try {
            String url = (l.getLatitude() != null && l.getLongitude() != null)
                ? "https://www.google.com/maps/search/?api=1&query=" + l.getLatitude() + "," + l.getLongitude()
                : "https://www.google.com/maps/search/?api=1&query=" +
                  URLEncoder.encode(safe(l.getNom()) + " " + safe(l.getVille()), StandardCharsets.UTF_8);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    /* ══════════════════════════════════════════════
       HELPERS
    ══════════════════════════════════════════════ */
    private void heartStyle(Button h, boolean a) {
        h.setStyle("-fx-background-color:" + (a ? "rgba(225,29,72,0.88)" : "rgba(255,255,255,0.88)") + ";" +
                   "-fx-text-fill:" + (a ? "white" : "#e11d48") + ";" +
                   "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.22),6,0,0,2);-fx-cursor:hand;");
    }

    private String fmtBudget(Double mn, Double mx) {
        if (mn == null && mx == null) return "";
        if (mn != null && mn == 0 && (mx == null || mx == 0)) return "🆓 Gratuit";
        if (mn != null && mx != null) return String.format("%.0f–%.0f TND", mn, mx);
        if (mn != null) return String.format("≥%.0f TND", mn);
        return String.format("≤%.0f TND", mx);
    }

    private Image loadImg(String raw) {
        String p = safe(raw).trim();
        try {
            if (!p.isEmpty()) {
                if (p.startsWith("http://") || p.startsWith("https://") || p.startsWith("file:"))
                    return new Image(p, true);
                if (p.startsWith("/")) {
                    URL u = getClass().getResource(p);
                    if (u != null) return new Image(u.toExternalForm(), true);
                }
            }
        } catch (Exception ignored) {}
        URL fb = getClass().getResource("/images/demo/hero/hero.jpg");
        return fb == null ? null : new Image(fb.toExternalForm(), true);
    }

    private void showState(String msg) {
        if (stateLabel == null) return;
        stateLabel.setText(msg); stateLabel.setVisible(true); stateLabel.setManaged(true);
    }
    private void hideState() {
        if (stateLabel == null) return;
        stateLabel.setVisible(false); stateLabel.setManaged(false);
    }
    private String safe(String s) { return s == null ? "" : s; }
    private String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
}
