package controllers.front.home;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;

public class FrontHomeController {

    @FXML private TextField heroSearchField;
    @FXML private HBox citiesRow;

    private record City(String name, String meta, String imagePath) {}

    @FXML
    private void initialize() {
        buildTrendingCities();
        animateEntrance();
    }

    private void animateEntrance() {
        if (citiesRow == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(180), citiesRow);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void buildTrendingCities() {
        if (citiesRow == null) return;
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

        // Background image via CSS inline (sans crash si l’image n’existe pas)
        String bgStyle = resolveBgStyle(c.imagePath);
        if (!bgStyle.isEmpty()) {
            card.setStyle(bgStyle);
        }

        VBox overlay = new VBox(4);
        overlay.getStyleClass().add("cityOverlay");

        Label title = new Label(c.name);
        title.getStyleClass().add("cityName");

        Label meta = new Label(c.meta);
        meta.getStyleClass().add("cityMeta");
        meta.setWrapText(true);

        overlay.getChildren().addAll(title, meta);

        StackPane.setAlignment(overlay, javafx.geometry.Pos.BOTTOM_LEFT);
        card.getChildren().add(overlay);

        // Hover animation
        ScaleTransition in = new ScaleTransition(Duration.millis(140), card);
        in.setToX(1.03); in.setToY(1.03);

        ScaleTransition out = new ScaleTransition(Duration.millis(140), card);
        out.setToX(1.0); out.setToY(1.0);

        card.setOnMouseEntered(e -> in.playFromStart());
        card.setOnMouseExited(e -> out.playFromStart());

        // Click action
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(e -> info("Ville sélectionnée", "Explorer: " + c.name + "\n(Ici tu branches la page Lieux/Recherche filtrée)"));

        return card;
    }

    private String resolveBgStyle(String resourcePath) {
        try {
            URL u = getClass().getResource(resourcePath);
            if (u == null) return ""; // image absente => pas d'erreur, juste fond par défaut
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

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}