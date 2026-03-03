package controllers.front.lieux;

import controllers.front.shell.FrontDashboardController;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import models.lieux.Lieu;
import models.users.User;
import services.lieux.FavoriLieuService;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MesFavorisController {

    @FXML private Label countLabel;
    @FXML private FlowPane cardsPane;
    @FXML private VBox emptyState;

    private final FavoriLieuService favoriService = new FavoriLieuService();

    private ShellNavigator navigator;
    private User currentUser;

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loadFavoris();
    }

    @FXML
    private void initialize() {
        // loadFavoris() est appelé depuis setCurrentUser
    }

    private void loadFavoris() {
        if (currentUser == null || cardsPane == null) return;

        List<Lieu> favoris = favoriService.getFavorisForUser(currentUser.getId());
        cardsPane.getChildren().clear();

        boolean empty = favoris.isEmpty();
        if (emptyState != null) { emptyState.setVisible(empty); emptyState.setManaged(empty); }
        if (countLabel != null) countLabel.setText(favoris.size() + " lieu" + (favoris.size() > 1 ? "x" : "") + " en favori");

        for (Lieu l : favoris) {
            cardsPane.getChildren().add(buildCard(l));
        }
    }

    private VBox buildCard(Lieu l) {
        VBox card = new VBox(10);
        card.getStyleClass().add("lieuCard");
        card.setPrefWidth(300);

        // Image
        StackPane imgWrap = new StackPane();
        imgWrap.getStyleClass().add("cardImageWrap");

        ImageView iv = new ImageView();
        iv.setFitWidth(300);
        iv.setFitHeight(160);
        iv.setPreserveRatio(false);
        Rectangle clip = new Rectangle(300, 160);
        clip.setArcWidth(18); clip.setArcHeight(18);
        iv.setClip(clip);
        iv.setImage(loadImageOrFallback(l.getImageUrl()));

        // Bouton cœur (retirer des favoris) — design cercle blanc
        Button heartBtn = new Button("♥");
        heartBtn.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 999;" +
            "-fx-border-radius: 999;" +
            "-fx-min-width: 38; -fx-max-width: 38;" +
            "-fx-min-height: 38; -fx-max-height: 38;" +
            "-fx-padding: 0;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 17px;" +
            "-fx-text-fill: #e11d48;" +
            "-fx-alignment: center;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 8, 0, 0, 2);"
        );
        heartBtn.setOnMouseEntered(e -> heartBtn.setStyle(
            "-fx-background-color: #fff1f2;" +
            "-fx-background-radius: 999;" +
            "-fx-border-radius: 999;" +
            "-fx-min-width: 38; -fx-max-width: 38;" +
            "-fx-min-height: 38; -fx-max-height: 38;" +
            "-fx-padding: 0;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 17px;" +
            "-fx-text-fill: #e11d48;" +
            "-fx-alignment: center;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 8, 0, 0, 2);"
        ));
        heartBtn.setOnMouseExited(e -> heartBtn.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 999;" +
            "-fx-border-radius: 999;" +
            "-fx-min-width: 38; -fx-max-width: 38;" +
            "-fx-min-height: 38; -fx-max-height: 38;" +
            "-fx-padding: 0;" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 17px;" +
            "-fx-text-fill: #e11d48;" +
            "-fx-alignment: center;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 8, 0, 0, 2);"
        ));
        heartBtn.setTooltip(new javafx.scene.control.Tooltip("Retirer des favoris"));
        heartBtn.setOnAction(e -> {
            favoriService.remove(currentUser.getId(), l.getId());
            loadFavoris();
        });
        StackPane.setAlignment(heartBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(heartBtn, new Insets(8));

        imgWrap.getChildren().addAll(iv, heartBtn);

        // Textes
        Label name = new Label(safe(l.getNom()));
        name.getStyleClass().add("cardTitle");

        Label meta = new Label(safe(l.getVille()) + " • " + safe(l.getCategorie()));
        meta.getStyleClass().add("cardMeta");

        Label addr = new Label(safe(l.getAdresse()));
        addr.getStyleClass().add("cardAddr");
        addr.setWrapText(true);

        // Actions
        HBox actions = new HBox(8);
        Button details = new Button("Voir détails");
        details.getStyleClass().add("primaryBtn");
        details.setOnAction(e -> {
            if (navigator != null)
                navigator.navigate(FrontDashboardController.ROUTE_LIEU_DETAILS_PREFIX + l.getId());
        });

        Button offres = new Button("Offres");
        offres.getStyleClass().add("ghostBtn");
        offres.setOnAction(e -> {
            if (navigator != null) {
                FrontOfferContext.setSelectedLieuId(l.getId());
                navigator.navigate(FrontDashboardController.ROUTE_OFFRES);
            }
        });

        actions.getChildren().addAll(details, offres);
        card.getChildren().addAll(imgWrap, name, meta, addr, actions);

        // Double-clic → détails
        card.setOnMouseClicked(ev -> {
            if (ev.getClickCount() >= 2 && navigator != null)
                navigator.navigate(FrontDashboardController.ROUTE_LIEU_DETAILS_PREFIX + l.getId());
        });

        return card;
    }

    private Image loadImageOrFallback(String raw) {
        String path = safe(raw).trim();
        try {
            if (!path.isEmpty()) {
                if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:"))
                    return new Image(path, true);
                if (path.startsWith("/")) {
                    URL u = getClass().getResource(path);
                    if (u != null) return new Image(u.toExternalForm(), true);
                }
            }
        } catch (Exception ignored) {}
        URL fb = getClass().getResource("/images/demo/hero/hero.jpg");
        return fb == null ? null : new Image(fb.toExternalForm(), true);
    }

    private String safe(String s) { return s == null ? "" : s; }
}
