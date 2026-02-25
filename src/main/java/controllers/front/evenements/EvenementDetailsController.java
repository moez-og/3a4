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
        dialog.setTitle("Nombre de tickets");
        dialog.setHeaderText("Combien de tickets souhaites-tu ?");

        // Spinner 1..maxTickets
        int cap = Math.min(maxTickets, 10); // max 10 tickets par personne
        Spinner<Integer> spinner = new Spinner<>(1, cap, 1);
        spinner.setEditable(false);
        spinner.setPrefWidth(120);

        // Prix dynamique
        Label prixDynLabel = new Label();
        updatePrixDynLabel(prixDynLabel, 1);
        spinner.valueProperty().addListener((obs, old, nv) -> updatePrixDynLabel(prixDynLabel, nv));

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        content.setAlignment(Pos.CENTER_LEFT);

        Label eventLabel = new Label("Événement : " + safe(current.getTitre()));
        eventLabel.setStyle("-fx-font-weight: 900; -fx-font-size: 13;");

        Label prixUnit = new Label("Prix unitaire : " +
                String.format(Locale.FRENCH, "%.2f", current.getPrix()) + " TND");
        prixUnit.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");

        HBox spinnerRow = new HBox(10);
        spinnerRow.setAlignment(Pos.CENTER_LEFT);
        spinnerRow.getChildren().addAll(new Label("Tickets :"), spinner);

        content.getChildren().addAll(eventLabel, prixUnit, spinnerRow, prixDynLabel);

        dialog.getDialogPane().setContent(content);

        ButtonType confirmer = new ButtonType("Confirmer", ButtonBar.ButtonData.OK_DONE);
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmer, annuler);

        dialog.setResultConverter(bt -> bt == confirmer ? spinner.getValue() : null);

        return dialog.showAndWait();
    }

    private void updatePrixDynLabel(Label label, int nbTickets) {
        double total = current.getPrix() * nbTickets;
        label.setText("Total : " + String.format(Locale.FRENCH, "%.2f", total) + " TND"
                + " (" + nbTickets + " ticket" + (nbTickets > 1 ? "s" : "") + ")");
        label.setStyle("-fx-font-weight: 900; -fx-font-size: 14; -fx-text-fill: #d97706;");
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

    private String safe(String s) { return s == null ? "" : s; }
}