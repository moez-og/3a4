package controllers.front.evenements;

import controllers.front.shell.FrontDashboardController;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import models.evenements.Evenement;
import models.evenements.Inscription;
import models.lieux.Lieu;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import services.evenements.PaiementService;
import services.evenements.WeatherService;
import services.lieux.LieuService;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Contrôleur page de détails d'un événement (front user).
 *
 * 4 états de boutons :
 *  1. Pas inscrit          → "S'inscrire" (bleu)
 *  2. EN_ATTENTE           → "En attente" (ambre, désactivé) + "Se désinscrire"
 *  3. CONFIRMEE, pas payé  → "Payer" (doré) + "Se désinscrire"
 *  4. Payé                 → "Payé ✓" (vert, désactivé)
 */
public class EvenementDetailsController {

    // ── TOP BAR ──
    @FXML private Button inscriptionBtn;

    // ── IMAGE ──
    @FXML private ImageView image;

    // ── INFOS CARD ──
    @FXML private Label titre;
    @FXML private Label statutBadge;
    @FXML private Label typeBadge;
    @FXML private Label dateDebut;
    @FXML private Label dateFin;
    @FXML private Label capaciteInfo;
    @FXML private Label inscritsCount;
    @FXML private Label prixLabel;
    @FXML private Label description;

    // ── ACTION BAR BAS ──
    @FXML private HBox actionBarBottom;
    @FXML private Button inscriptionBtnBottom;
    @FXML private Button payerBtn;
    @FXML private Button desinscrireBtn;
    @FXML private Label messageLabel;

    // ── WEATHER CARD ──
    @FXML private VBox weatherCard;
    @FXML private Label weatherIcon;
    @FXML private Label weatherDesc;
    @FXML private Label weatherTemp;
    @FXML private Label weatherWind;
    @FXML private Label weatherPrecip;
    @FXML private Label weatherAdvice;

    // ── PARTICIPANTS ──
    @FXML private Label participantsTitle;
    @FXML private Label placesRestantes;
    @FXML private Region progressBar;
    @FXML private Label tauxLabel;
    @FXML private VBox participantsList;
    @FXML private Label participantsEmpty;

    // ── SERVICES ──
    private final EvenementService evenementService     = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();
    private final PaiementService paiementService       = new PaiementService();
    private final WeatherService weatherService         = new WeatherService();
    private LieuService lieuService;
    {
        try { lieuService = new LieuService(); }
        catch (Exception e) { lieuService = null; System.err.println("LieuService init error: " + e.getMessage()); }
    }

    // ── ÉTAT ──
    private ShellNavigator navigator;
    private User currentUser;

