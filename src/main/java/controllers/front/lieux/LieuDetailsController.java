package controllers.front.lieux;

import controllers.front.shell.FrontDashboardController;
import javafx.animation.*;
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
import services.lieux.AvisSuggestionService;
import services.lieux.EvaluationLieuService;
import services.lieux.FavoriLieuService;
import services.lieux.LieuService;
import services.lieux.ModerationService;
import utils.ui.ShellNavigator;
import utils.ui.FrontOfferContext;
import services.offres.OffreService;
import models.offres.Offre;
import utils.geo.TunisiaGeo;
import utils.ui.AutoCompleteComboBox;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.scene.input.KeyCode;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
import javafx.scene.shape.Rectangle;
import javafx.application.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LieuDetailsController {

    private static final PseudoClass PC_ON = PseudoClass.getPseudoClass("on");

    // ====== GALERIE ======
    @FXML private ImageView image;
    @FXML private StackPane galeriePane;
    @FXML private Button    prevBtn;
    @FXML private Button    nextBtn;
    @FXML private Label     imageCounter;
    @FXML private HBox      thumbsRow;

    // ====== LIEU INFOS ======
    @FXML private Label breadcrumb;
    @FXML private Label name;
    @FXML private Label categorie;
    @FXML private Label type;

    @FXML private Label ville;
    @FXML private Label adresse;
    @FXML private Label coords;

    @FXML private Label  description;
    @FXML private Label  budget;
    @FXML private Label  budgetBadge;
    @FXML private Button siteWebBtn;
    @FXML private Button instagramBtn;
    @FXML private Label  telephone;

    @FXML private VBox descriptionCard;
    @FXML private VBox contactCard;
    @FXML private VBox budgetCard;

    // ====== STATUT BANDEAU ======
    @FXML private HBox  statutBannerBox;
    @FXML private Label statutBannerLabel;

    // ====== AVIS (FXML) ======
    @FXML private Label     avisTitle;
    @FXML private Label     avisAvg;
    @FXML private TextField avisSearchField;
    @FXML private VBox      avisList;
    @FXML private Label     avisEmpty;

    // ====== HORAIRES (FXML) ======
    @FXML private VBox  horaireCard;
    @FXML private VBox  horairesList;
    @FXML private Label horairesEmpty;
    @FXML private Label badgeOuvert;
    @FXML private Label badgeFerme;

    // ====== FAVORI ======
    @FXML private Button favoriBtn;

    // ====== LAYOUT (split-view inline adaptation) ======
    @FXML private HBox twoColLayout;
    @FXML private VBox galerieColBox;
    @FXML private VBox infoColBox;

    // ====== ACTIONS BLOC (boutons infoCol) ======
    @FXML private VBox   actionsBlock;
    @FXML private HBox   actionsRow2;
    @FXML private Button offresBtn;
    @FXML private Button sortieBtn;
    @FXML private VBox inlineSortieContainer;

    // ====== INLINE SORTIE STATE ======
    private boolean sortieFormOpen = false;
    private final OffreService offreService = new OffreService();

    // ====== GALERIE STATE ======
    private List<javafx.scene.image.Image> galleryImages = new ArrayList<>();
    private int currentImageIndex = 0;

    private final LieuService lieuService = new LieuService();
    private final EvaluationLieuService evalService = new EvaluationLieuService();
    private final ModerationService moderationService = new ModerationService();
    private final AvisSuggestionService suggestionService = new AvisSuggestionService();
    private final FavoriLieuService favoriService = new FavoriLieuService();

    private ShellNavigator navigator;
    private User currentUser;

    /** Callback optionnel — appelé quand le bouton "← Retour" est cliqué en mode inline (split view) */
    private Runnable onBackCallback = null;

    private int lieuId = -1;
    private Lieu current;

    private final List<EvaluationLieu> allAvis = new ArrayList<>();

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    /** Injecte un callback pour le mode split-view (panel droit inline) */
    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
        // Activer immédiatement le mode inline dès que le callback est injecté
        javafx.application.Platform.runLater(this::applyInlineLayout);
    }

    /**
     * Adapte le layout pour le mode panel droit (split-view) :
     * - Passe de 2 colonnes côte-à-côte à une seule colonne (galerie en haut, infos en dessous)
     * - Réduit la taille de l'image et du titre
     * - Supprime les contraintes de largeur fixes
     */
    private void applyInlineLayout() {
        if (twoColLayout == null || galerieColBox == null || infoColBox == null) return;

        // ── 1. Supprimer toutes les contraintes fixes des colonnes ──────────
        galerieColBox.setMinWidth(0);    galerieColBox.setPrefWidth(-1);  galerieColBox.setMaxWidth(Double.MAX_VALUE);
        infoColBox.setMinWidth(0);       infoColBox.setPrefWidth(-1);     infoColBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(galerieColBox, Priority.ALWAYS);
        HBox.setHgrow(infoColBox, Priority.ALWAYS);

        // ── 2. Contraindre l'ImageView + galeriePane pour ne PAS dépasser ──
        if (galeriePane != null) {
            galeriePane.setMinWidth(0);
            galeriePane.setPrefWidth(-1);
            galeriePane.setMaxWidth(Double.MAX_VALUE);
        }
        if (image != null) {
            // fitWidth/Height seront recalculés dynamiquement via un listener
            image.setFitHeight(210);
            image.setFitWidth(0);       // 0 = auto (calculé par le listener)
            image.setPreserveRatio(false);
        }

        // ── 3. Titre compact ───────────────────────────────────────────────
        if (name != null) {
            name.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-wrap-text: true;");
        }

        // ── 4. Réorganiser HBox → VBox (1 colonne) ───────────────────────
        javafx.scene.layout.VBox wrapper = new javafx.scene.layout.VBox(10);
        wrapper.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.Parent parent = twoColLayout.getParent();
        if (parent instanceof javafx.scene.layout.VBox parentVBox) {
            int idx = parentVBox.getChildren().indexOf(twoColLayout);
            if (idx >= 0) {
                twoColLayout.getChildren().clear();
                wrapper.getChildren().addAll(galerieColBox, infoColBox);
                parentVBox.getChildren().set(idx, wrapper);

                // ── 5. Binder la largeur de l'image au parent dès qu'il a une largeur réelle ──
                // On attend que le wrapper ait une largeur mesurée, puis on fixe fitWidth
                wrapper.widthProperty().addListener((obs, oldW, newW) -> {
                    double w = newW.doubleValue();
                    if (w > 0 && image != null) {
                        image.setFitWidth(w);
                        // Clip arrondi de l'image
                        if (galeriePane != null) {
                            javafx.scene.shape.Rectangle imgClip = new javafx.scene.shape.Rectangle(w, 210);
                            imgClip.setArcWidth(22); imgClip.setArcHeight(22);
                            image.setClip(imgClip);
                        }
                    }
                });
            }
        }
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
        // Mode split-view : fermer le panel droit via callback
        if (onBackCallback != null) {
            onBackCallback.run();
            return;
        }
        // Mode fullscreen : retour navigation normale
        if (navigator != null) navigator.navigate(FrontDashboardController.ROUTE_LIEUX);
    }

    /* ===================== GALERIE ===================== */

    @FXML
    public void prevImage() {
        if (galleryImages.isEmpty()) return;
        currentImageIndex = (currentImageIndex - 1 + galleryImages.size()) % galleryImages.size();
        showImage(currentImageIndex);
    }

    @FXML
    public void nextImage() {
        if (galleryImages.isEmpty()) return;
        currentImageIndex = (currentImageIndex + 1) % galleryImages.size();
        showImage(currentImageIndex);
    }

    private int lastImageIndex = 0;

    private void showImage(int idx) {
        if (image == null || galleryImages.isEmpty()) return;

        // Direction du slide : droite si on avance, gauche si on recule
        int direction = (idx > lastImageIndex || (lastImageIndex == galleryImages.size() - 1 && idx == 0)) ? 1 : -1;
        lastImageIndex = idx;

        // Slide + fade out
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(160), image);
        slideOut.setToX(-30 * direction);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(160), image);
        fadeOut.setToValue(0);

        ParallelTransition out = new ParallelTransition(slideOut, fadeOut);
        out.setOnFinished(ev -> {
            image.setImage(galleryImages.get(idx));
            image.setTranslateX(30 * direction);
            image.setOpacity(0);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(220), image);
            slideIn.setToX(0);
            slideIn.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), image);
            fadeIn.setToValue(1);
            fadeIn.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(slideIn, fadeIn).play();
        });
        out.play();

        // Compteur
        if (imageCounter != null)
            imageCounter.setText((idx + 1) + " / " + galleryImages.size());

        // Miniature active avec animation scale
        if (thumbsRow != null) {
            for (int i = 0; i < thumbsRow.getChildren().size(); i++) {
                var n = thumbsRow.getChildren().get(i);
                n.getStyleClass().remove("thumbActive");
                if (i == idx) {
                    n.getStyleClass().add("thumbActive");
                    ScaleTransition st = new ScaleTransition(Duration.millis(130), n);
                    st.setToX(1.08); st.setToY(1.08);
                    ScaleTransition back = new ScaleTransition(Duration.millis(130), n);
                    back.setToX(1.0); back.setToY(1.0);
                    st.setOnFinished(e -> back.play());
                    st.play();
                }
            }
        }

        boolean multiple = galleryImages.size() > 1;
        if (prevBtn != null) { prevBtn.setVisible(multiple); prevBtn.setManaged(multiple); }
        if (nextBtn != null) { nextBtn.setVisible(multiple); nextBtn.setManaged(multiple); }
    }

    private void buildGallery(Lieu l) {
        galleryImages.clear();

        // Collecter toutes les images : imagesPaths d'abord, puis imageUrl en fallback
        List<String> paths = new ArrayList<>(l.getImagesPaths());
        if (paths.isEmpty() && l.getImageUrl() != null && !l.getImageUrl().isBlank())
            paths.add(l.getImageUrl());

        for (String p : paths) {
            javafx.scene.image.Image img = loadImageOrFallback(p);
            if (img != null) galleryImages.add(img);
        }

        if (galleryImages.isEmpty()) {
            javafx.scene.image.Image fb = loadImageOrFallback(null);
            if (fb != null) galleryImages.add(fb);
        }

        // Miniatures
        if (thumbsRow != null) {
            thumbsRow.getChildren().clear();
            for (int i = 0; i < galleryImages.size(); i++) {
                final int idx = i;
                ImageView thumb = new ImageView(galleryImages.get(i));
                thumb.setFitWidth(72);
                thumb.setFitHeight(50);
                thumb.setPreserveRatio(false);
                Rectangle clip = new Rectangle(72, 50);
                clip.setArcWidth(10); clip.setArcHeight(10);
                thumb.setClip(clip);
                thumb.getStyleClass().add("thumbImg");
                thumb.setOnMouseClicked(e -> { currentImageIndex = idx; showImage(idx); });
                thumbsRow.getChildren().add(thumb);
            }
        }

        // Afficher la 1ère image
        currentImageIndex = 0;
        showImage(0);

        // Masquer miniatures si une seule image
        if (thumbsRow != null && thumbsRow.getParent() != null) {
            boolean show = galleryImages.size() > 1;
            thumbsRow.getParent().setVisible(show);
            ((javafx.scene.layout.Region) thumbsRow.getParent()).setManaged(show);
        }
    }

    @FXML
    public void openSiteWeb() {
        if (current == null) return;
        String url = safe(current.getSiteWeb()).trim();
        if (!url.isEmpty()) {
            if (!url.startsWith("http")) url = "https://" + url;
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception ignored) {}
        }
    }

    @FXML
    public void openInstagram() {
        if (current == null) return;
        String insta = safe(current.getInstagram()).trim();
        if (!insta.isEmpty()) {
            // Accepte "@handle", "handle", ou URL complète
            String url = insta.startsWith("http") ? insta
                : "https://www.instagram.com/" + insta.replace("@", "");
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception ignored) {}
        }
    }

    @FXML
    public void toggleFavori() {
        if (currentUser == null || current == null) return;
        boolean nowFavori = favoriService.toggle(currentUser.getId(), current.getId());
        updateFavoriBtn(nowFavori);

        // Animation pulse sur le bouton
        if (favoriBtn != null) {
            ScaleTransition grow = new ScaleTransition(Duration.millis(90), favoriBtn);
            grow.setToX(1.22); grow.setToY(1.22);
            ScaleTransition shrink = new ScaleTransition(Duration.millis(160), favoriBtn);
            shrink.setToX(1.0); shrink.setToY(1.0);
            shrink.setInterpolator(Interpolator.EASE_OUT);
            grow.setOnFinished(e -> shrink.play());
            grow.play();
        }
    }

    private void updateFavoriBtn(boolean isFavori) {
        if (favoriBtn == null) return;
        if (isFavori) {
            favoriBtn.setText("♥  Favori");
            favoriBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white;" +
                "-fx-background-radius: 10; -fx-font-weight: 900; -fx-cursor: hand; -fx-padding: 8 18;"
            );
        } else {
            favoriBtn.setText("♡  Ajouter aux favoris");
            favoriBtn.setStyle(
                "-fx-background-color: white; -fx-text-fill: #ef4444;" +
                "-fx-border-color: #ef4444; -fx-border-width: 2; -fx-border-radius: 10;" +
                "-fx-background-radius: 10; -fx-font-weight: 900; -fx-cursor: hand; -fx-padding: 8 18;"
            );
        }
    }

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

    /* ── Voir les offres liées à ce lieu — popup modal ── */
    @FXML
    public void openOffres() {
        if (current == null) return;

        List<Offre> offres = new ArrayList<>();
        try {
            offres = offreService.obtenirOffresParLieu(current.getId());
        } catch (Exception ignored) {}

        showOffresPopup(offres);
    }

    private void showOffresPopup(List<Offre> offres) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        try {
            if (offresBtn != null && offresBtn.getScene() != null)
                dialog.initOwner(offresBtn.getScene().getWindow());
        } catch (Exception ignored) {}
        dialog.setTitle("Offres — " + safe(current.getNom()));
        dialog.setResizable(true);

        // ── Header ──
        Label titleLbl = new Label("🏷  Offres associées à ce lieu");
        titleLbl.setStyle("-fx-font-size:17px;-fx-font-weight:900;-fx-text-fill:#0f172a;");

        Region spacerTop = new Region();
        HBox.setHgrow(spacerTop, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
            "-fx-background-color:rgba(15,23,42,0.08);-fx-background-radius:999;" +
            "-fx-text-fill:#374151;-fx-font-weight:900;-fx-cursor:hand;" +
            "-fx-min-width:32;-fx-min-height:32;-fx-font-size:13px;");
        closeBtn.setOnAction(e -> dialog.close());

        HBox header = new HBox(10, titleLbl, spacerTop, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 14, 0));

        // ── Subtitle ──
        Label subtitle = new Label(
            safe(current.getNom()) + (safe(current.getVille()).isEmpty() ? "" : "  ·  " + safe(current.getVille()))
        );
        subtitle.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(15,23,42,0.45);-fx-font-weight:700;");

        // ── Divider ──
        Region divider = new Region();
        divider.setStyle("-fx-background-color:rgba(15,23,42,0.10);-fx-min-height:1;-fx-max-height:1;");
        divider.setMaxWidth(Double.MAX_VALUE);

        // ── Offres list ──
        VBox offresList = new VBox(10);
        offresList.setPadding(new Insets(10, 0, 0, 0));

        if (offres == null || offres.isEmpty()) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(30, 20, 30, 20));

            Label emptyIcon = new Label("🏷");
            emptyIcon.setStyle("-fx-font-size:40px;");

            Label emptyLbl = new Label("Aucune offre disponible");
            emptyLbl.setStyle(
                "-fx-font-size:16px;-fx-font-weight:900;-fx-text-fill:#374151;");

            Label emptyHint = new Label("Ce lieu n'a pas encore d'offres associées.");
            emptyHint.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(15,23,42,0.45);");

            emptyBox.getChildren().addAll(emptyIcon, emptyLbl, emptyHint);
            offresList.getChildren().add(emptyBox);
        } else {
            for (Offre o : offres) {
                offresList.getChildren().add(buildOffreCard(o));
            }
        }

        ScrollPane sp = new ScrollPane(offresList);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setStyle("-fx-background-color:transparent;-fx-background:transparent;-fx-border-color:transparent;");
        sp.setPrefHeight(offres == null || offres.isEmpty() ? 180 : Math.min(440, offres.size() * 100 + 40));

        VBox content = new VBox(0, header, subtitle, new Region() {{ setStyle("-fx-min-height:8;"); }}, divider, sp);
        content.setPadding(new Insets(22, 22, 16, 22));
        content.setStyle("-fx-background-color:white;-fx-background-radius:18;");
        content.setPrefWidth(520);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) dialog.close();
        });

        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private VBox buildOffreCard(Offre o) {
        VBox card = new VBox(8);
        card.setStyle(
            "-fx-background-color:#f8fafc;-fx-background-radius:14;" +
            "-fx-border-color:rgba(15,23,42,0.08);-fx-border-radius:14;-fx-padding:14 16;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nomLbl = new Label(safe(o.getTitre()).isEmpty() ? "Offre #" + o.getId() : o.getTitre());
        nomLbl.setStyle("-fx-font-size:14px;-fx-font-weight:900;-fx-text-fill:#0f172a;");
        HBox.setHgrow(nomLbl, Priority.ALWAYS);

        // Statut badge
        String statut = safe(o.getStatut()).toUpperCase();
        String badgeColor = statut.equals("ACTIVE") || statut.equals("ACTIF") ? "#16a34a" :
                            statut.equals("EXPIREE") || statut.equals("EXPIRE") ? "#dc2626" : "#d4af37";
        Label statutLbl = new Label(statut.isEmpty() ? "—" : statut);
        statutLbl.setStyle(
            "-fx-font-size:10px;-fx-font-weight:800;-fx-text-fill:white;" +
            "-fx-background-color:" + badgeColor + ";-fx-background-radius:999;-fx-padding:3 10;");

        titleRow.getChildren().addAll(nomLbl, statutLbl);

        // Type + pourcentage
        String typeTxt = safe(o.getType());
        String pctTxt = o.getPourcentage() > 0 ? String.format("-%.0f%%", o.getPourcentage()) : "";
        Label typeLbl = new Label(
            (typeTxt.isEmpty() ? "" : "📌 " + typeTxt) +
            (pctTxt.isEmpty() ? "" : "   🏷 " + pctTxt + " de réduction")
        );
        typeLbl.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(15,23,42,0.65);-fx-font-weight:700;");

        // Description
        String desc = safe(o.getDescription()).trim();
        Label descLbl = new Label(desc.isEmpty() ? "Aucune description" : desc);
        descLbl.setStyle("-fx-font-size:12px;-fx-text-fill:rgba(15,23,42,0.55);");
        descLbl.setWrapText(true);

        // Dates
        String dates = "";
        if (o.getDate_debut() != null && o.getDate_fin() != null) {
            dates = "📅  Du " + o.getDate_debut() + "  au  " + o.getDate_fin();
        } else if (o.getDate_debut() != null) {
            dates = "📅  À partir du " + o.getDate_debut();
        } else if (o.getDate_fin() != null) {
            dates = "📅  Jusqu'au " + o.getDate_fin();
        }

        card.getChildren().addAll(titleRow, typeLbl);
        if (!desc.isEmpty()) card.getChildren().add(descLbl);
        if (!dates.isEmpty()) {
            Label dateLbl = new Label(dates);
            dateLbl.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(15,23,42,0.45);-fx-font-weight:700;");
            card.getChildren().add(dateLbl);
        }

        return card;
    }

    /* ── Ajouter une sortie associée à ce lieu — formulaire inline ── */
    @FXML
    public void openSortieCreate() {
        if (inlineSortieContainer == null) return;

        // Toggle : si déjà ouvert, fermer
        if (sortieFormOpen) {
            closeSortieInlineForm();
            return;
        }

        // Changer le libellé du bouton
        if (sortieBtn != null) {
            sortieBtn.setText("✕  Fermer le formulaire");
        }

        buildAndShowInlineSortieForm();
        sortieFormOpen = true;

        inlineSortieContainer.setVisible(true);
        inlineSortieContainer.setManaged(true);

        // Scroll jusqu'au formulaire
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node parent = inlineSortieContainer.getParent();
            while (parent != null && !(parent instanceof ScrollPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof ScrollPane sp) {
                sp.setVvalue(1.0);
            }
        });
    }

    private void closeSortieInlineForm() {
        if (inlineSortieContainer != null) {
            inlineSortieContainer.getChildren().clear();
            inlineSortieContainer.setVisible(false);
            inlineSortieContainer.setManaged(false);
        }
        if (sortieBtn != null) sortieBtn.setText("＋  Ajouter une sortie");
        sortieFormOpen = false;
    }

    private static final int SORTIE_TITLE_MAX = 60;
    private static final List<String> SORTIE_ACTIVITY_PRESETS =
        List.of("Marche","Footing","Pique-nique","Sortie café","Restaurant","Randonnée","Autre");

    private void buildAndShowInlineSortieForm() {
        if (inlineSortieContainer == null || current == null) return;
        inlineSortieContainer.getChildren().clear();

        services.sorties.AnnonceSortieService sortieService = new services.sorties.AnnonceSortieService();

        // ── Title ──
        Label formTitle = new Label("＋  Nouvelle sortie");
        formTitle.setStyle(
            "-fx-font-size:15px;-fx-font-weight:900;-fx-text-fill:#0f172a;" +
            "-fx-padding:0 0 4 0;");

        Label formSub = new Label("Formulaire pré-rempli avec les informations du lieu");
        formSub.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(15,23,42,0.45);-fx-font-weight:700;");

        // ── Fields ──
        // Titre
        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de la sortie");
        tfTitre.setStyle(fieldStyle());

        Label titleCount = new Label("0/" + SORTIE_TITLE_MAX);
        titleCount.setStyle("-fx-font-size:10px;-fx-text-fill:rgba(15,23,42,0.45);-fx-font-weight:700;");
        tfTitre.textProperty().addListener((obs,o,n) -> {
            if (n != null && n.length() > SORTIE_TITLE_MAX) tfTitre.setText(o);
            titleCount.setText(Math.min(safe(tfTitre.getText()).length(), SORTIE_TITLE_MAX) + "/" + SORTIE_TITLE_MAX);
        });

        HBox titreRow = new HBox(8, tfTitre, titleCount);
        titreRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfTitre, Priority.ALWAYS);

        // Ville (AUTO-FILL)
        ComboBox<String> cbVille = new ComboBox<>(
            javafx.collections.FXCollections.observableArrayList(utils.geo.TunisiaGeo.villes()));
        cbVille.setStyle(fieldStyle());
        cbVille.setMaxWidth(Double.MAX_VALUE);
        AutoCompleteComboBox.install(cbVille);
        // Pre-fill ville from lieu
        String villeLieu = safe(current.getVille()).trim();
        if (!villeLieu.isEmpty()) {
            String matched = utils.geo.TunisiaGeo.villes().stream()
                .filter(v -> v.equalsIgnoreCase(villeLieu)).findFirst().orElse(null);
            if (matched != null) cbVille.getSelectionModel().select(matched);
            else { cbVille.getItems().add(0, villeLieu); cbVille.getSelectionModel().selectFirst(); }
        }

        // Lieu/région (AUTO-FILL)
        ComboBox<String> cbRegion = new ComboBox<>();
        cbRegion.setStyle(fieldStyle());
        cbRegion.setMaxWidth(Double.MAX_VALUE);
        AutoCompleteComboBox.install(cbRegion);

        TextField tfRegionAutre = new TextField();
        tfRegionAutre.setStyle(fieldStyle());
        tfRegionAutre.setVisible(false);
        tfRegionAutre.setManaged(false);
        tfRegionAutre.setPromptText("Région / quartier");

        cbVille.valueProperty().addListener((obs,o,villeSel) -> {
            cbRegion.getItems().setAll(utils.geo.TunisiaGeo.regionsForVille(villeSel));
            AutoCompleteComboBox.refreshOriginalItems(cbRegion);
            cbRegion.getSelectionModel().clearSelection();
            tfRegionAutre.setVisible(false); tfRegionAutre.setManaged(false);
        });

        // Pre-fill region from lieu nom/adresse
        cbRegion.getItems().setAll(utils.geo.TunisiaGeo.regionsForVille(cbVille.getValue()));
        String lieuNomForRegion = safe(current.getNom()).trim();
        String adresseLieu = safe(current.getAdresse()).trim();
        String regionPreset = cbRegion.getItems().stream()
            .filter(r -> r.equalsIgnoreCase(lieuNomForRegion) || r.equalsIgnoreCase(adresseLieu))
            .findFirst().orElse(null);
        if (regionPreset != null) {
            cbRegion.getSelectionModel().select(regionPreset);
        } else if (!lieuNomForRegion.isEmpty()) {
            tfRegionAutre.setText(lieuNomForRegion);
            cbRegion.getSelectionModel().select(utils.geo.TunisiaGeo.REGION_OTHER);
            tfRegionAutre.setVisible(true); tfRegionAutre.setManaged(true);
        }

        cbRegion.valueProperty().addListener((obs,o,regSel) -> {
            boolean other = utils.geo.TunisiaGeo.REGION_OTHER.equals(regSel);
            tfRegionAutre.setVisible(other); tfRegionAutre.setManaged(other);
        });

        VBox regionWrap = new VBox(6, cbRegion, tfRegionAutre);

        // Point de rencontre
        TextField tfPoint = new TextField();
        tfPoint.setStyle(fieldStyle());
        tfPoint.setPromptText("Point de rencontre (ex: devant l'entrée principale)");
        // Auto-fill from lieu adresse
        if (!adresseLieu.isEmpty()) tfPoint.setText(adresseLieu);

        // Activité (AUTO-FILL from categorie)
        ComboBox<String> cbAct = new ComboBox<>(
            javafx.collections.FXCollections.observableArrayList(SORTIE_ACTIVITY_PRESETS));
        cbAct.setStyle(fieldStyle());
        cbAct.setMaxWidth(Double.MAX_VALUE);
        String categoriePreset = safe(current.getCategorie()).trim();
        String matchedAct = SORTIE_ACTIVITY_PRESETS.stream()
            .filter(a -> a.equalsIgnoreCase(categoriePreset)).findFirst().orElse(null);
        if (matchedAct != null) cbAct.getSelectionModel().select(matchedAct);
        else cbAct.getSelectionModel().select("Restaurant");

        TextField tfAutreAct = new TextField();
        tfAutreAct.setStyle(fieldStyle());
        tfAutreAct.setVisible(false); tfAutreAct.setManaged(false);
        tfAutreAct.setPromptText("Type d'activité");
        if (matchedAct == null && !categoriePreset.isEmpty()) {
            tfAutreAct.setText(categoriePreset);
            cbAct.getSelectionModel().select("Autre");
            tfAutreAct.setVisible(true); tfAutreAct.setManaged(true);
        }
        cbAct.valueProperty().addListener((obs,o,n) -> {
            boolean other = "Autre".equalsIgnoreCase(String.valueOf(n));
            tfAutreAct.setVisible(other); tfAutreAct.setManaged(other);
        });
        VBox actWrap = new VBox(6, cbAct, tfAutreAct);

        // Date + heure
        DatePicker dpDate = new DatePicker(LocalDate.now().plusDays(1));
        dpDate.setStyle(fieldStyle());
        dpDate.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) setDisable(item.isBefore(LocalDate.now()));
            }
        });
        Spinner<Integer> spHour = new Spinner<>(0, 23, 10); spHour.setEditable(true);
        Spinner<Integer> spMin = new Spinner<>(0, 59, 0); spMin.setEditable(true);
        HBox timeRow2 = new HBox(8, dpDate, new Label("h"), spHour, new Label("min"), spMin);
        timeRow2.setAlignment(Pos.CENTER_LEFT);

        // Budget
        CheckBox cbNoBudget = new CheckBox("Aucun budget");
        TextField tfBudget = new TextField();
        tfBudget.setStyle(fieldStyle());
        tfBudget.setPromptText("Budget max (TND)");
        cbNoBudget.selectedProperty().addListener((obs,o,n) -> {
            tfBudget.setDisable(Boolean.TRUE.equals(n));
            if (Boolean.TRUE.equals(n)) tfBudget.setText("0");
            else if ("0".equals(tfBudget.getText())) tfBudget.clear();
        });
        // Pre-fill budget from lieu
        if (current.getBudgetMax() != null && current.getBudgetMax() > 0)
            tfBudget.setText(String.format("%.0f", current.getBudgetMax()));

        HBox budgetRow2 = new HBox(10, cbNoBudget, tfBudget);
        budgetRow2.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfBudget, Priority.ALWAYS);

        // Places
        Spinner<Integer> spPlaces = new Spinner<>(1, 999, 5); spPlaces.setEditable(true);

        // Description
        TextArea taDesc = new TextArea();
        taDesc.setStyle(fieldStyle());
        taDesc.setPromptText("Description de la sortie (optionnel)");
        taDesc.setPrefRowCount(3);

        // Error label
        Label errLbl = new Label("");
        errLbl.setStyle("-fx-text-fill:#dc2626;-fx-font-size:12px;-fx-font-weight:800;-fx-wrap-text:true;");
        errLbl.setWrapText(true);
        errLbl.setVisible(false); errLbl.setManaged(false);

        // ── Buttons ──
        Button btnSave = new Button("✓  Créer la sortie");
        btnSave.setStyle(
            "-fx-background-color:linear-gradient(to right,#1e3a5f,#163259);" +
            "-fx-background-radius:10;-fx-text-fill:white;-fx-font-weight:900;" +
            "-fx-font-size:13px;-fx-padding:10 22;-fx-cursor:hand;");

        Button btnCancelForm = new Button("Annuler");
        btnCancelForm.setStyle(
            "-fx-background-color:transparent;-fx-border-color:rgba(15,23,42,0.18);" +
            "-fx-border-radius:10;-fx-background-radius:10;-fx-text-fill:#374151;" +
            "-fx-font-weight:800;-fx-font-size:13px;-fx-padding:10 22;-fx-cursor:hand;");
        btnCancelForm.setOnAction(e -> closeSortieInlineForm());

        btnSave.setOnAction(e -> {
            String titre2 = safe(tfTitre.getText()).trim();
            if (titre2.length() < 5) {
                errLbl.setText("Titre trop court (min 5 caractères)");
                errLbl.setVisible(true); errLbl.setManaged(true); return;
            }
            String ville2 = safe(cbVille.getValue()).trim();
            if (ville2.isEmpty()) {
                errLbl.setText("Choisir une ville");
                errLbl.setVisible(true); errLbl.setManaged(true); return;
            }
            LocalDate d = dpDate.getValue();
            if (d == null || !LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue())).isAfter(LocalDateTime.now())) {
                errLbl.setText("Date + heure doit être dans le futur");
                errLbl.setVisible(true); errLbl.setManaged(true); return;
            }
            try {
                models.sorties.AnnonceSortie newSortie = new models.sorties.AnnonceSortie();
                if (currentUser != null) newSortie.setUserId(currentUser.getId());
                newSortie.setTitre(titre2);
                newSortie.setVille(ville2);

                String regSel2 = cbRegion.getValue();
                String lieuTxt = utils.geo.TunisiaGeo.REGION_OTHER.equals(regSel2)
                    ? safe(tfRegionAutre.getText()).trim() : safe(regSel2);
                newSortie.setLieuTexte(lieuTxt.isEmpty() ? safe(current.getNom()) : lieuTxt);

                newSortie.setPointRencontre(safe(tfPoint.getText()).trim());

                String actSel = safe(cbAct.getValue()).trim();
                newSortie.setTypeActivite("Autre".equalsIgnoreCase(actSel)
                    ? safe(tfAutreAct.getText()).trim() : actSel);

                newSortie.setDateSortie(LocalDateTime.of(d, LocalTime.of(spHour.getValue(), spMin.getValue())));

                double budgetVal = 0;
                if (!cbNoBudget.isSelected()) {
                    String raw = safe(tfBudget.getText()).trim().replace(',', '.');
                    if (!raw.isEmpty()) try { budgetVal = Double.parseDouble(raw); } catch (Exception ignored2) {}
                }
                newSortie.setBudgetMax(budgetVal);
                newSortie.setNbPlaces(spPlaces.getValue());
                newSortie.setStatut("OUVERTE");
                newSortie.setDescription(safe(taDesc.getText()).trim().isEmpty() ? null : taDesc.getText().trim());

                sortieService.add(newSortie);

                // Success feedback
                closeSortieInlineForm();
                showInlineSuccessToast("Sortie créée avec succès !");
            } catch (Exception ex) {
                errLbl.setText("Erreur : " + safe(ex.getMessage()));
                errLbl.setVisible(true); errLbl.setManaged(true);
            }
        });

        HBox formFooter = new HBox(10, btnCancelForm, btnSave);
        formFooter.setAlignment(Pos.CENTER_RIGHT);
        formFooter.setPadding(new Insets(6, 0, 0, 0));

        // ── Grid layout ──
        GridPane grid2 = new GridPane();
        grid2.setHgap(10); grid2.setVgap(10);
        ColumnConstraints cc1 = new ColumnConstraints(110);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setHgrow(Priority.ALWAYS);
        grid2.getColumnConstraints().addAll(cc1, cc2);

        int row2 = 0;
        grid2.add(inlineLbl("Titre *"), 0, row2); grid2.add(titreRow, 1, row2++);
        grid2.add(inlineLbl("Ville *"), 0, row2); grid2.add(cbVille, 1, row2++);
        grid2.add(inlineLbl("Lieu / région"), 0, row2); grid2.add(regionWrap, 1, row2++);
        grid2.add(inlineLbl("Point RDV"), 0, row2); grid2.add(tfPoint, 1, row2++);
        grid2.add(inlineLbl("Activité"), 0, row2); grid2.add(actWrap, 1, row2++);
        grid2.add(inlineLbl("Date *"), 0, row2); grid2.add(timeRow2, 1, row2++);
        grid2.add(inlineLbl("Budget"), 0, row2); grid2.add(budgetRow2, 1, row2++);
        grid2.add(inlineLbl("Places"), 0, row2); grid2.add(spPlaces, 1, row2++);
        grid2.add(inlineLbl("Description"), 0, row2); grid2.add(taDesc, 1, row2++);

        // Pre-fill badge info row
        HBox prefilledInfo = new HBox(8);
        prefilledInfo.setStyle(
            "-fx-background-color:#f0f9ff;-fx-border-color:#bae6fd;" +
            "-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 12;");
        Label infoIcon = new Label("ℹ️");
        Label infoTxt = new Label(
            "Pré-rempli avec : " + safe(current.getNom()) +
            (villeLieu.isEmpty() ? "" : " · " + villeLieu) +
            (categoriePreset.isEmpty() ? "" : " · " + categoriePreset));
        infoTxt.setStyle("-fx-font-size:11px;-fx-text-fill:#0369a1;-fx-font-weight:700;");
        infoTxt.setWrapText(true);
        HBox.setHgrow(infoTxt, Priority.ALWAYS);
        prefilledInfo.getChildren().addAll(infoIcon, infoTxt);

        // Wrapper card
        VBox formCard = new VBox(12,
            formTitle, formSub, prefilledInfo,
            new Region() {{ setStyle("-fx-min-height:2;"); }},
            grid2, errLbl, formFooter
        );
        formCard.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:rgba(30,58,95,0.15);" +
            "-fx-border-radius:14;-fx-background-radius:14;" +
            "-fx-padding:18 16;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),10,0,0,3);");
        formCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(formCard, new Insets(10, 0, 0, 0));

        inlineSortieContainer.getChildren().add(formCard);
    }

    private Label inlineLbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px;-fx-font-weight:800;-fx-text-fill:#374151;-fx-wrap-text:true;");
        return l;
    }

    private String fieldStyle() {
        return "-fx-background-color:#f8fafc;-fx-border-color:rgba(15,23,42,0.12);" +
               "-fx-border-radius:8;-fx-background-radius:8;-fx-font-size:12px;-fx-padding:6 10;";
    }

    private void showInlineSuccessToast(String message) {
        if (sortieBtn == null) return;
        javafx.stage.Window window = null;
        try { window = sortieBtn.getScene().getWindow(); } catch (Exception ignored) {}
        if (window == null) return;

        Stage toast = new Stage();
        toast.initOwner(window);
        toast.initStyle(StageStyle.TRANSPARENT);
        toast.initModality(Modality.NONE);

        Label lbl = new Label("✓  " + message);
        lbl.setStyle("-fx-background-color:#16a34a;-fx-background-radius:10;" +
            "-fx-text-fill:white;-fx-font-weight:900;-fx-font-size:13px;-fx-padding:12 22;");

        StackPane root2 = new StackPane(lbl);
        root2.setStyle("-fx-background-color:transparent;");
        Scene s = new Scene(root2);
        s.setFill(Color.TRANSPARENT);
        toast.setScene(s);
        toast.setOpacity(0);
        toast.show();

        toast.setX(window.getX() + (window.getWidth() - toast.getWidth()) / 2);
        toast.setY(window.getY() + window.getHeight() - 80);

        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,        new KeyValue(toast.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(200),  new KeyValue(toast.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(2.2), new KeyValue(toast.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(2.6), new KeyValue(toast.opacityProperty(), 0))
        );
        tl.setOnFinished(e -> toast.close());
        tl.play();
    }

    /* ======================= LIEU ======================= */

    private void loadLieu() {
        if (lieuId <= 0) return;

        current = lieuService.getById(lieuId);
        if (current == null) {
            if (name != null) name.setText("Lieu introuvable");
            return;
        }

        // ── Breadcrumb ────────────────────────────────────────
        if (breadcrumb != null)
            breadcrumb.setText("Accueil  /  " + safe(current.getNom()));

        // ── Galerie ───────────────────────────────────────────
        buildGallery(current);

        // ── Nom / badges ──────────────────────────────────────
        if (name     != null) name.setText(safe(current.getNom()));
        if (categorie != null) categorie.setText(safe(current.getCategorie()));
        if (type     != null) {
            String t = safe(current.getType()).trim();
            type.setText(t.isEmpty() ? "" : t);
            type.setVisible(!t.isEmpty()); type.setManaged(!t.isEmpty());
        }

        // ── Adresse / coords ──────────────────────────────────
        if (adresse != null) adresse.setText(safe(current.getAdresse()));
        if (ville   != null) {
            ville.setText(safe(current.getVille()));
            ville.setVisible(!safe(current.getVille()).isEmpty());
            ville.setManaged(!safe(current.getVille()).isEmpty());
        }
        if (coords != null) {
            String c = (current.getLatitude() == null || current.getLongitude() == null) ? ""
                    : "📌 " + current.getLatitude() + ", " + current.getLongitude();
            coords.setText(c);
            coords.setVisible(!c.isEmpty()); coords.setManaged(!c.isEmpty());
        }

        // ── Description ───────────────────────────────────────
        String desc = safe(current.getDescription()).trim();
        if (descriptionCard != null) {
            descriptionCard.setVisible(!desc.isEmpty()); descriptionCard.setManaged(!desc.isEmpty());
        }
        if (description != null) description.setText(desc);

        // ── Budget ────────────────────────────────────────────
        String budgetTxt = formatBudget(current.getBudgetMin(), current.getBudgetMax());
        boolean hasBudget = !budgetTxt.isEmpty();
        if (budgetCard != null) { budgetCard.setVisible(hasBudget); budgetCard.setManaged(hasBudget); }
        if (budget    != null) budget.setText(budgetTxt);
        if (budgetBadge != null) budgetBadge.setText(formatBudgetBadge(current.getBudgetMin(), current.getBudgetMax()));

        // ── Téléphone ─────────────────────────────────────────
        String tel = safe(current.getTelephone()).trim();
        if (telephone != null) {
            telephone.setText(tel.isEmpty() ? "" : "☎  " + tel);
            telephone.setVisible(!tel.isEmpty()); telephone.setManaged(!tel.isEmpty());
        }

        // ── Site web ──────────────────────────────────────────
        String web = safe(current.getSiteWeb()).trim();
        if (siteWebBtn != null) {
            siteWebBtn.setVisible(!web.isEmpty()); siteWebBtn.setManaged(!web.isEmpty());
            if (!web.isEmpty()) siteWebBtn.setText("🌐  " + web);
        }

        // ── Instagram ─────────────────────────────────────────
        String insta = safe(current.getInstagram()).trim();
        if (instagramBtn != null) {
            instagramBtn.setVisible(!insta.isEmpty()); instagramBtn.setManaged(!insta.isEmpty());
            if (!insta.isEmpty()) instagramBtn.setText("📸  " + (insta.startsWith("@") ? insta : "@" + insta));
        }

        // ── Carte contact (masquer si rien) ───────────────────
        if (contactCard != null) {
            boolean hasContact = !tel.isEmpty() || !web.isEmpty() || !insta.isEmpty();
            contactCard.setVisible(hasContact); contactCard.setManaged(hasContact);
        }

        // ── Statut bandeau (Ouvert / Fermé) ───────────────────
        buildStatutBanner();

        // ── Horaires ──────────────────────────────────────────
        if (horaireCard != null) {
            loadHoraires();
        }

        // ── Avis ──────────────────────────────────────────────
        loadAvis();

        // ── Bouton favori ─────────────────────────────────────
        if (favoriBtn != null && currentUser != null) {
            updateFavoriBtn(favoriService.isFavori(currentUser.getId(), current.getId()));
        }

        // ── Bouton "Voir les offres" : visible uniquement si lieu PRIVE ───
        boolean isPrive = "PRIVE".equalsIgnoreCase(safe(current.getType()).trim());
        if (offresBtn != null) {
            offresBtn.setVisible(isPrive);
            offresBtn.setManaged(isPrive);
        }
        // actionsRow2 : visible dès qu'au moins un des deux boutons l'est
        if (actionsRow2 != null) {
            actionsRow2.setVisible(true);
            actionsRow2.setManaged(true);
        }

        // Anime le bloc actions en entrée
        if (actionsBlock != null) {
            actionsBlock.setOpacity(0);
            actionsBlock.setTranslateY(12);
            PauseTransition p = new PauseTransition(Duration.millis(350));
            p.setOnFinished(ev -> {
                FadeTransition ft = new FadeTransition(Duration.millis(300), actionsBlock);
                ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition tt = new TranslateTransition(Duration.millis(300), actionsBlock);
                tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(ft, tt).play();
            });
            p.play();
        }

        // ── Animations d'entrée des cards ─────────────────────
        animateDetailsIn();
    }

    /**
     * Anime l'entrée de tous les blocs de la page détails :
     * chaque carte/section fade+slide depuis le bas en cascade.
     */
    private void animateDetailsIn() {
        // Nodes clés à animer
        javafx.scene.Node[] nodes = {
            descriptionCard, horaireCard,
            budgetCard, contactCard,
            statutBannerBox
        };
        for (int i = 0; i < nodes.length; i++) {
            javafx.scene.Node n = nodes[i];
            if (n == null || !n.isVisible()) continue;
            n.setOpacity(0);
            n.setTranslateY(16);
            int delay = 80 + i * 60;
            PauseTransition p = new PauseTransition(Duration.millis(delay));
            p.setOnFinished(ev -> {
                FadeTransition ft = new FadeTransition(Duration.millis(300), n);
                ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition tt = new TranslateTransition(Duration.millis(300), n);
                tt.setToY(0); tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(ft, tt).play();
            });
            p.play();
        }
    }

    /** Bandeau statut ouvert/fermé en haut de la colonne droite */
    private void buildStatutBanner() {
        if (statutBannerBox == null || current == null) return;
        if (current.getHoraires() == null || current.getHoraires().isEmpty()) {
            statutBannerBox.setVisible(false); statutBannerBox.setManaged(false);
            return;
        }
        // Réutilise la logique existante des badges
        boolean ouvert = badgeOuvert != null && badgeOuvert.isVisible();
        // On lit directement l'état calculé par buildHoraires — on appelle buildHoraires d'abord
        // donc on récupère l'état après
        javafx.application.Platform.runLater(() -> {
            if (badgeOuvert != null && badgeOuvert.isVisible()) {
                statutBannerBox.setVisible(true); statutBannerBox.setManaged(true);
                if (statutBannerLabel != null) statutBannerLabel.setText("🕐  " + badgeOuvert.getText() + "  — horaires aujourd'hui");
                statutBannerBox.getStyleClass().removeAll("statutBannerFerme");
                if (!statutBannerBox.getStyleClass().contains("statutBannerOuvert"))
                    statutBannerBox.getStyleClass().add("statutBannerOuvert");
            } else if (badgeFerme != null && badgeFerme.isVisible()) {
                statutBannerBox.setVisible(true); statutBannerBox.setManaged(true);
                if (statutBannerLabel != null) statutBannerLabel.setText("🔒  " + badgeFerme.getText() + "  — actuellement fermé");
                statutBannerBox.getStyleClass().removeAll("statutBannerOuvert");
                if (!statutBannerBox.getStyleClass().contains("statutBannerFerme"))
                    statutBannerBox.getStyleClass().add("statutBannerFerme");
            } else {
                statutBannerBox.setVisible(false); statutBannerBox.setManaged(false);
            }
        });
    }

    private String formatBudget(Double min, Double max) {
        if (min == null && max == null) return "";
        if (min != null && max != null) return String.format("Entre %.0f TND et %.0f TND", min, max);
        if (min != null) return String.format("A partir de %.0f TND", min);
        return String.format("Jusqu a %.0f TND", max);
    }

    private String formatBudgetBadge(Double min, Double max) {
        if (min == null && max == null) return "Gratuit";
        double avg = (min != null && max != null) ? (min + max) / 2.0
                   : (min != null ? min : max);
        if (avg == 0)  return "Gratuit";
        if (avg < 20)  return "Petit budget";
        if (avg <= 80) return "Budget moyen";
        return "Premium";
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
        if (avisEmpty != null) { avisEmpty.setManaged(empty); avisEmpty.setVisible(empty); }
        if (empty) return;

        for (int i = 0; i < list.size(); i++) {
            Node card = buildAvisCard(list.get(i));
            card.setOpacity(0);
            card.setTranslateX(-12);
            avisList.getChildren().add(card);

            int delay = i * 55;
            PauseTransition p = new PauseTransition(Duration.millis(delay));
            p.setOnFinished(ev -> {
                FadeTransition ft = new FadeTransition(Duration.millis(260), card);
                ft.setToValue(1); ft.setInterpolator(Interpolator.EASE_OUT);
                TranslateTransition tt = new TranslateTransition(Duration.millis(260), card);
                tt.setToX(0); tt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(ft, tt).play();
            });
            p.play();
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

        // ── Chips de suggestions ────────────────────────────────
        Label lblSuggTitle = new Label("💡 Suggestions — cliquez pour remplir :");
        lblSuggTitle.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: 800;" +
            "-fx-text-fill: rgba(22,58,92,0.65); -fx-padding: 6 0 2 0;"
        );

        FlowPane chipsPane = new FlowPane();
        chipsPane.setHgap(8);
        chipsPane.setVgap(6);
        chipsPane.setPrefWrapLength(400);

        String categorieLieu = (current != null && current.getCategorie() != null)
            ? current.getCategorie() : "";

        // Méthode locale pour rafraîchir les chips selon la note courante
        Runnable refreshChips = () -> {
            chipsPane.getChildren().clear();
            int note = selectedNote.get();
            List<String> suggestions = suggestionService.getSuggestions(note, categorieLieu);
            for (String suggestion : suggestions) {
                // Texte court affiché sur le chip (50 premiers caractères + …)
                String chipLabel = suggestion.length() > 52
                    ? suggestion.substring(0, 50) + "…"
                    : suggestion;

                Button chip = new Button(chipLabel);
                chip.setWrapText(false);
                chip.setStyle(
                    "-fx-background-color: #f0f4ff;" +
                    "-fx-border-color: #c7d2fe;" +
                    "-fx-border-radius: 999; -fx-background-radius: 999;" +
                    "-fx-text-fill: #3730a3; -fx-font-size: 11px; -fx-font-weight: 700;" +
                    "-fx-padding: 4 12; -fx-cursor: hand;"
                );
                chip.setOnMouseEntered(e -> chip.setStyle(
                    "-fx-background-color: #4f46e5;" +
                    "-fx-border-color: #4f46e5;" +
                    "-fx-border-radius: 999; -fx-background-radius: 999;" +
                    "-fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 700;" +
                    "-fx-padding: 4 12; -fx-cursor: hand;"
                ));
                chip.setOnMouseExited(e -> chip.setStyle(
                    "-fx-background-color: #f0f4ff;" +
                    "-fx-border-color: #c7d2fe;" +
                    "-fx-border-radius: 999; -fx-background-radius: 999;" +
                    "-fx-text-fill: #3730a3; -fx-font-size: 11px; -fx-font-weight: 700;" +
                    "-fx-padding: 4 12; -fx-cursor: hand;"
                ));
                // Clic → remplissage automatique du TextArea
                chip.setOnAction(e -> {
                    area.setText(suggestion);
                    area.positionCaret(suggestion.length());
                    area.requestFocus();
                });
                chipsPane.getChildren().add(chip);
            }
        };

        // Initialiser les chips avec la note par défaut
        refreshChips.run();

        // Mettre à jour les chips quand la note change
        selectedNote.addListener((obs, oldNote, newNote) -> refreshChips.run());

        VBox suggestionsBox = new VBox(0, lblSuggTitle, chipsPane);
        suggestionsBox.setStyle("-fx-padding: 0 0 4 0;");

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

        VBox content = new VBox(10, grid, suggestionsBox, lblHintGlobal);
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