package controllers.front.evenements;

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
import models.evenements.Evenement;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import services.evenements.WeatherService;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Contrôleur de la page liste des événements (front user).
 *
 * Adapté aux vrais modèles :
 *  - Evenement : titre, description, dateDebut, dateFin, capaciteMax (int),
 *                statut (OUVERT/FERME/ANNULE), type (PRIVE/PUBLIC), prix (double), imageUrl
 *  - InscriptionService : existsForUser(), addInscription(), delete(), countByEvent(), getByEventId()
 */
public class EvenementsController {

    // ====== FXML ======
    @FXML private HBox typesChips;
    @FXML private HBox statutsChips;
    @FXML private TextField searchField;
    @FXML private FlowPane cardsPane;
    @FXML private Label countLabel;
    @FXML private Label inscritLabel;
    @FXML private Label stateLabel;

    // ====== SERVICES ======
    private final EvenementService evenementService     = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final WeatherService weatherService         = new WeatherService();

    // ====== ÉTAT ======
    private ShellNavigator navigator;
    private User currentUser;

    private List<Evenement> all = new ArrayList<>();
    private String selectedType   = null;  // PRIVE / PUBLIC
    private String selectedStatut = null;  // OUVERT / FERME / ANNULE

    /** Mode d'affichage : "all" | "inscrits" */
    private String viewMode = "all";

