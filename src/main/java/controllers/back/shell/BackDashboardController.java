package controllers.back.shell;

import controllers.common.notifications.NotificationsCenterController;
import controllers.front.shell.FrontDashboardController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.sorties.AnnonceSortie;
import models.users.User;
import services.sorties.AnnonceSortieService;
import services.sorties.ParticipationSortieService;
import services.users.UserService;
import services.notifications.NotificationService;
import utils.ui.ViewPaths;

import java.io.IOException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackDashboardController {

    private static final String DASHBOARD_VIEW_PATH = "/fxml/back/dashboard/DashboardAdmin.fxml";
    private static final String USERS_VIEW_PATH = "/fxml/back/users/UserDashboard.fxml";
    private static final String SORTIES_VIEW_PATH = "/fxml/back/sorties/SortiesAdmin.fxml";
    private static final String LIEUX_VIEW_PATH = "/fxml/back/lieux/LieuxAdmin.fxml";
    private static final String OFFRES_VIEW_PATH = "/fxml/back/offres/OffresAdmin.fxml";
    private static final String EVENTS_VIEW_PATH = "/fxml/back/evenements/EvenementsAdmin.fxml";
    private static final String LOGIN_VIEW_PATH = "/fxml/common/auth/Login.fxml";

    @FXML private StackPane dynamicContent;

    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;

    @FXML private Label userName;
    @FXML private Label userRole;

    @FXML private Button btnNotif;
    @FXML private Label notifBadge;

    @FXML private Button btnDashboard;
    @FXML private Button btnUtilisateurs;
    @FXML private Button btnSorties;
    @FXML private Button btnLieux;
    @FXML private Button btnOffres;
    @FXML private Button btnEvents;
    @FXML private Button btnGoFront;

    private Stage primaryStage;
    private User currentUser;

    private final NotificationService notificationService = new NotificationService();
    private Timeline notifPoller;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        refreshHeaderUser();
        disableButtonsBasedOnRole();
        refreshUnreadBadgeAsync();
        startNotifPolling();
    }

    @FXML
    private void initialize() {
        refreshHeaderUser();
        showDashboard();
    }

    // ===== Navigation =====

    @FXML
    public void showDashboard() {
        setActive(btnDashboard);
        setHeader("Dashboard", "Vue d’ensemble");

        // Dashboard dynamique: stats users
        // Si ça échoue, fallback sur FXML dashboard
        loadDashboardStatsWithFallback();
    }

    @FXML
    public void showUsers() {
        if (!checkAuthorization("admin")) {
            showError("Accès refusé", "Espace réservé aux admins", "");
            return;
        }
        setActive(btnUtilisateurs);
        setHeader("Gestion des Utilisateurs", "Recherche, ajout, modification et suppression");
        loadAndSetCachedView("users", USERS_VIEW_PATH);
    }

    @FXML
    public void showSorties() {
        setActive(btnSorties);
        setHeader("Gestion des Sorties", "Catalogue, annonces, participations");
        loadAndSetCachedView("sorties", SORTIES_VIEW_PATH);
    }

    @FXML
    public void showLieux() {
        setActive(btnLieux);
        setHeader("Gestion des Lieux", "Catalogue, recherche, ajout, modification et suppression");
        loadAndSetCachedView("lieux", LIEUX_VIEW_PATH);
    }

    @FXML
    public void showOffres() {
        setActive(btnOffres);
        setHeader("Gestion des Offres", "Promos, partenariats, coupons");
        loadAndSetCachedView("offres", OFFRES_VIEW_PATH);
    }

    @FXML
    public void showEvents() {
        setActive(btnEvents);
        setHeader("Gestion des Événements", "Agenda, inscriptions, gestion");
        loadAndSetCachedView("events", EVENTS_VIEW_PATH);
    }

    @FXML
    public void goToFront() {
        try {
            Stage stage = resolveStage();
            currentUser = null;
            viewCache.clear();
            controllerCache.clear();
            stopNotifPolling();
            setBadgeValue(0);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(LOGIN_VIEW_PATH));
            Parent root = loader.load();

            Object controller = loader.getController();
            invokeIfExists(controller, "setPrimaryStage", Stage.class, stage);

            Scene scene = new Scene(root, 900, 700);
            stage.setTitle("Fin Tokhroj - Login");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            showError("Erreur", "Impossible de revenir au Login", e.getMessage());
        }
    }

    @FXML
    public void showNotifications() {
        if (currentUser == null || currentUser.getId() <= 0) {
            showError("Notifications", "Connexion requise", "Connecte-toi pour voir tes notifications.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/common/notifications/NotificationsCenter.fxml"));
            Parent root = loader.load();

            NotificationsCenterController c = loader.getController();
            c.setCurrentUser(currentUser);
            c.setOnChange(this::refreshUnreadBadgeAsync);

            Stage dialog = new Stage();
            dialog.setTitle("Notifications");
            dialog.initOwner(resolveStage());
            dialog.setScene(new Scene(root, 720, 640));
            dialog.setResizable(true);
            dialog.showAndWait();

            refreshUnreadBadgeAsync();
        } catch (Exception e) {
            showError("Notifications", "Ouverture impossible", safe(e.getMessage()));
        }
    }

    /**
     * Bascule vers le FrontOffice en gardant l'utilisateur connecté.
     * Déclenché par clic sur le bloc "Fin Tokhroj" (brandCard) dans la sidebar.
     */
    @FXML
    public void openFront() {
        if (currentUser == null) {
            showError("Session", "Utilisateur non défini", "Connexion requise pour ouvrir le Front.");
            return;
        }

        try {
            Stage stage = resolveStage();

            FXMLLoader loader = new FXMLLoader(getClass().getResource(ViewPaths.FRONT_SHELL));
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller instanceof FrontDashboardController front) {
                front.setPrimaryStage(stage);
                front.setCurrentUser(currentUser);
            }

            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1200, 720);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            stage.setTitle("Fin Tokhroj");
            stage.setResizable(true);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            showError("Erreur", "Impossible d'ouvrir le Front", e.getMessage());
        }
    }

    // ===== Authorization & Role Management =====

    private boolean checkAuthorization(String requiredRole) {
        if (currentUser == null) return false;
        String role = safe(currentUser.getRole()).trim();
        return role.equalsIgnoreCase(requiredRole);
    }

    private void disableButtonsBasedOnRole() {
        if (currentUser == null) return;

        String role = safe(currentUser.getRole()).trim().toLowerCase();

        if ("admin".equalsIgnoreCase(role)) {
            setDisable(btnDashboard, false);
            setDisable(btnUtilisateurs, false);
            setDisable(btnSorties, false);
            setDisable(btnLieux, false);
            setDisable(btnOffres, false);
            setDisable(btnEvents, false);
            setDisable(btnGoFront, false);
            return;
        }

        if ("partenaire".equalsIgnoreCase(role)) {
            setDisable(btnDashboard, false);
            setDisable(btnUtilisateurs, true);
            setDisable(btnSorties, true);
            setDisable(btnLieux, false);
            setDisable(btnOffres, false);
            setDisable(btnEvents, false);
            setDisable(btnGoFront, false);
            return;
        }

        // autres rôles
        setDisable(btnDashboard, false);
        setDisable(btnUtilisateurs, true);
        setDisable(btnSorties, true);
        setDisable(btnLieux, true);
        setDisable(btnOffres, true);
        setDisable(btnEvents, true);
        setDisable(btnGoFront, true);
    }

    private void setDisable(Button b, boolean value) {
        if (b != null) b.setDisable(value);
    }

    // ===== Dashboard stats =====

    private void loadDashboardStatsWithFallback() {
        new Thread(() -> {
            try {
                UserService userService = new UserService();
                List<User> users = userService.obtenirTous();

                long totalUsers = users.size();
                long nbAdmins = users.stream().filter(u -> "admin".equalsIgnoreCase(safe(u.getRole()))).count();
                long nbPartenaires = users.stream().filter(u -> "partenaire".equalsIgnoreCase(safe(u.getRole()))).count();
                long nbAbonnes = users.stream().filter(u -> "abonne".equalsIgnoreCase(safe(u.getRole()))).count();

                // Sorties & participations
                AnnonceSortieService annonceService = new AnnonceSortieService();
                ParticipationSortieService participationService = new ParticipationSortieService();

                List<AnnonceSortie> sorties = annonceService.getAll();
                long totalSorties = sorties.size();
                long nbOuvertes = sorties.stream().filter(a -> "OUVERTE".equalsIgnoreCase(safe(a.getStatut()))).count();
                long nbAnnulees = sorties.stream().filter(a -> "ANNULEE".equalsIgnoreCase(safe(a.getStatut()))).count();
                long nbFermees = sorties.stream().filter(a -> "FERMEE".equalsIgnoreCase(safe(a.getStatut()))).count();
                long nbAvenir = sorties.stream().filter(a -> a.getDateSortie() != null && a.getDateSortie().isAfter(java.time.LocalDateTime.now())).count();

                long partTotal = participationService.countAll();
                long partPending = participationService.countByStatus("EN_ATTENTE");
                long partConfirmed = participationService.countByStatuses("CONFIRMEE", "ACCEPTEE");
                long partRefused = participationService.countByStatus("REFUSEE");

                Platform.runLater(() -> {
                    VBox statsContainer = buildDashboardWithStats(
                            totalUsers, nbAdmins, nbPartenaires, nbAbonnes,
                            totalSorties, nbOuvertes, nbAvenir, nbAnnulees, nbFermees,
                            partTotal, partPending, partConfirmed, partRefused
                    );
                    setContent(statsContainer);
                });

            } catch (SQLException e) {
                Platform.runLater(() -> {
                    loadAndSetCachedView("dashboard", DASHBOARD_VIEW_PATH);
                    showError("Erreur", "Impossible de charger les statistiques", e.getMessage());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    loadAndSetCachedView("dashboard", DASHBOARD_VIEW_PATH);
                    showError("Erreur", "Erreur Dashboard", e.getMessage());
                });
            }
        }).start();
    }

    private VBox buildDashboardWithStats(
            long totalUsers, long nbAdmins, long nbPartenaires, long nbAbonnes,
            long totalSorties, long nbOuvertes, long nbAvenir, long nbAnnulees, long nbFermees,
            long partTotal, long partPending, long partConfirmed, long partRefused
    ) {
        VBox main = new VBox(20);
        main.setPadding(new Insets(30, 40, 30, 40));
        main.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label("Statistiques des Utilisateurs (Total: " + totalUsers + ")");
        titleLabel.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: #163a5c;");

        BarChart<String, Number> barChart = createBarChart(nbAdmins, nbPartenaires, nbAbonnes);
        barChart.setPrefHeight(450);
        barChart.setMaxWidth(Double.MAX_VALUE);

        Label sortiesTitle = new Label("Statistiques des Sorties (Total: " + totalSorties + ")");
        sortiesTitle.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: #163a5c;");
        BarChart<String, Number> sortiesChart = createSortiesChart(nbOuvertes, nbAvenir, nbAnnulees, nbFermees);
        sortiesChart.setPrefHeight(420);
        sortiesChart.setMaxWidth(Double.MAX_VALUE);

        Label partsTitle = new Label("Statistiques des Participations (Total: " + partTotal + ")");
        partsTitle.setStyle("-fx-font-size: 26; -fx-font-weight: bold; -fx-text-fill: #163a5c;");
        BarChart<String, Number> partsChart = createParticipationsChart(partPending, partConfirmed, partRefused);
        partsChart.setPrefHeight(420);
        partsChart.setMaxWidth(Double.MAX_VALUE);

        main.getChildren().addAll(titleLabel, barChart, sortiesTitle, sortiesChart, partsTitle, partsChart);
        VBox.setVgrow(barChart, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(sortiesChart, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(partsChart, javafx.scene.layout.Priority.ALWAYS);
        return main;
    }

    private BarChart<String, Number> createBarChart(long nbAdmins, long nbPartenaires, long nbAbonnes) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Catégories d'utilisateurs");
        xAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Nombre");
        yAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(true);
        barChart.setBarGap(20);
        barChart.setCategoryGap(40);
        barChart.setStyle(
                "-fx-background-color: rgba(255,255,255,0.85);" +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 25;" +
                        "-fx-border-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
        );

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Admins", nbAdmins));
        series.getData().add(new XYChart.Data<>("Partenaires", nbPartenaires));
        series.getData().add(new XYChart.Data<>("Abonnés", nbAbonnes));

        barChart.getData().add(series);
        return barChart;
    }

    private BarChart<String, Number> createSortiesChart(long nbOuvertes, long nbAvenir, long nbAnnulees, long nbFermees) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Catégories de sorties");
        xAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Nombre");
        yAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(true);
        barChart.setBarGap(20);
        barChart.setCategoryGap(40);
        barChart.setStyle(
                "-fx-background-color: rgba(255,255,255,0.85);" +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 25;" +
                        "-fx-border-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
        );

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Ouvertes", nbOuvertes));
        series.getData().add(new XYChart.Data<>("À venir", nbAvenir));
        series.getData().add(new XYChart.Data<>("Annulées", nbAnnulees));
        series.getData().add(new XYChart.Data<>("Fermées", nbFermees));

        barChart.getData().add(series);
        return barChart;
    }

    private BarChart<String, Number> createParticipationsChart(long pending, long confirmed, long refused) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Statut des participations");
        xAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Nombre");
        yAxis.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 12; -fx-font-weight: bold;");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(true);
        barChart.setBarGap(20);
        barChart.setCategoryGap(40);
        barChart.setStyle(
                "-fx-background-color: rgba(255,255,255,0.85);" +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 25;" +
                        "-fx-border-radius: 16;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 12, 0, 0, 4);"
        );

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("EN_ATTENTE", pending));
        series.getData().add(new XYChart.Data<>("CONFIRMÉES", confirmed));
        series.getData().add(new XYChart.Data<>("REFUSÉES", refused));

        barChart.getData().add(series);
        return barChart;
    }

    // ===== Helpers =====

    private Stage resolveStage() {
        if (primaryStage != null) return primaryStage;
        if (dynamicContent != null && dynamicContent.getScene() != null) {
            return (Stage) dynamicContent.getScene().getWindow();
        }
        return new Stage();
    }

    private void setHeader(String title, String subtitle) {
        if (pageTitle != null) pageTitle.setText(title);
        if (pageSubtitle != null) pageSubtitle.setText(subtitle);
    }

    private void setActive(Button activeBtn) {
        Button[] all = {btnDashboard, btnUtilisateurs, btnSorties, btnLieux, btnOffres, btnEvents, btnGoFront};
        for (Button b : all) {
            if (b == null) continue;
            b.getStyleClass().remove("active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }
    }

    private void setContent(Node node) {
        if (dynamicContent == null) return;
        dynamicContent.getChildren().setAll(node);
    }

    private void refreshHeaderUser() {
        if (userName == null || userRole == null) return;

        String displayName = "Utilisateur";
        String role = "ROLE";

        if (currentUser != null) {
            String full = (safe(currentUser.getPrenom()) + " " + safe(currentUser.getNom())).trim();
            if (!full.isEmpty()) displayName = full;

            String r = safe(currentUser.getRole()).trim();
            if (!r.isEmpty()) role = r.toUpperCase();
        }

        userName.setText(displayName);
        userRole.setText(role);
    }

    private void startNotifPolling() {
        stopNotifPolling();
        if (currentUser == null || currentUser.getId() <= 0) return;

        notifPoller = new Timeline(new KeyFrame(Duration.seconds(5), e -> refreshUnreadBadgeAsync()));
        notifPoller.setCycleCount(Timeline.INDEFINITE);
        notifPoller.play();
    }

    private void stopNotifPolling() {
        if (notifPoller != null) {
            try { notifPoller.stop(); } catch (Exception ignored) {}
            notifPoller = null;
        }
    }

    private void refreshUnreadBadgeAsync() {
        if (currentUser == null || currentUser.getId() <= 0) {
            setBadgeValue(0);
            return;
        }

        int uid = currentUser.getId();
        new Thread(() -> {
            long c;
            try {
                c = notificationService.countUnread(uid);
            } catch (Exception e) {
                c = 0;
            }
            long finalC = c;
            Platform.runLater(() -> setBadgeValue(finalC));
        }, "back-notif-unread-poll").start();
    }

    private void setBadgeValue(long count) {
        if (notifBadge == null) return;
        long v = Math.max(0, count);
        boolean show = v > 0;
        notifBadge.setText(v > 99 ? "99+" : String.valueOf(v));
        notifBadge.setVisible(show);
        notifBadge.setManaged(show);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void loadAndSetCachedView(String cacheKey, String fxmlPath) {
        Node cached = viewCache.get(cacheKey);
        if (cached != null) {
            Object controller = controllerCache.get(cacheKey);
            invokeIfExists(controller, "setPrimaryStage", Stage.class, resolveStage());
            invokeIfExists(controller, "setCurrentUser", User.class, currentUser);
            setContent(cached);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (loader.getLocation() == null) throw new RuntimeException("FXML introuvable: " + fxmlPath);

            Node view = loader.load();
            Object controller = loader.getController();

            invokeIfExists(controller, "setPrimaryStage", Stage.class, resolveStage());
            invokeIfExists(controller, "setCurrentUser", User.class, currentUser);

            viewCache.put(cacheKey, view);
            controllerCache.put(cacheKey, controller);
            setContent(view);

        } catch (IOException e) {
            showError("Erreur de chargement", "Impossible de charger la vue", e.getMessage());
        }
    }

    private void invokeIfExists(Object target, String methodName, Class<?> paramType, Object value) {
        if (target == null || value == null) return;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, value);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            showError("Erreur", "Injection controller", "Problème lors de " + methodName + "(): " + e.getMessage());
        }
    }

    private void showError(String title, String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }
}