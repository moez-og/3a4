package controllers.front.sorties;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SortiesController {

    @FXML private TextField searchField;

    @FXML private Label statUpcoming;
    @FXML private Label statToday;
    @FXML private Label statMy;

    @FXML private ToggleButton chipAll;
    @FXML private ToggleButton chipToday;
    @FXML private ToggleButton chipWeekend;
    @FXML private ToggleButton chipPopular;

    @FXML private HBox upcomingRow;
    @FXML private HBox popularRow;

    private final ToggleGroup chips = new ToggleGroup();
    private final List<SortieCard> all = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM");

    private record SortieCard(
            String title,
            String ville,
            LocalDate date,
            String desc,
            List<String> tags,
            boolean popular,
            boolean joined
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
        if (chipToday != null) chipToday.setToggleGroup(chips);
        if (chipWeekend != null) chipWeekend.setToggleGroup(chips);
        if (chipPopular != null) chipPopular.setToggleGroup(chips);
        if (chipAll != null) chipAll.setSelected(true);
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now();

        all.add(new SortieCard("Balade à Sidi Bou Saïd", "Sidi Bou Saïd", today.plusDays(1),
                "Balade + photos, départ 16:00. Groupe friendly.",
                List.of("Photo", "Chill", "Côte"), true, false));

        all.add(new SortieCard("Brunch La Marsa", "La Marsa", today,
                "Brunch + café, places limitées.",
                List.of("Food", "Week-end", "Relax"), true, true));

        all.add(new SortieCard("Musée du Bardo (visite)", "Tunis", today.plusDays(3),
                "Visite guidée + découverte des collections.",
                List.of("Culture", "Musée"), false, false));

        all.add(new SortieCard("Sunset Hammamet", "Hammamet", nextWeekendDay(today),
                "Coucher de soleil + marche sur la plage.",
                List.of("Plage", "Sunset"), true, false));

        all.add(new SortieCard("Street Food Nabeul", "Nabeul", today.plusDays(6),
                "Tour street food, dégustation et spots locaux.",
                List.of("Food", "Découverte"), false, false));

        all.add(new SortieCard("Session Karting", "Tunis", today.plusDays(2),
                "Karting en groupe, niveau débutant OK.",
                List.of("Sport", "Adrénaline"), true, true));

        all.add(new SortieCard("Soirée Jeux", "La Marsa", today.plusDays(5),
                "Jeux de société, ambiance détente.",
                List.of("Fun", "Groupe"), false, false));
    }

    private LocalDate nextWeekendDay(LocalDate base) {
        DayOfWeek d = base.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY) return base;
        if (d == DayOfWeek.SUNDAY) return base.plusDays(6);
        return base.plusDays(DayOfWeek.SATURDAY.getValue() - d.getValue());
    }

    private void refreshStats() {
        LocalDate today = LocalDate.now();
        long upcoming = all.stream().filter(s -> !s.date().isBefore(today)).count();
        long todayCount = all.stream().filter(s -> s.date().isEqual(today)).count();
        long my = all.stream().filter(SortieCard::joined).count();

        if (statUpcoming != null) statUpcoming.setText(String.valueOf(upcoming));
        if (statToday != null) statToday.setText(String.valueOf(todayCount));
        if (statMy != null) statMy.setText(String.valueOf(my));
    }

    @FXML private void filterAll() { render("all"); }
    @FXML private void filterToday() { render("today"); }
    @FXML private void filterWeekend() { render("weekend"); }
    @FXML private void filterPopular() { render("popular"); }

    @FXML
    private void refresh() {
        info("Sorties", "Actualisation (brancher service Sorties).");
        render(currentFilter());
    }

    private String currentFilter() {
        if (chipToday != null && chipToday.isSelected()) return "today";
        if (chipWeekend != null && chipWeekend.isSelected()) return "weekend";
        if (chipPopular != null && chipPopular.isSelected()) return "popular";
        return "all";
    }

    private void render(String filter) {
        if (upcomingRow == null || popularRow == null) return;

        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        LocalDate today = LocalDate.now();

        List<SortieCard> filtered = all.stream()
                .filter(s -> q.isEmpty() || (s.title().toLowerCase().contains(q) ||
                        s.ville().toLowerCase().contains(q) ||
                        s.tags().toString().toLowerCase().contains(q)))
                .filter(s -> switch (filter) {
                    case "today" -> s.date().isEqual(today);
                    case "weekend" -> isWeekend(s.date());
                    case "popular" -> s.popular();
                    default -> true;
                })
                .toList();

        upcomingRow.getChildren().clear();
        popularRow.getChildren().clear();

        filtered.stream()
                .filter(s -> !s.date().isBefore(today))
                .limit(10)
                .forEach(s -> upcomingRow.getChildren().add(createCard(s)));

        filtered.stream()
                .filter(SortieCard::popular)
                .limit(10)
                .forEach(s -> popularRow.getChildren().add(createCard(s)));

        if (upcomingRow.getChildren().isEmpty()) {
            upcomingRow.getChildren().add(emptyCard("Aucune sortie trouvée", "Change le filtre ou la recherche."));
        }
        if (popularRow.getChildren().isEmpty()) {
            popularRow.getChildren().add(emptyCard("Rien de populaire pour ce filtre", "Essaie \"Tous\" ou \"Populaires\"."));
        }
    }

    private boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    @FXML
    private void showMyParticipations() {
        info("Mes participations", "Brancher: liste des participations de l’utilisateur connecté.");
    }

    @FXML
    private void createAnnonce() {
        info("Créer une annonce", "Brancher: formulaire de création (front).");
    }

    private VBox createCard(SortieCard s) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("itemCard", "hoverable");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(s.title());
        title.getStyleClass().add("itemTitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(badgeText(s));
        badge.getStyleClass().addAll("badge", badgeClass(s));

        header.getChildren().addAll(title, spacer, badge);

        Label meta = new Label("📍 " + s.ville() + "   •   🗓 " + s.date().format(DATE_FMT));
        meta.getStyleClass().add("itemMeta");

        Label desc = new Label(s.desc());
        desc.getStyleClass().add("itemDesc");
        desc.setWrapText(true);

        HBox tags = new HBox(8);
        for (String t : s.tags()) {
            Label tag = new Label(t);
            tag.getStyleClass().add("tag");
            tags.getChildren().add(tag);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnDetails = new Button("Détails");
        btnDetails.getStyleClass().add("ghostBtnSmall");
        btnDetails.setOnAction(e -> info("Détails", s.title() + "\n\n" + s.desc()));

        Button btnJoin = new Button(s.joined() ? "Déjà participé" : "Participer");
        btnJoin.getStyleClass().add(s.joined() ? "ghostBtnSmall" : "primaryBtnSmall");
        btnJoin.setDisable(s.joined());
        btnJoin.setOnAction(e -> info("Participation", "Brancher: création participation + moyens de contact."));

        actions.getChildren().addAll(btnDetails, btnJoin);

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

    private String badgeText(SortieCard s) {
        LocalDate today = LocalDate.now();
        if (s.date().isEqual(today)) return "Aujourd’hui";
        if (isWeekend(s.date())) return "Week-end";
        if (s.popular()) return "Tendance";
        return "À venir";
    }

    private String badgeClass(SortieCard s) {
        LocalDate today = LocalDate.now();
        if (s.date().isEqual(today)) return "badgeGreen";
        if (isWeekend(s.date())) return "badgeGold";
        if (s.popular()) return "badgeBlue";
        return "badgeGold";
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}