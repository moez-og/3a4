package controllers.front.sorties;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.users.User;
import services.sorties.AnnonceSortieService;
import services.sorties.ParticipationSortieService;
import services.users.UserService;
import utils.ui.ShellNavigator;

import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SortieDetailsController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    @FXML private StackPane heroBox;
    @FXML private ImageView heroImage;

    @FXML private Label title;
    @FXML private Label chipStatut;
    @FXML private Label chipActivite;
    @FXML private Label dateText;

    @FXML private Label locationLine;
    @FXML private Label meetingLine;

    @FXML private Label infoDate;
    @FXML private Label infoBudget;
    @FXML private Label infoPlaces;
    @FXML private Label infoStatut;
    @FXML private Label infoOrganisateur;

    @FXML private Label description;
    @FXML private FlowPane questionsPane;
    @FXML private Label questionsEmpty;

    @FXML private Button btnParticiper;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    private Stage primaryStage;
    private ShellNavigator navigator;
    private User currentUser;

    private int sortieId = -1;
    private AnnonceSortie current;

    private final AnnonceSortieService service = new AnnonceSortieService();
    private final UserService userService = new UserService();
    private final ParticipationSortieService participationService = new ParticipationSortieService();

    private final Rectangle heroClip = new Rectangle();

    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }
    public void setNavigator(ShellNavigator nav) { this.navigator = nav; }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        applyControls();
    }

    public void setSortieId(int id) {
        this.sortieId = id;
        load();
    }

    @FXML
    private void initialize() {
        setupHeroCover();

        if (title != null) title.setText("Détails");
        if (chipStatut != null) chipStatut.setText("—");
        if (chipActivite != null) chipActivite.setText("—");
        if (dateText != null) dateText.setText("—");
        if (description != null) description.setText("");

        if (questionsEmpty != null) {
            questionsEmpty.setVisible(false);
            questionsEmpty.setManaged(false);
        }

        applyControls();
    }

    private void setupHeroCover() {
        if (heroImage == null) return;

        heroImage.setPreserveRatio(true);
        heroImage.setSmooth(true);
        heroImage.setCache(true);

        // petit rendu plus premium (léger)
        ColorAdjust ca = new ColorAdjust();
        ca.setContrast(0.06);
        ca.setSaturation(0.06);
        ca.setBrightness(-0.02);
        heroImage.setEffect(ca);

        if (heroBox != null) {
            heroClip.setArcWidth(28);
            heroClip.setArcHeight(28);
            heroBox.setClip(heroClip);

            heroBox.layoutBoundsProperty().addListener((obs, o, b) -> {
                heroClip.setWidth(b.getWidth());
                heroClip.setHeight(b.getHeight());
                Platform.runLater(this::applyCoverSizing);
            });
        }

        heroImage.imageProperty().addListener((obs, o, n) -> Platform.runLater(this::applyCoverSizing));
        Platform.runLater(this::applyCoverSizing);
    }

    private void applyCoverSizing() {
        if (heroBox == null || heroImage == null) return;
        Image img = heroImage.getImage();
        if (img == null) return;

        double paneW = heroBox.getWidth();
        double paneH = heroBox.getHeight();
        if (paneW <= 2 || paneH <= 2) return;

        double imgW = img.getWidth();
        double imgH = img.getHeight();
        if (imgW <= 2 || imgH <= 2) return;

        double scaleW = paneW / imgW;
        double scaleH = paneH / imgH;

        // cover = max(scaleW, scaleH)
        if (scaleW >= scaleH) {
            heroImage.setFitWidth(paneW);
            heroImage.setFitHeight(0);
        } else {
            heroImage.setFitHeight(paneH);
            heroImage.setFitWidth(0);
        }

        // petit zoom doux (stable)
        heroImage.setScaleX(1.03);
        heroImage.setScaleY(1.03);
    }

    @FXML
    private void goBack() {
        if (navigator != null) {
            navigator.navigate("sorties");
            return;
        }
        if (primaryStage != null) primaryStage.close();
    }

    @FXML
    private void edit() {
        if (current == null) return;
        if (navigator != null) navigator.navigate("sortie-edit:" + current.getId());
    }

    @FXML
    private void delete() {
        if (current == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Suppression");
        a.setHeaderText("Supprimer cette annonce ?");
        a.setContentText("Cette action est définitive.");

        var r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        try {
            service.delete(current.getId());
            if (navigator != null) navigator.navigate("sorties");
        } catch (Exception ex) {
            error("Erreur", "Suppression impossible", safe(ex.getMessage()));
        }
    }

    @FXML
    private void participer() {
        if (current == null || currentUser == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/front/sorties/ParticipationDialogView.fxml"));
            Parent root = loader.load();

            ParticipationDialogController c = loader.getController();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            if (heroImage != null && heroImage.getScene() != null) {
                dialog.initOwner(heroImage.getScene().getWindow());
            }
            dialog.setTitle("Participation");
            dialog.setScene(new Scene(root));

            c.setDialogStage(dialog);
            c.setContext(currentUser, current);
            c.setOnSuccess(this::load);

            dialog.showAndWait();

        } catch (Exception e) {
            error("Erreur", "Ouverture impossible", safe(e.getMessage()));
        }
    }

    @FXML
    private void copyInfos() {
        if (current == null) return;

        String when = (current.getDateSortie() == null) ? "—" : DT_FMT.format(current.getDateSortie());
        String budget = (current.getBudgetMax() <= 0)
                ? "Aucun budget"
                : (MONEY_FMT.format(current.getBudgetMax()) + " TND max");

        String txt = "Annonce de sortie\n" +
                "Titre: " + safe(current.getTitre()) + "\n" +
                "Ville: " + safe(current.getVille()) + "\n" +
                "Région: " + safe(current.getLieuTexte()) + "\n" +
                "Activité: " + safe(current.getTypeActivite()) + "\n" +
                "Date: " + when + "\n" +
                "Budget: " + budget + "\n" +
                "Places: " + current.getNbPlaces() + "\n" +
                "Point de rencontre: " + safe(current.getPointRencontre());

        ClipboardContent c = new ClipboardContent();
        c.putString(txt);
        Clipboard.getSystemClipboard().setContent(c);

        info("Copié", "Les infos ont été copiées.");
    }

    private void load() {
        if (sortieId <= 0) return;
        try {
            current = service.getById(sortieId);
            if (current == null) {
                error("Introuvable", "Annonce introuvable", "ID: " + sortieId);
                return;
            }
            render(current);
            applyControls();
        } catch (Exception e) {
            error("Erreur", "Chargement impossible", safe(e.getMessage()));
        }
    }

    private void render(AnnonceSortie a) {
        if (heroImage != null) {
            heroImage.setImage(loadImageOrFallback(a.getImageUrl()));
            Platform.runLater(this::applyCoverSizing);
        }

        if (title != null) title.setText(safe(a.getTitre()));

        String st = safe(a.getStatut());
        if (chipStatut != null) {
            chipStatut.setText(st.isEmpty() ? "—" : st);
            chipStatut.getStyleClass().removeIf(s -> s.startsWith("status-"));
            if (!st.isEmpty()) chipStatut.getStyleClass().add("status-" + st.toLowerCase());
        }

        if (chipActivite != null) chipActivite.setText(safe(a.getTypeActivite()));

        String when = (a.getDateSortie() == null) ? "—" : DT_FMT.format(a.getDateSortie());
        if (dateText != null) dateText.setText("📅 " + when);

        if (locationLine != null) {
            String loc = (safe(a.getVille()) + " • " + safe(a.getLieuTexte())).trim();
            locationLine.setText("📍 " + loc);
        }
        if (meetingLine != null) meetingLine.setText("🤝 " + safe(a.getPointRencontre()));

        if (infoDate != null) infoDate.setText(when);

        String budget = (a.getBudgetMax() <= 0)
                ? "Aucun budget"
                : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");

        if (infoBudget != null) infoBudget.setText(budget);
        if (infoPlaces != null) infoPlaces.setText(a.getNbPlaces() + " place(s)");
        if (infoStatut != null) infoStatut.setText(st.isEmpty() ? "—" : st);

        String org = "Utilisateur #" + a.getUserId();
        try {
            User u = userService.trouverParId(a.getUserId());
            if (u != null) {
                String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                if (!full.isEmpty()) org = full;
            }
        } catch (Exception ignored) {}
        if (infoOrganisateur != null) infoOrganisateur.setText(org);

        String desc = safe(a.getDescription()).trim();
        if (description != null) description.setText(desc.isEmpty() ? "Aucune description." : desc);

        if (questionsPane != null) {
            questionsPane.getChildren().clear();
            List<String> qs = a.getQuestions();

            if (qs != null) {
                for (String q : qs) {
                    String s = safe(q).trim();
                    if (s.isEmpty()) continue;

                    Label chip = new Label("❓ " + s);
                    chip.getStyleClass().add("qChip");
                    chip.setPadding(new Insets(8, 10, 8, 10));
                    questionsPane.getChildren().add(chip);
                }
            }

            boolean realEmpty = questionsPane.getChildren().isEmpty();
            if (questionsEmpty != null) {
                questionsEmpty.setVisible(realEmpty);
                questionsEmpty.setManaged(realEmpty);
            }
        }
    }

    private void applyControls() {
        boolean owner = (currentUser != null && current != null && current.getUserId() == currentUser.getId());

        if (btnEdit != null) { btnEdit.setVisible(owner); btnEdit.setManaged(owner); }
        if (btnDelete != null) { btnDelete.setVisible(owner); btnDelete.setManaged(owner); }

        boolean canParticipate = (currentUser != null && current != null
                && !owner
                && "OUVERTE".equalsIgnoreCase(safe(current.getStatut())));

        if (btnParticiper != null) {
            btnParticiper.setVisible(canParticipate);
            btnParticiper.setManaged(canParticipate);

            if (canParticipate) {
                ParticipationSortie ex = null;
                try {
                    ex = participationService.getByAnnonceAndUser(current.getId(), currentUser.getId());
                } catch (Exception ignored) {}

                if (ex != null) {
                    btnParticiper.setDisable(true);
                    btnParticiper.setText("Demande envoyée");
                } else {
                    btnParticiper.setDisable(false);
                    btnParticiper.setText("Participer");
                }
            }
        }
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void error(String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private Image loadImageOrFallback(String pathOrUrl) {
        Image im = loadImageOrNull(pathOrUrl);
        if (im != null) return im;
        return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), true);
    }

    private Image loadImageOrNull(String pathOrUrl) {
        try {
            if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return null;
            String p = pathOrUrl.trim();

            File f = new File(p);
            if (p.startsWith("file:")) return new Image(p, true);
            if (f.exists()) return new Image(f.toURI().toString(), true);
            if (p.startsWith("http://") || p.startsWith("https://")) return new Image(p, true);
            if (p.startsWith("/")) {
                var u = getClass().getResource(p);
                if (u != null) return new Image(u.toExternalForm(), true);
            }
        } catch (Exception ignored) {}
        return null;
    }
}