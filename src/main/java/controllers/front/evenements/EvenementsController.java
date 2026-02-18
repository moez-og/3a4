package controllers.front.evenements;

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

public class EvenementsController {

    @FXML
    private TextField searchField;

    @FXML
    private Label statThisWeek;
    @FXML
    private Label statThisMonth;
    @FXML
    private Label statMy;

    @FXML
    private ToggleButton chipAll;
    @FXML
    private ToggleButton chipWeek;
    @FXML
    private ToggleButton chipMonth;
    @FXML
    private ToggleButton chipFree;

    @FXML
    private HBox featuredRow;
    @FXML
    private VBox agendaList;

    private final ToggleGroup chips = new ToggleGroup();
    private final List<EventCard> all = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE dd MMM");

    private record EventCard(
            String title,
            String lieu,
            LocalDate date,
            String hour,
            String desc,
            List<String> tags,
            boolean free,
            boolean registered,
            boolean featured
    ) {
    }

    @FXML
    private void initialize() {
        bindChips();
        seedDemoData();
        refreshStats();
        render("all");
    }

    private void bindChips() {
        if (chipAll != null) chipAll.setToggleGroup(chips);
        if (chipWeek != null) chipWeek.setToggleGroup(chips);
        if (chipMonth != null) chipMonth.setToggleGroup(chips);
        if (chipFree != null) chipFree.setToggleGroup(chips);
        if (chipAll != null) chipAll.setSelected(true);
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now();

        all.add(new EventCard("Festival Food & Music", "Tunis", today.plusDays(2), "18:00",
                "Concerts + food trucks, entrée gratuite avant 19h.",
                List.of("Musique", "Food"), false, false, true));

        all.add(new EventCard("Marche photo – Sidi Bou Saïd", "Sidi Bou Saïd", today.plusDays(1), "16:30",
                "Parcours photo, spots iconiques, conseils lightroom.",
                List.of("Photo", "Balade"), true, true, true));

        all.add(new EventCard("Conférence Tech Étudiants", "La Marsa", today.plusDays(6), "10:00",
                "Talks, networking, stands partenaires.",
                List.of("Tech", "Networking"), true, false, false));

        all.add(new EventCard("Soirée Stand-up", "Tunis", today.plusDays(9), "20:00",
                "Plateau humour + open mic.",
                List.of("Show", "Fun"), false, false, false));

        all.add(new EventCard("Journée plage & sports", "Hammamet", nextWeekend(today), "09:30",
                "Beach volley + baignade + chill.",
                List.of("Plage", "Sport"), true, false, true));
    }

    private LocalDate nextWeekend(LocalDate base) {
        DayOfWeek d = base.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY) return base;
        if (d == DayOfWeek.SUNDAY) return base.plusDays(6);
        return base.plusDays(DayOfWeek.SATURDAY.getValue() - d.getValue());
    }

    private void refreshStats() {
        LocalDate today = LocalDate.now();

        long week = all.stream().filter(e -> !e.date().isBefore(today) && e.date().isBefore(today.plusDays(7))).count();
        long month = all.stream().filter(e -> e.date().getMonthValue() == today.getMonthValue() && e.date().getYear() == today.getYear()).count();
        long my = all.stream().filter(EventCard::registered).count();

        if (statThisWeek != null) statThisWeek.setText(String.valueOf(week));
        if (statThisMonth != null) statThisMonth.setText(String.valueOf(month));
        if (statMy != null) statMy.setText(String.valueOf(my));
    }

    @FXML
    private void filterAll() {
        render("all");
    }

    @FXML
    private void filterWeek() {
        render("week");
    }

    @FXML
    private void filterMonth() {
        render("month");
    }

    @FXML
    private void filterFree() {
        render("free");
    }

    @FXML
    private void refresh() {
        info("Événements", "Actualisation (brancher service Événements).");
        render(currentFilter());
    }

    private String currentFilter() {
        if (chipWeek != null && chipWeek.isSelected()) return "week";
        if (chipMonth != null && chipMonth.isSelected()) return "month";
        if (chipFree != null && chipFree.isSelected()) return "free";
        return "all";
    }

    private void render(String filter) {
        if (featuredRow == null || agendaList == null) return;

        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        LocalDate today = LocalDate.now();

        List<EventCard> filtered = all.stream()
                .filter(e -> q.isEmpty() || (e.title().toLowerCase().contains(q) ||
                        e.lieu().toLowerCase().contains(q) ||
                        e.tags().toString().toLowerCase().contains(q)))
                .filter(e -> switch (filter) {
                    case "week" -> !e.date().isBefore(today) && e.date().isBefore(today.plusDays(7));
                    case "month" ->
                            e.date().getMonthValue() == today.getMonthValue() && e.date().getYear() == today.getYear();
                    case "free" -> e.free();
                    default -> true;
                })
                .toList();

        featuredRow.getChildren().clear();
        agendaList.getChildren().clear();

        filtered.stream()
                .filter(EventCard::featured)
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .limit(8)
                .forEach(e -> featuredRow.getChildren().add(createFeaturedCard(e)));

        filtered.stream()
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .limit(12)
                .forEach(e -> agendaList.getChildren().add(createAgendaCard(e)));

        if (featuredRow.getChildren().isEmpty()) {
            featuredRow.getChildren().add(emptyCard("Aucun événement à la une", "Change le filtre ou la recherche."));
        }
        if (agendaList.getChildren().isEmpty()) {
            agendaList.getChildren().add(emptyCard("Agenda vide", "Réessaie avec \"Tout\"."));
        }
    }

    @FXML
    private void showMyRegistrations() {
        info("Mes inscriptions", "Brancher: inscriptions liées à l’utilisateur connecté.");
    }

    @FXML
    private void createEvent() {
        info("Proposer un événement", "Brancher: formulaire (titre, lieu, date, capacité).");
    }

    private VBox createFeaturedCard(EventCard e) {
        VBox card = baseCard(e);
        card.setPrefWidth(340);
        return card;
    }

    private VBox createAgendaCard(EventCard e) {
        VBox card = baseCard(e);
        card.setPrefWidth(9999);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox baseCard(EventCard e) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("itemCard", "hoverable");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(e.title());
        title.getStyleClass().add("itemTitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(e.free() ? "Gratuit" : "Payant");
        badge.getStyleClass().addAll("badge", e.free() ? "badgeGreen" : "badgeBlue");

        header.getChildren().addAll(title, spacer, badge);

        Label meta = new Label("📍 " + e.lieu() + "   •   🗓 " + e.date().format(DATE_FMT) + "   •   ⏰ " + e.hour());
        meta.getStyleClass().add("itemMeta");

        Label desc = new Label(e.desc());
        desc.getStyleClass().add("itemDesc");
        desc.setWrapText(true);

        HBox tags = new HBox(8);
        for (String t : e.tags()) {
            Label tag = new Label(t);
            tag.getStyleClass().add("tag");
            tags.getChildren().add(tag);
        }

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button btnDetails = new Button("Détails");
        btnDetails.getStyleClass().add("ghostBtnSmall");
        btnDetails.setOnAction(ev -> info("Détails", e.title() + "\n\n" + e.desc()));

        Button btnRegister = new Button(e.registered() ? "Déjà inscrit" : "S’inscrire");
        btnRegister.getStyleClass().add(e.registered() ? "ghostBtnSmall" : "primaryBtnSmall");
        btnRegister.setDisable(e.registered());
        btnRegister.setOnAction(ev -> info("Inscription", "Brancher: inscription + contrôle capacité."));

        actions.getChildren().addAll(btnDetails, btnRegister);

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