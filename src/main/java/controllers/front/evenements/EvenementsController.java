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

    // ====== CALENDAR FXML ======
    @FXML private VBox cardsSection;
    @FXML private VBox calendarSection;
    @FXML private VBox calendarContainer;
    @FXML private Label calMonthLabel;
    @FXML private Button btnToggleCalendar;

    // ====== SERVICES ======
    private final EvenementService evenementService     = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final PaiementService paiementService       = new PaiementService();
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

    private YearMonth calCurrentMonth = YearMonth.now();
    private static final Locale LOCALE_FR = Locale.FRENCH;

    // ====== INJECTION ======

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refreshStats();
        applyFilters();
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

        String[] colorNames = {"blue", "red", "yellow", "green", "purple", "orange"};

        // Update month label
        String monthName = calCurrentMonth.getMonth().getDisplayName(TextStyle.FULL, LOCALE_FR);
        monthName = monthName.substring(0, 1).toUpperCase() + monthName.substring(1);
        calMonthLabel.setText(monthName + " " + calCurrentMonth.getYear());

        // ── Header row (Lun → Dim) ──
        GridPane header = new GridPane();
        header.getStyleClass().add("cal-header");
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            header.getColumnConstraints().add(cc);
        }
        String[] dayNames = {"Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"};
        for (int i = 0; i < 7; i++) {
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.getStyleClass().add(i == 6 ? "cal-day-header-sunday" : "cal-day-header");
            dayLabel.setMaxWidth(Double.MAX_VALUE);
            dayLabel.setAlignment(Pos.CENTER);
            header.add(dayLabel, i, 0);
        }
        calendarContainer.getChildren().add(header);

        // ── Build grid ──
        GridPane grid = new GridPane();
        grid.getStyleClass().add("cal-grid");
        grid.setHgap(0);
        grid.setVgap(0);
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        LocalDate firstOfMonth = calCurrentMonth.atDay(1);
        int dayOfWeekOffset = firstOfMonth.getDayOfWeek().getValue() - 1;
        int daysInMonth = calCurrentMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        // Collect events for this month
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

        // Assign stable color per event
        Map<Integer, String> eventColorMap = new HashMap<>();
        int colorIdx = 0;
        for (Evenement ev : monthEvents) {
            if (!eventColorMap.containsKey(ev.getId())) {
                eventColorMap.put(ev.getId(), colorNames[colorIdx % colorNames.length]);
                colorIdx++;
            }
        }

        int totalCells = dayOfWeekOffset + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 7; col++) {
                int cellIndex = row * 7 + col;
                int dayNumber = cellIndex - dayOfWeekOffset + 1;

                VBox cell = new VBox(1);
                cell.getStyleClass().add("cal-cell");
                cell.setMinHeight(100);
                cell.setPrefHeight(110);
                cell.setPadding(new Insets(0));
                if (col == 0) cell.getStyleClass().add("cal-cell-first-col");
                if (col == 6) cell.getStyleClass().add("cal-sunday-cell");

                boolean isCurrentMonth = dayNumber >= 1 && dayNumber <= daysInMonth;
                LocalDate cellDate;
                int displayDay;
                if (dayNumber < 1) {
                    YearMonth prevMonth = calCurrentMonth.minusMonths(1);
                    displayDay = prevMonth.lengthOfMonth() + dayNumber;
                    cellDate = prevMonth.atDay(displayDay);
                    cell.getStyleClass().add("cal-empty");
                } else if (dayNumber > daysInMonth) {
                    displayDay = dayNumber - daysInMonth;
                    cellDate = calCurrentMonth.plusMonths(1).atDay(displayDay);
                    cell.getStyleClass().add("cal-empty");
                } else {
                    displayDay = dayNumber;
                    cellDate = calCurrentMonth.atDay(dayNumber);
                }

                // ── Day number row (dots + number) ──
                HBox dayRow = new HBox();
                dayRow.setAlignment(Pos.CENTER_RIGHT);
                dayRow.setPadding(new Insets(4, 0, 2, 0));

                final LocalDate fCellDate = cellDate;
                List<Evenement> dayEvents = monthEvents.stream()
                        .filter(e -> {
                            LocalDate evStart = e.getDateDebut().toLocalDate();
                            LocalDate evEnd = e.getDateFin() != null ? e.getDateFin().toLocalDate() : evStart;
                            return !fCellDate.isBefore(evStart) && !fCellDate.isAfter(evEnd);
                        })
                        .collect(Collectors.toList());

                // Colored dots
                HBox dotsBox = new HBox(3);
                dotsBox.setAlignment(Pos.CENTER_LEFT);
                dotsBox.setPadding(new Insets(0, 0, 0, 6));
                for (int dIdx = 0; dIdx < Math.min(dayEvents.size(), 5); dIdx++) {
                    String evColor = eventColorMap.getOrDefault(dayEvents.get(dIdx).getId(), "blue");
                    Region dot = new Region();
                    dot.getStyleClass().addAll("cal-dot", "cal-dot-" + evColor);
                    dotsBox.getChildren().add(dot);
                }
                HBox.setHgrow(dotsBox, Priority.ALWAYS);

                if (isCurrentMonth && cellDate.equals(today)) {
                    Label todayLabel = new Label(String.valueOf(displayDay));
                    todayLabel.getStyleClass().add("cal-today-badge");
                    dayRow.getChildren().addAll(dotsBox, todayLabel);
                    cell.getStyleClass().add("cal-today-cell");
                } else {
                    Label numLabel = new Label(String.valueOf(displayDay));
                    numLabel.getStyleClass().add(isCurrentMonth ? "cal-day-number" : "cal-day-number-muted");
                    dayRow.getChildren().addAll(dotsBox, numLabel);
                }
                cell.getChildren().add(dayRow);

                // ── Event bars (current month only) ──
                if (isCurrentMonth) {
                    VBox eventsBox = new VBox(2);
                    eventsBox.setPadding(new Insets(2, 4, 4, 4));

                    int maxVisible = 2;
                    for (int idx = 0; idx < Math.min(dayEvents.size(), maxVisible); idx++) {
                        Evenement ev = dayEvents.get(idx);
                        String color = eventColorMap.getOrDefault(ev.getId(), "blue");

                        HBox bar = new HBox(4);
                        bar.getStyleClass().addAll("cal-event-bar", "cal-bar-" + color);
                        bar.setAlignment(Pos.CENTER_LEFT);
                        bar.setMaxWidth(Double.MAX_VALUE);
                        HBox.setHgrow(bar, Priority.ALWAYS);

                        Region barDot = new Region();
                        barDot.getStyleClass().addAll("cal-bar-dot", "cal-dot-" + color);
                        barDot.setMinSize(8, 8);
                        barDot.setMaxSize(8, 8);

                        Label titleLbl = new Label(truncate(safe(ev.getTitre()), 12));
                        titleLbl.setStyle("-fx-font-size:10px;-fx-font-weight:700;");
                        HBox.setHgrow(titleLbl, Priority.ALWAYS);

                        // Status indicator
                        String statutEmoji = "OUVERT".equalsIgnoreCase(ev.getStatut()) ? "●"
                                : "FERME".equalsIgnoreCase(ev.getStatut()) ? "○" : "✕";
                        Label statutLbl = new Label(statutEmoji);
                        statutLbl.setStyle("-fx-font-size:9px;-fx-text-fill:" +
                                ("OUVERT".equalsIgnoreCase(ev.getStatut()) ? "#22c55e" : "#94a3b8") + ";");

                        bar.getChildren().addAll(barDot, titleLbl, statutLbl);

                        // Tooltip
                        String tooltipText = "📌 " + safe(ev.getTitre())
                                + "\n📅 " + formatDateRange(ev)
                                + "\n" + formatType(ev.getType()) + "  •  " + formatStatut(ev.getStatut())
                                + (ev.getPrix() > 0 ? "\n💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "\n💰 Gratuit");
                        Tooltip tp = new Tooltip(tooltipText);
                        tp.setShowDelay(Duration.millis(200));
                        tp.setStyle("-fx-font-size:12px;-fx-font-family:'Segoe UI';-fx-background-radius:8;");
                        Tooltip.install(bar, tp);

                        // Click → open event details page
                        final Evenement clickEv = ev;
                        bar.setOnMouseClicked(me -> {
                            me.consume();
                            openDetailsPage(clickEv);
                        });

                        eventsBox.getChildren().add(bar);
                    }

                    if (dayEvents.size() > maxVisible) {
                        Label more = new Label("+" + (dayEvents.size() - maxVisible) + " autres");
                        more.getStyleClass().add("cal-more-label");
                        final List<Evenement> allDayEvents = dayEvents;
                        more.setOnMouseClicked(me -> {
                            me.consume();
                            showDayEventsPopup(fCellDate, allDayEvents);
                        });
                        eventsBox.getChildren().add(more);
                    }

                    cell.getChildren().add(eventsBox);
                    VBox.setVgrow(eventsBox, Priority.ALWAYS);
                }

                grid.add(cell, col, row);
            }
        }

        calendarContainer.getChildren().add(grid);
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
            VBox card = new VBox(6);
            card.setStyle("-fx-background-color:rgba(15,23,42,0.03);-fx-background-radius:12;"
                    + "-fx-border-color:rgba(15,23,42,0.06);-fx-border-radius:12;-fx-padding:12 14;");

            Label evTitle = new Label(safe(ev.getTitre()));
            evTitle.setStyle("-fx-font-weight:900;-fx-font-size:14px;-fx-text-fill:#163a5c;");
            evTitle.setWrapText(true);

            Label evDate = new Label("📅 " + formatDateRange(ev));
            evDate.setStyle("-fx-text-fill:rgba(22,58,92,0.65);-fx-font-weight:700;-fx-font-size:12px;");

            Label evInfo = new Label(formatType(ev.getType()) + "  •  " + formatStatut(ev.getStatut())
                    + (ev.getPrix() > 0 ? "  •  💰 " + String.format(Locale.FRENCH, "%.2f", ev.getPrix()) + " TND" : "  •  💰 Gratuit"));
            evInfo.setStyle("-fx-text-fill:rgba(22,58,92,0.55);-fx-font-weight:700;-fx-font-size:11.5px;");
            evInfo.setWrapText(true);

            Button btnOpen = new Button("Voir détails →");
            btnOpen.setStyle("-fx-background-color:linear-gradient(to right,#0b2550,#1a4a7a);"
                    + "-fx-text-fill:white;-fx-font-weight:900;-fx-background-radius:12;-fx-padding:8 14;-fx-cursor:hand;");
            btnOpen.setOnAction(e -> {
                popup.close();
                openDetailsPage(ev);
            });

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