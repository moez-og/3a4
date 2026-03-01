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
import models.evenements.Inscription;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import services.evenements.PaiementService;
import services.evenements.RecommendationService;
import services.evenements.WeatherService;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

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

    // ====== STATS BAR BUTTONS ======
    @FXML private Button btnAll;
    @FXML private Button btnInscrits;
    @FXML private Button btnRecommandes;

    // ====== CALENDAR FXML ======
    @FXML private VBox cardsSection;
    @FXML private VBox calendarSection;
    @FXML private VBox calendarContainer;
    @FXML private Label calMonthLabel;
    @FXML private Button btnToggleCalendar;
    @FXML private Button btnFrontViewMonth, btnFrontViewWeek, btnFrontViewDay;
    @FXML private VBox calFrontDetailPanel;

    // ====== SERVICES ======
    private final EvenementService evenementService     = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final PaiementService paiementService       = new PaiementService();
    private final RecommendationService recommendationService = new RecommendationService();
    private final WeatherService weatherService         = new WeatherService();

    // ====== ÉTAT ======
    private ShellNavigator navigator;
    private User currentUser;

    private List<Evenement> all = new ArrayList<>();
    private String selectedType   = null;  // PRIVE / PUBLIC
    private String selectedStatut = null;  // OUVERT / FERME / ANNULE

    /** Mode d'affichage : "all" | "inscrits" | "recommandes" */
    private String viewMode = "all";

    /** Liste des événements recommandés (cache local) */
    private List<Evenement> recommendedEvents = new ArrayList<>();
    private boolean recommendationsLoading = false;

    private static final DateTimeFormatter FMT_SHORT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH);

    private YearMonth calCurrentMonth = YearMonth.now();
    private String calFrontViewMode = "month";
    private static final Locale LOCALE_FR = Locale.FRENCH;

    // Calendar color palette (blue coherent palette)
    private static final String[][] CAL_COLORS = {
        {"#E8F0FE", "#1a4a7a"},  // blue (primary)
        {"#E8F0FE", "#1a73e8"},  // blue
        {"#E6F4EA", "#137333"},  // green
        {"#FFF8E1", "#E37400"},  // yellow
        {"#FFE2EC", "#C2185B"},  // rose
        {"#FFF0E1", "#C2410C"},  // orange
    };

    // ====== INJECTION ======

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refreshStats();
        applyFilters();
    }

    /**
     * Recharge les données depuis la BDD et re-rend les cartes.
     * Appelé quand on revient sur la page événements pour refléter
     * les changements (ex : paiement effectué).
     */
    public void refreshCards() {
        loadData();
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
        updateModeButtons();
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
        updateModeButtons();
        applyFilters();
    }

    @FXML
    public void showMesInscriptions() {
        if (currentUser == null) {
            showState("Connecte-toi pour voir tes inscriptions.");
            return;
        }
        viewMode = "inscrits";
        updateModeButtons();
        applyFilters();
    }

    @FXML
    public void showRecommandes() {
        if (currentUser == null) {
            showState("Connecte-toi pour voir les recommandations.");
            return;
        }
        viewMode = "recommandes";
        updateModeButtons();
        loadRecommendations();
    }

    private void loadRecommendations() {
        if (recommendationsLoading) return;
        recommendationsLoading = true;
        showState("🤖 Analyse de vos intérêts en cours...");

        new Thread(() -> {
            try {
                List<Evenement> recs = recommendationService.getRecommendations(currentUser.getId());
                String analysis = recommendationService.getLastInterestsAnalysis();
                javafx.application.Platform.runLater(() -> {
                    recommendedEvents = recs;
                    recommendationsLoading = false;
                    if (recs.isEmpty()) {
                        showState("Aucune recommandation disponible. Inscrivez-vous à des événements pour améliorer les suggestions !");
                        renderCards(Collections.emptyList());
                    } else {
                        hideState();
                        renderRecommendedCards(recs, analysis);
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    recommendationsLoading = false;
                    showState("Erreur lors du chargement des recommandations: " + safe(e.getMessage()));
                });
            }
        }).start();
    }

    private void renderRecommendedCards(List<Evenement> recs, String analysis) {
        cardsPane.getChildren().clear();
        if (countLabel != null) countLabel.setText(recs.size() + " recommandation(s)");

        // Bandeau d'analyse IA
        VBox aiBanner = new VBox(6);
        aiBanner.getStyleClass().add("evRecoBanner");
        aiBanner.setPadding(new Insets(14, 18, 14, 18));
        aiBanner.setMaxWidth(Double.MAX_VALUE);

        HBox bannerHeader = new HBox(8);
        bannerHeader.setAlignment(Pos.CENTER_LEFT);
        Label aiIcon = new Label("🤖");
        aiIcon.setStyle("-fx-font-size: 22px;");
        Label aiTitle = new Label(recommendationService.isApiKeyConfigured()
                ? "Recommandations IA (Gemini)" : "Recommandations intelligentes");
        aiTitle.getStyleClass().add("evRecoTitle");
        bannerHeader.getChildren().addAll(aiIcon, aiTitle);

        Label aiDesc = new Label(analysis != null && !analysis.isEmpty()
                ? analysis : "Basé sur votre historique d'inscriptions");
        aiDesc.getStyleClass().add("evRecoDesc");
        aiDesc.setWrapText(true);

        aiBanner.getChildren().addAll(bannerHeader, aiDesc);
        cardsPane.getChildren().add(aiBanner);

        // Cartes recommandées avec badge de pertinence
        for (int i = 0; i < recs.size(); i++) {
            Evenement ev = recs.get(i);
            VBox card = buildCard(ev);
            // Ajouter un badge de ranking
            Label rankBadge = new Label("#" + (i + 1) + " Recommandé");
            rankBadge.getStyleClass().add("evRecoBadge");
            if (i == 0) rankBadge.getStyleClass().add("evRecoBadgeTop");
            // Insérer le badge au début de la carte (après l'image)
            if (card.getChildren().size() > 1) {
                card.getChildren().add(1, rankBadge);
            } else {
                card.getChildren().add(rankBadge);
            }
            cardsPane.getChildren().add(card);
        }
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

    // ====== MODE BUTTON TOGGLING ======

    /**
     * Met à jour les boutons de la barre de stats pour refléter le mode actif.
     * Le bouton actif reçoit la classe evStatsBtnActive, les autres l'ont retirée.
     */
    private void updateModeButtons() {
        if (btnAll == null) return;
        Button[] modeBtns = {btnAll, btnInscrits, btnRecommandes};
        for (Button b : modeBtns) {
            b.getStyleClass().remove("evStatsBtnActive");
        }
        switch (viewMode) {
            case "all"         -> btnAll.getStyleClass().add("evStatsBtnActive");
            case "inscrits"    -> btnInscrits.getStyleClass().add("evStatsBtnActive");
            case "recommandes" -> btnRecommandes.getStyleClass().add("evStatsBtnActive");
        }
    }

    // ====== FILTRAGE ======

    private void applyFilters() {
        String q = safe(searchField == null ? "" : searchField.getText()).trim().toLowerCase(Locale.ROOT);

        // Mode recommandations — pas de filtrage, affichage direct
        if ("recommandes".equals(viewMode) && !recommendedEvents.isEmpty()) {
            renderRecommendedCards(recommendedEvents, recommendationService.getLastInterestsAnalysis());
            refreshStats();
            return;
        }

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

        // ── BADGE ÉTAT INSCRIPTION ──
        Inscription userInscription = null;
        if (currentUser != null) {
            try {
                userInscription = inscriptionService.getByEventId(ev.getId()).stream()
                        .filter(i -> i.getUserId() == currentUser.getId())
                        .findFirst().orElse(null);
            } catch (Exception e) {
                System.err.println("Erreur chargement inscription pour event " + ev.getId() + ": " + e.getMessage());
            }
        }

        Label etatLabel = new Label();
        applyInscriptionState(etatLabel, userInscription, ev);
        etatLabel.setPadding(new javafx.geometry.Insets(0, 14, 8, 14));

        card.getChildren().addAll(imgWrap, titre, typeTxt, dates, prixLabel, places, weatherAdvice, etatLabel);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> openDetailsPage(ev));
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

    // ====== ÉTAT D'INSCRIPTION SUR LA CARTE ======

    /**
     * Applique le texte et le style du bouton selon l'état réel de l'inscription.
     *
     *  - null (pas d'inscription)        → "S'inscrire"       (evGhostBtn)
     *  - EN_ATTENTE                       → "⏳ En attente"    (evAttenteBtn)
     *  - CONFIRMEE + NON_PAYE            → "💳 À payer"       (evAPayerBtn)
     *  - CONFIRMEE + PAYE                → "✅ Payé"          (evPayeTag)
     *  - ANNULEE                          → "❌ Annulé"        (evAnnuleTag)
     */
    private void applyInscriptionState(Label lbl, Inscription insc, Evenement ev) {
        lbl.getStyleClass().removeAll("evNonInscritTag", "evInscritTag", "evAttenteTag",
                "evAPayerTag", "evPayeTag", "evAnnuleTag", "evCompletTag");

        if (insc == null) {
            boolean complet = false;
            try { complet = inscriptionService.countByEvent(ev.getId()) >= ev.getCapaciteMax(); } catch (Exception ignored) {}
            boolean annule = "ANNULE".equalsIgnoreCase(ev.getStatut());
            boolean ferme  = "FERME".equalsIgnoreCase(ev.getStatut());

            if (complet) {
                lbl.setText("🚫 Complet");
                lbl.getStyleClass().add("evCompletTag");
            } else if (annule) {
                lbl.setText("❌ Annulé");
                lbl.getStyleClass().add("evAnnuleTag");
            } else if (ferme) {
                lbl.setText("🔒 Fermé");
                lbl.getStyleClass().add("evCompletTag");
            } else {
                lbl.setText("Non inscrit");
                lbl.getStyleClass().add("evNonInscritTag");
            }
            return;
        }

        String statut = safe(insc.getStatut()).toUpperCase();
        String paiement = safe(insc.getPaiement()).toUpperCase();

        boolean paid = false;
        try { paid = paiementService.isPaid(insc.getId()); } catch (Exception ignored) {}
        if (paid) paiement = "PAYE";

        switch (statut) {
            case "EN_ATTENTE" -> {
                lbl.setText("⏳ En attente");
                lbl.getStyleClass().add("evAttenteTag");
            }
            case "CONFIRMEE" -> {
                if ("PAYE".equals(paiement)) {
                    lbl.setText("✅ Payé");
                    lbl.getStyleClass().add("evPayeTag");
                } else {
                    lbl.setText("💳 En attente de paiement");
                    lbl.getStyleClass().add("evAPayerTag");
                }
            }
            case "ANNULEE" -> {
                lbl.setText("❌ Annulé");
                lbl.getStyleClass().add("evAnnuleTag");
            }
            default -> {
                lbl.setText("✓ Inscrit");
                lbl.getStyleClass().add("evInscritTag");
            }
        }
    }

    // ====== NAVIGATION ======

    private void openDetailsPage(Evenement ev) {
        if (navigator == null) return;
        navigator.navigate("evenement-details:" + ev.getId());
    }

    // ═══════════════════════════════════════════════════════════
    //  CALENDRIER — Vue mensuelle des événements
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void onToggleCalendar() {
        calCurrentMonth = YearMonth.now();
        calFrontViewMode = "month";
        updateFrontViewToggle();
        renderCalendar();
        cardsSection.setVisible(false);
        cardsSection.setManaged(false);
        calendarSection.setVisible(true);
        calendarSection.setManaged(true);
        btnToggleCalendar.setText("🃏 Cartes");
        btnToggleCalendar.setOnAction(e -> onRetourFromCalendar());
    }

    @FXML
    public void onRetourFromCalendar() {
        calendarSection.setVisible(false);
        calendarSection.setManaged(false);
        cardsSection.setVisible(true);
        cardsSection.setManaged(true);
        btnToggleCalendar.setText("📅 Calendrier");
        btnToggleCalendar.setOnAction(e -> onToggleCalendar());
        hideFrontDetail();
    }

    @FXML public void onViewMonth() { calFrontViewMode = "month"; updateFrontViewToggle(); renderCalendar(); }
    @FXML public void onViewWeek()  { calFrontViewMode = "week";  updateFrontViewToggle(); renderCalendar(); }
    @FXML public void onViewDay()   { calFrontViewMode = "day";   updateFrontViewToggle(); renderCalendar(); }

    private void updateFrontViewToggle() {
        if (btnFrontViewMonth == null) return;
        btnFrontViewMonth.getStyleClass().remove("evCalTogActive");
        btnFrontViewWeek.getStyleClass().remove("evCalTogActive");
        btnFrontViewDay.getStyleClass().remove("evCalTogActive");
        switch (calFrontViewMode) {
            case "month": btnFrontViewMonth.getStyleClass().add("evCalTogActive"); break;
            case "week":  btnFrontViewWeek.getStyleClass().add("evCalTogActive"); break;
            case "day":   btnFrontViewDay.getStyleClass().add("evCalTogActive"); break;
        }
    }

    @FXML
    public void onPrevMonth() {
        calCurrentMonth = calCurrentMonth.minusMonths(1);
        renderCalendar();
    }

    @FXML
    public void onNextMonth() {
        calCurrentMonth = calCurrentMonth.plusMonths(1);
        renderCalendar();
    }

    @FXML
    public void onCalendarToday() {
        calCurrentMonth = YearMonth.now();
        renderCalendar();
    }

    private void renderCalendar() {
        if (calendarContainer == null) return;
        calendarContainer.getChildren().clear();

        // Update month label
        String monthName = calCurrentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE_FR);
        monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
        calMonthLabel.setText(monthName + " " + calCurrentMonth.getYear());

        // Assign stable color to each event by id
        Map<Integer, Integer> eventColorMap = new HashMap<>();
        int colorIdx = 0;
        for (Evenement ev : all) {
            if (!eventColorMap.containsKey(ev.getId())) {
                eventColorMap.put(ev.getId(), colorIdx % CAL_COLORS.length);
                colorIdx++;
            }
        }

        switch (calFrontViewMode) {
            case "week":  renderFrontWeekView(eventColorMap); break;
            case "day":   renderFrontDayView(eventColorMap); break;
            default:      renderFrontMonthView(eventColorMap); break;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MONTH VIEW — Modern Grid
    // ═══════════════════════════════════════════════════════════

    private void renderFrontMonthView(Map<Integer, Integer> eventColorMap) {
        GridPane header = new GridPane();
        header.getStyleClass().add("evCalMHeader");
        String[] dayAbbr = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            header.getColumnConstraints().add(cc);
            Label lbl = new Label(dayAbbr[i]);
            lbl.getStyleClass().add(i == 6 ? "evCalDayHdrSun" : "evCalDayHdr");
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            header.add(lbl, i, 0);
        }
        calendarContainer.getChildren().add(header);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("evCalMGrid");
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        LocalDate firstOfMonth = calCurrentMonth.atDay(1);
        int offset = firstOfMonth.getDayOfWeek().getValue() - 1;
        int daysInMonth = calCurrentMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        LocalDateTime monthStart = calCurrentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = calCurrentMonth.atEndOfMonth().atTime(23, 59, 59);
        List<Evenement> monthEvents = all.stream()
                .filter(e -> {
                    if (e.getDateDebut() == null) return false;
                    LocalDateTime d = e.getDateDebut();
                    LocalDateTime fin = e.getDateFin() != null ? e.getDateFin() : d;
                    return !(fin.isBefore(monthStart) || d.isAfter(monthEnd));
                })
                .collect(Collectors.toList());

        int totalCells = offset + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int cellIdx = row * 7 + col;
                int dayNum = cellIdx - offset + 1;

                VBox cell = new VBox(2);
                cell.getStyleClass().add("evCalMCell");
                cell.setMinHeight(100);
                cell.setPrefHeight(110);

                boolean isCurrent = dayNum >= 1 && dayNum <= daysInMonth;
                LocalDate cellDate;
                int displayDay;

                if (dayNum < 1) {
                    YearMonth prev = calCurrentMonth.minusMonths(1);
                    displayDay = prev.lengthOfMonth() + dayNum;
                    cellDate = prev.atDay(displayDay);
                    cell.getStyleClass().add("evCalMCellOutside");
                } else if (dayNum > daysInMonth) {
                    displayDay = dayNum - daysInMonth;
                    cellDate = calCurrentMonth.plusMonths(1).atDay(displayDay);
                    cell.getStyleClass().add("evCalMCellOutside");
                } else {
                    displayDay = dayNum;
                    cellDate = calCurrentMonth.atDay(dayNum);
                }

                if (col == 6) cell.getStyleClass().add("evCalMCellSun");

                HBox dayRow = new HBox();
                dayRow.setAlignment(Pos.CENTER_LEFT);
                dayRow.setPadding(new Insets(6, 8, 2, 8));

                Label numLabel = new Label(String.valueOf(displayDay));
                if (isCurrent && cellDate.equals(today)) {
                    numLabel.getStyleClass().add("evCalMToday");
                    cell.getStyleClass().add("evCalMCellToday");
                } else {
                    numLabel.getStyleClass().add(isCurrent ? "evCalMDayNum" : "evCalMDayMuted");
                }
                dayRow.getChildren().add(numLabel);
                cell.getChildren().add(dayRow);

                if (isCurrent) {
                    final LocalDate fDate = cellDate;
                    List<Evenement> dayEvs = monthEvents.stream()
                            .filter(e -> {
                                LocalDate s = e.getDateDebut().toLocalDate();
                                LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                                return !fDate.isBefore(s) && !fDate.isAfter(en);
                            })
                            .collect(Collectors.toList());

                    VBox evBox = new VBox(2);
                    evBox.setPadding(new Insets(0, 4, 4, 4));
                    int maxShow = 2;

                    for (int idx = 0; idx < Math.min(dayEvs.size(), maxShow); idx++) {
                        Evenement ev = dayEvs.get(idx);
                        int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                        HBox chip = buildFrontChip(ev, ci);
                        evBox.getChildren().add(chip);
                    }

                    if (dayEvs.size() > maxShow) {
                        Label more = new Label("+" + (dayEvs.size() - maxShow) + " autres");
                        more.getStyleClass().add("evCalMore");
                        final List<Evenement> allDayEvs = dayEvs;
                        more.setOnMouseClicked(me -> {
                            me.consume();
                            showDayEventsPopup(fDate, allDayEvs);
                        });
                        evBox.getChildren().add(more);
                    }

                    cell.getChildren().add(evBox);
                    VBox.setVgrow(evBox, Priority.ALWAYS);
                }

                grid.add(cell, col, row);
            }
        }
        calendarContainer.getChildren().add(grid);
    }

    private HBox buildFrontChip(Evenement ev, int colorIndex) {
        String bgColor = CAL_COLORS[colorIndex % CAL_COLORS.length][0];
        String textColor = CAL_COLORS[colorIndex % CAL_COLORS.length][1];

        HBox chip = new HBox(4);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chip, Priority.ALWAYS);
        chip.setStyle("-fx-background-color: " + bgColor + ";"
                + "-fx-background-radius: 6; -fx-padding: 3 6; -fx-cursor: hand;");

        String timeStr = ev.getDateDebut() != null
                ? ev.getDateDebut().format(DateTimeFormatter.ofPattern("HH:mm")) : "";
        Label timeLbl = new Label(timeStr);
        timeLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");

        Label titleLbl = new Label(truncate(safe(ev.getTitre()), 10));
        titleLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: " + textColor + ";");
        HBox.setHgrow(titleLbl, Priority.ALWAYS);

        chip.getChildren().addAll(timeLbl, titleLbl);

        Tooltip tp = new Tooltip("📌 " + safe(ev.getTitre())
                + "\n📅 " + formatDateRange(ev)
                + "\n" + formatType(ev.getType()) + "  •  " + formatStatut(ev.getStatut())
                + (ev.getPrix() > 0 ? "\n💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "\n💰 Gratuit"));
        tp.setShowDelay(Duration.millis(150));
        tp.setStyle("-fx-font-size: 12px; -fx-font-family: 'Segoe UI'; -fx-background-radius: 8;");
        Tooltip.install(chip, tp);

        chip.setOnMouseClicked(me -> {
            me.consume();
            showFrontEventDetail(ev, colorIndex);
        });
        return chip;
    }

    // ═══════════════════════════════════════════════════════════
    //  WEEK VIEW
    // ═══════════════════════════════════════════════════════════

    private void renderFrontWeekView(Map<Integer, Integer> eventColorMap) {
        LocalDate ref = calCurrentMonth.atDay(
                Math.min(LocalDate.now().getDayOfMonth(), calCurrentMonth.lengthOfMonth()));
        if (!ref.getMonth().equals(calCurrentMonth.getMonth())) ref = calCurrentMonth.atDay(1);
        LocalDate weekStart = ref.with(java.time.DayOfWeek.MONDAY);

        VBox weekBox = new VBox(0);
        weekBox.getStyleClass().add("evCalWContainer");

        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            final LocalDate fDay = day;
            boolean isToday = day.equals(LocalDate.now());

            VBox dayBox = new VBox(6);
            dayBox.getStyleClass().add("evCalWDay");
            if (isToday) dayBox.getStyleClass().add("evCalWDayToday");
            dayBox.setPadding(new Insets(12, 16, 12, 16));

            HBox dayHeader = new HBox(10);
            dayHeader.setAlignment(Pos.CENTER_LEFT);
            String dayName = day.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_FR);
            dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
            Label dayTitle = new Label(dayName + " " + day.getDayOfMonth());
            dayTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: "
                    + (isToday ? "#1a4a7a" : "#163a5c") + ";");
            if (isToday) {
                Label badge = new Label("Aujourd'hui");
                badge.setStyle("-fx-background-color: #1a4a7a; -fx-text-fill: white; "
                        + "-fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 8; -fx-background-radius: 999;");
                dayHeader.getChildren().addAll(dayTitle, badge);
            } else {
                dayHeader.getChildren().add(dayTitle);
            }
            dayBox.getChildren().add(dayHeader);

            List<Evenement> dayEvs = all.stream()
                    .filter(e -> {
                        if (e.getDateDebut() == null) return false;
                        LocalDate s = e.getDateDebut().toLocalDate();
                        LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                        return !fDay.isBefore(s) && !fDay.isAfter(en);
                    })
                    .collect(Collectors.toList());

            if (dayEvs.isEmpty()) {
                Label noEv = new Label("Aucun événement");
                noEv.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic;");
                dayBox.getChildren().add(noEv);
            } else {
                for (Evenement ev : dayEvs) {
                    int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                    String bgColor = CAL_COLORS[ci % CAL_COLORS.length][0];
                    String textColor = CAL_COLORS[ci % CAL_COLORS.length][1];

                    HBox card = new HBox(12);
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.setStyle("-fx-background-color: " + bgColor
                            + "; -fx-background-radius: 10; -fx-padding: 10 14; -fx-cursor: hand;");
                    card.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(card, Priority.ALWAYS);

                    Region colorBar = new Region();
                    colorBar.setMinWidth(4); colorBar.setPrefWidth(4); colorBar.setMinHeight(32);
                    colorBar.setStyle("-fx-background-color: " + textColor + "; -fx-background-radius: 2;");

                    VBox info = new VBox(2);
                    HBox.setHgrow(info, Priority.ALWAYS);
                    Label title = new Label(safe(ev.getTitre()));
                    title.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");
                    Label time = new Label("🕐 " + formatDateRange(ev));
                    time.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.8;");
                    info.getChildren().addAll(title, time);

                    Label status = new Label(formatStatut(safe(ev.getStatut())));
                    status.setStyle("-fx-background-color: rgba(255,255,255,0.6); -fx-background-radius: 999; "
                            + "-fx-padding: 2 8; -fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");

                    card.getChildren().addAll(colorBar, info, status);
                    card.setOnMouseClicked(me -> { me.consume(); showFrontEventDetail(ev, ci); });
                    dayBox.getChildren().add(card);
                }
            }

            weekBox.getChildren().add(dayBox);
        }
        calendarContainer.getChildren().add(weekBox);
    }

    // ═══════════════════════════════════════════════════════════
    //  DAY VIEW
    // ═══════════════════════════════════════════════════════════

    private void renderFrontDayView(Map<Integer, Integer> eventColorMap) {
        LocalDate day = calCurrentMonth.atDay(
                Math.min(LocalDate.now().getDayOfMonth(), calCurrentMonth.lengthOfMonth()));
        if (!day.getMonth().equals(calCurrentMonth.getMonth())) day = calCurrentMonth.atDay(1);
        final LocalDate fDay = day;

        VBox container = new VBox(0);
        container.getStyleClass().add("evCalDContainer");
        container.setPadding(new Insets(16));

        String dayName = day.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE_FR);
        dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
        Label dayTitle = new Label(dayName + " " + day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", LOCALE_FR)));
        dayTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #163a5c; -fx-padding: 0 0 16 0;");
        container.getChildren().add(dayTitle);

        List<Evenement> dayEvs = all.stream()
                .filter(e -> {
                    if (e.getDateDebut() == null) return false;
                    LocalDate s = e.getDateDebut().toLocalDate();
                    LocalDate en = e.getDateFin() != null ? e.getDateFin().toLocalDate() : s;
                    return !fDay.isBefore(s) && !fDay.isAfter(en);
                })
                .sorted((a, b) -> {
                    if (a.getDateDebut() == null || b.getDateDebut() == null) return 0;
                    return a.getDateDebut().compareTo(b.getDateDebut());
                })
                .collect(Collectors.toList());

        if (dayEvs.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(60));
            Label emptyIcon = new Label("📅");
            emptyIcon.setStyle("-fx-font-size: 48px;");
            Label emptyText = new Label("Aucun événement ce jour");
            emptyText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 16px; -fx-font-weight: 700;");
            empty.getChildren().addAll(emptyIcon, emptyText);
            container.getChildren().add(empty);
        } else {
            for (int i = 0; i < dayEvs.size(); i++) {
                Evenement ev = dayEvs.get(i);
                int ci = eventColorMap.getOrDefault(ev.getId(), 0);
                String bgColor = CAL_COLORS[ci % CAL_COLORS.length][0];
                String textColor = CAL_COLORS[ci % CAL_COLORS.length][1];

                HBox timeline = new HBox(14);
                timeline.setAlignment(Pos.TOP_LEFT);
                timeline.setPadding(new Insets(0, 0, 12, 0));

                // Time column
                VBox timeCol = new VBox(2);
                timeCol.setMinWidth(60);
                timeCol.setAlignment(Pos.TOP_RIGHT);
                String startTime = ev.getDateDebut() != null ? ev.getDateDebut().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";
                String endTime = ev.getDateFin() != null ? ev.getDateFin().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";
                Label tStart = new Label(startTime);
                tStart.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #163a5c;");
                Label tEnd = new Label(endTime);
                tEnd.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
                timeCol.getChildren().addAll(tStart, tEnd);

                // Dot
                VBox dotCol = new VBox();
                dotCol.setAlignment(Pos.TOP_CENTER);
                dotCol.setMinWidth(18);
                Region dot = new Region();
                dot.setMinSize(12, 12);
                dot.setMaxSize(12, 12);
                dot.setStyle("-fx-background-color: " + textColor + "; -fx-background-radius: 999;");
                Region line = new Region();
                line.setMinWidth(2); line.setPrefWidth(2); line.setMinHeight(36);
                line.setStyle("-fx-background-color: " + (i < dayEvs.size() - 1 ? "rgba(0,0,0,0.06)" : "transparent") + ";");
                VBox.setVgrow(line, Priority.ALWAYS);
                dotCol.getChildren().addAll(dot, line);

                // Card
                VBox card = new VBox(4);
                HBox.setHgrow(card, Priority.ALWAYS);
                card.setStyle("-fx-background-color: " + bgColor
                        + "; -fx-background-radius: 12; -fx-padding: 12 16; -fx-cursor: hand;");

                Label cardTitle = new Label(safe(ev.getTitre()));
                cardTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: " + textColor + ";");
                cardTitle.setWrapText(true);
                Label cardInfo = new Label(formatType(ev.getType()) + "  •  " + formatStatut(ev.getStatut())
                        + (ev.getPrix() > 0 ? "  •  💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "  •  Gratuit"));
                cardInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: " + textColor + "; -fx-opacity: 0.7;");

                card.getChildren().addAll(cardTitle, cardInfo);
                card.setOnMouseClicked(me -> { me.consume(); showFrontEventDetail(ev, ci); });

                timeline.getChildren().addAll(timeCol, dotCol, card);
                container.getChildren().add(timeline);
            }
        }
        calendarContainer.getChildren().add(container);
    }

    // ═══════════════════════════════════════════════════════════
    //  EVENT DETAIL — Side panel (front)
    // ═══════════════════════════════════════════════════════════

    private void showFrontEventDetail(Evenement ev, int colorIndex) {
        if (calFrontDetailPanel == null) return;
        calFrontDetailPanel.getChildren().clear();
        calFrontDetailPanel.setVisible(true);
        calFrontDetailPanel.setManaged(true);

        String accentColor = CAL_COLORS[colorIndex % CAL_COLORS.length][1];
        String bgColor = CAL_COLORS[colorIndex % CAL_COLORS.length][0];

        // Close
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        Button btnClose = new Button("✕");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 4 8;");
        btnClose.setOnAction(e -> hideFrontDetail());
        topRow.getChildren().add(btnClose);
        calFrontDetailPanel.getChildren().add(topRow);

        // Header
        VBox headerBox = new VBox(6);
        headerBox.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12; -fx-padding: 14;");
        Label evTitle = new Label(safe(ev.getTitre()));
        evTitle.setWrapText(true);
        evTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: " + accentColor + ";");
        Label statusChip = new Label(formatStatut(safe(ev.getStatut())));
        statusChip.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-padding: 3 10; "
                + "-fx-background-radius: 999; -fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + accentColor + ";");
        headerBox.getChildren().addAll(evTitle, statusChip);
        calFrontDetailPanel.getChildren().add(headerBox);

        Region sep = new Region();
        sep.setMinHeight(10);
        calFrontDetailPanel.getChildren().add(sep);

        // Details
        addFrontDetailRow(calFrontDetailPanel, "📅 Date", formatDateRange(ev));
        addFrontDetailRow(calFrontDetailPanel, "🎭 Type", formatType(safe(ev.getType())));
        addFrontDetailRow(calFrontDetailPanel, "💰 Prix", ev.getPrix() > 0
                ? String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "Gratuit");
        addFrontDetailRow(calFrontDetailPanel, "👥 Places", String.valueOf(ev.getCapaciteMax()));

        // Description
        String desc = safe(ev.getDescription());
        if (!desc.isEmpty()) {
            Region sep2 = new Region();
            sep2.setMinHeight(8);
            calFrontDetailPanel.getChildren().add(sep2);
            Label descTitle = new Label("Description");
            descTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #94a3b8;");
            Label descText = new Label(truncate(desc, 180));
            descText.setWrapText(true);
            descText.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
            calFrontDetailPanel.getChildren().addAll(descTitle, descText);
        }

        // Action button
        Region sep3 = new Region();
        sep3.setMinHeight(14);
        calFrontDetailPanel.getChildren().add(sep3);

        Button btnDetails = new Button("Voir détails →");
        btnDetails.setStyle("-fx-background-color: " + accentColor
                + "; -fx-text-fill: white; -fx-font-weight: 800; -fx-font-size: 12px; "
                + "-fx-padding: 8 16; -fx-background-radius: 8; -fx-cursor: hand;");
        btnDetails.setMaxWidth(Double.MAX_VALUE);
        btnDetails.setOnAction(e -> {
            hideFrontDetail();
            openDetailsPage(ev);
        });
        calFrontDetailPanel.getChildren().add(btnDetails);
    }

    private void addFrontDetailRow(VBox container, String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 0, 3, 0));
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #94a3b8; -fx-min-width: 70;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #163a5c;");
        val.setWrapText(true);
        HBox.setHgrow(val, Priority.ALWAYS);
        row.getChildren().addAll(lbl, val);
        container.getChildren().add(row);
    }

    private void hideFrontDetail() {
        if (calFrontDetailPanel != null) {
            calFrontDetailPanel.setVisible(false);
            calFrontDetailPanel.setManaged(false);
            calFrontDetailPanel.getChildren().clear();
        }
    }

    /** Popup showing all events for a specific day */
    private void showDayEventsPopup(LocalDate date, List<Evenement> events) {
        javafx.stage.Stage popup = new javafx.stage.Stage();
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setTitle("Événements du " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        VBox root = new VBox(12);
        root.setPadding(new Insets(20, 24, 16, 24));
        root.setStyle("-fx-background-color:white;-fx-background-radius:14;");

        Label title = new Label("📅 " + date.format(DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy", LOCALE_FR)));
        title.setStyle("-fx-font-size:18px;-fx-font-weight:900;-fx-text-fill:#163a5c;");

        Label countLbl = new Label(events.size() + " événement(s)");
        countLbl.setStyle("-fx-text-fill:rgba(22,58,92,0.60);-fx-font-weight:700;");

        VBox eventsList = new VBox(10);
        for (Evenement ev : events) {
            int ci = eventColorMap(ev);
            String bgColor = CAL_COLORS[ci][0];
            String textColor = CAL_COLORS[ci][1];

            VBox card = new VBox(6);
            card.setStyle("-fx-background-color:" + bgColor + ";-fx-background-radius:12;-fx-padding:12 14;");

            Label evTitle = new Label(safe(ev.getTitre()));
            evTitle.setStyle("-fx-font-weight:900;-fx-font-size:14px;-fx-text-fill:" + textColor + ";");
            evTitle.setWrapText(true);

            Label evDate = new Label("📅 " + formatDateRange(ev));
            evDate.setStyle("-fx-text-fill:" + textColor + ";-fx-font-weight:700;-fx-font-size:12px;-fx-opacity:0.8;");

            Label evInfo = new Label(formatType(ev.getType()) + "  •  " + formatStatut(ev.getStatut())
                    + (ev.getPrix() > 0 ? "  •  💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "  •  Gratuit"));
            evInfo.setStyle("-fx-text-fill:" + textColor + ";-fx-font-weight:700;-fx-font-size:11.5px;-fx-opacity:0.7;");
            evInfo.setWrapText(true);

            Button btnOpen = new Button("Voir détails →");
            btnOpen.setStyle("-fx-background-color:" + textColor
                    + ";-fx-text-fill:white;-fx-font-weight:900;-fx-background-radius:10;-fx-padding:8 14;-fx-cursor:hand;");
            btnOpen.setOnAction(e -> { popup.close(); openDetailsPage(ev); });

            card.getChildren().addAll(evTitle, evDate, evInfo, btnOpen);
            eventsList.getChildren().add(card);
        }

        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(eventsList);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        sp.setMaxHeight(350);
        sp.setStyle("-fx-background-color:transparent;");

        Button btnClose = new Button("Fermer");
        btnClose.setStyle("-fx-background-color:rgba(15,23,42,0.06);-fx-background-radius:12;"
                + "-fx-text-fill:#163a5c;-fx-font-weight:900;-fx-padding:8 16;-fx-cursor:hand;");
        btnClose.setOnAction(e -> popup.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, countLbl, sp, footer);

        javafx.scene.Scene sc = new javafx.scene.Scene(root, 480, 420);
        popup.setScene(sc);
        popup.setResizable(true);
        popup.centerOnScreen();
        popup.showAndWait();
    }

    private int eventColorMap(Evenement ev) {
        int idx = 0;
        for (Evenement e : all) {
            if (e.getId() == ev.getId()) return idx % CAL_COLORS.length;
            idx++;
        }
        return 0;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
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
        if (!path.isEmpty()) {
            // 1) URL distante (http/https) ou URI file:
            try {
                if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
                    return new Image(path, true);
                }
            } catch (Exception ignored) {}

            // 2) Chemin de fichier local (ex: C:\Users\...\image.jpg)
            try {
                java.io.File f = new java.io.File(path);
                if (f.exists()) return new Image(f.toURI().toString(), true);
            } catch (Exception ignored) {}

            // 3) Ressource classpath (ex: /images/evenements/xxx.jpg)
            try {
                String resPath = path.startsWith("/") ? path : "/" + path;
                URL u = getClass().getResource(resPath);
                if (u != null) return new Image(u.toExternalForm(), true);
            } catch (Exception ignored) {}
        }
        // Fallback
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