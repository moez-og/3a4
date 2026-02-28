package controllers.back.lieux;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import models.offres.Offre;
import services.lieux.LieuService;
import services.offres.OffreService;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class LieuxAdminController {

    @FXML private ScrollPane cardsScroll;
    @FXML private TilePane   cardsPane;
    @FXML private ComboBox<String> filterCombo;
    @FXML private TextField  searchField;
    @FXML private Button     btnAdd;
    @FXML private Label      kpiTotal;
    @FXML private Label      kpiPublic;

    private final LieuService lieuService = new LieuService();
    private final ObservableList<Lieu> masterList  = FXCollections.observableArrayList();
    private final FilteredList<Lieu>  filteredList = new FilteredList<>(masterList, p -> true);

    private Node   selectedCard = null;
    private double lastViewportW = -1;

    private static final double CARD_MIN_W    = 280;
    private static final double CARD_TARGET_W = 300;

    private static final String[] JOURS = {
        "LUNDI","MARDI","MERCREDI","JEUDI","VENDREDI","SAMEDI","DIMANCHE"
    };

    // Patterns de validation
    private static final Pattern PATTERN_TEL    = Pattern.compile("^[+]?[0-9 \\-().]{6,20}$");
    private static final Pattern PATTERN_URL    = Pattern.compile("^(https?://)[^\\s]{3,}$");
    private static final Pattern PATTERN_INSTA  = Pattern.compile("^@?[a-zA-Z0-9_.]{1,30}$");
    private static final Pattern PATTERN_HEURE  = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");

    @FXML
    public void initialize() {
        setupFilterCombo();
        setupSearchFilter();
        setupButtons();
        setupResponsiveTiles();
        loadData();
    }

    private void setupFilterCombo() {
        filterCombo.setItems(FXCollections.observableArrayList("Nom","Ville","Categorie","Type"));
        filterCombo.getSelectionModel().select("Nom");
        filterCombo.valueProperty().addListener((o, ov, nv) -> renderCards(filteredList));
    }

    private void setupSearchFilter() {
        searchField.textProperty().addListener((o, ov, nv) -> {
            filteredList.setPredicate(l -> matchesFilter(l, nv));
            renderCards(filteredList);
        });
    }

    private boolean matchesFilter(Lieu l, String q) {
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) return true;
        String mode = filterCombo.getValue() == null ? "Nom" : filterCombo.getValue();
        return switch (mode) {
            case "Ville"     -> contains(l.getVille(), query);
            case "Categorie" -> contains(l.getCategorie(), query);
            case "Type"      -> contains(l.getType(), query);
            default          -> contains(l.getNom(), query);
        };
    }

    private boolean contains(String s, String q) { return s != null && s.toLowerCase().contains(q); }

    private void setupButtons() { btnAdd.setOnAction(e -> openEditor(null)); }

    private void setupResponsiveTiles() {
        if (cardsScroll == null || cardsPane == null) return;
        cardsScroll.setFitToWidth(true);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        cardsPane.setPrefColumns(1);
        cardsPane.setPrefTileWidth(CARD_TARGET_W);

        cardsScroll.viewportBoundsProperty().addListener((o, ob, b) -> {
            if (b == null) return;
            double vw = b.getWidth();
            if (lastViewportW > 0 && Math.abs(vw - lastViewportW) < 0.5) return;
            lastViewportW = vw;
            applyGrid(vw);
        });
        Bounds b = cardsScroll.getViewportBounds();
        if (b != null && b.getWidth() > 0) applyGrid(b.getWidth());
    }

    private void applyGrid(double vw) {
        double gap = cardsPane.getHgap() > 0 ? cardsPane.getHgap() : 14;
        double avail = Math.max(0, vw - 12);
        int cols = Math.max(1, Math.min(4, (int) Math.floor((avail + gap) / (CARD_MIN_W + gap))));
        double tileW = (avail - gap * (cols - 1)) / cols;
        if (tileW < CARD_MIN_W) { cols = 1; tileW = avail; }
        cardsPane.setPrefColumns(cols);
        cardsPane.setPrefTileWidth(tileW);
    }

    private void loadData() {
        try {
            masterList.clear();
            List<Lieu> lieux = lieuService.getAll();
            masterList.addAll(lieux);
            updateKpis(lieux);
            renderCards(filteredList);
        } catch (Exception e) {
            showError("Erreur","Chargement impossible", e.getMessage());
        }
    }

    private void updateKpis(List<Lieu> lieux) {
        if (kpiTotal  != null) kpiTotal.setText(String.valueOf(lieux.size()));
        long pub = lieux.stream().filter(l -> "PUBLIC".equalsIgnoreCase(safe(l.getType()))).count();
        if (kpiPublic != null) kpiPublic.setText(String.valueOf(pub));
    }

    private void renderCards(List<Lieu> lieux) {
        cardsPane.getChildren().clear();
        selectedCard = null;
        for (Lieu l : lieux) cardsPane.getChildren().add(createCard(l));
        if (lieux.isEmpty()) {
            Label e = new Label("Aucun lieu trouve.");
            e.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 800;");
            cardsPane.getChildren().add(e);
        }
    }

    private Node createCard(Lieu lieu) {
        VBox card = new VBox(10);
        card.getStyleClass().add("lieu-card");
        card.setMinWidth(CARD_MIN_W);
        card.prefWidthProperty().bind(cardsPane.prefTileWidthProperty());
        card.maxWidthProperty().bind(cardsPane.prefTileWidthProperty());

        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");
        imgWrap.setMaxWidth(Double.MAX_VALUE);

        ImageView iv = new ImageView();
        iv.fitWidthProperty().bind(imgWrap.widthProperty());
        iv.setFitHeight(150); iv.setPreserveRatio(false); iv.setSmooth(true);
        Rectangle clip = new Rectangle(CARD_TARGET_W, 150);
        clip.setArcWidth(20); clip.setArcHeight(20);
        clip.widthProperty().bind(iv.fitWidthProperty());
        iv.setClip(clip);

        String imgSrc = (lieu.getImagesPaths() != null && !lieu.getImagesPaths().isEmpty())
                ? lieu.getImagesPaths().get(0) : lieu.getImageUrl();
        iv.setImage(loadImageOrFallback(imgSrc));
        imgWrap.getChildren().add(iv);

        Label typeChip = new Label(safe(lieu.getType()));
        typeChip.getStyleClass().addAll("statusChip",
            "PUBLIC".equalsIgnoreCase(lieu.getType()) ? "status-ouverte" : "status-cloturee");
        StackPane.setAlignment(typeChip, Pos.TOP_LEFT);
        StackPane.setMargin(typeChip, new Insets(10,0,0,10));
        imgWrap.getChildren().add(typeChip);

        Label title  = new Label(safe(lieu.getNom()));   title.getStyleClass().add("cardTitle"); title.setWrapText(true);
        Label meta   = new Label(safe(lieu.getVille()) + " - " + safe(lieu.getCategorie())); meta.getStyleClass().add("cardMeta"); meta.setWrapText(true);
        Label adresse = new Label(">> " + safe(lieu.getAdresse())); adresse.getStyleClass().add("cardLine"); adresse.setWrapText(true);
        int nbImg = lieu.getImagesPaths() != null ? lieu.getImagesPaths().size() : 0;
        int nbHor = lieu.getHoraires()    != null ? lieu.getHoraires().size()    : 0;
        Label info = new Label("Images: " + nbImg + "   Horaires: " + nbHor + " jour(s)"); info.getStyleClass().add("cardLine");

        Button voirOffres = new Button("Voir offres"); voirOffres.getStyleClass().add("card-btn");
        Button edit = new Button("Modifier"); edit.getStyleClass().add("card-btn");
        Button del  = new Button("Supprimer"); del.getStyleClass().addAll("card-btn","danger");

        // Empêche le clic sur un bouton de déclencher les handlers du card (select / double-click)
        for (Button b : new Button[]{voirOffres, edit, del}) {
            b.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> ev.consume());
        }

        voirOffres.setOnAction(e -> openOffersDialog(lieu));
        edit.setOnAction(e -> openEditor(lieu));
        del.setOnAction(e -> {
            if (confirmDelete("Supprimer le lieu : " + safe(lieu.getNom()) + " ?")) {
                try { lieuService.delete(lieu.getId()); loadData(); }
                catch (Exception ex) { showError("Erreur","Suppression impossible", ex.getMessage()); }
            }
        });

        HBox actions = new HBox(10, voirOffres, edit, del); actions.getStyleClass().add("card-actions");
        card.getChildren().addAll(imgWrap, title, meta, adresse, info, actions);
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> selectCard(card));
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> { if (e.getClickCount() == 2) openEditor(lieu); });
        return card;
    }

    // ======================================================================
    //  OFFRES PAR LIEU (lecture)
    // ======================================================================

    private void openOffersDialog(Lieu lieu) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Offres du lieu");

        Label title = new Label("Offres du lieu");
        title.getStyleClass().add("dialogTitle");

        Label sub = new Label(safe(lieu.getNom()) + " • " + safe(lieu.getVille()));
        sub.setStyle("-fx-text-fill: rgba(15,42,68,0.70); -fx-font-weight: 800;");

        Label info = new Label("");
        info.setVisible(false);
        info.setManaged(false);
        info.setStyle("-fx-text-fill: rgba(15,42,68,0.70); -fx-font-weight: 800;");

        VBox list = new VBox(10);
        list.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("editorScroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button close = new Button("Fermer");
        close.getStyleClass().add("ghostBtn");
        close.setOnAction(e -> dialog.close());
        HBox footer = new HBox(10, close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, title, sub, info, scroll, footer);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("dialogRoot");

        Scene scene = new Scene(root, 860, 640);
        try {
            scene.getStylesheets().add(getClass().getResource("/styles/back/sorties-admin.css").toExternalForm());
        } catch (Exception ignored) {
        }
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.centerOnScreen();

        // Charger les offres (avec fallback si liaison non supportée)
        try {
            OffreService offreService = new OffreService();
            List<Offre> offres;
            if (offreService.supportsLieuAssociation()) {
                offres = offreService.obtenirOffresParLieu(lieu.getId());
            } else {
                info.setText("Info : la BD ne relie pas les offres aux lieux (affichage de toutes les offres). ");
                info.setVisible(true);
                info.setManaged(true);
                offres = offreService.obtenirToutes();
            }

            list.getChildren().clear();
            for (Offre o : offres) {
                list.getChildren().add(buildOffreCard(o));
            }
            if (offres.isEmpty()) {
                Label empty = new Label("Aucune offre pour ce lieu.");
                empty.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 800;");
                list.getChildren().add(empty);
            }
        } catch (SQLException ex) {
            Label empty = new Label("Impossible de charger les offres : " + ex.getMessage());
            empty.setStyle("-fx-text-fill: rgba(220,38,38,0.90); -fx-font-weight: 800;");
            list.getChildren().setAll(empty);
        } catch (RuntimeException ex) {
            Label empty = new Label("Offres indisponibles : " + ex.getMessage());
            empty.setStyle("-fx-text-fill: rgba(220,38,38,0.90); -fx-font-weight: 800;");
            list.getChildren().setAll(empty);
        }

        dialog.showAndWait();
    }

    private Node buildOffreCard(Offre offre) {
        VBox card = new VBox(8);
        card.getStyleClass().add("lieu-card");
        card.setMaxWidth(Double.MAX_VALUE);

        String titre = safe(offre.getTitre()).isBlank() ? ("Offre #" + offre.getId()) : safe(offre.getTitre());
        Label t = new Label(titre);
        t.getStyleClass().add("cardTitle");
        t.setWrapText(true);

        String pourc = offre.getPourcentage() > 0 ? (String.format(Locale.US, "%.0f%%", offre.getPourcentage())) : "-";
        String type = safe(offre.getType());
        String range = formatOffreDateRange(offre);
        Label meta = new Label((type.isBlank() ? "" : (type + " • ")) + "Réduction: " + pourc + (range.isBlank() ? "" : (" • " + range)));
        meta.getStyleClass().add("cardMeta");
        meta.setWrapText(true);

        String desc = safe(offre.getDescription()).trim();
        Label d = new Label(desc.isBlank() ? "(sans description)" : desc);
        d.getStyleClass().add("cardLine");
        d.setWrapText(true);

        Label chip = new Label(safe(offre.getStatut()).isBlank() ? "-" : safe(offre.getStatut()));
        chip.getStyleClass().addAll("statusChip", statusClassForOffre(offre));

        HBox top = new HBox(10, chip);
        top.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(top, t, meta, d);
        return card;
    }

    private String statusClassForOffre(Offre offre) {
        String s = safe(offre.getStatut()).trim().toLowerCase(Locale.ROOT);
        if ("active".equals(s) || "ouverte".equals(s) || "open".equals(s)) return "status-ouverte";
        if ("annulee".equals(s) || "annulée".equals(s) || "cancelled".equals(s) || "canceled".equals(s)) return "status-annulee";

        // Si date_fin passée -> cloturée
        try {
            if (offre.getDate_fin() != null) {
                LocalDate end = offre.getDate_fin().toLocalDate();
                if (end.isBefore(LocalDate.now())) return "status-cloturee";
            }
        } catch (Exception ignored) {
        }
        return "status-cloturee";
    }

    private String formatOffreDateRange(Offre offre) {
        try {
            LocalDate start = offre.getDate_debut() != null ? offre.getDate_debut().toLocalDate() : null;
            LocalDate end = offre.getDate_fin() != null ? offre.getDate_fin().toLocalDate() : null;
            if (start == null && end == null) return "";
            if (start != null && end != null) return start + " → " + end;
            if (start != null) return "Début: " + start;
            return "Fin: " + end;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void selectCard(Node card) {
        if (selectedCard != null) selectedCard.getStyleClass().remove("selected");
        selectedCard = card; card.getStyleClass().add("selected");
    }

    // ======================================================================
    //  ÉDITEUR PRINCIPAL
    // ======================================================================

    private void openEditor(Lieu existing) {
        boolean isEdit = existing != null;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(isEdit ? "Modifier Lieu" : "Ajouter un lieu");

        // ---- Champs principaux ----
        TextField tfNom     = new TextField(); tfNom.setPromptText("Nom du lieu *");
        TextField tfVille   = new TextField(); tfVille.setPromptText("Ville *");
        TextField tfAdresse = new TextField(); tfAdresse.setPromptText("Adresse complete *");
        TextField tfTel     = new TextField(); tfTel.setPromptText("+216 71 000 000");
        TextField tfSiteWeb = new TextField(); tfSiteWeb.setPromptText("https://exemple.com");
        TextField tfInsta   = new TextField(); tfInsta.setPromptText("@compte_instagram");

        ComboBox<String> cbCategorie = new ComboBox<>();
        List<String> cats = lieuService.getDistinctCategories();
        cbCategorie.setItems(FXCollections.observableArrayList(
            (cats != null && !cats.isEmpty()) ? cats
            : List.of("CAFE","RESTO","RESTO_BAR","LIEU_PUBLIC","PARC_ATTRACTION","MUSEE","PLAGE","CENTRE_COMMERCIAL")
        ));
        cbCategorie.setPromptText("-- Choisir une categorie --");
        cbCategorie.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbType = new ComboBox<>();
        cbType.setItems(FXCollections.observableArrayList("PUBLIC","PRIVE"));
        cbType.setPromptText("-- Choisir un type --");
        cbType.setMaxWidth(Double.MAX_VALUE);

        TextField tfLat = new TextField(); tfLat.setEditable(false); tfLat.setPromptText("Cliquez sur la carte...");
        TextField tfLng = new TextField(); tfLng.setEditable(false); tfLng.setPromptText("Cliquez sur la carte...");

        // Labels d'erreur pour chaque champ
        Label errNom     = errLabel();
        Label errVille   = errLabel();
        Label errAdresse = errLabel();
        Label errCat     = errLabel();
        Label errType    = errLabel();
        Label errTel     = errLabel();
        Label errSite    = errLabel();
        Label errInsta   = errLabel();
        Label errMap     = errLabel();

        // ---- Images ----
        List<String> selectedImages = new ArrayList<>();
        if (isEdit && existing.getImagesPaths() != null) selectedImages.addAll(existing.getImagesPaths());

        HBox imagesPreviewBox = new HBox(8);
        imagesPreviewBox.setAlignment(Pos.CENTER_LEFT);

        Label lblImgCount = new Label("0 image(s)");
        lblImgCount.getStyleClass().add("hint");

        Button btnPickImg = new Button("Ajouter des images");
        btnPickImg.getStyleClass().add("btn-pill");
        btnPickImg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Selectionner des images");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images","*.jpg","*.jpeg","*.png","*.gif","*.webp","*.bmp")
            );
            List<File> files = fc.showOpenMultipleDialog(dialog);
            if (files != null) {
                for (File f : files) selectedImages.add(f.getAbsolutePath());
                refreshImages(imagesPreviewBox, selectedImages, lblImgCount, dialog);
            }
        });

        // ---- Horaires ----
        Map<String, CheckBox>    cbOuverts = new LinkedHashMap<>();
        Map<String, TextField[]> horFields = new LinkedHashMap<>();
        Map<String, Label[]>     horErrors = new LinkedHashMap<>();

        Map<String, LieuHoraire> horaireMap = new LinkedHashMap<>();
        for (String j : JOURS) { LieuHoraire h = new LieuHoraire(); h.setJour(j); horaireMap.put(j, h); }
        if (isEdit && existing.getHoraires() != null)
            for (LieuHoraire h : existing.getHoraires()) horaireMap.put(h.getJour(), h);

        GridPane gridHor = new GridPane();
        gridHor.setHgap(8); gridHor.setVgap(6); gridHor.setPadding(new Insets(10));
        gridHor.setStyle("-fx-background-color:rgba(236,242,248,0.60);-fx-background-radius:14;-fx-border-radius:14;-fx-border-color:rgba(15,23,42,0.08);");

        String[] hHdr = {"Ouvert","Jour","Ouv. 1","Ferm. 1","Ouv. 2","Ferm. 2",""};
        for (int c = 0; c < hHdr.length; c++) {
            Label lh = new Label(hHdr[c]); lh.getStyleClass().add("formLabel");
            lh.setStyle("-fx-font-size:11px;"); gridHor.add(lh, c, 0);
        }

        int hRow = 1;
        for (String jour : JOURS) {
            LieuHoraire h = horaireMap.get(jour);
            CheckBox cb = new CheckBox(); cb.setSelected(h.isOuvert());
            cbOuverts.put(jour, cb);

            Label lblJ = new Label(jourLabel(jour));
            lblJ.setStyle("-fx-font-weight:800;-fx-font-size:12px;-fx-min-width:80;");

            TextField o1 = tf2(safe(h.getHeureOuverture1()), "08:00", 68);
            TextField f1 = tf2(safe(h.getHeureFermeture1()), "12:00", 68);
            TextField o2 = tf2(safe(h.getHeureOuverture2()), "14:00", 68);
            TextField f2 = tf2(safe(h.getHeureFermeture2()), "22:00", 68);

            Label errHor = errLabel();
            horErrors.put(jour, new Label[]{errHor});

            for (TextField t : new TextField[]{o1, f1, o2, f2}) t.setDisable(!h.isOuvert());
            cb.selectedProperty().addListener((obs, ov, nv) -> {
                for (TextField t : new TextField[]{o1, f1, o2, f2}) t.setDisable(!nv);
                if (!nv) { errHor.setText(""); errHor.setVisible(false); errHor.setManaged(false); }
            });

            horFields.put(jour, new TextField[]{o1, f1, o2, f2});
            gridHor.add(cb,   0, hRow);
            gridHor.add(lblJ, 1, hRow);
            gridHor.add(o1,   2, hRow);
            gridHor.add(f1,   3, hRow);
            gridHor.add(o2,   4, hRow);
            gridHor.add(f2,   5, hRow);
            gridHor.add(errHor, 6, hRow);
            hRow++;
        }

        // ---- Pre-remplissage ----
        if (isEdit) {
            tfNom.setText(safe(existing.getNom()));
            tfVille.setText(safe(existing.getVille()));
            tfAdresse.setText(safe(existing.getAdresse()));
            tfTel.setText(safe(existing.getTelephone()));
            tfSiteWeb.setText(safe(existing.getSiteWeb()));
            tfInsta.setText(safe(existing.getInstagram()));
            cbCategorie.getSelectionModel().select(safe(existing.getCategorie()));
            cbType.getSelectionModel().select(safe(existing.getType()).isBlank() ? "PUBLIC" : existing.getType());
            tfLat.setText(existing.getLatitude()  == null ? "" : String.valueOf(existing.getLatitude()));
            tfLng.setText(existing.getLongitude() == null ? "" : String.valueOf(existing.getLongitude()));
            refreshImages(imagesPreviewBox, selectedImages, lblImgCount, dialog);
        } else {
            if (!cbCategorie.getItems().isEmpty()) cbCategorie.getSelectionModel().clearSelection();
            cbType.getSelectionModel().clearSelection();
        }

        // ---- Validation en temps réel par champ ----
        tfNom.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateNom(tfNom, errNom); });
        tfVille.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateVille(tfVille, errVille); });
        tfAdresse.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateAdresse(tfAdresse, errAdresse); });
        tfTel.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateTel(tfTel, errTel); });
        tfSiteWeb.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateSiteWeb(tfSiteWeb, errSite); });
        tfInsta.focusedProperty().addListener((o, ov, nv) -> { if (!nv) validateInsta(tfInsta, errInsta); });
        cbCategorie.valueProperty().addListener((o, ov, nv) -> { if (nv != null) setErr(errCat, ""); });
        cbType.valueProperty().addListener((o, ov, nv) -> { if (nv != null) setErr(errType, ""); });
        tfLat.textProperty().addListener((o, ov, nv) -> { if (!nv.isBlank()) setErr(errMap, ""); });

        // Coloration rouge du champ si erreur (style inline sur focus lost)
        setupFieldStyle(tfNom,     errNom);
        setupFieldStyle(tfVille,   errVille);
        setupFieldStyle(tfAdresse, errAdresse);
        setupFieldStyle(tfTel,     errTel);
        setupFieldStyle(tfSiteWeb, errSite);
        setupFieldStyle(tfInsta,   errInsta);

        // ---- Preview live ----
        VBox previewCard = new VBox(12);
        previewCard.getStyleClass().add("previewCard");
        previewCard.setPrefWidth(360); previewCard.setMinWidth(280); previewCard.setMaxWidth(480);

        StackPane previewImgWrap = new StackPane();
        previewImgWrap.getStyleClass().add("previewImageWrap");

        ImageView previewIV = new ImageView();
        previewIV.setFitWidth(320); previewIV.setFitHeight(180);
        previewIV.setPreserveRatio(false); previewIV.setSmooth(true);
        Rectangle clipPrev = new Rectangle(320,180);
        clipPrev.setArcWidth(20); clipPrev.setArcHeight(20);
        previewIV.setClip(clipPrev);

        Label previewImgEmpty = new Label("Aucune image"); previewImgEmpty.getStyleClass().add("previewImageEmpty");
        previewImgWrap.getChildren().addAll(previewIV, previewImgEmpty);

        Label previewTypeBadge = new Label("PUBLIC");
        previewTypeBadge.getStyleClass().addAll("statusChip","status-ouverte");
        StackPane.setAlignment(previewTypeBadge, Pos.TOP_LEFT);
        StackPane.setMargin(previewTypeBadge, new Insets(10,0,0,10));
        previewImgWrap.getChildren().add(previewTypeBadge);

        previewCard.widthProperty().addListener((o, ov, w) -> {
            double ww = Math.max(260, w.doubleValue()-24);
            previewIV.setFitWidth(ww); clipPrev.setWidth(ww);
        });

        Label previewTitle    = new Label("Nom du lieu");     previewTitle.getStyleClass().add("previewTitle"); previewTitle.setWrapText(true);
        Label previewMeta     = new Label("Ville - Cat");     previewMeta.getStyleClass().add("previewMeta");  previewMeta.setWrapText(true);
        Label previewAdr      = new Label(">> Adresse");      previewAdr.getStyleClass().add("previewLine");   previewAdr.setWrapText(true);
        Label previewCont     = new Label("Tel: -  Site: -"); previewCont.getStyleClass().add("previewLine");  previewCont.setWrapText(true);
        Label previewHorSec   = new Label("Horaires d'ouverture"); previewHorSec.getStyleClass().add("previewSectionTitle");
        VBox  previewHorBox   = new VBox(2);
        Label liveHint        = new Label(""); liveHint.getStyleClass().add("liveHint");
        Label headerLabel     = new Label("Apercu en direct"); headerLabel.getStyleClass().add("previewHeader");

        previewCard.getChildren().addAll(
            headerLabel, previewImgWrap, previewTitle, previewMeta,
            previewAdr, previewCont, previewHorSec, previewHorBox, liveHint
        );

        Runnable updatePreview = () -> {
            String nom  = safe(tfNom.getText()).trim();
            previewTitle.setText(nom.isEmpty() ? "Nom du lieu" : nom);

            String ville = safe(tfVille.getText()).trim();
            String cat   = safe(cbCategorie.getValue()).trim();
            previewMeta.setText((ville.isEmpty() ? "Ville" : ville) + " - " + (cat.isEmpty() ? "Categorie" : cat));

            String adr = safe(tfAdresse.getText()).trim();
            previewAdr.setText(">> " + (adr.isEmpty() ? "-" : adr));

            String tel  = safe(tfTel.getText()).trim();
            String site = safe(tfSiteWeb.getText()).trim();
            previewCont.setText("Tel: " + (tel.isEmpty() ? "-" : tel) + "   Site: " + (site.isEmpty() ? "-" : site));

            String type = cbType.getValue();
            previewTypeBadge.setText(type == null ? "PUBLIC" : type);
            previewTypeBadge.getStyleClass().removeIf(c -> c.startsWith("status-"));
            previewTypeBadge.getStyleClass().add("PUBLIC".equalsIgnoreCase(type) ? "status-ouverte" : "status-cloturee");

            previewHorBox.getChildren().clear();
            for (String jour : JOURS) {
                CheckBox cb = cbOuverts.get(jour);
                if (cb != null && cb.isSelected()) {
                    TextField[] f = horFields.get(jour);
                    String s1 = f[0].getText().trim(), e1 = f[1].getText().trim();
                    String s2 = f[2].getText().trim(), e2 = f[3].getText().trim();
                    String line = jourLabel(jour) + ": " + (s1.isEmpty()?"?":s1) + " - " + (e1.isEmpty()?"?":e1);
                    if (!s2.isEmpty()) line += " / " + s2 + " - " + (e2.isEmpty()?"?":e2);
                    Label lh = new Label(line); lh.getStyleClass().add("previewLine");
                    previewHorBox.getChildren().add(lh);
                }
            }
            if (previewHorBox.getChildren().isEmpty()) {
                Label lh = new Label("Aucun horaire defini"); lh.getStyleClass().add("hint");
                previewHorBox.getChildren().add(lh);
            }

            if (!selectedImages.isEmpty()) {
                Image img = loadImageOrNull(selectedImages.get(0));
                previewIV.setImage(img);
                previewImgEmpty.setVisible(img == null); previewImgEmpty.setManaged(img == null);
            } else {
                previewIV.setImage(null); previewImgEmpty.setVisible(true); previewImgEmpty.setManaged(true);
            }

            // Hint dynamique dans le preview
            String hint = buildGlobalHint(tfNom, tfVille, tfAdresse, cbCategorie, cbType, tfLat, tfTel, tfSiteWeb, tfInsta, cbOuverts, horFields);
            liveHint.setText(hint);
            liveHint.setVisible(!hint.isEmpty()); liveHint.setManaged(!hint.isEmpty());
        };

        // Listeners preview
        tfNom.textProperty().addListener((a,b,c)     -> updatePreview.run());
        tfVille.textProperty().addListener((a,b,c)   -> updatePreview.run());
        tfAdresse.textProperty().addListener((a,b,c) -> updatePreview.run());
        tfTel.textProperty().addListener((a,b,c)     -> updatePreview.run());
        tfSiteWeb.textProperty().addListener((a,b,c) -> updatePreview.run());
        cbCategorie.valueProperty().addListener((a,b,c) -> updatePreview.run());
        cbType.valueProperty().addListener((a,b,c)      -> updatePreview.run());
        tfLat.textProperty().addListener((a,b,c)        -> updatePreview.run());
        for (String jour : JOURS) {
            cbOuverts.get(jour).selectedProperty().addListener((a,b,c) -> updatePreview.run());
            for (TextField t : horFields.get(jour)) t.textProperty().addListener((a,b,c) -> updatePreview.run());
        }

        // ---- Map ----
        WebView webView = new WebView();
        webView.setPrefSize(480,280);
        WebEngine engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.load(getClass().getResource("/map/map_picker.html").toExternalForm());
        engine.titleProperty().addListener((obs, ot, nt) -> {
            if (nt == null || !nt.contains(",")) return;
            String[] mp = nt.split("\\|", 2);
            String[] co = mp[0].split(",");
            if (co.length != 2) return;
            tfLat.setText(co[0].trim()); tfLng.setText(co[1].trim());
            if (mp.length == 2 && !mp[1].isBlank()) {
                tfAdresse.setText(mp[1].trim());
                String ville = extractCity(mp[1].trim());
                if (ville != null && !ville.isBlank()) tfVille.setText(ville);
            }
        });

        // ---- Layout formulaire ----
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(6);
        ColumnConstraints cc1 = new ColumnConstraints(); cc1.setPercentWidth(36);
        ColumnConstraints cc2 = new ColumnConstraints(); cc2.setPercentWidth(64);
        grid.getColumnConstraints().addAll(cc1, cc2);

        int r = 0;
        grid.add(lab("Nom *"),       0, r); grid.add(tfNom,      1, r++); grid.add(errNom,     1, r++);
        grid.add(lab("Ville *"),     0, r); grid.add(tfVille,    1, r++); grid.add(errVille,   1, r++);
        grid.add(lab("Adresse *"),   0, r); grid.add(tfAdresse,  1, r++); grid.add(errAdresse, 1, r++);
        grid.add(lab("Categorie *"), 0, r); grid.add(cbCategorie,1, r++); grid.add(errCat,     1, r++);
        grid.add(lab("Type *"),      0, r); grid.add(cbType,     1, r++); grid.add(errType,    1, r++);
        grid.add(lab("Telephone"),   0, r); grid.add(tfTel,      1, r++); grid.add(errTel,     1, r++);
        grid.add(lab("Site web"),    0, r); grid.add(tfSiteWeb,  1, r++); grid.add(errSite,    1, r++);
        grid.add(lab("Instagram"),   0, r); grid.add(tfInsta,    1, r++); grid.add(errInsta,   1, r++);

        Label lblSecImg = new Label("Images du lieu"); lblSecImg.getStyleClass().add("previewSectionTitle");
        HBox imgCtrl = new HBox(10, btnPickImg, lblImgCount); imgCtrl.setAlignment(Pos.CENTER_LEFT);
        ScrollPane imgScroll = new ScrollPane(imagesPreviewBox);
        imgScroll.setFitToHeight(true); imgScroll.setPrefViewportHeight(100);
        imgScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        imgScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        imgScroll.getStyleClass().add("editorScroll");

        Label lblMap = new Label("Localisation * (cliquez sur la carte)"); lblMap.getStyleClass().add("previewSectionTitle");
        HBox latBox = new HBox(6, lab("Lat:"), tfLat); latBox.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(tfLat, Priority.ALWAYS);
        HBox lngBox = new HBox(6, lab("Lng:"), tfLng); lngBox.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(tfLng, Priority.ALWAYS);
        HBox coords = new HBox(12, latBox, lngBox); coords.setAlignment(Pos.CENTER_LEFT);

        Label lblSecHor = new Label("Horaires d'ouverture"); lblSecHor.getStyleClass().add("previewSectionTitle");

        VBox formContent = new VBox(12,
            grid,
            sep(), lblSecImg, imgCtrl, imgScroll,
            sep(), lblMap, webView, coords, errMap,
            sep(), lblSecHor, gridHor
        );
        formContent.setPadding(new Insets(0,8,8,0));

        ScrollPane formScroll = new ScrollPane(formContent);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        formScroll.getStyleClass().add("editorScroll");

        // ---- Footer ----
        Button btnSave   = new Button(isEdit ? "Enregistrer" : "Creer");
        Button btnCancel = new Button("Annuler");
        btnSave.getStyleClass().add("primaryBtn");
        btnCancel.getStyleClass().add("ghostBtn");
        HBox footer = new HBox(10, btnCancel, btnSave); footer.setAlignment(Pos.CENTER_RIGHT);

        btnCancel.setOnAction(e -> dialog.close());

        btnSave.setOnAction(e -> {
            // 1. Valider tous les champs
            boolean ok = true;
            ok &= validateNom(tfNom, errNom);
            ok &= validateVille(tfVille, errVille);
            ok &= validateAdresse(tfAdresse, errAdresse);
            ok &= validateTel(tfTel, errTel);
            ok &= validateSiteWeb(tfSiteWeb, errSite);
            ok &= validateInsta(tfInsta, errInsta);

            if (cbCategorie.getValue() == null) {
                setErr(errCat, "Veuillez choisir une categorie"); ok = false;
            }
            if (cbType.getValue() == null) {
                setErr(errType, "Veuillez choisir un type"); ok = false;
            }
            if (tfLat.getText().isBlank()) {
                setErr(errMap, "Cliquez sur la carte pour definir la position du lieu"); ok = false;
            }

            // 2. Valider les horaires cochés
            for (String jour : JOURS) {
                if (cbOuverts.get(jour).isSelected()) {
                    TextField[] f = horFields.get(jour);
                    Label[]     errs = horErrors.get(jour);
                    String o1 = f[0].getText().trim(), fe1 = f[1].getText().trim();
                    String o2 = f[2].getText().trim(), fe2 = f[3].getText().trim();
                    String msg = "";
                    if (o1.isEmpty())  msg = "Heure d'ouverture 1 requise";
                    else if (!PATTERN_HEURE.matcher(o1).matches()) msg = "Format invalide (HH:mm)";
                    else if (fe1.isEmpty()) msg = "Heure de fermeture 1 requise";
                    else if (!PATTERN_HEURE.matcher(fe1).matches()) msg = "Format invalide (HH:mm)";
                    else if (fe1.compareTo(o1) <= 0) msg = "Fermeture doit etre apres ouverture";
                    else if (!o2.isEmpty() || !fe2.isEmpty()) {
                        if (o2.isEmpty()) msg = "Heure d'ouverture 2 requise";
                        else if (!PATTERN_HEURE.matcher(o2).matches()) msg = "Format invalide (HH:mm)";
                        else if (fe2.isEmpty()) msg = "Heure de fermeture 2 requise";
                        else if (!PATTERN_HEURE.matcher(fe2).matches()) msg = "Format invalide (HH:mm)";
                        else if (fe2.compareTo(o2) <= 0) msg = "Fermeture 2 doit etre apres ouverture 2";
                        else if (o2.compareTo(fe1) < 0) msg = "Plage 2 doit etre apres la plage 1";
                    }
                    if (!msg.isEmpty()) { setErr(errs[0], msg); ok = false; }
                    else { setErr(errs[0], ""); }
                }
            }

            if (!ok) return;

            // 3. Vérifier doublon
            String nom   = tfNom.getText().trim();
            String ville = tfVille.getText().trim();
            String adresse = tfAdresse.getText().trim();
            int excludeId = isEdit ? existing.getId() : -1;

            if (lieuService.existsByNomVille(nom, ville, excludeId)) {
                setErr(errNom, "Un lieu avec ce nom existe deja dans cette ville !");
                setErr(errVille, "Voir ci-dessus");
                showWarning("Doublon detecte",
                    "Le lieu \"" + nom + "\" existe deja a " + ville + ".\nModifiez le nom ou choisissez une autre ville.");
                return;
            }
            if (lieuService.existsByNomAdresse(nom, adresse, excludeId)) {
                setErr(errNom, "Un lieu avec ce nom existe deja a cette adresse !");
                setErr(errAdresse, "Voir ci-dessus");
                showWarning("Doublon detecte",
                    "Le lieu \"" + nom + "\" existe deja a l'adresse : " + adresse);
                return;
            }

            // 4. Lat/Lng
            Double lat, lng;
            try {
                lat = Double.valueOf(tfLat.getText().trim());
                lng = Double.valueOf(tfLng.getText().trim());
            } catch (NumberFormatException ex) { setErr(errMap,"Coordonnees invalides"); return; }

            // 5. Collecter horaires
            List<LieuHoraire> horaires = new ArrayList<>();
            for (String jour : JOURS) {
                if (cbOuverts.get(jour).isSelected()) {
                    TextField[] f = horFields.get(jour);
                    LieuHoraire h = new LieuHoraire();
                    h.setJour(jour); h.setOuvert(true);
                    h.setHeureOuverture1(f[0].getText().trim());
                    h.setHeureFermeture1(f[1].getText().trim());
                    h.setHeureOuverture2(f[2].getText().trim());
                    h.setHeureFermeture2(f[3].getText().trim());
                    horaires.add(h);
                }
            }

            // 6. Sauvegarder
            try {
                if (isEdit) {
                    existing.setNom(nom); existing.setVille(ville); existing.setAdresse(adresse);
                    existing.setCategorie(cbCategorie.getValue()); existing.setType(cbType.getValue());
                    existing.setLatitude(lat); existing.setLongitude(lng);
                    existing.setTelephone(safe(tfTel.getText()).trim());
                    existing.setSiteWeb(safe(tfSiteWeb.getText()).trim());
                    existing.setInstagram(safe(tfInsta.getText()).trim());
                    existing.setHoraires(horaires);
                    existing.setImagesPaths(new ArrayList<>(selectedImages));
                    lieuService.update(existing);
                } else {
                    Lieu toAdd = new Lieu();
                    toAdd.setIdOffre(1);
                    toAdd.setNom(nom); toAdd.setVille(ville); toAdd.setAdresse(adresse);
                    toAdd.setCategorie(cbCategorie.getValue()); toAdd.setType(cbType.getValue());
                    toAdd.setLatitude(lat); toAdd.setLongitude(lng);
                    toAdd.setTelephone(safe(tfTel.getText()).trim());
                    toAdd.setSiteWeb(safe(tfSiteWeb.getText()).trim());
                    toAdd.setInstagram(safe(tfInsta.getText()).trim());
                    toAdd.setHoraires(horaires);
                    toAdd.setImagesPaths(new ArrayList<>(selectedImages));
                    lieuService.add(toAdd);
                }
                loadData();
                dialog.close();
            } catch (Exception ex) {
                showError("Erreur","Enregistrement impossible", ex.getMessage());
            }
        });

        updatePreview.run();

        // ---- Shell ----
        Label headline = new Label(isEdit ? "Modifier un lieu" : "Ajouter un lieu");
        headline.getStyleClass().add("dialogTitle");

        SplitPane split = new SplitPane(formScroll, previewCard);
        split.setDividerPositions(0.65);
        split.getStyleClass().add("editorSplit");
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox shell = new VBox(12, headline, split, footer);
        shell.setPadding(new Insets(16));
        shell.getStyleClass().add("dialogRoot");

        Scene scene = new Scene(shell, 1020, 820);
        scene.getStylesheets().add(
            getClass().getResource("/styles/back/sorties-admin.css").toExternalForm()
        );
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    // ======================================================================
    //  VALIDATION
    // ======================================================================

    /** Retourne true si valide, false sinon + affiche l'erreur. */
    private boolean validateNom(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty())    { setErr(err,"Le nom est obligatoire"); return false; }
        if (v.length() < 2) { setErr(err,"Le nom doit avoir au moins 2 caracteres"); return false; }
        if (v.length() > 120){ setErr(err,"Le nom ne peut pas depasser 120 caracteres"); return false; }
        if (!v.matches(".*[a-zA-ZÀ-ÿ].*")) { setErr(err,"Le nom doit contenir des lettres"); return false; }
        setErr(err,""); return true;
    }

    private boolean validateVille(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty())     { setErr(err,"La ville est obligatoire"); return false; }
        if (v.length() < 2)  { setErr(err,"Ville trop courte"); return false; }
        if (v.length() > 80) { setErr(err,"Ville trop longue (max 80 car.)"); return false; }
        if (v.matches(".*\\d.*")) { setErr(err,"La ville ne doit pas contenir de chiffres"); return false; }
        setErr(err,""); return true;
    }

    private boolean validateAdresse(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty())     { setErr(err,"L'adresse est obligatoire"); return false; }
        if (v.length() < 5)  { setErr(err,"Adresse trop courte (min 5 car.)"); return false; }
        if (v.length() > 200){ setErr(err,"Adresse trop longue (max 200 car.)"); return false; }
        setErr(err,""); return true;
    }

    private boolean validateTel(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty()) { setErr(err,""); return true; } // optionnel
        if (!PATTERN_TEL.matcher(v).matches()) {
            setErr(err,"Format invalide. Ex: +216 71 000 000"); return false;
        }
        setErr(err,""); return true;
    }

    private boolean validateSiteWeb(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty()) { setErr(err,""); return true; } // optionnel
        if (!PATTERN_URL.matcher(v).matches()) {
            setErr(err,"Doit commencer par https:// ou http://"); return false;
        }
        setErr(err,""); return true;
    }

    private boolean validateInsta(TextField tf, Label err) {
        String v = safe(tf.getText()).trim();
        if (v.isEmpty()) { setErr(err,""); return true; } // optionnel
        if (!PATTERN_INSTA.matcher(v).matches()) {
            setErr(err,"Format invalide. Ex: @mon_compte (max 30 car.)"); return false;
        }
        setErr(err,""); return true;
    }

    /** Hint global pour le preview live (résumé des erreurs restantes). */
    private String buildGlobalHint(TextField tfNom, TextField tfVille, TextField tfAdresse,
                                   ComboBox<String> cbCat, ComboBox<String> cbType,
                                   TextField tfLat,
                                   TextField tfTel, TextField tfSiteWeb, TextField tfInsta,
                                   Map<String, CheckBox> cbOuverts, Map<String, TextField[]> horFields) {
        List<String> msgs = new ArrayList<>();
        if (safe(tfNom.getText()).trim().isEmpty())     msgs.add("Nom obligatoire");
        if (safe(tfVille.getText()).trim().isEmpty())   msgs.add("Ville obligatoire");
        if (safe(tfAdresse.getText()).trim().isEmpty()) msgs.add("Adresse obligatoire");
        if (cbCat.getValue() == null)  msgs.add("Categorie obligatoire");
        if (cbType.getValue() == null) msgs.add("Type obligatoire");
        if (tfLat.getText().isBlank()) msgs.add("Cliquez sur la carte");
        String tel = safe(tfTel.getText()).trim();
        if (!tel.isEmpty() && !PATTERN_TEL.matcher(tel).matches()) msgs.add("Telephone invalide");
        String site = safe(tfSiteWeb.getText()).trim();
        if (!site.isEmpty() && !PATTERN_URL.matcher(site).matches()) msgs.add("URL site invalide");
        String insta = safe(tfInsta.getText()).trim();
        if (!insta.isEmpty() && !PATTERN_INSTA.matcher(insta).matches()) msgs.add("Instagram invalide");
        return msgs.isEmpty() ? "" : String.join(" • ", msgs);
    }

    /** Affiche ou efface l'erreur sur un label + colore le champ. */
    private void setErr(Label err, String msg) {
        err.setText(msg);
        err.setVisible(!msg.isEmpty());
        err.setManaged(!msg.isEmpty());
    }

    private Label errLabel() {
        Label l = new Label("");
        l.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 0 0 2 2;");
        l.setVisible(false); l.setManaged(false);
        return l;
    }

    /** Colore le champ en rouge quand son label d'erreur a du texte. */
    private void setupFieldStyle(TextField tf, Label err) {
        err.textProperty().addListener((o, ov, nv) -> {
            if (nv != null && !nv.isEmpty()) {
                tf.setStyle("-fx-border-color: #dc2626; -fx-border-width: 2; -fx-border-radius: 14;");
            } else {
                tf.setStyle("");
            }
        });
    }

    // ======================================================================
    //  IMAGES PREVIEW
    // ======================================================================

    private void refreshImages(HBox box, List<String> paths, Label countLabel, Stage owner) {
        box.getChildren().clear();
        int[] idx = {0};
        for (String path : new ArrayList<>(paths)) {
            final int fi = idx[0];
            ImageView iv = new ImageView();
            iv.setFitWidth(72); iv.setFitHeight(72); iv.setPreserveRatio(true);
            try {
                File f = new File(path);
                String uri = f.exists() ? f.toURI().toString() : (path.startsWith("/") ? "file:"+path : path);
                iv.setImage(new Image(uri, true));
            } catch (Exception ignored) {}

            Button btnRm = new Button("X");
            btnRm.setStyle(
                "-fx-font-size:9px;-fx-padding:2 5;" +
                "-fx-background-color:rgba(220,38,38,0.85);" +
                "-fx-text-fill:white;-fx-background-radius:999;-fx-cursor:hand;"
            );
            btnRm.setOnAction(ev -> { paths.remove(fi); refreshImages(box, paths, countLabel, owner); });

            VBox cell = new VBox(3, iv, btnRm); cell.setAlignment(Pos.CENTER);
            cell.setStyle("-fx-background-color:rgba(236,242,248,0.80);-fx-background-radius:12;-fx-padding:6;");
            box.getChildren().add(cell);
            idx[0]++;
        }
        countLabel.setText(paths.size() + " image(s)");
    }

    // ======================================================================
    //  IMAGE LOADING
    // ======================================================================

    private Image loadImageOrFallback(String p) {
        try {
            if (p != null && !p.trim().isEmpty()) {
                String path = p.trim();
                File f = new File(path);
                if (path.startsWith("file:")) return new Image(path, true);
                if (f.exists())              return new Image(f.toURI().toString(), true);
                if (path.startsWith("http")) return new Image(path, true);
                if (path.startsWith("/")) { var u = getClass().getResource(path); if (u != null) return new Image(u.toExternalForm(), true); }
            }
        } catch (Exception ignored) {}
        try { return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), true); }
        catch (Exception e) { return null; }
    }

    private Image loadImageOrNull(String p) {
        try {
            if (p != null && !p.trim().isEmpty()) {
                String path = p.trim();
                File f = new File(path);
                if (path.startsWith("file:")) return new Image(path, true);
                if (f.exists())              return new Image(f.toURI().toString(), true);
                if (path.startsWith("http")) return new Image(path, true);
                if (path.startsWith("/")) { var u = getClass().getResource(path); if (u != null) return new Image(u.toExternalForm(), true); }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ======================================================================
    //  UI HELPERS
    // ======================================================================

    private Label lab(String t)  { Label l = new Label(t); l.getStyleClass().add("formLabel"); return l; }

    private TextField tf2(String val, String prompt, double prefW) {
        TextField t = new TextField(val); t.setPromptText(prompt); t.setPrefWidth(prefW); return t;
    }

    private Separator sep() {
        Separator s = new Separator(); s.setStyle("-fx-background-color:rgba(15,23,42,0.08);"); return s;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String jourLabel(String jour) {
        return switch (jour) {
            case "LUNDI"    -> "Lundi";   case "MARDI"    -> "Mardi";
            case "MERCREDI" -> "Mercredi";case "JEUDI"    -> "Jeudi";
            case "VENDREDI" -> "Vendredi";case "SAMEDI"   -> "Samedi";
            case "DIMANCHE" -> "Dimanche";default -> jour;
        };
    }

    private String extractCity(String displayName) {
        if (displayName == null) return null;
        String[] parts = displayName.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        if (parts.length == 0) return null;
        if (parts.length <= 2) return (parts[0].matches(".*\\d.*") && parts.length>1) ? parts[1] : parts[0];
        return parts[1];
    }

    private boolean confirmDelete(String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirmation"); a.setHeaderText(null); a.setContentText(message);
        return a.showAndWait().map(res -> res == ButtonType.OK).orElse(false);
    }

    private void showWarning(String title, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String title, String header, String details) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(header); a.setContentText(details);
        a.showAndWait();
    }
}