    private int evenementId = -1;
    private Evenement current;
    private Inscription userInscription; // inscription de l'utilisateur courant (si existante)

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.FRENCH);

    // ── INJECTION ──

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refreshButtons();
    }

    public void setEvenementId(int id) {
        this.evenementId = id;
        loadEvenement();
        loadParticipants();
    }

    @FXML
    private void initialize() { /* tout se charge via setEvenementId */ }

    // ── NAVIGATION ──

    @FXML
    public void goBack() {
        if (navigator != null) navigator.navigate(FrontDashboardController.ROUTE_EVENTS);
    }

    // ── ACTIONS ──

    /**
     * Bouton principal "S'inscrire" — ouvre un dialog pour choisir le nombre de tickets.
     */
    @FXML
    public void toggleInscription() {
        if (currentUser == null) {
            showMessage("Connecte-toi pour t'inscrire à cet événement.", false);
            return;
        }
        if (current == null) return;

        String st = safe(current.getStatut()).toUpperCase();
        if ("ANNULE".equals(st) || "FERME".equals(st)) {
            showMessage("Cet événement n'accepte plus d'inscriptions.", false);
            return;
        }

        // Si déjà inscrit, on ne fait rien via ce bouton (géré par desinscrireBtn)
        if (userInscription != null) return;

        try {
            // Vérif capacité
            int used = inscriptionService.countByEvent(evenementId);
            int remaining = current.getCapaciteMax() - used;
            if (remaining <= 0) {
                showMessage("Désolé, l'événement est complet !", false);
                return;
            }

            // Dialog pour choisir le nombre de tickets
            Optional<Integer> nbTickets = showTicketDialog(remaining);
            if (nbTickets.isEmpty()) return;

            int tickets = nbTickets.get();
            float montantTotal = (float) (current.getPrix() * tickets);

            inscriptionService.addInscription(evenementId, currentUser.getId(), montantTotal, tickets);
            showMessage("Inscription envoyée ! En attente de confirmation par l'admin.", true);
            loadUserInscription();
            loadParticipants();
            refreshButtons();

        } catch (Exception e) {
            showMessage("Erreur : " + safe(e.getMessage()), false);
        }
    }

    /**
     * Bouton "Se désinscrire" — supprime l'inscription de l'utilisateur.
     */
    @FXML
    public void handleDesinscrire() {
        if (currentUser == null || userInscription == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Se désinscrire");
        confirm.setHeaderText("Se désinscrire de « " + safe(current.getTitre()) + " » ?");
        confirm.setContentText("Tu pourras te réinscrire si des places sont disponibles.");
        ButtonType oui = new ButtonType("Oui, me désinscrire", ButtonBar.ButtonData.OK_DONE);
        ButtonType non = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(oui, non);

        confirm.showAndWait().ifPresent(bt -> {
            if (bt == oui) {
                inscriptionService.delete(userInscription.getId());
                userInscription = null;
                showMessage("Tu es désinscrit de cet événement.", false);
                loadParticipants();
                refreshButtons();
            }
        });
    }

    /**
     * Bouton "Payer" — navigue vers la page de paiement.
     */
    @FXML
    public void handlePayer() {
        if (userInscription == null || navigator == null) return;
        // Route : paiement:<inscriptionId>
        navigator.navigate(FrontDashboardController.ROUTE_PAIEMENT_PREFIX + userInscription.getId());
    }

    // ── DIALOG TICKETS ──

    private Optional<Integer> showTicketDialog(int maxTickets) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Réservation de tickets");

        // ── DialogPane styling ──
        DialogPane pane = dialog.getDialogPane();
        pane.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #eef3ff, #f7f9fc);" +
            "-fx-background-radius: 18;" +
            "-fx-font-family: 'Segoe UI';"
        );
        pane.setHeaderText(null);
        pane.setGraphic(null);

        // ── Spinner 1..max ──
        int cap = Math.min(maxTickets, 10);
        Spinner<Integer> spinner = new Spinner<>(1, cap, 1);
        spinner.setEditable(false);
        spinner.setPrefWidth(130);
        spinner.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(15,23,42,0.12);" +
            "-fx-border-radius: 12;" +
            "-fx-font-size: 14;" +
            "-fx-font-weight: 900;"
        );

        // ── Header banner ──
        Label headerIcon = new Label("🎟");
        headerIcon.setStyle("-fx-font-size: 28;");

        Label headerTitle = new Label("Réservation");
        headerTitle.setStyle(
            "-fx-text-fill: white;" +
            "-fx-font-size: 20;" +
            "-fx-font-weight: 900;"
        );

        Label headerSub = new Label("Choisis le nombre de tickets");
        headerSub.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.80);" +
            "-fx-font-size: 12;" +
            "-fx-font-weight: 700;"
        );

        VBox headerText = new VBox(2, headerTitle, headerSub);
        headerText.setAlignment(Pos.CENTER_LEFT);

        HBox headerRow = new HBox(12, headerIcon, headerText);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(18, 22, 18, 22));
        headerRow.setStyle(
            "-fx-background-color: linear-gradient(to right, #0b2550, #1a4a7a);" +
            "-fx-background-radius: 14;"
        );

        // ── Event info card ──
        Label eventLabel = new Label("📌  " + safe(current.getTitre()));
        eventLabel.setStyle(
            "-fx-font-weight: 900;" +
            "-fx-font-size: 14;" +
            "-fx-text-fill: #0f2a44;"
        );
        eventLabel.setWrapText(true);

        Label prixUnit = new Label("💰  Prix unitaire : " +
                String.format(Locale.FRENCH, "%.2f", current.getPrix()) + " TND");
        prixUnit.setStyle(
            "-fx-font-size: 12.5;" +
            "-fx-text-fill: rgba(15,42,68,0.70);" +
            "-fx-font-weight: 800;"
        );

        Label placesInfo = new Label("👥  " + maxTickets + " place(s) disponible(s)");
        placesInfo.setStyle(
            "-fx-font-size: 12;" +
            "-fx-text-fill: rgba(15,42,68,0.60);" +
            "-fx-font-weight: 700;"
        );

        VBox infoCard = new VBox(8, eventLabel, prixUnit, placesInfo);
        infoCard.setPadding(new Insets(14, 18, 14, 18));
        infoCard.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 14;" +
            "-fx-border-color: rgba(15,23,42,0.08);" +
            "-fx-border-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0.1, 0, 3);"
        );

        // ── Spinner row ──
        Label ticketLabel = new Label("Nombre de tickets");
        ticketLabel.setStyle(
            "-fx-font-size: 13;" +
            "-fx-font-weight: 900;" +
            "-fx-text-fill: #163a5c;"
        );

        // ── Boutons – / + custom ──
        Button btnMinus = new Button("−");
        btnMinus.setStyle(
            "-fx-background-color: rgba(15,23,42,0.08);" +
            "-fx-background-radius: 10;" +
            "-fx-font-size: 16;" +
            "-fx-font-weight: 900;" +
            "-fx-text-fill: #163a5c;" +
            "-fx-padding: 6 14;" +
            "-fx-cursor: hand;"
        );
        btnMinus.setOnAction(e -> spinner.decrement());

        Button btnPlus = new Button("+");
        btnPlus.setStyle(
            "-fx-background-color: rgba(15,23,42,0.08);" +
            "-fx-background-radius: 10;" +
            "-fx-font-size: 16;" +
            "-fx-font-weight: 900;" +
            "-fx-text-fill: #163a5c;" +
            "-fx-padding: 6 14;" +
            "-fx-cursor: hand;"
        );
        btnPlus.setOnAction(e -> spinner.increment());

        Label spinnerValue = new Label("1");
        spinnerValue.setStyle(
            "-fx-font-size: 22;" +
            "-fx-font-weight: 900;" +
            "-fx-text-fill: #0b2550;" +
            "-fx-min-width: 50;" +
            "-fx-alignment: center;"
        );
        spinnerValue.setAlignment(Pos.CENTER);
        spinner.valueProperty().addListener((obs, old, nv) -> spinnerValue.setText(String.valueOf(nv)));

        HBox counterRow = new HBox(14, btnMinus, spinnerValue, btnPlus);
        counterRow.setAlignment(Pos.CENTER);
        counterRow.setPadding(new Insets(10, 0, 6, 0));

        // Hide the actual spinner (use it as data source only)
        spinner.setVisible(false);
        spinner.setManaged(false);

        // ── Prix dynamique ──
        Label prixDynLabel = new Label();
        updatePrixDynLabel(prixDynLabel, 1);
        spinner.valueProperty().addListener((obs, old, nv) -> updatePrixDynLabel(prixDynLabel, nv));

        // ── Separator ──
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: rgba(15,23,42,0.08);");

        // ── Assemble content ──
        VBox content = new VBox(14);
        content.setPadding(new Insets(18, 22, 14, 22));
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(
            headerRow,
            infoCard,
            ticketLabel,
            counterRow,
            separator,
            prixDynLabel
        );

        pane.setContent(content);
        pane.setPrefWidth(420);

        // ── Buttons ──
        ButtonType confirmer = new ButtonType("✓ Confirmer", ButtonBar.ButtonData.OK_DONE);
        ButtonType annuler   = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(confirmer, annuler);

        // Style buttons after they exist
        javafx.application.Platform.runLater(() -> {
            Button okBtn = (Button) pane.lookupButton(confirmer);
            if (okBtn != null) {
                okBtn.setStyle(
                    "-fx-background-color: linear-gradient(to right, #0b2550, #1a4a7a);" +
                    "-fx-background-radius: 12;" +
                    "-fx-text-fill: white;" +
                    "-fx-font-weight: 900;" +
                    "-fx-font-size: 13;" +
                    "-fx-padding: 10 22;" +
                    "-fx-cursor: hand;"
                );
            }
            Button cancelBtn = (Button) pane.lookupButton(annuler);
            if (cancelBtn != null) {
                cancelBtn.setStyle(
                    "-fx-background-color: rgba(15,23,42,0.06);" +
                    "-fx-background-radius: 12;" +
                    "-fx-text-fill: #173a57;" +
                    "-fx-font-weight: 900;" +
                    "-fx-font-size: 13;" +
                    "-fx-padding: 10 22;" +
                    "-fx-cursor: hand;"
                );
            }
        });

        dialog.setResultConverter(bt -> bt == confirmer ? spinner.getValue() : null);

        return dialog.showAndWait();
    }

    private void updatePrixDynLabel(Label label, int nbTickets) {
        double total = current.getPrix() * nbTickets;
        label.setText("💳  Total : " + String.format(Locale.FRENCH, "%.2f", total) + " TND"
                + "  (" + nbTickets + " ticket" + (nbTickets > 1 ? "s" : "") + ")");
        label.setStyle(
            "-fx-font-weight: 900;" +
            "-fx-font-size: 15;" +
            "-fx-text-fill: #d97706;" +
            "-fx-padding: 8 16;" +
            "-fx-background-color: rgba(212,175,55,0.12);" +
            "-fx-background-radius: 10;"
        );
    }

    // ── CHARGEMENT ÉVÉNEMENT ──

    private void loadEvenement() {
        if (evenementId <= 0) return;

        try {
            current = evenementService.getById(evenementId);
        } catch (Exception e) {
            showMessage("Erreur chargement événement.", false);
            return;
        }

        if (current == null) {
            if (titre != null) titre.setText("Événement introuvable");
            return;
        }

        // Image
        if (image != null) {
            image.setImage(loadImageOrFallback(current.getImageUrl()));
            Rectangle clip = new Rectangle(980, 300);
            clip.setArcWidth(22);
            clip.setArcHeight(22);
            image.setClip(clip);
        }

        // Titre
        if (titre != null) titre.setText(safe(current.getTitre()));

        // Statut badge
        if (statutBadge != null) {
            statutBadge.setText(formatStatut(current.getStatut()));
            statutBadge.getStyleClass().removeAll(
                    "evBadgeOuvert", "evBadgeFerme", "evBadgeAnnule");
            statutBadge.getStyleClass().add(getStatutClass(current.getStatut()));
        }

        // Type badge
        if (typeBadge != null) typeBadge.setText(formatType(current.getType()));

        // Dates
        if (dateDebut != null) dateDebut.setText(
                current.getDateDebut() != null ? current.getDateDebut().format(FMT) : "—");
        if (dateFin != null) dateFin.setText(
                current.getDateFin() != null ? current.getDateFin().format(FMT) : "—");

        // Capacité
        if (capaciteInfo != null)
            capaciteInfo.setText("Capacité : " + current.getCapaciteMax() + " personnes");

        // Prix
        if (prixLabel != null)
            prixLabel.setText(current.getPrix() <= 0
                    ? "Entrée gratuite"
                    : String.format(Locale.FRENCH, "%.2f", current.getPrix()) + " TND");

        // Description
        if (description != null) {
            String d = safe(current.getDescription());
            description.setText(d.isEmpty() ? "Aucune description disponible." : d);
        }

        loadUserInscription();
        refreshButtons();
        loadWeather();
    }

    // ── CHARGEMENT MÉTÉO ──

    private void loadWeather() {
        if (current == null || weatherCard == null) return;

        // Masquer par défaut
        weatherCard.setVisible(false);
        weatherCard.setManaged(false);

        // Charger en arrière-plan pour ne pas bloquer l'UI
        new Thread(() -> {
            try {
                // Récupérer le lieu pour les coordonnées GPS
                // Valeurs par défaut : Tunis
                double lat = 36.8065;
                double lon = 10.1815;

                if (current.getLieuId() != null && current.getLieuId() > 0 && lieuService != null) {
                    try {
                        Lieu lieu = lieuService.getById(current.getLieuId());
                        if (lieu != null && lieu.getLatitude() != null && lieu.getLongitude() != null
                                && lieu.getLatitude() != 0 && lieu.getLongitude() != 0) {
                            lat = lieu.getLatitude();
                            lon = lieu.getLongitude();
                        }
                    } catch (Exception ignored) { /* garde les coords par défaut */ }
                }

                // Déterminer si l'événement est en extérieur
                boolean isOutdoor = current.getType() != null &&
                        (current.getType().toLowerCase().contains("plein air")
                                || current.getType().toLowerCase().contains("outdoor")
                                || current.getType().toLowerCase().contains("extérieur")
                                || current.getType().toLowerCase().contains("ext")
                                || "PUBLIC".equalsIgnoreCase(current.getType()));

                // Appel API météo
                WeatherService.WeatherResult weather = weatherService.getWeather(
                        lat, lon,
                        current.getDateDebut(), isOutdoor);

                if (weather == null) return;

                // Mettre à jour l'UI sur le thread JavaFX
                javafx.application.Platform.runLater(() -> {
                    weatherCard.setVisible(true);
                    weatherCard.setManaged(true);

                    if (weatherIcon != null)
                        weatherIcon.setText(weather.icon);
                    if (weatherDesc != null)
                        weatherDesc.setText(weather.description);
                    if (weatherTemp != null)
                        weatherTemp.setText(String.format("%.0f°", weather.temperature));
                    if (weatherWind != null)
                        weatherWind.setText(String.format("Vent  %.0f km/h", weather.windSpeed));
                    if (weatherPrecip != null)
                        weatherPrecip.setText(weather.precipitation > 0
                                ? String.format("Pluie  %.1f mm", weather.precipitation)
                                : "Pas de pluie");

                    if (weatherAdvice != null) {
                        // Short advice text for the pill
                        String shortAdvice;
                        if (weather.attendancePercent >= 75) shortAdvice = "✓ Idéal";
                        else if (weather.attendancePercent >= 50) shortAdvice = "⚠ Mitigé";
                        else shortAdvice = "✗ Défavorable";
                        weatherAdvice.setText(shortAdvice);
                        weatherAdvice.getStyleClass().removeAll(
                                "evWeatherAdviceGood", "evWeatherAdviceCaution", "evWeatherAdviceBad");
                        if (weather.attendancePercent >= 75) {
                            weatherAdvice.getStyleClass().add("evWeatherAdviceGood");
                        } else if (weather.attendancePercent >= 50) {
                            weatherAdvice.getStyleClass().add("evWeatherAdviceCaution");
                        } else {
                            weatherAdvice.getStyleClass().add("evWeatherAdviceBad");
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Erreur chargement météo: " + e.getMessage());
            }
        }).start();
    }

    // ── CHARGEMENT INSCRIPTION UTILISATEUR ──

    private void loadUserInscription() {
        userInscription = null;
        if (currentUser == null || evenementId <= 0) return;
        try {
            inscriptionService.getByEventId(evenementId).stream()
                    .filter(i -> i.getUserId() == currentUser.getId())
                    .findFirst()
                    .ifPresent(i -> userInscription = i);
        } catch (Exception ignored) {}
    }

    // ── CHARGEMENT PARTICIPANTS ──

    private void loadParticipants() {
        if (participantsList == null || evenementId <= 0) return;

        try {
            List<Inscription> inscrits = inscriptionService.getByEventId(evenementId);
            int count = inscrits.size();

            if (participantsTitle != null)
                participantsTitle.setText("Participants / " + count);

            // Mise à jour inscritsCount
            if (inscritsCount != null && current != null)
                inscritsCount.setText(count + "/" + current.getCapaciteMax() + " inscrits");

            updateProgressBar(count);

            participantsList.getChildren().clear();

            boolean empty = inscrits.isEmpty();
            if (participantsEmpty != null) {
                participantsEmpty.setVisible(empty);
                participantsEmpty.setManaged(empty);
            }

            for (Inscription insc : inscrits) {
                participantsList.getChildren().add(buildParticipantRow(insc));
            }

        } catch (Exception e) {
            if (participantsTitle != null) participantsTitle.setText("Participants : erreur de chargement");
        }
    }

    private void updateProgressBar(int count) {
        if (current == null) return;
        int max = current.getCapaciteMax();

        int restantes = Math.max(0, max - count);
        double taux   = max > 0 ? (double) count / max : 0;

        if (placesRestantes != null) placesRestantes.setText(restantes + " place(s) restante(s)");
        if (tauxLabel != null)       tauxLabel.setText(String.format("%.0f%%", taux * 100));

        if (progressBar != null) {
            // 600px = largeur max approximative de la track
            progressBar.setPrefWidth(600 * Math.min(taux, 1.0));
            String color = taux >= 1.0 ? "#dc2626"
                    : taux >= 0.75 ? "#d97706"
                    : "#16a34a";
            progressBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999;");
        }
    }

    private HBox buildParticipantRow(Inscription insc) {
        HBox row = new HBox(12);
        row.getStyleClass().add("evParticipantRow");
        row.setPadding(new Insets(8, 10, 8, 10));

        // Avatar avec initiale du userId
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("evParticipantAvatar");
        Label initial = new Label("#" + insc.getUserId());
        initial.getStyleClass().add("evParticipantAvatarTxt");
        initial.setStyle("-fx-font-size: 10;");
        avatar.getChildren().add(initial);

        VBox info = new VBox(2);

        // Nom : pas disponible dans le modèle → on affiche "Participant #id"
        Label nomLabel = new Label("Participant #" + insc.getUserId());
        nomLabel.getStyleClass().add("evParticipantNom");

        // Date d'inscription (dateCreation)
        String dateInsc = insc.getDateCreation() != null
                ? insc.getDateCreation().toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
        Label dateLabel = new Label(dateInsc.isEmpty() ? "" : "Inscrit le " + dateInsc);
        dateLabel.getStyleClass().add("evParticipantDate");

        // Statut + tickets
        String ticketInfo = insc.getNbTickets() > 1
                ? " · " + insc.getNbTickets() + " tickets"
                : " · 1 ticket";
        Label statutPaie = new Label(
                "Statut : " + safe(insc.getStatut()) + ticketInfo);
        statutPaie.getStyleClass().add("evParticipantDate");

        info.getChildren().addAll(nomLabel, dateLabel, statutPaie);
        HBox.setHgrow(info, Priority.ALWAYS);

        row.getChildren().addAll(avatar, info);

        // Bouton retirer : uniquement si admin OU si c'est soi-même
        if (currentUser != null) {
            boolean isSelf  = currentUser.getId() == insc.getUserId();
            boolean isAdmin = "admin".equalsIgnoreCase(safe(currentUser.getRole()));
            if (isSelf || isAdmin) {
                Button del = new Button("Retirer");
                del.getStyleClass().add("evDangerBtn");
                del.setOnAction(e -> {
                    inscriptionService.delete(insc.getId());
                    if (isSelf) userInscription = null;
                    loadParticipants();
                    refreshButtons();
                });
                row.getChildren().add(del);
            }
        }

        return row;
    }

    // ── REFRESH BOUTONS (4 ÉTATS) ──

    private void refreshButtons() {
        if (current == null) return;

        loadUserInscription();

        boolean inscrit = userInscription != null;
        String statutInsc = inscrit ? safe(userInscription.getStatut()).toUpperCase() : "";
        boolean confirmed = "CONFIRMEE".equals(statutInsc);
        boolean paid = false;

        if (inscrit && confirmed) {
            try {
                paid = paiementService.isPaid(userInscription.getId());
            } catch (Exception ignored) {}
        }

        // ── Bouton principal top (inscriptionBtn) ──
        if (inscriptionBtn != null) {
            if (!inscrit) {
                // État 1 : Pas inscrit → "S'inscrire" (bleu)
                inscriptionBtn.setText("✓ S'inscrire");
                setButtonStyle(inscriptionBtn, "evPrimaryBtn");
                inscriptionBtn.setDisable(false);
                inscriptionBtn.setOnAction(e -> toggleInscription());
            } else if ("EN_ATTENTE".equals(statutInsc)) {
                // État 2 : En attente (ambre, désactivé)
                inscriptionBtn.setText("⏳ En attente de confirmation");
                setButtonStyle(inscriptionBtn, "evAmbreBtn");
                inscriptionBtn.setDisable(true);
            } else if (confirmed && !paid) {
                // État 3 : Confirmé, pas payé → "Payer" (doré)
                inscriptionBtn.setText("💳 Payer");
                setButtonStyle(inscriptionBtn, "evGoldBtn");
                inscriptionBtn.setDisable(false);
                inscriptionBtn.setOnAction(e -> handlePayer());
            } else if (paid) {
                // État 4 : Payé (vert, désactivé)
                inscriptionBtn.setText("✓ Payé");
                setButtonStyle(inscriptionBtn, "evPaidBtn");
                inscriptionBtn.setDisable(true);
            } else {
                // Autres statuts (REFUSEE, ANNULEE...)
                inscriptionBtn.setText("❌ " + statutInsc);
                setButtonStyle(inscriptionBtn, "evDangerBtn");
                inscriptionBtn.setDisable(true);
            }
        }

        // ── Bouton bas "S'inscrire" (inscriptionBtnBottom) ──
        if (inscriptionBtnBottom != null) {
            inscriptionBtnBottom.setVisible(!inscrit);
            inscriptionBtnBottom.setManaged(!inscrit);
        }

        // ── Bouton "Payer" bas ──
        if (payerBtn != null) {
            boolean showPay = inscrit && confirmed && !paid;
            payerBtn.setVisible(showPay);
            payerBtn.setManaged(showPay);
        }

        // ── Bouton "Se désinscrire" bas ──
        if (desinscrireBtn != null) {
            boolean showUnsub = inscrit && !paid;
            desinscrireBtn.setVisible(showUnsub);
            desinscrireBtn.setManaged(showUnsub);
        }

        // ── Désactiver inscription si événement fermé/annulé ──
        String st = safe(current.getStatut()).toUpperCase();
        boolean blocked = ("ANNULE".equals(st) || "FERME".equals(st)) && !inscrit;

        if (!inscrit) {
            try {
                int used = inscriptionService.countByEvent(evenementId);
                if (used >= current.getCapaciteMax()) blocked = true;
            } catch (Exception ignored) {}
        }

        if (blocked) {
            if (inscriptionBtn != null) inscriptionBtn.setDisable(true);
            if (inscriptionBtnBottom != null) inscriptionBtnBottom.setDisable(true);
        }
    }

    private void setButtonStyle(Button btn, String styleClass) {
        btn.getStyleClass().removeAll(
                "evPrimaryBtn", "evInscritBtn", "evAmbreBtn", "evGoldBtn", "evPaidBtn", "evDangerBtn");
        if (!btn.getStyleClass().contains(styleClass)) {
            btn.getStyleClass().add(styleClass);
        }
    }

    private void showMessage(String msg, boolean success) {
        if (messageLabel == null) return;
        messageLabel.setText(msg);
        messageLabel.getStyleClass().removeAll("evMsgSuccess", "evMsgError");
        messageLabel.getStyleClass().add(success ? "evMsgSuccess" : "evMsgError");
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    // ── UTILITAIRES ──

    private String getStatutClass(String s) {
        if (s == null) return "evBadgeOuvert";
        return switch (s.toUpperCase()) {
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

    private String safe(String s) { return s == null ? "" : s; }
}