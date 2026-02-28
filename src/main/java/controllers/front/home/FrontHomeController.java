package controllers.front.home;

import controllers.front.shell.FrontDashboardController;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FrontHomeController {

    private static final double CARD_RADIUS = 22;

    @FXML private TextField heroSearchField;
    @FXML private HBox citiesRow;

    @FXML private StackPane recoSpotCard;
    @FXML private StackPane recoOfferCard;
    @FXML private StackPane recoSortieCard;

    private ShellNavigator navigator;

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    private record City(String name, String meta, String imagePath) {}

    @FXML
    private void initialize() {
        buildTrendingCities();
        setupRecoCards();
        animateEntrance();
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
        if (!bgStyle.isEmpty()) card.setStyle(bgStyle);

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
        card.setOnMouseClicked(e -> goLieuxCity(c.name));

        return card;
    }

    private void goLieuxCity(String city) {
        if (navigator == null) {
            info("Navigation", "Navigator non injecté.");
            return;
        }
        String v = enc(city);
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "ville=" + v + ";q=" + v);
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

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s.trim(), StandardCharsets.UTF_8);
    }

    /* ===== Actions HERO ===== */

    @FXML
    private void explore() {
        if (navigator == null) return;

        String q = heroSearchField == null ? "" : heroSearchField.getText().trim();
        if (q.isEmpty()) {
            navigator.navigate(FrontDashboardController.ROUTE_LIEUX);
            return;
        }
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "q=" + enc(q));
    }

    /* ===== Chips -> filtrage direct vers Lieux ===== */
    @FXML private void chipCafes() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "cat=" + enc("CAFE") + ";q=" + enc("cafe"));
    }
    @FXML private void chipRestaurants() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "cat=" + enc("RESTAURANT") + ";q=" + enc("restaurant"));
    }
    @FXML private void chipMusees() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "cat=" + enc("MUSEE") + ";q=" + enc("musee"));
    }
    @FXML private void chipPlages() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "cat=" + enc("PLAGE") + ";q=" + enc("plage"));
    }
    @FXML private void chipSorties() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_SORTIES);
    }

    @FXML
    private void seeAllCities() {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX);
    }

    @FXML
    private void refreshReco() {
        // UI statique pour l’instant (pas de service reco)
        // On peut ajouter une logique DB ensuite.
    }

    /* ===== Reco actions ===== */

    @FXML
    private void openRecoSpotClick(MouseEvent e) {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_LIEUX_FILTER_PREFIX + "q=" + enc("cafe"));
    }

    @FXML
    private void openRecoOfferClick(MouseEvent e) {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_OFFRES);
    }

    @FXML
    private void openRecoSortieClick(MouseEvent e) {
        if (navigator == null) return;
        navigator.navigate(FrontDashboardController.ROUTE_SORTIES);
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