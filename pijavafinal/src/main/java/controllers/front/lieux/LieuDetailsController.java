package controllers.front.lieux;

import controllers.front.shell.FrontDashboardController;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import models.lieux.EvaluationLieu;
import models.lieux.Lieu;
import models.users.User;
import services.lieux.EvaluationLieuService;
import services.lieux.LieuService;
import utils.ui.ShellNavigator;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
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

    private final LieuService lieuService;

    {
        try {
            lieuService = new LieuService();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private final EvaluationLieuService evalService = new EvaluationLieuService();

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

        TextArea area = new TextArea(existing == null ? "" : safe(existing.getCommentaire()));
        area.setPromptText("Écrire un commentaire...");
        area.setWrapText(true);
        area.setPrefRowCount(4);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        grid.add(new Label("Note"), 0, 0);
        HBox ratingRow = new HBox(10, starsBox, noteValue);
        grid.add(ratingRow, 1, 0);
        grid.add(new Label("Commentaire"), 0, 1);
        grid.add(area, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // boutons stylés
        Node okBtn = dialog.getDialogPane().lookupButton(save);
        if (okBtn != null) okBtn.getStyleClass().add("primaryBtn");
        Node cancelBtn = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("ghostBtn");

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