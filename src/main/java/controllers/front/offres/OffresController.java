package controllers.front.offres;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class OffresController {

    @FXML private TextField searchField;

    @FXML private Label statActive;
    @FXML private Label statPartners;
    @FXML private Label statExpiring;

    @FXML private ToggleButton chipAll;
    @FXML private ToggleButton chipFood;
    @FXML private ToggleButton chipLoisirs;
    @FXML private ToggleButton chipTransport;
    @FXML private ToggleButton chipHot;

    @FXML private HBox featuredRow;
    @FXML private HBox latestRow;

    private final ToggleGroup chips = new ToggleGroup();
    private final List<OfferCard> all = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM");

    private record OfferCard(
            String title,
            String partner,
            String category,
            int discountPercent,
            LocalDate until,
            List<String> tags,
            boolean hot
    ) {}

    @FXML
    private void initialize() {
        bindChips();
        seedDemoData();
        refreshStats();
        render("all");
    }

    private void bindChips() {
        if (chipAll != null) chipAll.setToggleGroup(chips);
        if (chipFood != null) chipFood.setToggleGroup(chips);
        if (chipLoisirs != null) chipLoisirs.setToggleGroup(chips);
        if (chipTransport != null) chipTransport.setToggleGroup(chips);
        if (chipHot != null) chipHot.setToggleGroup(chips);
        if (chipAll != null) chipAll.setSelected(true);
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now();
        all.add(new OfferCard("−20% Brunch Week-end", "Café Panorama", "Food", 20, today.plusDays(4),
                List.of("Promo", "Brunch"), true));

        all.add(new OfferCard("2 billets = 1 offert", "Musée CityPass", "Loisirs", 50, today.plusDays(10),
                List.of("Culture", "Famille"), false));

        all.add(new OfferCard("Réduction taxi aéroport", "RideTun", "Transport", 15, today.plusDays(2),
                List.of("Déplacement", "Rapide"), true));

        all.add(new OfferCard("−10% Menu étudiant", "Street Food Nabeul", "Food", 10, today.plusDays(14),
                List.of("Budget", "Food"), false));

        all.add(new OfferCard("Accès piscine −30%", "Hammamet Resort", "Loisirs", 30, today.plusDays(1),
                List.of("Été", "Plage"), true));
    }

    private void refreshStats() {
        long active = all.size();
        long partners = all.stream().map(OfferCard::partner).distinct().count();
        long expiring = all.stream().filter(o -> o.until().isBefore(LocalDate.now().plusDays(3))).count();

        if (statActive != null) statActive.setText(String.valueOf(active));
        if (statPartners != null) statPartners.setText(String.valueOf(partners));
        if (statExpiring != null) statExpiring.setText(String.valueOf(expiring));
    }

    @FXML private void filterAll() { render("all"); }
    @FXML private void filterFood() { render("food"); }
    @FXML private void filterLoisirs() { render("loisirs"); }
    @FXML private void filterTransport() { render("transport"); }
    @FXML private void filterHot() { render("hot"); }

    @FXML
    private void refresh() {
        info("Offres", "Actualisation (brancher service Offres).");
        render(currentFilter());
    }

    private String currentFilter() {
        if (chipFood != null && chipFood.isSelected()) return "food";
        if (chipLoisirs != null && chipLoisirs.isSelected()) return "loisirs";
        if (chipTransport != null && chipTransport.isSelected()) return "transport";
        if (chipHot != null && chipHot.isSelected()) return "hot";
        return "all";
    }

    private void render(String filter) {
        if (featuredRow == null || latestRow == null) return;

        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();

        List<OfferCard> filtered = all.stream()
                .filter(o -> q.isEmpty() || (o.title().toLowerCase().contains(q) ||
                        o.partner().toLowerCase().contains(q) ||
                        o.category().toLowerCase().contains(q) ||
                        o.tags().toString().toLowerCase().contains(q)))
                .filter(o -> switch (filter) {
                    case "food" -> o.category().equalsIgnoreCase("Food");
                    case "loisirs" -> o.category().equalsIgnoreCase("Loisirs");
                    case "transport" -> o.category().equalsIgnoreCase("Transport");
                    case "hot" -> o.hot();
                    default -> true;
                })
                .toList();

        featuredRow.getChildren().clear();
        latestRow.getChildren().clear();

        filtered.stream()
                .sorted((a, b) -> Integer.compare(b.discountPercent(), a.discountPercent()))
                .limit(8)
                .forEach(o -> featuredRow.getChildren().add(createCard(o, true)));

        filtered.stream()
                .sorted((a, b) -> b.until().compareTo(a.until()))
                .limit(10)
                .forEach(o -> latestRow.getChildren().add(createCard(o, false)));

        if (featuredRow.getChildren().isEmpty()) {
            featuredRow.getChildren().add(emptyCard("Aucune offre trouvée", "Change le filtre ou la recherche."));
        }
        if (latestRow.getChildren().isEmpty()) {
            latestRow.getChildren().add(emptyCard("Aucune nouveauté", "Réessaie avec \"Tout\"."));
        }
    }

    @FXML
    private void showMyCoupons() {
        info("Mes coupons", "Brancher: liste des coupons/achats liés à l’utilisateur.");
    }

    @FXML
    private void createOffer() {
        info("Proposer une offre", "Brancher: formulaire (partenaire/discount/période).");
    }

    private VBox createCard(OfferCard o, boolean featured) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("itemCard", "hoverable");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(o.title());
        title.getStyleClass().add("itemTitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("−" + o.discountPercent() + "%");
        badge.getStyleClass().addAll("badge", featured ? "badgeGold" : (o.hot() ? "badgeRed" : "badgeBlue"));

        header.getChildren().addAll(title, spacer, badge);

        Label meta = new Label("🏷 " + o.category() + "   •   🤝 " + o.partner());
        meta.getStyleClass().add("itemMeta");

        Label desc = new Label("Valable jusqu’au " + o.until().format(DATE_FMT));
        desc.getStyleClass().add("itemDesc");

        HBox tags = new HBox(8);
        for (String t : o.tags()) {
            Label tag = new Label(t);
            tag.getStyleClass().add("tag");
            tags.getChildren().add(tag);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnDetails = new Button("Détails");
        btnDetails.getStyleClass().add("ghostBtnSmall");
        btnDetails.setOnAction(e -> info("Détails", o.title() + "\nPartenaire: " + o.partner()));

        Button btnUse = new Button("Utiliser");
        btnUse.getStyleClass().add("primaryBtnSmall");
        btnUse.setOnAction(e -> info("Coupon", "Brancher: génération/activation coupon."));

        actions.getChildren().addAll(btnDetails, btnUse);

        card.getChildren().addAll(header, meta, desc, tags, actions);
        return card;
    }

    private VBox emptyCard(String titleText, String descText) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("itemCard", "hoverable");

        Label title = new Label(titleText);
        title.getStyleClass().add("itemTitle");

        Label desc = new Label(descText);
        desc.getStyleClass().add("itemDesc");
        desc.setWrapText(true);

        card.getChildren().addAll(title, desc);
        return card;
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}