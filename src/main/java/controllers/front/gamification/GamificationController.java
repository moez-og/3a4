package controllers.front.gamification;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import models.gamification.Badge;
import models.gamification.ClassementEntry;
import models.gamification.UserPoints;
import models.users.User;
import services.gamification.GamificationService;
import services.gamification.RecompenseOffreService;
import services.gamification.RecompenseOffreService.OffreBadgeResult;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Contrôleur du tableau de bord Gamification.
 * Affiche : niveau + points, badges, classement local, offres récompenses.
 */
public class GamificationController {

    @FXML private VBox root;
    @FXML private Label lblNiveauEmoji;
    @FXML private Label lblNiveau;
    @FXML private Label lblPoints;
    @FXML private Label lblRang;
    @FXML private Label lblPointsManquants;
    @FXML private ProgressBar progressNiveau;
    @FXML private FlowPane badgesPane;
    @FXML private VBox classementBox;
    @FXML private TabPane tabPane;
    @FXML private Label lblNbBadges;
    @FXML private Label lblTotalBadges;
    @FXML private VBox mesOffresBox;

    private final GamificationService    gamifService     = new GamificationService();
    private final RecompenseOffreService recompenseService = new RecompenseOffreService();
    private User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadData();
    }

    private void loadData() {
        if (currentUser == null) return;

        // Expirer les offres échues avant affichage
        recompenseService.expireOffresEchues();

        int userId = currentUser.getId();
        UserPoints up = gamifService.getOrCreateUserPoints(userId);
        int rang = gamifService.getRangUtilisateur(userId);
        up.setRang(rang);

        // ── Points & Niveau ──────────────────────────────────────────────
        lblNiveauEmoji.setText(up.getNiveauEmoji());
        lblNiveau.setText(up.getNiveau() != null ? up.getNiveau() : "BRONZE");
        lblPoints.setText(String.format("%,d pts", up.getTotalPoints()));
        lblRang.setText(rang > 0 ? "Rang #" + rang : "Non classé");

        int manquants = up.getPointsPourNiveauSuivant();
        lblPointsManquants.setText(manquants > 0
            ? "Encore " + manquants + " pts pour le niveau suivant"
            : "🏆 Niveau maximum atteint !");

        double progress = up.getProgressionNiveau();
        progressNiveau.setProgress(0);
        animateProgress(progressNiveau, progress);

        String niveauColor = switch (up.getNiveau() != null ? up.getNiveau() : "BRONZE") {
            case "ARGENT"  -> "#9E9E9E";
            case "OR"      -> "#FFD700";
            case "PLATINE" -> "#00BCD4";
            default        -> "#CD7F32";
        };
        lblNiveauEmoji.setStyle("-fx-font-size: 40px;");
        lblNiveau.setStyle("-fx-text-fill: " + niveauColor + "; -fx-font-weight: bold; -fx-font-size: 18px;");
        progressNiveau.setStyle("-fx-accent: " + niveauColor + ";");

        // ── Badges ───────────────────────────────────────────────────────
        List<Badge> mesBadges  = gamifService.getBadgesUtilisateur(userId);
        List<Badge> tousBadges = gamifService.getTousBadges();
        lblNbBadges.setText(String.valueOf(mesBadges.size()));
        lblTotalBadges.setText("/ " + tousBadges.size() + " badges");

        badgesPane.getChildren().clear();
        for (Badge badge : tousBadges) {
            boolean obtenu = mesBadges.stream().anyMatch(b -> b.getId() == badge.getId());
            badgesPane.getChildren().add(createBadgeCard(badge, obtenu));
        }

        // ── Classement ───────────────────────────────────────────────────
        classementBox.getChildren().clear();
        for (ClassementEntry entry : gamifService.getClassementLocal(20)) {
            boolean isMe = entry.getUserId() == userId;
            classementBox.getChildren().add(createClassementRow(entry, isMe));
        }

        // ── Mes Offres ───────────────────────────────────────────────────
        loadMesOffres(userId);
    }

    // ── Mes Offres ────────────────────────────────────────────────────────

    private void loadMesOffres(int userId) {
        if (mesOffresBox == null) return;
        mesOffresBox.getChildren().clear();

        List<OffreBadgeResult> offres = recompenseService.getOffresActivesUser(userId);

        if (offres.isEmpty()) {
            Label vide = new Label("Aucune offre active pour le moment.\nGagnez des badges pour débloquer vos réductions sur les lieux partenaires !");
            vide.setStyle("-fx-text-fill: #78909C; -fx-font-size: 13px; -fx-wrap-text: true;");
            mesOffresBox.getChildren().add(vide);
            return;
        }

        for (OffreBadgeResult offre : offres) {
            mesOffresBox.getChildren().add(createOffreCard(offre));
        }
    }

    private HBox createOffreCard(OffreBadgeResult offre) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle("""
            -fx-background-color: linear-gradient(to right, #0D2137, #0D2B0D);
            -fx-background-radius: 12;
            -fx-border-color: #2E7D32;
            -fx-border-radius: 12;
            -fx-border-width: 1.5;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 3);
            """);

        // Bloc pourcentage
        VBox pctBox = new VBox(2);
        pctBox.setAlignment(Pos.CENTER);
        pctBox.setMinWidth(72);
        Label pctLbl = new Label(offre.getDisplayPourcentage());
        pctLbl.setStyle("-fx-text-fill: #66BB6A; -fx-font-size: 26px; -fx-font-weight: bold;");
        Label offLbl = new Label("RÉDUCTION");
        offLbl.setStyle("-fx-text-fill: #388E3C; -fx-font-size: 9px; -fx-font-weight: bold;");
        pctBox.getChildren().addAll(pctLbl, offLbl);

        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-background-color: #2E7D32;");

        // Bloc infos
        VBox infoBox = new VBox(5);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label titreLbl = new Label("🎁  " + offre.getTitre());
        titreLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        String desc = offre.getDescription() != null
            ? offre.getDescription()
            : "Valable sur tous les lieux partenaires";
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 11px; -fx-wrap-text: true;");
        descLbl.setMaxWidth(300);

        Label partenaireLbl = new Label("✅  Valable sur tous les lieux partenaires");
        partenaireLbl.setStyle("-fx-text-fill: #66BB6A; -fx-font-size: 11px;");

        infoBox.getChildren().addAll(titreLbl, descLbl, partenaireLbl);

        // Bloc expiration
        VBox expBox = new VBox(3);
        expBox.setAlignment(Pos.CENTER_RIGHT);
        expBox.setMinWidth(85);

        int jours = offre.getJoursRestants();
        String couleur = jours <= 2 ? "#FF5252" : jours <= 5 ? "#FFD740" : "#66BB6A";
        Label joursLbl = new Label(jours + "j");
        joursLbl.setStyle("-fx-text-fill: " + couleur + "; -fx-font-size: 22px; -fx-font-weight: bold;");
        Label resteLbl = new Label("restants");
        resteLbl.setStyle("-fx-text-fill: #78909C; -fx-font-size: 10px;");

        String dateFin = offre.getDateFin() != null
            ? offre.getDateFin().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            : "";
        Label dateLbl = new Label("Expire le\n" + dateFin);
        dateLbl.setStyle("-fx-text-fill: #546E7A; -fx-font-size: 10px; -fx-text-alignment: center;");

        expBox.getChildren().addAll(joursLbl, resteLbl, dateLbl);

        card.getChildren().addAll(pctBox, sep, infoBox, expBox);

        // Animation apparition
        FadeTransition ft = new FadeTransition(Duration.millis(500), card);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        return card;
    }

    // ── Badge Card ────────────────────────────────────────────────────────

    private VBox createBadgeCard(Badge badge, boolean obtenu) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setPrefWidth(120);
        card.setMaxWidth(120);

        String bgColor = obtenu ? "#1E2A3A" : "#151D27";
        String opacity = obtenu ? "1.0" : "0.4";
        String border  = obtenu ? "#4FC3F7" : "#2C3E50";

        card.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 12;
            -fx-border-color: %s;
            -fx-border-radius: 12;
            -fx-border-width: 1.5;
            -fx-opacity: %s;
            -fx-cursor: hand;
            """, bgColor, border, opacity));

        Label emojiLbl = new Label(badge.getEmoji());
        emojiLbl.setStyle("-fx-font-size: 28px;");

        Label nomLbl = new Label(badge.getNom());
        nomLbl.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-wrap-text: true; -fx-text-alignment: center;");
        nomLbl.setMaxWidth(110);

        Label descLbl = new Label(badge.getDescription());
        descLbl.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 9px; -fx-wrap-text: true; -fx-text-alignment: center;");
        descLbl.setMaxWidth(110);

        if (obtenu) {
            Label checkLbl = new Label("✓ Obtenu");
            checkLbl.setStyle("-fx-text-fill: #4FC3F7; -fx-font-size: 9px; -fx-font-weight: bold;");
            card.getChildren().addAll(emojiLbl, nomLbl, descLbl, checkLbl);
            FadeTransition ft = new FadeTransition(Duration.millis(600), card);
            ft.setFromValue(0.3);
            ft.setToValue(1.0);
            ft.play();
        } else {
            Label ptsLbl = new Label("+" + badge.getSeuilRequis() + " actions");
            ptsLbl.setStyle("-fx-text-fill: #546E7A; -fx-font-size: 9px;");
            card.getChildren().addAll(emojiLbl, nomLbl, descLbl, ptsLbl);
        }

        Tooltip tip = new Tooltip(badge.getDescription() + "\n+bonus: " + badge.getPointsBonus() + " pts");
        tip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(card, tip);

        return card;
    }

    // ── Classement Row ────────────────────────────────────────────────────

    private HBox createClassementRow(ClassementEntry entry, boolean isMe) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));

        String bg     = isMe ? "#1A3A5C" : (entry.getRang() % 2 == 0 ? "#111920" : "#0E151C");
        String border = isMe ? "#4FC3F7" : "transparent";
        row.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 8;
            -fx-border-color: %s;
            -fx-border-radius: 8;
            -fx-border-width: %s;
            """, bg, border, isMe ? "1.5" : "0"));

        Label rangLbl = new Label(entry.getRangEmoji());
        rangLbl.setStyle("-fx-font-size: 18px; -fx-min-width: 40px;");

        StackPane avatar = new StackPane();
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        Circle circle = new Circle(18);
        circle.setFill(Color.web(isMe ? "#1565C0" : "#1E2A3A"));
        circle.setStroke(Color.web(isMe ? "#4FC3F7" : "#2C3E50"));
        circle.setStrokeWidth(1.5);
        String initiale = entry.getPrenom() != null && !entry.getPrenom().isEmpty()
            ? String.valueOf(entry.getPrenom().charAt(0)).toUpperCase() : "?";
        Label avatarLbl = new Label(initiale);
        avatarLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        avatar.getChildren().addAll(circle, avatarLbl);

        VBox nameBox = new VBox(2);
        Label nameLbl = new Label(entry.getNomComplet() + (isMe ? " (Vous)" : ""));
        nameLbl.setStyle("-fx-text-fill: " + (isMe ? "#4FC3F7" : "white") + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label nivLbl = new Label(entry.getNiveauEmoji() + " " + entry.getNiveau() + "  •  " + entry.getNbBadges() + " badges");
        nivLbl.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 11px;");
        nameBox.getChildren().addAll(nameLbl, nivLbl);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label ptsLbl = new Label(String.format("%,d", entry.getTotalPoints()) + " pts");
        ptsLbl.setStyle("-fx-text-fill: #FFD700; -fx-font-weight: bold; -fx-font-size: 14px;");

        row.getChildren().addAll(rangLbl, avatar, nameBox, ptsLbl);
        return row;
    }

    // ── Progress Animation ────────────────────────────────────────────────

    private void animateProgress(ProgressBar bar, double to) {
        Timeline tl = new Timeline(new KeyFrame(
            Duration.millis(1200),
            new KeyValue(bar.progressProperty(), to, Interpolator.EASE_OUT)
        ));
        tl.play();
    }

    @FXML
    public void initialize() {
        // Les données sont chargées via setCurrentUser()
    }
}
