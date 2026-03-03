package controllers.front.evenements;

import controllers.front.shell.FrontDashboardController;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import models.evenements.Evenement;
import models.evenements.Inscription;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Contrôleur page de détails d'un événement (front user).
 *
 * Adapté aux vrais modèles :
 *  - Evenement : capaciteMax (int, pas Integer), getPrix() (double), pas de getCategorie()/getLieu()
 *  - Inscription : getEventId(), getUserId(), getStatut(), getPaiement(), getDateCreation()
 *                  PAS de getUserNom() — on affiche "Participant #userId"
 *  - InscriptionService : existsForUser(), addInscription(evId, userId, float),
 *                         delete(inscriptionId), countByEvent(evId), getByEventId(evId)
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
    @FXML private Button inscriptionBtnBottom;
    @FXML private Label messageLabel;

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

    // ── ÉTAT ──
    private ShellNavigator navigator;
    private User currentUser;

    private int evenementId = -1;
    private Evenement current;

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

        try {
            boolean inscrit = inscriptionService.existsForUser(evenementId, currentUser.getId());

            if (inscrit) {
                // Confirmation désinscription
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Se désinscrire");
                confirm.setHeaderText("Se désinscrire de « " + safe(current.getTitre()) + " » ?");
                confirm.setContentText("Tu pourras te réinscrire si des places sont disponibles.");
                ButtonType oui = new ButtonType("Oui, me désinscrire", ButtonBar.ButtonData.OK_DONE);
                ButtonType non = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirm.getButtonTypes().setAll(oui, non);

                confirm.showAndWait().ifPresent(bt -> {
                    if (bt == oui) {
                        // Trouver l'inscription de cet utilisateur
                        inscriptionService.getByEventId(evenementId).stream()
                                .filter(i -> i.getUserId() == currentUser.getId())
                                .findFirst()
                                .ifPresent(i -> {
                                    inscriptionService.delete(i.getId());
                                    showMessage("Tu es désinscrit de cet événement.", false);
                                    loadParticipants();
                                    refreshButtons();
                                });
                    }
                });
            } else {
                // Vérif capacité
                int used = inscriptionService.countByEvent(evenementId);
                if (used >= current.getCapaciteMax()) {
                    showMessage("Désolé, l'événement est complet !", false);
                    return;
                }
                inscriptionService.addInscription(evenementId, currentUser.getId(), (float) current.getPrix());
                showMessage("🎉 Inscription confirmée ! À bientôt.", true);
                loadParticipants();
                refreshButtons();
            }
        } catch (Exception e) {
            showMessage("Erreur : " + safe(e.getMessage()), false);
        }
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

        refreshButtons();
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

        // Statut paiement
        Label statutPaie = new Label(
                "Statut : " + safe(insc.getStatut()) + "  ·  Paiement : " + safe(insc.getPaiement()));
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
                    loadParticipants();
                    refreshButtons();
                });
                row.getChildren().add(del);
            }
        }

        return row;
    }

    // ── REFRESH BOUTONS ──

    private void refreshButtons() {
        if (current == null) return;

        boolean inscrit = false;
        if (currentUser != null) {
            try { inscrit = inscriptionService.existsForUser(evenementId, currentUser.getId()); }
            catch (Exception ignored) {}
        }

        String insTxt   = inscrit ? "✓ Déjà inscrit · Se désinscrire" : "✓ S'inscrire";
        String insStyle = inscrit ? "evInscritBtn" : "evPrimaryBtn";

        for (Button b : new Button[]{inscriptionBtn, inscriptionBtnBottom}) {
            if (b == null) continue;
            b.setText(insTxt);
            b.getStyleClass().removeAll("evPrimaryBtn", "evInscritBtn");
            b.getStyleClass().add(insStyle);
        }

        // Désactiver si événement fermé/annulé ET pas encore inscrit
        String st = safe(current.getStatut()).toUpperCase();
        boolean blocked = ("ANNULE".equals(st) || "FERME".equals(st)) && !inscrit;

        // Aussi désactiver si complet ET pas inscrit
        if (!inscrit) {
            try {
                int used = inscriptionService.countByEvent(evenementId);
                if (used >= current.getCapaciteMax()) blocked = true;
            } catch (Exception ignored) {}
        }

        final boolean finalBlocked = blocked;
        for (Button b : new Button[]{inscriptionBtn, inscriptionBtnBottom}) {
            if (b != null) b.setDisable(finalBlocked);
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