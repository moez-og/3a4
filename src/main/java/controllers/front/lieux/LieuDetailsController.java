package controllers.front.lieux;

import controllers.front.shell.FrontDashboardController;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import models.lieux.EvaluationLieu;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import models.users.User;
import services.lieux.EvaluationLieuService;
import services.lieux.LieuService;
import services.lieux.ModerationService;
import utils.ui.ShellNavigator;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LieuDetailsController {

    private static final PseudoClass PC_ON = PseudoClass.getPseudoClass("on");

    // ====== LIEU INFOS ======
    @FXML private ImageView image;

    @FXML private Label name;
    @FXML private Label categorie;
    @FXML private Label type;

    @FXML private Label ville;
    @FXML private Label adresse;
    @FXML private Label coords;

    @FXML private Label description;
    @FXML private Label budget;

    @FXML private Label telephone;
    @FXML private Label siteWeb;
    @FXML private Label instagram;

    // ====== AVIS (FXML) ======
    @FXML private Label avisTitle;
    @FXML private Label avisAvg;
    @FXML private TextField avisSearchField;
    @FXML private VBox avisList;
    @FXML private Label avisEmpty;

    // ====== HORAIRES (FXML) ======
    @FXML private VBox  horaireCard;
    @FXML private VBox  horairesList;
    @FXML private Label horairesEmpty;
    @FXML private Label badgeOuvert;
    @FXML private Label badgeFerme;

    private final LieuService lieuService = new LieuService();
    private final EvaluationLieuService evalService = new EvaluationLieuService();
    private final ModerationService moderationService = new ModerationService();

    private ShellNavigator navigator;
    private User currentUser;

    private int lieuId = -1;
    private Lieu current;

    private final List<EvaluationLieu> allAvis = new ArrayList<>();

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User u) {
        this.currentUser = u;
    }

    public void setLieuId(int id) {
        this.lieuId = id;
        loadLieu();
        loadHoraires();
        loadAvis();
    }

    @FXML
    private void initialize() {
        if (avisSearchField != null) {
            avisSearchField.textProperty().addListener((obs, o, n) -> renderAvis(filterAvis(n)));
        }
    }

    @FXML
    public void goBack() {
        if (navigator != null) navigator.navigate(FrontDashboardController.ROUTE_LIEUX);
    }



    /* ======================= PARTAGE ======================= */

    @FXML
    public void openShare() {
        if (current == null) return;

        String shareUrl  = buildShareUrl();
        String nomLieu   = safe(current.getNom());
        String villeLieu = safe(current.getVille());
        String shareText = nomLieu + " - " + villeLieu + " | Découvrez ce lieu sur Travel Guide";

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Partager");
        dialog.setResizable(false);

        // Titre + bouton fermer
        Label titleLbl = new Label("Partager");
        titleLbl.setStyle("-fx-font-size:17px;-fx-font-weight:900;-fx-text-fill:#0f172a;");
        Region spacerTop = new Region();
        HBox.setHgrow(spacerTop, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color:rgba(15,23,42,0.06);-fx-background-radius:999;"
            + "-fx-text-fill:#374151;-fx-font-weight:900;-fx-cursor:hand;-fx-min-width:28;-fx-min-height:28;");
        closeBtn.setOnAction(e -> dialog.close());

        HBox topBar = new HBox(10, titleLbl, spacerTop, closeBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 12, 0));

        // Boutons réseaux sociaux
        HBox networksBox = new HBox(18);
        networksBox.setAlignment(Pos.CENTER_LEFT);
        networksBox.setPadding(new Insets(8, 0, 8, 0));

        networksBox.getChildren().add(makeNetworkBtn("●", "WhatsApp", "#25D366",
            () -> openUrl("https://wa.me/?text=" + enc(shareText + " " + shareUrl))));
        networksBox.getChildren().add(makeNetworkBtn("f", "Facebook", "#1877F2",
            () -> openUrl("https://www.facebook.com/sharer/sharer.php?u=" + enc(shareUrl))));
        networksBox.getChildren().add(makeNetworkBtn("✗", "X", "#000000",
            () -> openUrl("https://twitter.com/intent/tweet?text=" + enc(shareText) + "&url=" + enc(shareUrl))));
        networksBox.getChildren().add(makeNetworkBtn("✉", "E-mail", "#6B7280",
            () -> openUrl("mailto:?subject=" + enc("Lieu : " + nomLieu) + "&body=" + enc(shareText + "\n\n" + shareUrl))));
        networksBox.getChildren().add(makeNetworkBtn("✈", "Telegram", "#229ED9",
            () -> openUrl("https://t.me/share/url?url=" + enc(shareUrl) + "&text=" + enc(shareText))));
        networksBox.getChildren().add(makeNetworkBtn("</>", "Intégrer", "#374151", () -> {
            String embed = "<iframe src=\"" + shareUrl + "\" width=\"600\" height=\"400\"></iframe>";
            copyToClipboard(embed);
            showToastOnDialog(dialog, "Code intégré copié !");
        }));

        // Séparateur
        Region sep = new Region();
        sep.setStyle("-fx-background-color:rgba(15,23,42,0.09);-fx-min-height:1;-fx-pref-height:1;-fx-max-height:1;");
        sep.setMaxWidth(Double.MAX_VALUE);

        // Zone lien
        Label lblLien = new Label("Lien du lieu");
        lblLien.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(22,58,92,0.55);-fx-font-weight:700;");

        TextField linkField = new TextField(shareUrl);
        linkField.setEditable(false);
        linkField.setStyle("-fx-background-color:transparent;-fx-border-color:transparent;-fx-font-size:12px;-fx-text-fill:#374151;-fx-font-weight:700;");
        HBox.setHgrow(linkField, Priority.ALWAYS);

        Button copyBtn = new Button("Copier");
        copyBtn.setStyle("-fx-background-color:linear-gradient(to right,#d4af37,#c9920f);"
            + "-fx-background-radius:8;-fx-text-fill:white;-fx-font-weight:900;-fx-font-size:12px;"
            + "-fx-padding:8 16;-fx-cursor:hand;");
        copyBtn.setOnAction(e -> {
            copyToClipboard(shareUrl);
            copyBtn.setText("✓ Copié !");
            copyBtn.setStyle("-fx-background-color:#16a34a;-fx-background-radius:8;"
                + "-fx-text-fill:white;-fx-font-weight:900;-fx-font-size:12px;-fx-padding:8 16;-fx-cursor:hand;");
            Timeline tl = new Timeline(new KeyFrame(Duration.seconds(2), ev -> {
                copyBtn.setText("Copier");
                copyBtn.setStyle("-fx-background-color:linear-gradient(to right,#d4af37,#c9920f);"
                    + "-fx-background-radius:8;-fx-text-fill:white;-fx-font-weight:900;"
                    + "-fx-font-size:12px;-fx-padding:8 16;-fx-cursor:hand;");
            }));
            tl.play();
        });

        HBox linkBox = new HBox(0, linkField, copyBtn);
        linkBox.setStyle("-fx-background-color:#f3f4f6;-fx-background-radius:10;"
            + "-fx-border-color:rgba(15,23,42,0.10);-fx-border-radius:10;-fx-padding:2 2 2 12;");
        linkBox.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, topBar, networksBox, sep, lblLien, linkBox);
        content.setPadding(new Insets(20));
        content.setPrefWidth(480);
        content.setStyle("-fx-background-color:white;-fx-background-radius:16;");

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);

        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private String buildShareUrl() {
        if (current.getLatitude() != null && current.getLongitude() != null) {
            return "https://www.google.com/maps/search/?api=1&query="
                + current.getLatitude() + "," + current.getLongitude();
        }
        return "https://www.google.com/maps/search/?api=1&query="
            + enc(safe(current.getNom()) + " " + safe(current.getVille()));
    }

    private VBox makeNetworkBtn(String icon, String label, String color, Runnable action) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size:28px;-fx-font-weight:900;-fx-text-fill:" + color + ";"
            + "-fx-background-color:" + color + "22;-fx-background-radius:999;"
            + "-fx-min-width:52;-fx-min-height:52;-fx-alignment:CENTER;-fx-padding:0;");
        iconLbl.setMinSize(52, 52);
        iconLbl.setMaxSize(52, 52);
        iconLbl.setAlignment(Pos.CENTER);

        Label nameLbl = new Label(label);
        nameLbl.setStyle("-fx-font-size:11px;-fx-font-weight:800;-fx-text-fill:#374151;-fx-padding:4 0 0 0;");
        nameLbl.setAlignment(Pos.CENTER);

        VBox btn = new VBox(6, iconLbl, nameLbl);
        btn.setAlignment(Pos.CENTER);
        btn.setMinWidth(62);
        btn.setStyle("-fx-cursor:hand;-fx-padding:6 4;-fx-background-radius:12;");
        btn.setOnMouseClicked(e -> action.run());
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseEntered(ev -> btn.setStyle("-fx-background-color:rgba(15,23,42,0.06);"
            + "-fx-background-radius:12;-fx-cursor:hand;-fx-padding:6 4;"));
        btn.setOnMouseExited(ev -> btn.setStyle("-fx-cursor:hand;-fx-padding:6 4;-fx-background-radius:12;"));
        return btn;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection sel = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        } catch (Exception ignored) {}
    }

    private String enc(String s) {
        return URLEncoder.encode(safe(s), StandardCharsets.UTF_8);
    }

    private void showToastOnDialog(Stage owner, String message) {
        Stage toast = new Stage();
        toast.initOwner(owner);
        toast.initStyle(StageStyle.TRANSPARENT);
        toast.initModality(Modality.NONE);

        Label lbl = new Label(message);
        lbl.setStyle("-fx-background-color:#0f172a;-fx-background-radius:8;"
            + "-fx-text-fill:white;-fx-font-weight:900;-fx-font-size:12px;-fx-padding:8 18;");

        StackPane root = new StackPane(lbl);
        root.setStyle("-fx-background-color:transparent;");

        Scene s = new Scene(root);
        s.setFill(Color.TRANSPARENT);
        toast.setScene(s);
        toast.setOpacity(0);
        toast.show();

        toast.setX(owner.getX() + (owner.getWidth() - toast.getWidth()) / 2);
        toast.setY(owner.getY() + owner.getHeight() - 60);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(toast.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(200),  new KeyValue(toast.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(1.8), new KeyValue(toast.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(2.2), new KeyValue(toast.opacityProperty(), 0))
        );
        tl.setOnFinished(e -> toast.close());
        tl.play();
    }


    @FXML
    public void openMaps() {
        if (current == null) return;

        try {
            String url;
            if (current.getLatitude() != null && current.getLongitude() != null) {
                url = "https://www.google.com/maps/search/?api=1&query=" + current.getLatitude() + "," + current.getLongitude();
            } else {
                String q = URLEncoder.encode(safe(current.getNom()) + " " + safe(current.getVille()), StandardCharsets.UTF_8);
                url = "https://www.google.com/maps/search/?api=1&query=" + q;
            }
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {}
    }

    /* ======================= LIEU ======================= */

    private void loadLieu() {
        if (lieuId <= 0) return;

        current = lieuService.getById(lieuId);
        if (current == null) {
            if (name != null) name.setText("Lieu introuvable");
            return;
        }

        if (image != null) image.setImage(loadImageOrFallback(current.getImageUrl()));

        if (name != null) name.setText(safe(current.getNom()));
        if (categorie != null) categorie.setText(safe(current.getCategorie()));
        if (type != null) type.setText(safe(current.getType()).isEmpty() ? "PUBLIC" : safe(current.getType()));

        if (ville != null) ville.setText("Ville: " + safe(current.getVille()));
        if (adresse != null) adresse.setText("Adresse: " + safe(current.getAdresse()));

        String c = (current.getLatitude() == null || current.getLongitude() == null)
                ? "Coordonnées: —"
                : "Coordonnées: " + current.getLatitude() + ", " + current.getLongitude();
        if (coords != null) coords.setText(c);

        String desc = safe(current.getDescription());
        if (description != null) description.setText(desc.isEmpty() ? "Description: —" : desc);

        if (budget != null) budget.setText(formatBudget(current.getBudgetMin(), current.getBudgetMax()));

        if (telephone != null) telephone.setText(lineOrEmpty("☎ ", current.getTelephone()));
        if (siteWeb != null) siteWeb.setText(lineOrEmpty("🌐 ", current.getSiteWeb()));
        if (instagram != null) instagram.setText(lineOrEmpty("📸 ", current.getInstagram()));
    }

    private String formatBudget(Double min, Double max) {
        if (min == null && max == null) return "";
        if (min != null && max != null) return "Budget: " + min + " – " + max + " TND";
        if (min != null) return "Budget: à partir de " + min + " TND";
        return "Budget: jusqu’à " + max + " TND";
    }

    private String lineOrEmpty(String prefix, String v) {
        String s = safe(v).trim();
        return s.isEmpty() ? "" : prefix + s;
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

    /* ======================= HORAIRES ======================= */

    private void loadHoraires() {
        if (horairesList == null || current == null) return;

        List<LieuHoraire> horaires = lieuService.getHorairesByLieuId(lieuId);
        horairesList.getChildren().clear();

        boolean aucun = horaires.isEmpty();
        if (horairesEmpty != null) { horairesEmpty.setVisible(aucun); horairesEmpty.setManaged(aucun); }

        // Calcul ouvert/fermé maintenant
        boolean isOuvert = isLieuOuvertMaintenant(horaires);
        if (badgeOuvert != null) { badgeOuvert.setVisible(isOuvert);  badgeOuvert.setManaged(isOuvert); }
        if (badgeFerme  != null) { badgeFerme.setVisible(!isOuvert);  badgeFerme.setManaged(!isOuvert); }

        String[] JOURS_ORDER = {"LUNDI","MARDI","MERCREDI","JEUDI","VENDREDI","SAMEDI","DIMANCHE"};

        for (String jour : JOURS_ORDER) {
            final String jourFinal = jour;
            LieuHoraire h = horaires.stream()
                .filter(x -> jourFinal.equalsIgnoreCase(x.getJour()))
                .findFirst().orElse(null);

            // Déterminer si c'est aujourd'hui
            boolean isToday = isJourAujourdhui(jour);

            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("horaireRow");
            if (isToday) row.getStyleClass().add("horaireRowToday");

            Label lblJour = new Label(jourLabelFr(jour));
            lblJour.getStyleClass().add("horaireJour");
            lblJour.setMinWidth(90);

            Label lblHeure;
            if (h == null || !h.isOuvert()) {
                lblHeure = new Label("Fermé");
                lblHeure.getStyleClass().add("horaireFerme");
            } else {
                String plage = formatPlage(h.getHeureOuverture1(), h.getHeureFermeture1(),
                                           h.getHeureOuverture2(), h.getHeureFermeture2());
                lblHeure = new Label(plage);
                lblHeure.getStyleClass().add("horaireHeure");
            }

            // Badge "Aujourd'hui" + indicateur ouvert/fermé maintenant
            HBox badges = new HBox(6);
            badges.setAlignment(Pos.CENTER_LEFT);

            if (isToday) {
                Label todayBadge = new Label("Aujourd'hui");
                todayBadge.getStyleClass().add("horaireToday");
                badges.getChildren().add(todayBadge);

                if (h != null && h.isOuvert()) {
                    Label statusNow = isOuvert
                        ? new Label("• Ouvert maintenant")
                        : new Label("• Fermé maintenant");
                    statusNow.getStyleClass().add(isOuvert ? "horaireOuvertNow" : "horaireFermeNow");
                    badges.getChildren().add(statusNow);
                }
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(lblJour, lblHeure, spacer, badges);
            horairesList.getChildren().add(row);
        }
    }

    /**
     * Vérifie si le lieu est ouvert en ce moment en comparant
     * le jour et l'heure actuels aux horaires du lieu.
     */
    private boolean isLieuOuvertMaintenant(List<LieuHoraire> horaires) {
        if (horaires == null || horaires.isEmpty()) return false;

        String aujourdHui = jourDuJour();
        LocalTime maintenant = LocalTime.now();

        for (LieuHoraire h : horaires) {
            if (!aujourdHui.equalsIgnoreCase(h.getJour())) continue;
            if (!h.isOuvert()) return false;

            // Vérifier plage 1
            if (dansPlage(maintenant, h.getHeureOuverture1(), h.getHeureFermeture1())) return true;
            // Vérifier plage 2 (si présente)
            if (dansPlage(maintenant, h.getHeureOuverture2(), h.getHeureFermeture2())) return true;
        }
        return false;
    }

    private boolean dansPlage(LocalTime now, String ouv, String ferm) {
        if (ouv == null || ouv.isBlank() || ferm == null || ferm.isBlank()) return false;
        try {
            LocalTime o = LocalTime.parse(ouv.length() == 5 ? ouv : ouv.substring(0, 5));
            LocalTime f = LocalTime.parse(ferm.length() == 5 ? ferm : ferm.substring(0, 5));
            return !now.isBefore(o) && now.isBefore(f);
        } catch (Exception e) { return false; }
    }

    private String jourDuJour() {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        return switch (dow) {
            case MONDAY    -> "LUNDI";
            case TUESDAY   -> "MARDI";
            case WEDNESDAY -> "MERCREDI";
            case THURSDAY  -> "JEUDI";
            case FRIDAY    -> "VENDREDI";
            case SATURDAY  -> "SAMEDI";
            case SUNDAY    -> "DIMANCHE";
        };
    }

    private boolean isJourAujourdhui(String jour) {
        return jourDuJour().equalsIgnoreCase(jour);
    }

    private String jourLabelFr(String jour) {
        return switch (jour.toUpperCase()) {
            case "LUNDI"    -> "Lundi";
            case "MARDI"    -> "Mardi";
            case "MERCREDI" -> "Mercredi";
            case "JEUDI"    -> "Jeudi";
            case "VENDREDI" -> "Vendredi";
            case "SAMEDI"   -> "Samedi";
            case "DIMANCHE" -> "Dimanche";
            default -> jour;
        };
    }

    private String formatPlage(String o1, String f1, String o2, String f2) {
        String p1 = safe(o1).trim() + " – " + safe(f1).trim();
        if (o2 != null && !o2.isBlank()) {
            return p1 + "   /   " + safe(o2).trim() + " – " + safe(f2).trim();
        }
        return p1;
    }

    /* ======================= AVIS ======================= */

    private void loadAvis() {
        if (avisList == null || avisTitle == null || avisAvg == null) return;
        if (lieuId <= 0) return;

        allAvis.clear();
        allAvis.addAll(evalService.getByLieuId(lieuId));

        updateAvisHeader();
        renderAvis(filterAvis(avisSearchField == null ? "" : avisSearchField.getText()));
    }

    private void updateAvisHeader() {
        int count = allAvis.size();
        double avg = evalService.avgNote(lieuId);

        avisTitle.setText("Avis des visiteurs / " + count);

        if (count == 0) avisAvg.setText("—");
        else avisAvg.setText(stars(avg) + "  " + String.format(Locale.US, "%.1f", avg) + "/5");
    }

    private List<EvaluationLieu> filterAvis(String qRaw) {
        String q = safe(qRaw).trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new ArrayList<>(allAvis);

        List<EvaluationLieu> out = new ArrayList<>();
        for (EvaluationLieu a : allAvis) {
            String blob = (safe(a.getAuteur()) + " " + safe(a.getCommentaire())).toLowerCase(Locale.ROOT);
            if (blob.contains(q)) out.add(a);
        }
        return out;
    }

    private void renderAvis(List<EvaluationLieu> list) {
        avisList.getChildren().clear();

        boolean empty = list.isEmpty();
        if (avisEmpty != null) {
            avisEmpty.setManaged(empty);
            avisEmpty.setVisible(empty);
        }
        if (empty) return;

        for (EvaluationLieu a : list) {
            avisList.getChildren().add(buildAvisCard(a));
        }
    }

    private Node buildAvisCard(EvaluationLieu a) {
        HBox root = new HBox(12);
        root.getStyleClass().add("avisItem");
        root.setPadding(new Insets(12));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("avisAvatar");
        Label initial = new Label(getInitial(a.getAuteur()));
        initial.getStyleClass().add("avisAvatarTxt");
        avatar.getChildren().add(initial);

        VBox mid = new VBox(4);

        HBox line1 = new HBox(10);
        Label author = new Label(a.getAuteur());
        author.getStyleClass().add("avisAuthor");

        Label date = new Label(formatDate(a));
        date.getStyleClass().add("avisMeta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label stars = new Label(stars(a.getNote()) + "  " + a.getNote() + "/5");
        stars.getStyleClass().add("avisStars");

        line1.getChildren().addAll(author, date, spacer, stars);

        Label comment = new Label(safe(a.getCommentaire()).isEmpty() ? "—" : a.getCommentaire());
        comment.getStyleClass().add("avisComment");
        comment.setWrapText(true);

        mid.getChildren().addAll(line1, comment);
        HBox.setHgrow(mid, Priority.ALWAYS);

        VBox actions = new VBox(8);
        actions.getStyleClass().add("avisActions");

        if (canEdit(a)) {
            Button edit = new Button("Modifier");
            edit.getStyleClass().add("ghostBtn");
            edit.setOnAction(e -> showAvisDialog(a));

            Button del = new Button("Supprimer");
            del.getStyleClass().add("dangerBtn");
            del.setOnAction(e -> confirmDelete(a));

            actions.getChildren().addAll(edit, del);
        }

        root.getChildren().addAll(avatar, mid, actions);
        return root;
    }

    private boolean canEdit(EvaluationLieu a) {
        if (currentUser == null) return false;
        if (currentUser.getId() == a.getUserId()) return true;
        String r = safe(currentUser.getRole()).toLowerCase(Locale.ROOT);
        return r.equals("admin");
    }

    @FXML
    public void openAddAvis() {
        if (currentUser == null) {
            info("Avis", "Connecte-toi pour ajouter un avis.");
            return;
        }
        EvaluationLieu existing = allAvis.stream()
                .filter(x -> x.getUserId() == currentUser.getId())
                .findFirst().orElse(null);

        showAvisDialog(existing);
    }

    private void showAvisDialog(EvaluationLieu existing) {
        if (currentUser == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Avis");
        dialog.setHeaderText(existing == null ? "Ajouter un avis" : "Modifier mon avis");

        // Style du dialog
        dialog.getDialogPane().getStyleClass().add("avisDialog");
        URL css = getClass().getResource("/styles/lieux/avis-dialog.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());

        ButtonType save = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        // ⭐ Star rating clickable
        final IntegerProperty selectedNote = new SimpleIntegerProperty(existing == null ? 5 : existing.getNote());
        final IntegerProperty hoverNote = new SimpleIntegerProperty(0);

        HBox starsBox = new HBox(6);
        starsBox.getStyleClass().add("starPicker");

        List<Button> starBtns = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            Button b = new Button();
            b.setFocusTraversable(false);
            b.getStyleClass().add("starBtn");
            b.setOnAction(e -> selectedNote.set(idx));
            b.setOnMouseEntered(e -> hoverNote.set(idx));
            b.setOnMouseExited(e -> hoverNote.set(0));
            starBtns.add(b);
            starsBox.getChildren().add(b);
        }

        Label noteValue = new Label();
        noteValue.getStyleClass().add("starValue");
        noteValue.textProperty().bind(Bindings.createStringBinding(() -> {
            int v = hoverNote.get() > 0 ? hoverNote.get() : selectedNote.get();
            return v + "/5";
        }, selectedNote, hoverNote));

        Runnable refreshStars = () -> {
            int v = hoverNote.get() > 0 ? hoverNote.get() : selectedNote.get();
            for (int i = 0; i < starBtns.size(); i++) {
                int starIndex = i + 1;
                Button b = starBtns.get(i);
                boolean on = starIndex <= v;
                b.setText(on ? "★" : "☆");
                b.pseudoClassStateChanged(PC_ON, on);
            }
        };

        refreshStars.run();
        selectedNote.addListener((obs, o, n) -> refreshStars.run());
        hoverNote.addListener((obs, o, n) -> refreshStars.run());

        // ---- Zone de commentaire ----
        final int MIN_CHARS = 10;
        final int MAX_CHARS = 500;

        TextArea area = new TextArea(existing == null ? "" : safe(existing.getCommentaire()));
        area.setPromptText("Ecrire un commentaire (min. " + MIN_CHARS + " caracteres)...");
        area.setWrapText(true);
        area.setPrefRowCount(4);

        // Compteur de caractères
        Label lblCounter = new Label("0 / " + MAX_CHARS + " caracteres");
        lblCounter.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: 700;" +
            "-fx-text-fill: rgba(22,58,92,0.55);"
        );

        // Label d'erreur sous la zone de texte
        Label lblErrComment = new Label("");
        lblErrComment.setStyle(
            "-fx-text-fill: #dc2626; -fx-font-size: 12px;" +
            "-fx-font-weight: 800; -fx-padding: 2 0 0 2;"
        );
        lblErrComment.setWrapText(true);
        lblErrComment.setMaxWidth(400);
        lblErrComment.setVisible(false);
        lblErrComment.setManaged(false);

        // Mise à jour compteur + validation en temps réel
        area.textProperty().addListener((obs, ov, nv) -> {
            int len = nv == null ? 0 : nv.length();
            lblCounter.setText(len + " / " + MAX_CHARS + " caracteres");

            // Couleur du compteur selon l'état
            if (len == 0) {
                lblCounter.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:rgba(22,58,92,0.55);");
            } else if (len < MIN_CHARS) {
                lblCounter.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:#dc2626;");
            } else if (len > MAX_CHARS) {
                lblCounter.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:#dc2626;");
            } else {
                lblCounter.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:#16a34a;");
            }

            // Masquer l'erreur dès que l'utilisateur tape
            if (len > 0) {
                lblErrComment.setVisible(false);
                lblErrComment.setManaged(false);
                // Supprimer le style rouge du TextArea
                area.setStyle("");
            }
        });

        // Initialiser le compteur si edition
        if (existing != null) {
            int initLen = safe(existing.getCommentaire()).length();
            lblCounter.setText(initLen + " / " + MAX_CHARS + " caracteres");
            if (initLen >= MIN_CHARS && initLen <= MAX_CHARS) {
                lblCounter.setStyle("-fx-font-size:11px;-fx-font-weight:700;-fx-text-fill:#16a34a;");
            }
        }

        // Layout commentaire avec compteur + erreur
        VBox commentBox = new VBox(4, area, lblCounter, lblErrComment);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.getColumnConstraints().add(new ColumnConstraints(110));
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(cc2);

        grid.add(new Label("Note"), 0, 0);
        HBox ratingRow = new HBox(10, starsBox, noteValue);
        ratingRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(ratingRow, 1, 0);

        Label lblComment = new Label("Commentaire *");
        lblComment.setStyle("-fx-font-weight: 900;");
        grid.add(lblComment, 0, 1);
        grid.add(commentBox, 1, 1);

        // Hint global sous la grille
        Label lblHintGlobal = new Label("* Le commentaire est obligatoire (min. " + MIN_CHARS + " car., max. " + MAX_CHARS + " car.)");
        lblHintGlobal.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: rgba(22,58,92,0.55); -fx-padding: 4 0 0 0;"
        );

        VBox content = new VBox(10, grid, lblHintGlobal);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);

        // Boutons stylés
        Node okBtn = dialog.getDialogPane().lookupButton(save);
        if (okBtn != null) okBtn.getStyleClass().add("primaryBtn");
        Node cancelBtn = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("ghostBtn");

        // ---- Bloquer la soumission si commentaire invalide ----
        if (okBtn != null) {
            okBtn.addEventFilter(ActionEvent.ACTION, evt -> {
                String com = safe(area.getText()).trim();
                String errMsg = "";

                if (com.isEmpty()) {
                    errMsg = "Le commentaire est obligatoire. Vous ne pouvez pas soumettre un avis vide.";
                } else if (com.length() < MIN_CHARS) {
                    errMsg = "Commentaire trop court. Minimum " + MIN_CHARS + " caracteres requis (actuellement " + com.length() + ").";
                } else if (com.length() > MAX_CHARS) {
                    errMsg = "Commentaire trop long. Maximum " + MAX_CHARS + " caracteres (actuellement " + com.length() + ").";
                } else {
                    // ── Modération bad words ──
                    ModerationService.ModerationResult modResult = moderationService.analyze(com);

                    if (modResult.isBlocked()) {
                        errMsg = modResult.reason;
                    } else if (modResult.isWarning()) {
                        // Avertissement : on propose le texte censuré mais on bloque quand même
                        errMsg = modResult.reason
                            + "\n\nSuggestion : " + modResult.suggestion;
                    }
                }

                if (!errMsg.isEmpty()) {
                    // Afficher l'erreur
                    lblErrComment.setText(errMsg);
                    lblErrComment.setVisible(true);
                    lblErrComment.setManaged(true);

                    // Colorer le TextArea en rouge
                    area.setStyle(
                        "-fx-border-color: #dc2626; -fx-border-width: 2; -fx-border-radius: 14;"
                    );
                    area.requestFocus();

                    // Annuler la fermeture du dialog
                    evt.consume();
                }
            });
        }

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != save) return;

            int note = Math.max(1, Math.min(5, selectedNote.get()));
            String com = safe(area.getText()).trim();

            evalService.upsert(lieuId, currentUser.getId(), note, com);
            loadAvis();
        });
    }

    private void confirmDelete(EvaluationLieu a) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Supprimer avis");
        alert.setHeaderText("Supprimer cet avis ?");
        alert.setContentText("Cette action est définitive.");

        ButtonType ok = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(ok, cancel);

        alert.showAndWait().ifPresent(bt -> {
            if (bt == ok) {
                evalService.deleteByLieuUser(a.getLieuId(), a.getUserId());
                loadAvis();
            }
        });
    }

    private String formatDate(EvaluationLieu a) {
        if (a.getDateEvaluation() == null) return "";
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return a.getDateEvaluation().format(f) + " sur Web";
    }

    private String stars(double note) {
        int n = (int) Math.round(note);
        if (n < 0) n = 0;
        if (n > 5) n = 5;
        return stars(n);
    }

    private String stars(int note) {
        int n = Math.max(0, Math.min(5, note));
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) sb.append(i <= n ? "★" : "☆");
        return sb.toString();
    }

    private String getInitial(String s) {
        String t = safe(s).trim();
        return t.isEmpty() ? "U" : String.valueOf(Character.toUpperCase(t.charAt(0)));
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String safe(String s) { return s == null ? "" : s; }
}