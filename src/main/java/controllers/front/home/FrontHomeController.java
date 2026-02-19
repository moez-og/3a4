package controllers.front.home;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import models.offres.Offre;
import services.offres.OffreService;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.util.List;
import java.util.Map;

public class FrontHomeController {

    private static final double CARD_RADIUS = 22;

    @FXML private TextField heroSearchField;
    @FXML private HBox citiesRow;

    @FXML private StackPane recoSpotCard;
    @FXML private StackPane recoOfferCard;
    @FXML private StackPane recoSortieCard;
    @FXML private HBox offresRow;

    private ShellNavigator navigator;
    private final OffreService offreService = new OffreService();

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    private record City(String name, String meta, String imagePath) {}

    @FXML
    private void initialize() {
        buildTrendingCities();
        buildHomeOffers();
        setupRecoCards();
        animateEntrance();
    }

    private void buildHomeOffers() {
        if (offresRow == null) return;
        offresRow.getChildren().clear();

        try {
            List<Offre> offres = offreService.obtenirOffresDisponibles();
            Map<Integer, String> lieuxMap = offreService.obtenirMapNomsLieux();

            int max = Math.min(offres.size(), 3);
            for (int i = 0; i < max; i++) {
                Offre offre = offres.get(i);
                String lieu = lieuxMap.getOrDefault(offre.getLieu_id(), "Lieu #" + offre.getLieu_id());
                offresRow.getChildren().add(createOfferCard(offre, lieu));
            }
        } catch (Exception e) {
            Label fallback = new Label("Offres indisponibles pour le moment.");
            fallback.getStyleClass().add("sectionHint");
            offresRow.getChildren().add(fallback);
        }
    }

    private VBox createOfferCard(Offre offre, String lieuName) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("contentCard", "hoverable");
        card.setPrefWidth(330);

        Label title = new Label(offre.getTitre());
        title.getStyleClass().add("sectionTitle");

        Label meta = new Label("-" + formatPercent(offre.getPourcentage()) + " · " + lieuName);
        meta.getStyleClass().add("sectionHint");

        Button btn = new Button("Voir Offre");
        btn.getStyleClass().add("linkBtn");
        btn.setOnAction(e -> openOffer(offre));

