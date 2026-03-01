package controllers.front.sorties;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.users.User;
import services.sorties.ParticipationSortieService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ParticipationDialogController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label lblTitre;
    @FXML private Label lblMeta;

    @FXML private ToggleButton tbEmail;
    @FXML private ToggleButton tbPhone;

    @FXML private TextField tfEmail;
    @FXML private TextField tfPhone;

    @FXML private Button btnSubmit;

    @FXML private TextArea taComment;

    @FXML private VBox questionsBox;
    @FXML private Label lblError;

    private final ParticipationSortieService participationService = new ParticipationSortieService();

    private Stage dialogStage;
    private User currentUser;
    private AnnonceSortie annonce;

    private boolean placesAvailable = true;

    private final List<TextArea> answerFields = new ArrayList<>();
    private Runnable onSuccess;

    public void setDialogStage(Stage s) { this.dialogStage = s; }
    public void setOnSuccess(Runnable r) { this.onSuccess = r; }

    public void setContext(User user, AnnonceSortie a) {
        this.currentUser = user;
        this.annonce = a;
        render();
    }

    @FXML
    private void initialize() {
        ToggleGroup g = new ToggleGroup();
        tbEmail.setToggleGroup(g);
        tbPhone.setToggleGroup(g);

        tbEmail.setSelected(true);
        tfEmail.setEditable(false);

        tfPhone.textProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            String digits = n.replaceAll("[^0-9]", "");
            if (digits.length() > 8) digits = digits.substring(0, 8);
            if (!digits.equals(n)) tfPhone.setText(digits);
        });
        tbEmail.selectedProperty().addListener((obs, o, n) -> applyContactMode());
        tbPhone.selectedProperty().addListener((obs, o, n) -> applyContactMode());

        lblError.setVisible(false);
        lblError.setManaged(false);

        applyContactMode();
    }

    private void render() {
        if (annonce == null) return;

        lblTitre.setText(safe(annonce.getTitre()));
        String when = (annonce.getDateSortie() == null) ? "—" : DT_FMT.format(annonce.getDateSortie());
        lblMeta.setText("📍 " + safe(annonce.getVille()) + " • " + safe(annonce.getLieuTexte())
                + "    |    📅 " + when
                + "    |    🏷 " + safe(annonce.getTypeActivite()));

        String email = (currentUser == null) ? "" : safe(currentUser.getEmail());
        tfEmail.setText(email);

        // places restantes (basé sur CONFIRMEE)
        placesAvailable = true;
        if (btnSubmit != null) btnSubmit.setDisable(false);

        int total = Math.max(0, annonce.getNbPlaces());
        if (total > 0) {
            int accepted = participationService.countAcceptedPlaces(annonce.getId());
            int remaining = Math.max(0, total - accepted);
            if (remaining <= 0) {
                placesAvailable = false;
                if (btnSubmit != null) btnSubmit.setDisable(true);
                showError("Aucune place disponible.");
            }
        }

        // questions
        questionsBox.getChildren().clear();
        answerFields.clear();

        List<String> qs = annonce.getQuestions();
        if (qs == null || qs.isEmpty()) {
            Label none = new Label("Aucune question.");
            none.getStyleClass().add("hintText");
            questionsBox.getChildren().add(none);
            return;
        }

        for (int i = 0; i < qs.size(); i++) {
            String q = safe(qs.get(i)).trim();
            if (q.isEmpty()) continue;

            Label l = new Label("Q" + (i + 1) + " — " + q);
            l.getStyleClass().add("qLabel");

            TextArea a = new TextArea();
            a.setWrapText(true);
            a.setPrefRowCount(2);
            a.setPromptText("Ta réponse…");
            a.getStyleClass().add("qAnswer");

            VBox block = new VBox(6, l, a);
            block.setPadding(new Insets(8, 10, 10, 10));
            block.getStyleClass().add("qBlock");

            questionsBox.getChildren().add(block);
            answerFields.add(a);
        }
    }

    private void applyContactMode() {
        boolean emailMode = tbEmail.isSelected();

        tfEmail.setVisible(emailMode);
        tfEmail.setManaged(emailMode);

        tfPhone.setVisible(!emailMode);
        tfPhone.setManaged(!emailMode);

        if (emailMode) tfPhone.clear();
    }

    @FXML
    private void submit() {
        hideError();

        if (currentUser == null) { showError("Session non définie."); return; }
        if (annonce == null) { showError("Annonce non définie."); return; }

        if (annonce.getUserId() == currentUser.getId()) {
            showError("Participation impossible sur ta propre annonce.");
            return;
        }
        if (!"OUVERTE".equalsIgnoreCase(safe(annonce.getStatut()))) {
            showError("Annonce non ouverte.");
            return;
        }

        if (participationService.getByAnnonceAndUser(annonce.getId(), currentUser.getId()) != null) {
            showError("Demande déjà envoyée.");
            return;
        }

        if (!placesAvailable) {
            showError("Aucune place disponible.");
            return;
        }

        boolean emailMode = tbEmail.isSelected();
        String contactPrefer = emailMode ? "EMAIL" : "TELEPHONE";
        String contactValue;

        if (emailMode) {
            contactValue = safe(currentUser.getEmail()).trim();
            if (contactValue.isEmpty()) { showError("Email introuvable pour cet utilisateur."); return; }
        } else {
            String digits = safe(tfPhone.getText()).trim().replaceAll("[^0-9]", "");
            if (digits.length() != 8) {
                showError("Téléphone invalide. Format: +216XXXXXXXX (8 chiffres).");
                return;
            }
            contactValue = "+216" + digits;
        }
        int nbPlaces = 1;
        List<String> answers = new ArrayList<>();
        for (TextArea a : answerFields) {
            String s = safe(a.getText()).trim();
            if (s.isEmpty()) {
                a.getStyleClass().remove("invalid");
                a.getStyleClass().add("invalid");
                showError("Réponds à toutes les questions.");
                return;
            }
            a.getStyleClass().remove("invalid");
            answers.add(s);
        }

        ParticipationSortie p = new ParticipationSortie();
        p.setAnnonceId(annonce.getId());
        p.setUserId(currentUser.getId());
        p.setStatut("EN_ATTENTE");
        p.setContactPrefer(contactPrefer);
        p.setContactValue(contactValue);
        p.setCommentaire(safe(taComment.getText()).trim());
        p.setNbPlaces(nbPlaces);
        p.setReponses(answers);

        try {
            participationService.addRequest(p);
            info("Demande envoyée", "Ta participation est enregistrée (EN_ATTENTE).");
            if (onSuccess != null) onSuccess.run();
            close();
        } catch (Exception e) {
            showError(safe(e.getMessage()));
        }
    }

    @FXML
    private void close() {
        if (dialogStage != null) dialogStage.close();
        else {
            Node n = questionsBox;
            if (n != null && n.getScene() != null) {
                Stage s = (Stage) n.getScene().getWindow();
                if (s != null) s.close();
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

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void hideError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        lblError.setText("");
    }

    private String safe(String s) { return s == null ? "" : s; }
}