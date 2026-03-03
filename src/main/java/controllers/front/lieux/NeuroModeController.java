package controllers.front.lieux;

import controllers.front.shell.FrontDashboardController;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.lieux.EvaluationLieu;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import models.users.User;
import services.lieux.EvaluationLieuService;
import services.lieux.FavoriLieuService;
import services.lieux.LieuService;
import utils.ui.AccessibilityManager;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * NeuroModeController — Vue simplifiée pour le mode neurodiversité.
 *
 * Affiche uniquement les 3 informations clés (prix, lieu, ambiance)
 * avec de grandes icônes, peu de texte et des boutons d'action clairs.
 */
public class NeuroModeController {

    // ── FXML ──────────────────────────────────────────────────────────────
    @FXML private ImageView heroImage;
    @FXML private Label     statusBadge;
    @FXML private Label     lieuIcon;
    @FXML private Label     lieuNom;
    @FXML private Label     lieuCategorie;

    // 3 infos clés
    @FXML private Label keyPrix;
    @FXML private Label keyLieu;
    @FXML private Label keyAmbiance;
    @FXML private Label ambianceIcon;

    // Résumé
    @FXML private VBox  resumeBlock;
    @FXML private Label resumeText;

    // Horaires simplifié
    @FXML private HBox  horairesBlock;
    @FXML private Label horairesText;

    // Note
    @FXML private HBox  noteBlock;
    @FXML private Label noteMoyenne;
    @FXML private Label noteCount;

    // Avis simplifié
    @FXML private VBox   avisSimpleBlock;
    @FXML private VBox   avisSimpleList;
    @FXML private Button voirTousAvisBtn;

    // Boutons
    @FXML private Button favoriBtn;
    @FXML private Button favoriBlockBtn;

    // ── Services ──────────────────────────────────────────────────────────
    private final LieuService           lieuService   = new LieuService();
    private final FavoriLieuService     favoriService = new FavoriLieuService();
    private final EvaluationLieuService evalService   = new EvaluationLieuService();

    // ── State ──────────────────────────────────────────────────────────────
    private ShellNavigator navigator;
    private User            currentUser;
    private Lieu            current;
    private int             lieuId = -1;
    private Runnable        onBackCallback;
    private Runnable        onSwitchToNormal;

    // ── Setters injection ─────────────────────────────────────────────────

    public void setNavigator(ShellNavigator nav) { this.navigator = nav; }

    public void setCurrentUser(User u) { this.currentUser = u; }

    public void setLieuId(int id) {
        this.lieuId = id;
        loadLieu();
    }

    public void setOnBackCallback(Runnable cb) { this.onBackCallback = cb; }

    /**
     * Callback appelé quand l'utilisateur clique "Revenir à la vue complète".
     */
    public void setOnSwitchToNormal(Runnable cb) { this.onSwitchToNormal = cb; }

    // ── Load ──────────────────────────────────────────────────────────────

    private void loadLieu() {
        if (lieuId <= 0) return;

        current = lieuService.getById(lieuId);
        if (current == null) {
            if (lieuNom != null) lieuNom.setText("Lieu introuvable");
            return;
        }

        fillUI();
        animateIn();
    }

    private void fillUI() {
        if (current == null) return;

        // ── Nom + icône catégorie ──────────────────────────────────────────
        String cat = safe(current.getCategorie());
        if (lieuIcon != null)
            lieuIcon.setText(AccessibilityManager.categoryIcon(cat));
        if (lieuNom != null)
            lieuNom.setText(safe(current.getNom()));
        if (lieuCategorie != null) {
            lieuCategorie.setText(cat.isEmpty() ? "" : cat);
            lieuCategorie.setVisible(!cat.isEmpty());
            lieuCategorie.setManaged(!cat.isEmpty());
        }

        // ── Image héro ────────────────────────────────────────────────────
        if (heroImage != null) {
            heroImage.setImage(loadImage(current.getImageUrl()));
            Rectangle clip = new Rectangle(700, 280);
            clip.setArcWidth(24); clip.setArcHeight(24);
            heroImage.setClip(clip);
        }

        // ── 3 INFOS CLÉS ──────────────────────────────────────────────────

        // Prix
        if (keyPrix != null)
            keyPrix.setText(AccessibilityManager.buildBudgetText(
                    current.getBudgetMin(), current.getBudgetMax()));

        // Localisation
        if (keyLieu != null) {
            String ville = safe(current.getVille());
            String adr   = safe(current.getAdresse());
            keyLieu.setText(ville.isEmpty() ? (adr.isEmpty() ? "—" : adr) : ville);
        }

        // Ambiance
        String ambianceStr = AccessibilityManager.ambianceIcon(
                current.getType(), current.getCategorie());
        String[] parts = ambianceStr.split(" ", 2);
        if (ambianceIcon != null && parts.length > 0)
            ambianceIcon.setText(parts[0]);
        if (keyAmbiance != null)
            keyAmbiance.setText(parts.length > 1 ? parts[1] : ambianceStr);

        // ── Statut ouvert/fermé ───────────────────────────────────────────
        buildStatusBadge();

        // ── Résumé court de la description ───────────────────────────────
        String desc = safe(current.getDescription()).trim();
        if (!desc.isEmpty() && resumeBlock != null) {
            String résumé = desc.length() > 120 ? desc.substring(0, 120).trim() + "…" : desc;
            if (resumeText != null) resumeText.setText(résumé);
            resumeBlock.setVisible(true);
            resumeBlock.setManaged(true);
        }

        // ── Horaires simplifiés ───────────────────────────────────────────
        buildHoraireSimple();

        // ── Note ──────────────────────────────────────────────────────────
        buildNote();

        // ── Avis simplifiés (max 3) ───────────────────────────────────────
        buildAvisSimple();

        // ── Favori ────────────────────────────────────────────────────────
        updateFavoriButtons();
    }