    private static final DateTimeFormatter FMT_SHORT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH);

    // ====== INJECTION ======

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refreshStats();
    }

    // ====== INIT ======

    @FXML
    private void initialize() {
        buildTypeChips();
        buildStatutChips();
        loadData();
        wireSearch();
    }

    private void loadData() {
        try {
            all = evenementService.getAll();
            applyFilters();
        } catch (Exception e) {
            showState("Erreur chargement événements : " + safe(e.getMessage()));
        }
    }

    private void wireSearch() {
        if (searchField == null) return;
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
    }

    // ====== ACTIONS FXML ======

    @FXML
    public void resetFilters() {
        selectedType   = null;
        selectedStatut = null;
        viewMode       = "all";
        if (searchField != null) searchField.setText("");
        clearChipSelection(typesChips);
        clearChipSelection(statutsChips);
        applyFilters();
    }

    @FXML
    public void selectAllTypes() {
        selectedType = null;
        clearChipSelection(typesChips);
        applyFilters();
    }

    @FXML
    public void selectAllStatuts() {
        selectedStatut = null;
        clearChipSelection(statutsChips);
        applyFilters();
    }

    @FXML
    public void showAll() {
        viewMode = "all";
        applyFilters();
    }

    @FXML
    public void showMesInscriptions() {
        if (currentUser == null) {
            showState("Connecte-toi pour voir tes inscriptions.");
            return;
        }
        viewMode = "inscrits";
        applyFilters();
    }

    // ====== CHIPS ======

    private void buildTypeChips() {
        if (typesChips == null) return;
        typesChips.getChildren().clear();
        String[][] types = {{"PUBLIC", "🌐 Public"}, {"PRIVE", "🔒 Privé"}};
        for (String[] t : types) {
            final String val = t[0];
            Button b = chipButton(t[1]);
            b.setOnAction(e -> { selectedType = val; markSelected(typesChips, b); applyFilters(); });
            typesChips.getChildren().add(b);
        }
    }

    private void buildStatutChips() {
        if (statutsChips == null) return;
        statutsChips.getChildren().clear();
        // Statuts réels du modèle : OUVERT / FERME / ANNULE
        String[][] statuts = {{"OUVERT","✅ Ouvert"}, {"FERME","🔒 Fermé"}, {"ANNULE","❌ Annulé"}};
        for (String[] s : statuts) {
            final String val = s[0];
            Button b = chipButton(s[1]);
            b.setOnAction(e -> { selectedStatut = val; markSelected(statutsChips, b); applyFilters(); });
            statutsChips.getChildren().add(b);
        }
    }

    private Button chipButton(String label) {
        Button b = new Button(label);
        b.getStyleClass().add("evChip");
        return b;
    }

    private void markSelected(HBox row, Button sel) {
        row.getChildren().forEach(n -> n.getStyleClass().remove("evChipSelected"));
        sel.getStyleClass().add("evChipSelected");
    }

    private void clearChipSelection(HBox row) {
        if (row != null) row.getChildren().forEach(n -> n.getStyleClass().remove("evChipSelected"));
    }

    // ====== FILTRAGE ======

    private void applyFilters() {
        String q = safe(searchField == null ? "" : searchField.getText()).trim().toLowerCase(Locale.ROOT);

        List<Evenement> filtered = new ArrayList<>();
        for (Evenement ev : all) {
            // Mode mes inscriptions
            if ("inscrits".equals(viewMode) && currentUser != null) {
                try {
                    if (!inscriptionService.existsForUser(ev.getId(), currentUser.getId())) continue;
                } catch (Exception ignored) { continue; }
            }

            if (selectedType != null && !selectedType.equalsIgnoreCase(safe(ev.getType()))) continue;
            if (selectedStatut != null && !selectedStatut.equalsIgnoreCase(safe(ev.getStatut()))) continue;

            if (!q.isEmpty()) {
                String blob = (safe(ev.getTitre()) + " " + safe(ev.getDescription())).toLowerCase(Locale.ROOT);
                if (!blob.contains(q)) continue;
            }

            filtered.add(ev);
        }

        renderCards(filtered);
        refreshStats();
    }

    // ====== RENDU CARDS ======

    private void renderCards(List<Evenement> liste) {
        cardsPane.getChildren().clear();
        if (countLabel != null) countLabel.setText(liste.size() + " événement(s)");
        if (liste.isEmpty()) { showState("Aucun événement trouvé avec ces filtres."); return; }
        hideState();
        for (Evenement ev : liste) cardsPane.getChildren().add(buildCard(ev));
    }

    private VBox buildCard(Evenement ev) {
        VBox card = new VBox(10);
        card.getStyleClass().add("evCard");
        card.setPrefWidth(320);

        // ── IMAGE ──
        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("evCardImageWrap");

        ImageView iv = new ImageView();
        iv.setFitWidth(320);
        iv.setFitHeight(170);
        iv.setPreserveRatio(false);

        Rectangle clip = new Rectangle(320, 170);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        iv.setClip(clip);
        iv.setImage(loadImageOrFallback(ev.getImageUrl()));

        // Badge statut (coin haut-droit)
        Label statutLabel = new Label(formatStatut(ev.getStatut()));
        statutLabel.getStyleClass().addAll("evCardBadge", getStatutClass(ev.getStatut()));
        StackPane.setAlignment(statutLabel, javafx.geometry.Pos.TOP_RIGHT);
        StackPane.setMargin(statutLabel, new javafx.geometry.Insets(10, 10, 0, 0));

        // Badge type (coin haut-gauche)
        Label typeLabel = new Label("PRIVE".equalsIgnoreCase(ev.getType()) ? "🔒" : "🌐");
        typeLabel.getStyleClass().add("evCardTypeIcon");
        StackPane.setAlignment(typeLabel, javafx.geometry.Pos.TOP_LEFT);
        StackPane.setMargin(typeLabel, new javafx.geometry.Insets(10, 0, 0, 10));

        imgWrap.getChildren().addAll(iv, statutLabel, typeLabel);

        // ── TITRE ──
        Label titre = new Label(safe(ev.getTitre()));
        titre.getStyleClass().add("evCardTitle");
        titre.setWrapText(true);
        titre.setPadding(new javafx.geometry.Insets(4, 14, 0, 14));

        // ── TYPE ──
        Label typeTxt = new Label(formatType(ev.getType()));
        typeTxt.getStyleClass().add("evCardMeta");
        typeTxt.setPadding(new javafx.geometry.Insets(0, 14, 0, 14));

        // ── DATES ──
        Label dates = new Label("📅 " + formatDateRange(ev));
        dates.getStyleClass().add("evCardDate");
        dates.setPadding(new javafx.geometry.Insets(0, 14, 0, 14));

        // ── PRIX ──
        Label prixLabel = new Label(ev.getPrix() <= 0
                ? "💰 Gratuit"
                : "💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND");
        prixLabel.getStyleClass().add("evCardMeta");
        prixLabel.setPadding(new javafx.geometry.Insets(0, 14, 0, 14));

        // ── PLACES ──
        int inscrits = 0;
        try { inscrits = inscriptionService.countByEvent(ev.getId()); } catch (Exception ignored) {}
        int restantes = Math.max(0, ev.getCapaciteMax() - inscrits);
        Label places = new Label("👥 " + inscrits + "/" + ev.getCapaciteMax()
                + "  ·  " + restantes + " place(s) restante(s)");
        places.getStyleClass().add("evCardPlaces");
        places.setPadding(new javafx.geometry.Insets(0, 14, 0, 14));

        // ── WEATHER ADVICE PILL ──
        Label weatherAdvice = new Label("⏳ Chargement…");
        weatherAdvice.getStyleClass().add("evWeatherAdvicePill");
        weatherAdvice.setPadding(new javafx.geometry.Insets(0, 14, 0, 14));

        // Charger la météo en arrière-plan
        if (ev.getDateDebut() != null) {
            new Thread(() -> {
                try {
                    WeatherService.WeatherResult wr = weatherService.getWeather(
                            36.8065, 10.1815, ev.getDateDebut(),
                            "PUBLIC".equalsIgnoreCase(ev.getType()));
                    if (wr != null) {
                        javafx.application.Platform.runLater(() -> {
                            if (wr.attendancePercent >= 75) {
                                weatherAdvice.setText(wr.icon + "  Météo idéale");
                                weatherAdvice.getStyleClass().add("evAdviceGood");
                            } else if (wr.attendancePercent >= 50) {
                                weatherAdvice.setText(wr.icon + "  Météo incertaine");
                                weatherAdvice.getStyleClass().add("evAdviceCaution");
                            } else {
                                weatherAdvice.setText(wr.icon + "  Météo défavorable");
                                weatherAdvice.getStyleClass().add("evAdviceBad");
                            }
                        });
                    } else {
                        javafx.application.Platform.runLater(() -> {
                            weatherAdvice.setText("⛅  Météo indisponible");
                            weatherAdvice.getStyleClass().add("evAdviceNeutral");
                        });
                    }
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        weatherAdvice.setText("⛅  Météo indisponible");
                        weatherAdvice.getStyleClass().add("evAdviceNeutral");
                    });
                }
            }).start();
        } else {
            weatherAdvice.setText("⛅  Date inconnue");
            weatherAdvice.getStyleClass().add("evAdviceNeutral");
        }

        // ── BOUTONS ──
        HBox actions = new HBox(10);
        actions.setPadding(new javafx.geometry.Insets(0, 14, 4, 14));

        Button details = new Button("Voir détails");
        details.getStyleClass().add("evPrimaryBtn");
        details.setOnAction(e -> openDetailsPage(ev));

        // Inscription rapide
        boolean inscrit = false;
        if (currentUser != null) {
            try { inscrit = inscriptionService.existsForUser(ev.getId(), currentUser.getId()); }
            catch (Exception ignored) {}
        }
        final boolean[] inscritRef = {inscrit};
        final int[] inscritsRef    = {inscrits};

        Button inscBtn = new Button(inscrit ? "✓ Inscrit" : "S'inscrire");
        inscBtn.getStyleClass().add(inscrit ? "evInscritBtn" : "evGhostBtn");

        boolean complet = restantes <= 0;
        boolean annule  = "ANNULE".equalsIgnoreCase(ev.getStatut());
        boolean ferme   = "FERME".equalsIgnoreCase(ev.getStatut());
        if ((complet || annule || ferme) && !inscrit) inscBtn.setDisable(true);

        inscBtn.setOnAction(e -> handleToggleInscription(ev, inscBtn, inscritRef, places, inscritsRef));

        actions.getChildren().addAll(details, inscBtn);
        card.getChildren().addAll(imgWrap, titre, typeTxt, dates, prixLabel, places, weatherAdvice, actions);
        card.setOnMouseClicked(e -> { if (e.getClickCount() >= 2) openDetailsPage(ev); });
        return card;
    }

    // ====== INSCRIPTION RAPIDE ======

    private void handleToggleInscription(Evenement ev, Button btn, boolean[] inscritRef,
                                         Label placesLabel, int[] inscritsRef) {
        if (currentUser == null) { showState("Connecte-toi pour t'inscrire."); return; }

        try {
            if (inscritRef[0]) {
                // Désinscrire : trouver l'inscription de cet user
                inscriptionService.getByEventId(ev.getId()).stream()
                        .filter(i -> i.getUserId() == currentUser.getId())
                        .findFirst()
                        .ifPresent(i -> {
                            inscriptionService.delete(i.getId());
                            inscritRef[0] = false;
                            inscritsRef[0]--;
                            btn.setText("S'inscrire");
                            btn.getStyleClass().remove("evInscritBtn");
                            btn.getStyleClass().add("evGhostBtn");
                            updatePlacesLabel(placesLabel, inscritsRef[0], ev.getCapaciteMax());
                        });
            } else {
                // Inscrire
                int used = inscriptionService.countByEvent(ev.getId());
                if (used >= ev.getCapaciteMax()) { showState("Événement complet !"); return; }
                inscriptionService.addInscription(ev.getId(), currentUser.getId(), (float) ev.getPrix());
                inscritRef[0] = true;
                inscritsRef[0]++;
                btn.setText("✓ Inscrit");
                btn.getStyleClass().remove("evGhostBtn");
                btn.getStyleClass().add("evInscritBtn");
                updatePlacesLabel(placesLabel, inscritsRef[0], ev.getCapaciteMax());
            }
            refreshStats();
        } catch (Exception ex) {
            showState("Erreur : " + safe(ex.getMessage()));
        }
    }

    private void updatePlacesLabel(Label lbl, int inscrits, int max) {
        if (lbl == null) return;
        int restantes = Math.max(0, max - inscrits);
        lbl.setText("👥 " + inscrits + "/" + max + "  ·  " + restantes + " place(s) restante(s)");
    }

    // ====== NAVIGATION ======

    private void openDetailsPage(Evenement ev) {
        if (navigator == null) return;
        navigator.navigate("evenement-details:" + ev.getId());
    }

    // ====== STATS ======

    private void refreshStats() {
        if (inscritLabel == null || currentUser == null) {
            if (inscritLabel != null) inscritLabel.setText("—");
            return;
        }
        try {
            long count = all.stream().filter(ev -> {
                try { return inscriptionService.existsForUser(ev.getId(), currentUser.getId()); }
                catch (Exception e) { return false; }
            }).count();
            inscritLabel.setText(count + " inscription(s)");
        } catch (Exception ignored) {
            inscritLabel.setText("—");
        }
    }

    // ====== UTILITAIRES ======

    private String getStatutClass(String statut) {
        if (statut == null) return "evBadgeOuvert";
        return switch (statut.toUpperCase()) {
            case "FERME"  -> "evBadgeFerme";
            case "ANNULE" -> "evBadgeAnnule";
            default       -> "evBadgeOuvert";
        };
    }

    private String formatStatut(String s) {
        if (s == null) return "";
        return switch (s.toUpperCase()) {
            case "OUVERT" -> "OUVERT";
            case "FERME"  -> "FERMÉ";
            case "ANNULE" -> "ANNULÉ";
            default       -> s;
        };
    }

    private String formatType(String s) {
        if (s == null) return "";
        return switch (s.toUpperCase()) {
            case "PUBLIC" -> "🌐 Public";
            case "PRIVE"  -> "🔒 Privé";
            default       -> s;
        };
    }

    private String formatDateRange(Evenement ev) {
        try {
            String debut = ev.getDateDebut() != null ? ev.getDateDebut().format(FMT_SHORT) : "?";
            String fin   = ev.getDateFin()   != null ? ev.getDateFin().format(FMT_SHORT)   : "?";
            return debut.equals(fin) ? debut : debut + " → " + fin;
        } catch (Exception e) { return "—"; }
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
}