        card.getChildren().addAll(title, meta, btn);
        return card;
    }

    private String formatPercent(float p) {
        if (Math.floor(p) == p) return ((int) p) + "%";
        return p + "%";
    }

    private void openOffer(Offre offre) {
        FrontOfferContext.setSelectedOffreId(offre.getId());
        if (navigator != null) {
            navigator.navigate("offres");
            return;
        }
        info("Offre", "Navigation vers détails offre: " + offre.getTitre());
    }

    private void animateEntrance() {
        if (citiesRow == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(180), citiesRow);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void setupRecoCards() {
        styleFeaturedCard(recoSpotCard);
        styleFeaturedCard(recoOfferCard);
        styleFeaturedCard(recoSortieCard);
    }

    private void styleFeaturedCard(StackPane card) {
        if (card == null) return;
        applyRoundedClip(card, CARD_RADIUS);
        installHoverScale(card);
        card.setCursor(Cursor.HAND);
    }

    private void buildTrendingCities() {
        if (citiesRow == null) return;
        citiesRow.setAlignment(Pos.CENTER_LEFT);
        citiesRow.getChildren().clear();

        List<City> cities = List.of(
                new City("Tunis", "Cafés · Musées · Sorties", "/images/demo/cities/tunis.jpg"),
                new City("La Marsa", "Plages · Restaurants", "/images/demo/cities/marsa.jpg"),
                new City("Sidi Bou Saïd", "Balades · Photos", "/images/demo/cities/sidi.jpg"),
                new City("Hammamet", "Plages · Activités", "/images/demo/cities/hammamet.jpg"),
                new City("Nabeul", "Marchés · Food", "/images/demo/cities/nabeul.jpg")
        );

        for (City c : cities) {
            StackPane card = createCityCard(c);
            citiesRow.getChildren().add(card);
        }
    }

    private StackPane createCityCard(City c) {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("cityCard", "hoverable");
        card.setPrefWidth(230);
        card.setMinHeight(270);

        String bgStyle = resolveBgStyle(c.imagePath);
        if (!bgStyle.isEmpty()) {
            card.setStyle(bgStyle);
        }

        // ✅ Clip arrondi réel = images vraiment cintrées (propre)
        applyRoundedClip(card, CARD_RADIUS);

        VBox overlay = new VBox(4);
        overlay.getStyleClass().add("cityOverlay");

        Label title = new Label(c.name);
        title.getStyleClass().add("cityName");

        Label meta = new Label(c.meta);
        meta.getStyleClass().add("cityMeta");
        meta.setWrapText(true);

        overlay.getChildren().addAll(title, meta);

        StackPane.setAlignment(overlay, Pos.BOTTOM_LEFT);
        card.getChildren().add(overlay);

        installHoverScale(card);

        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> info(
                "Ville sélectionnée",
                "Explorer: " + c.name + "\n(Ici tu branches la page Lieux/Recherche filtrée)"
        ));

        return card;
    }

    private void installHoverScale(StackPane card) {
        ScaleTransition in = new ScaleTransition(Duration.millis(150), card);
        in.setToX(1.03);
        in.setToY(1.03);

        ScaleTransition out = new ScaleTransition(Duration.millis(150), card);
        out.setToX(1.0);
        out.setToY(1.0);

        card.setOnMouseEntered(e -> in.playFromStart());
        card.setOnMouseExited(e -> out.playFromStart());
    }

    private void applyRoundedClip(StackPane node, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        node.setClip(clip);

        node.layoutBoundsProperty().addListener((obs, oldB, b) -> {
            clip.setWidth(b.getWidth());
            clip.setHeight(b.getHeight());
        });
    }

    private String resolveBgStyle(String resourcePath) {
        try {
            URL u = getClass().getResource(resourcePath);
            if (u == null) return "";
            return "-fx-background-image: url('" + u.toExternalForm() + "');";
        } catch (Exception ignored) {
            return "";
        }
    }

    /* ===== Actions HERO ===== */

    @FXML
    private void explore() {
        String q = heroSearchField == null ? "" : heroSearchField.getText().trim();
        if (q.isEmpty()) {
            info("Recherche", "Entre un mot-clé dans la barre de recherche.");
            return;
        }
        info("Recherche", "Recherche: " + q + "\n(Brancher recherche globale ici)");
    }

    @FXML private void chipCafes() { info("Catégorie", "Filtre: Cafés"); }
    @FXML private void chipRestaurants() { info("Catégorie", "Filtre: Restaurants"); }
    @FXML private void chipMusees() { info("Catégorie", "Filtre: Musées"); }
    @FXML private void chipPlages() { info("Catégorie", "Filtre: Plages"); }
    @FXML private void chipSorties() { info("Catégorie", "Filtre: Sorties"); }

    @FXML
    private void seeAllCities() {
        info("Villes", "Afficher toutes les villes (brancher une page dédiée).");
    }

    @FXML
    private void refreshReco() {
        info("Recommandations", "Actualisation (brancher service de recommandations).");
    }

    /* ===== Reco actions ===== */

    @FXML
    private void openRecoSpotClick(MouseEvent e) {
        info("Spot populaire", "Ouvrir les lieux recommandés (brancher la liste filtrée / détails).");
    }

    @FXML
    private void openRecoOfferClick(MouseEvent e) {
        if (navigator != null) {
            navigator.navigate("offres");
            return;
        }
        info("Offre du moment", "Ouvrir la section Offres (liste + détails).");
    }

    @FXML
    private void openRecoSortieClick(MouseEvent e) {
        info("Sortie recommandée", "Ouvrir la section Sorties (brancher liste + participation).");
    }

    @FXML private void openRecoSpotAction() { openRecoSpotClick(null); }
    @FXML private void openRecoOfferAction() { openRecoOfferClick(null); }
    @FXML private void openRecoSortieAction() { openRecoSortieClick(null); }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}