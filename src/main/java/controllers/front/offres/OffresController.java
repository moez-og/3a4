package controllers.front.offres;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.offres.CodePromo;
import models.offres.Offre;
import models.users.User;
import services.offres.CodePromoService;
import services.offres.OffreService;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.layout.BorderPane;

public class OffresController {

    @FXML private BorderPane offresPane;
    @FXML private ComboBox<String> filtreLieuCombo;
    @FXML private VBox offersListBox;

    @FXML private Label detailTitre;
    @FXML private Label detailLieu;
    @FXML private Label detailType;
    @FXML private Label detailPourcentage;
    @FXML private Label detailDates;
    @FXML private Label detailStatut;
    @FXML private Label detailDescription;
    @FXML private Label promoInfo;
    @FXML private Button btnGetPromo;
    @FXML private ImageView qrImageView;

    private ShellNavigator navigator;
    private User currentUser;
    private final OffreService offreService = new OffreService();
    private final CodePromoService codePromoService = new CodePromoService();

    private final List<Offre> allOffres = new ArrayList<>();
    private Map<Integer, String> lieuxMap;
    private Offre selectedOffre;

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    @FXML
    private void initialize() {
        filtreLieuCombo.getItems().setAll("Tous les lieux");
        filtreLieuCombo.setValue("Tous les lieux");
        filtreLieuCombo.valueProperty().addListener((obs, oldV, newV) -> renderOffersList());
        loadData();
    }

    @FXML
    private void onOpenAllOffers() {
        filtreLieuCombo.setValue("Tous les lieux");
        renderOffersList();
    }

    @FXML
    private void onGetPromo() {
        if (selectedOffre == null) {
            showInfo("Offre", "Sélectionne une offre d'abord.");
            return;
        }

        if (currentUser == null || currentUser.getId() <= 0) {
            showInfo("Authentification requise", "Tu dois être connecté pour obtenir un code promo.");
            return;
        }

        String role = safe(currentUser.getRole()).trim().toLowerCase();
        if (!"abonne".equals(role)) {
            showInfo("Accès refusé", "Seul un utilisateur abonné peut obtenir un code promo.");
            return;
        }

        try {
            CodePromo promo = codePromoService.genererOuRecupererCodePromo(selectedOffre.getId(), currentUser.getId());
            promoInfo.setText("Code promo actif · Expire le " + formatDate(promo.getDate_expiration()));
            qrImageView.setImage(new Image(promo.getQr_image_url(), true));
            qrImageView.setVisible(true);
        } catch (Exception e) {
            showError("Code Promo", e.getMessage());
        }
    }

    private void loadData() {
        try {
            lieuxMap = offreService.obtenirMapNomsLieux();

            List<Offre> dispo = offreService.obtenirOffresDisponibles();
            allOffres.clear();
            allOffres.addAll(dispo);

            for (String lieu : lieuxMap.values()) {
                if (!filtreLieuCombo.getItems().contains(lieu)) {
                    filtreLieuCombo.getItems().add(lieu);
                }
            }

            Integer selectedLieuId = FrontOfferContext.consumeSelectedLieuId();
            if (selectedLieuId != null && lieuxMap.containsKey(selectedLieuId)) {
                filtreLieuCombo.setValue(lieuxMap.get(selectedLieuId));
            }

            renderOffersList();

            Integer selectedOffreId = FrontOfferContext.consumeSelectedOffreId();
            if (selectedOffreId != null) {
                for (Offre o : allOffres) {
                    if (o.getId() == selectedOffreId) {
                        selectOffre(o);
                        break;
                    }
                }
            }

            if (selectedOffre == null && !allOffres.isEmpty()) {
                selectOffre(allOffres.get(0));
            }
        } catch (SQLException e) {
            showError("Offres", "Impossible de charger les offres: " + e.getMessage());
        }
    }

    private void renderOffersList() {
        offersListBox.getChildren().clear();
        String filter = safe(filtreLieuCombo.getValue());

        for (Offre offre : allOffres) {
            String lieuName = lieuxMap.getOrDefault(offre.getLieu_id(), "Lieu #" + offre.getLieu_id());
            if (!"Tous les lieux".equals(filter) && !lieuName.equals(filter)) {
                continue;
            }
            offersListBox.getChildren().add(buildOffreCard(offre, lieuName));
        }

        if (offersListBox.getChildren().isEmpty()) {
            Label empty = new Label("Aucune offre disponible pour ce lieu.");
            empty.getStyleClass().add("offres-empty");
            offersListBox.getChildren().add(empty);
        }
    }

    private HBox buildOffreCard(Offre offre, String lieuName) {
        Label title = new Label(safe(offre.getTitre()));
        title.getStyleClass().add("offre-item-title");

        Label meta = new Label(safe(offre.getType()) + " · " + formatPercent(offre.getPourcentage()) + " · " + lieuName);
        meta.getStyleClass().add("offre-item-meta");

        VBox texts = new VBox(3, title, meta);

        Button btn = new Button("Voir Offre");
        btn.getStyleClass().add("offre-see-btn");
        btn.setOnAction(e -> selectOffre(offre));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, texts, spacer, btn);
        row.getStyleClass().add("offre-item");
        row.setOnMouseClicked(e -> selectOffre(offre));
        return row;
    }

    private void selectOffre(Offre offre) {
        selectedOffre = offre;
        String lieuName = lieuxMap.getOrDefault(offre.getLieu_id(), "Lieu #" + offre.getLieu_id());

        detailTitre.setText(safe(offre.getTitre()));
        detailLieu.setText("Lieu: " + lieuName);
        detailType.setText("Type: " + safe(offre.getType()));
        detailPourcentage.setText("Réduction: " + formatPercent(offre.getPourcentage()));
        detailDates.setText("Validité: " + formatDate(offre.getDate_debut()) + " → " + formatDate(offre.getDate_fin()));
        detailStatut.setText("Statut: " + safe(offre.getStatut()));
        detailDescription.setText(safe(offre.getDescription()));

        promoInfo.setText("Aucun code promo généré.");
        qrImageView.setImage(null);
        qrImageView.setVisible(false);
        btnGetPromo.setDisable(false);
    }

    private String formatPercent(float p) {
        if (Math.floor(p) == p) return ((int) p) + "%";
        return p + "%";
    }

    private String formatDate(java.sql.Date d) {
        if (d == null) return "-";
        LocalDate l = d.toLocalDate();
        return l.getDayOfMonth() + "/" + l.getMonthValue() + "/" + l.getYear();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }
}
