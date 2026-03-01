package controllers.back.offres;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import models.lieux.Lieu;
import models.offres.CodePromo;
import models.offres.Offre;
import models.users.User;
import services.offres.CodePromoService;
import services.offres.OffreService;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AdminOffreController {

    @FXML private Button btnModeOffres;
    @FXML private Button btnModeCodes;
    @FXML private javafx.scene.layout.VBox sectionOffres;
    @FXML private javafx.scene.layout.VBox sectionCodes;

    @FXML private TableView<Offre> tableOffres;
    @FXML private TableColumn<Offre, Integer> colId;
    @FXML private TableColumn<Offre, String> colLieu;
    @FXML private TableColumn<Offre, String> colTitre;
    @FXML private TableColumn<Offre, String> colType;
    @FXML private TableColumn<Offre, Float> colPourcentage;
    @FXML private TableColumn<Offre, Date> colDateDebut;
    @FXML private TableColumn<Offre, Date> colDateFin;
    @FXML private TableColumn<Offre, String> colStatut;

    @FXML private ComboBox<Lieu> cbLieu;
    @FXML private TextField tfTitre;
    @FXML private TextArea taDescription;
    @FXML private TextField tfType;
    @FXML private TextField tfPourcentage;
    @FXML private DatePicker dpDateDebut;
    @FXML private DatePicker dpDateFin;
    @FXML private ComboBox<String> cbStatut;

    @FXML private TableView<CodePromo> tableCodesPromo;
    @FXML private TableColumn<CodePromo, Integer> colPromoId;
    @FXML private TableColumn<CodePromo, Integer> colPromoOffre;
    @FXML private TableColumn<CodePromo, String> colPromoUser;
    @FXML private TableColumn<CodePromo, Date> colPromoDateGen;
    @FXML private TableColumn<CodePromo, Date> colPromoDateExp;
    @FXML private TableColumn<CodePromo, String> colPromoStatut;
    @FXML private TableColumn<CodePromo, String> colPromoQr;

    @FXML private TextField tfPromoId;
    @FXML private TextField tfPromoOffreId;
    @FXML private TextField tfPromoUserId;
    @FXML private TextField tfPromoQr;
    @FXML private DatePicker dpPromoDateGen;
    @FXML private DatePicker dpPromoDateExp;
    @FXML private ComboBox<String> cbPromoStatut;

    private final OffreService offreService = new OffreService();
    private final CodePromoService codePromoService = new CodePromoService();
    private final ObservableList<Offre> offresList = FXCollections.observableArrayList();
    private final ObservableList<CodePromo> codesPromoList = FXCollections.observableArrayList();
    private final ObservableList<Lieu> lieuxList = FXCollections.observableArrayList();
    private final Map<Integer, String> lieuxLabelById = new HashMap<>();
    private User currentUser;

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    @FXML
    public void initialize() {
        initOffreTable();
        initPromoTable();
        initForm();
        initPromoForm();
        loadLieux();
        loadOffres();
        loadCodesPromo();
        showOffresMode();

        tableOffres.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (selected != null) {
                fillForm(selected);
            }
        });

        tableCodesPromo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (selected != null) {
                fillPromoForm(selected);
            }
        });
    }

    @FXML
    private void onShowOffresMode() {
        showOffresMode();
    }

    @FXML
    private void onShowCodesMode() {
        showCodesMode();
    }

    @FXML
    private void onAjouter() {
        try {
            Offre offre = buildOffreFromForm(null);
            offreService.ajouter(offre);
            showInfo("Succès", "Offre ajoutée avec succès.");
            loadOffres();
            clearForm();
        } catch (IllegalArgumentException ex) {
            showError("Validation", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur SQL", ex.getMessage());
        } catch (Exception ex) {
            showError("Erreur", ex.getMessage());
        }
    }

    @FXML
    private void onModifier() {
        Offre selected = tableOffres.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Information", "Sélectionnez une offre à modifier.");
            return;
        }

        try {
            Offre updated = buildOffreFromForm(selected);
            offreService.modifier(updated);
            showInfo("Succès", "Offre modifiée avec succès.");
            loadOffres();
            clearForm();
        } catch (IllegalArgumentException ex) {
            showError("Validation", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur SQL", ex.getMessage());
        } catch (Exception ex) {
            showError("Erreur", ex.getMessage());
        }
    }

    @FXML
    private void onSupprimer() {
        Offre selected = tableOffres.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Information", "Sélectionnez une offre à supprimer.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer l'offre: " + safe(selected.getTitre()) + " ?");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        try {
            offreService.supprimer(selected.getId());
            showInfo("Succès", "Offre supprimée avec succès.");
            loadOffres();
            clearForm();
        } catch (IllegalArgumentException ex) {
            showError("Validation", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur SQL", ex.getMessage());
        }
    }

    @FXML
    private void onReset() {
        clearForm();
    }

    private void initOffreTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colPourcentage.setCellValueFactory(new PropertyValueFactory<>("pourcentage"));
        colDateDebut.setCellValueFactory(new PropertyValueFactory<>("date_debut"));
        colDateFin.setCellValueFactory(new PropertyValueFactory<>("date_fin"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colLieu.setCellValueFactory(cell -> {
            int lieuId = cell.getValue().getLieu_id();
            String label = lieuxLabelById.getOrDefault(lieuId, "Lieu #" + lieuId);
            return new ReadOnlyStringWrapper(label);
        });

        tableOffres.setItems(offresList);
        tableOffres.setPlaceholder(new Label("Aucune offre à afficher"));
    }

    private void initPromoTable() {
        colPromoId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colPromoOffre.setCellValueFactory(new PropertyValueFactory<>("offre_id"));
        colPromoUser.setCellValueFactory(cell -> {
            int userId = cell.getValue().getUser_id();
            String txt = userId > 0 ? "User #" + userId : "-";
            return new ReadOnlyStringWrapper(txt);
        });
        colPromoDateGen.setCellValueFactory(new PropertyValueFactory<>("date_generation"));
        colPromoDateExp.setCellValueFactory(new PropertyValueFactory<>("date_expiration"));
        colPromoStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colPromoQr.setCellValueFactory(cell -> new ReadOnlyStringWrapper(abbreviate(cell.getValue().getQr_image_url(), 60)));

        tableCodesPromo.setItems(codesPromoList);
        tableCodesPromo.setPlaceholder(new Label("Aucun code promo généré"));
    }

    private void initForm() {
        cbLieu.setItems(lieuxList);
        cbStatut.getItems().setAll("active", "inactive", "expirée");
        cbStatut.setValue("active");
    }

    private void initPromoForm() {
        cbPromoStatut.getItems().setAll("ACTIF", "EXPIRE", "DESACTIVE");
        cbPromoStatut.setValue("ACTIF");
    }

    private void loadLieux() {
        try {
            lieuxList.setAll(offreService.obtenirTousLesLieux());
            lieuxLabelById.clear();
            for (Lieu lieu : lieuxList) {
                lieuxLabelById.put(lieu.getId(), lieu.toString());
            }
        } catch (SQLException e) {
            showError("Erreur SQL", "Impossible de charger les lieux: " + e.getMessage());
        }
    }

    private void loadOffres() {
        try {
            offresList.setAll(offreService.obtenirToutes());
            tableOffres.refresh();
        } catch (SQLException e) {
            showError("Erreur SQL", "Impossible de charger les offres: " + e.getMessage());
        }
    }

    private void loadCodesPromo() {
        try {
            codesPromoList.setAll(codePromoService.obtenirTousCodesPromo());
            tableCodesPromo.refresh();
        } catch (SQLException e) {
            showError("Erreur SQL", "Impossible de charger les codes promo: " + e.getMessage());
        }
    }

    private void fillForm(Offre offre) {
        tfTitre.setText(safe(offre.getTitre()));
        taDescription.setText(safe(offre.getDescription()));
        tfType.setText(safe(offre.getType()));
        tfPourcentage.setText(String.valueOf(offre.getPourcentage()));
        cbStatut.setValue(safe(offre.getStatut()).isBlank() ? "active" : offre.getStatut());

        LocalDate start = offre.getDate_debut() != null ? offre.getDate_debut().toLocalDate() : null;
        LocalDate end = offre.getDate_fin() != null ? offre.getDate_fin().toLocalDate() : null;
        dpDateDebut.setValue(start);
        dpDateFin.setValue(end);

        Lieu selectedLieu = lieuxList.stream()
                .filter(l -> l.getId() == offre.getLieu_id())
                .findFirst()
                .orElse(null);
        cbLieu.setValue(selectedLieu);
    }

    private Offre buildOffreFromForm(Offre existing) {
        Lieu lieu = cbLieu.getValue();
        if (lieu == null) {
            throw new IllegalArgumentException("Le lieu est obligatoire.");
        }

        String titre = safe(tfTitre.getText()).trim();
        String description = safe(taDescription.getText()).trim();
        String type = safe(tfType.getText()).trim();
        String pourcentageStr = safe(tfPourcentage.getText()).trim().replace(',', '.');
        String statut = safe(cbStatut.getValue()).trim();

        if (titre.isEmpty()) {
            throw new IllegalArgumentException("Le titre est obligatoire.");
        }
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Le type d'offre est obligatoire.");
        }

        float pourcentage;
        try {
            pourcentage = Float.parseFloat(pourcentageStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Le pourcentage doit être un nombre valide.");
        }

        LocalDate dateDebut = dpDateDebut.getValue();
        LocalDate dateFin = dpDateFin.getValue();
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates début et fin sont obligatoires.");
        }

        Offre offre = new Offre();
        if (existing != null) {
            offre.setId(existing.getId());
            offre.setUser_id(existing.getUser_id());
            offre.setEvent_id(existing.getEvent_id());
        } else {
            if (currentUser == null || currentUser.getId() <= 0) {
                throw new IllegalArgumentException("Impossible d'identifier l'utilisateur connecté.");
            }
            offre.setUser_id(currentUser.getId());
            offre.setEvent_id(0);
        }
        offre.setLieu_id(lieu.getId());
        offre.setTitre(titre);
        offre.setDescription(description);
        offre.setType(type);
        offre.setPourcentage(pourcentage);
        offre.setDate_debut(Date.valueOf(dateDebut));
        offre.setDate_fin(Date.valueOf(dateFin));
        offre.setStatut(statut);
        return offre;
    }

    @FXML
    private void onModifierCodePromo() {
        CodePromo selected = tableCodesPromo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Information", "Sélectionnez un code promo à modifier.");
            return;
        }

        try {
            CodePromo updated = buildCodePromoFromForm(selected);
            codePromoService.modifierCodePromo(updated);
            showInfo("Succès", "Code promo modifié avec succès.");
            loadCodesPromo();
            clearPromoForm();
        } catch (IllegalArgumentException ex) {
            showError("Validation", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur SQL", ex.getMessage());
        }
    }

    @FXML
    private void onSupprimerCodePromo() {
        CodePromo selected = tableCodesPromo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Information", "Sélectionnez un code promo à supprimer.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer le code promo #" + selected.getId() + " ?");

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }

        try {
            codePromoService.supprimerCodePromo(selected.getId());
            showInfo("Succès", "Code promo supprimé avec succès.");
            loadCodesPromo();
            clearPromoForm();
        } catch (SQLException ex) {
            showError("Erreur SQL", ex.getMessage());
        }
    }

    @FXML
    private void onResetCodePromo() {
        clearPromoForm();
    }

    private void fillPromoForm(CodePromo promo) {
        tfPromoId.setText(String.valueOf(promo.getId()));
        tfPromoOffreId.setText(String.valueOf(promo.getOffre_id()));
        tfPromoUserId.setText(promo.getUser_id() > 0 ? String.valueOf(promo.getUser_id()) : "");
        tfPromoQr.setText(safe(promo.getQr_image_url()));
        dpPromoDateGen.setValue(promo.getDate_generation() != null ? promo.getDate_generation().toLocalDate() : null);
        dpPromoDateExp.setValue(promo.getDate_expiration() != null ? promo.getDate_expiration().toLocalDate() : null);
        cbPromoStatut.setValue(safe(promo.getStatut()).isBlank() ? "actif" : promo.getStatut());
    }

    private CodePromo buildCodePromoFromForm(CodePromo existing) {
        int offreId = parsePositiveInt(tfPromoOffreId.getText(), "ID offre invalide.");
        int userId = parseOptionalInt(tfPromoUserId.getText());
        String qr = safe(tfPromoQr.getText()).trim();
        String statut = safe(cbPromoStatut.getValue()).trim();

        LocalDate gen = dpPromoDateGen.getValue();
        LocalDate exp = dpPromoDateExp.getValue();
        if (gen == null || exp == null) {
            throw new IllegalArgumentException("Dates génération/expiration obligatoires.");
        }

        CodePromo promo = new CodePromo();
        promo.setId(existing.getId());
        promo.setOffre_id(offreId);
        promo.setUser_id(userId);
        promo.setQr_image_url(qr);
        promo.setDate_generation(Date.valueOf(gen));
        promo.setDate_expiration(Date.valueOf(exp));
        promo.setStatut(statut);
        return promo;
    }

    private void clearForm() {
        tableOffres.getSelectionModel().clearSelection();
        cbLieu.setValue(null);
        tfTitre.clear();
        taDescription.clear();
        tfType.clear();
        tfPourcentage.clear();
        dpDateDebut.setValue(null);
        dpDateFin.setValue(null);
        cbStatut.setValue("active");
    }

    private void clearPromoForm() {
        tableCodesPromo.getSelectionModel().clearSelection();
        tfPromoId.clear();
        tfPromoOffreId.clear();
        tfPromoUserId.clear();
        tfPromoQr.clear();
        dpPromoDateGen.setValue(null);
        dpPromoDateExp.setValue(null);
        cbPromoStatut.setValue("ACTIF");
    }

    private void showOffresMode() {
        sectionOffres.setVisible(true);
        sectionOffres.setManaged(true);
        sectionCodes.setVisible(false);
        sectionCodes.setManaged(false);

        setModeButtonActive(btnModeOffres, true);
        setModeButtonActive(btnModeCodes, false);
    }

    private void showCodesMode() {
        sectionOffres.setVisible(false);
        sectionOffres.setManaged(false);
        sectionCodes.setVisible(true);
        sectionCodes.setManaged(true);

        setModeButtonActive(btnModeOffres, false);
        setModeButtonActive(btnModeCodes, true);
    }

    private void setModeButtonActive(Button button, boolean active) {
        if (button == null) return;
        button.getStyleClass().remove("mode-btn-active");
        if (active) {
            button.getStyleClass().add("mode-btn-active");
        }
    }

    private int parsePositiveInt(String txt, String errMessage) {
        try {
            int val = Integer.parseInt(safe(txt).trim());
            if (val <= 0) throw new NumberFormatException();
            return val;
        } catch (Exception e) {
            throw new IllegalArgumentException(errMessage);
        }
    }

    private int parseOptionalInt(String txt) {
        String v = safe(txt).trim();
        if (v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("ID utilisateur invalide.");
        }
    }

    private String abbreviate(String value, int max) {
        String v = safe(value);
        if (v.length() <= max) return v;
        return v.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
