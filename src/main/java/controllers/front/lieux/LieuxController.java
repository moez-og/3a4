package controllers.front.lieux;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import models.lieux.Lieu;
import models.offres.Offre;
import services.offres.OffreService;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LieuxController {

    @FXML private BorderPane lieuxPane;
    @FXML private VBox lieuxListBox;
    @FXML private Label lieuTitle;
    @FXML private Label lieuMeta;
    @FXML private VBox lieuOffersBox;

    private final OffreService offreService = new OffreService();
    private ShellNavigator navigator;

    private List<Lieu> lieux = new ArrayList<>();
    private Lieu selectedLieu;

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        loadLieux();
    }

    private void loadLieux() {
        try {
            lieux = offreService.obtenirTousLesLieux();
            lieuxListBox.getChildren().clear();
            for (Lieu lieu : lieux) {
                lieuxListBox.getChildren().add(buildLieuCard(lieu));
            }

            if (!lieux.isEmpty()) {
                selectLieu(lieux.get(0));
            }
        } catch (SQLException e) {
            showError("Lieux", "Impossible de charger les lieux: " + e.getMessage());
        }
    }

    private HBox buildLieuCard(Lieu lieu) {
        Label name = new Label(lieu.toString());
        name.getStyleClass().add("offre-item-title");

        Button btn = new Button("Voir les offres");
        btn.getStyleClass().add("offre-see-btn");
        btn.setOnAction(e -> openOffersForLieu(lieu));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, name, spacer, btn);
        row.getStyleClass().add("offre-item");
        row.setOnMouseClicked(e -> selectLieu(lieu));
        return row;
    }

    private void selectLieu(Lieu lieu) {
        selectedLieu = lieu;
        lieuTitle.setText(lieu.toString());
        lieuMeta.setText("Lieu partenaire disponible pour consultation des offres");

        lieuOffersBox.getChildren().clear();
        try {
            List<Offre> offres = offreService.obtenirOffresParLieu(lieu.getId());
            if (offres.isEmpty()) {
                Label empty = new Label("Aucune offre liée à ce lieu.");
                empty.getStyleClass().add("offres-empty");
                lieuOffersBox.getChildren().add(empty);
                return;
            }

            for (Offre offre : offres) {
                lieuOffersBox.getChildren().add(buildOffreItem(offre));
            }
        } catch (SQLException e) {
            showError("Offres", "Impossible de charger les offres du lieu: " + e.getMessage());
        }
    }

    private HBox buildOffreItem(Offre offre) {
        Label title = new Label(offre.getTitre() + " · -" + formatPercent(offre.getPourcentage()));
        title.getStyleClass().add("offre-item-meta");

        Button btn = new Button("Voir Offre");
        btn.getStyleClass().add("offre-see-btn");
        btn.setOnAction(e -> openOffer(offre));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, title, spacer, btn);
        row.getStyleClass().add("offre-item");
        return row;
    }

    private void openOffersForLieu(Lieu lieu) {
        FrontOfferContext.setSelectedLieuId(lieu.getId());
        if (navigator != null) {
            navigator.navigate("offres");
            return;
        }
        showInfo("Navigation", "Route offres indisponible pour le moment.");
    }

    private void openOffer(Offre offre) {
        FrontOfferContext.setSelectedLieuId(selectedLieu != null ? selectedLieu.getId() : null);
        FrontOfferContext.setSelectedOffreId(offre.getId());
        if (navigator != null) {
            navigator.navigate("offres");
            return;
        }
        showInfo("Navigation", "Route offres indisponible pour le moment.");
    }

    private String formatPercent(float p) {
        if (Math.floor(p) == p) return ((int) p) + "%";
        return p + "%";
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