    // ── Statut ────────────────────────────────────────────────────────────

    private void buildStatusBadge() {
        if (statusBadge == null || current == null) return;
        List<LieuHoraire> horaires = lieuService.getHorairesByLieuId(lieuId);
        if (horaires == null || horaires.isEmpty()) return;

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now   = LocalTime.now();

        for (LieuHoraire h : horaires) {
            if (h.getJour() == null) continue;
            try {
                DayOfWeek hJour = DayOfWeek.valueOf(h.getJour().toUpperCase());
                if (hJour == today) {
                    boolean ouvert = h.isOuvert()
                        && h.getHeureOuverture1() != null
                        && h.getHeureFermeture1() != null
                        && !now.isBefore(LocalTime.parse(h.getHeureOuverture1()))
                        && !now.isAfter(LocalTime.parse(h.getHeureFermeture1()));

                    if (ouvert) {
                        statusBadge.setText("✅  OUVERT");
                        statusBadge.getStyleClass().add("neuroStatusOuvert");
                    } else {
                        statusBadge.setText("🔒  FERMÉ");
                        statusBadge.getStyleClass().add("neuroStatusFerme");
                    }
                    statusBadge.setVisible(true);
                    statusBadge.setManaged(true);
                    break;
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Horaires simplifiés ───────────────────────────────────────────────

    private void buildHoraireSimple() {
        if (horairesBlock == null) return;
        List<LieuHoraire> horaires = lieuService.getHorairesByLieuId(lieuId);
        if (horaires == null || horaires.isEmpty()) return;

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (LieuHoraire h : horaires) {
            if (h.getJour() == null) continue;
            try {
                DayOfWeek hJour = DayOfWeek.valueOf(h.getJour().toUpperCase());
                if (hJour == today) {
                    String txt;
                    if (!h.isOuvert()) {
                        txt = "Fermé aujourd'hui";
                    } else if (h.getHeureOuverture1() != null && h.getHeureFermeture1() != null) {
                        txt = "Aujourd'hui : " + h.getHeureOuverture1() + " → " + h.getHeureFermeture1();
                    } else {
                        txt = "Horaires disponibles";
                    }
                    if (horairesText != null) horairesText.setText(txt);
                    horairesBlock.setVisible(true);
                    horairesBlock.setManaged(true);
                    break;
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Note moyenne ──────────────────────────────────────────────────────

    private void buildNote() {
        if (noteBlock == null) return;
        try {
            double avg = evalService.avgNote(lieuId);
            int    cnt = evalService.countByLieuId(lieuId);
            if (cnt > 0) {
                String stars = buildStars(avg);
                if (noteMoyenne != null) noteMoyenne.setText(stars + "  " + String.format("%.1f", avg) + " / 5");
                if (noteCount   != null) noteCount.setText(cnt + " avis");
                noteBlock.setVisible(true);
                noteBlock.setManaged(true);
            }
        } catch (Exception ignored) {}
    }

    private String buildStars(double note) {
        int full  = (int) Math.floor(note);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < full ? "★" : "☆");
        return sb.toString();
    }

    // ── Avis simplifié (max 3, résumé court) ─────────────────────────────

    private void buildAvisSimple() {
        if (avisSimpleBlock == null || avisSimpleList == null) return;
        try {
            List<EvaluationLieu> avis = evalService.getByLieuId(lieuId);
            if (avis == null || avis.isEmpty()) return;

            avisSimpleList.getChildren().clear();
            int max = Math.min(3, avis.size());
            for (int i = 0; i < max; i++) {
                avisSimpleList.getChildren().add(buildAvisCard(avis.get(i)));
            }

            if (avis.size() > 3 && voirTousAvisBtn != null) {
                voirTousAvisBtn.setVisible(true);
                voirTousAvisBtn.setManaged(true);
            }

            avisSimpleBlock.setVisible(true);
            avisSimpleBlock.setManaged(true);
        } catch (Exception ignored) {}
    }

    private HBox buildAvisCard(EvaluationLieu e) {
        // Étoiles
        Label stars = new Label(buildStars(e.getNote()));
        stars.setStyle("-fx-font-size: 16px; -fx-text-fill: #d4af37;");

        // Auteur
        Label auteur = new Label(e.getAuteur());
        auteur.setStyle("-fx-font-weight: 900; -fx-font-size: 13px; -fx-text-fill: #0b2550;");

        // Commentaire court
        String cmt = safe(e.getCommentaire()).trim();
        String short_cmt = cmt.length() > 80 ? cmt.substring(0, 80).trim() + "…" : cmt;
        Label commentaire = new Label(short_cmt.isEmpty() ? "(Aucun commentaire)" : short_cmt);
        commentaire.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #334155; -fx-wrap-text: true;");
        commentaire.setWrapText(true);
        commentaire.setMaxWidth(550);

        VBox text = new VBox(3, auteur, commentaire);
        text.getStyleClass().add("neuroAvisText");

        HBox row = new HBox(12, stars, text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("neuroAvisRow");
        return row;
    }

    // ── Favori ────────────────────────────────────────────────────────────

    private void updateFavoriButtons() {
        if (currentUser == null || current == null) return;
        boolean fav = favoriService.isFavori(currentUser.getId(), current.getId());
        setFavoriState(fav);
    }

    private void setFavoriState(boolean fav) {
        String topText   = fav ? "♥" : "♡";
        String blockText = fav ? "♥   Retiré des favoris" : "♡   Ajouter aux favoris";
        if (favoriBtn      != null) favoriBtn.setText(topText);
        if (favoriBlockBtn != null) {
            favoriBlockBtn.setText(blockText);
            favoriBlockBtn.getStyleClass().removeAll("neuroActionFavoriActive", "neuroActionFavori");
            favoriBlockBtn.getStyleClass().add(fav ? "neuroActionFavoriActive" : "neuroActionFavori");
        }
    }

    // ── Actions FXML ──────────────────────────────────────────────────────

    @FXML
    public void goBack() {
        if (onBackCallback != null) { onBackCallback.run(); return; }
        if (navigator != null) navigator.navigate(FrontDashboardController.ROUTE_LIEUX);
    }

    @FXML
    public void toggleFavori() {
        if (currentUser == null || current == null) return;
        boolean now = favoriService.toggle(currentUser.getId(), current.getId());
        setFavoriState(now);

        // Mini animation pulse
        if (favoriBtn != null) {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), favoriBtn);
            st.setToX(1.35); st.setToY(1.35);
            ScaleTransition bk = new ScaleTransition(Duration.millis(130), favoriBtn);
            bk.setToX(1.0); bk.setToY(1.0);
            bk.setInterpolator(Interpolator.EASE_OUT);
            st.setOnFinished(ev -> bk.play());
            st.play();
        }
    }

    @FXML
    public void openMaps() {
        if (current == null) return;
        try {
            String url;
            if (current.getLatitude() != null && current.getLongitude() != null) {
                url = "https://www.google.com/maps/search/?api=1&query="
                    + current.getLatitude() + "," + current.getLongitude();
            } else {
                String q = URLEncoder.encode(
                    safe(current.getNom()) + " " + safe(current.getVille()),
                    StandardCharsets.UTF_8);
                url = "https://www.google.com/maps/search/?api=1&query=" + q;
            }
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    @FXML
    public void openShare() {
        if (current == null) return;
        try {
            String txt = safe(current.getNom()) + " — " + safe(current.getVille())
                + "\n" + AccessibilityManager.buildNeuroSummary(current);
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(txt), null);
        } catch (Exception ignored) {}
    }

    @FXML
    public void openSortieCreate() {
        if (navigator == null) return;
        if (current != null) FrontOfferContext.setSelectedLieuId(current.getId());
        navigator.navigate(FrontDashboardController.ROUTE_SORTIES);
    }

    @FXML
    public void voirTousAvis() {
        if (onSwitchToNormal != null) onSwitchToNormal.run();
    }

    @FXML
    public void switchToNormal() {
        AccessibilityManager.get().setNeuroMode(false);
        if (onSwitchToNormal != null) {
            onSwitchToNormal.run();
        } else if (navigator != null && current != null) {
            navigator.navigate(FrontDashboardController.ROUTE_LIEU_DETAILS_PREFIX + current.getId());
        }
    }

    // ── Animation d'entrée ───────────────────────────────────────────────

    private void animateIn() {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node root = (heroImage != null) ? heroImage.getParent() : null;
            if (root == null) return;
            FadeTransition ft = new FadeTransition(Duration.millis(300), root);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        });
    }

    // ── Utilitaires ──────────────────────────────────────────────────────

    private Image loadImage(String raw) {
        String path = safe(raw).trim();
        try {
            if (!path.isEmpty()) {
                if (path.startsWith("http://") || path.startsWith("https://")
                        || path.startsWith("file:")) {
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
