package controllers.common.notifications;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import models.notifications.Notification;
import models.notifications.NotificationType;
import models.users.User;
import services.notifications.NotificationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationsCenterController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private VBox listBox;
    @FXML private Label emptyLabel;

    @FXML private Button btnRefresh;
    @FXML private Button btnMarkAll;

    private final NotificationService notificationService = new NotificationService();

    private User currentUser;
    private Runnable onChange;
    private Consumer<Notification> onOpen;

    private static final Pattern JSON_INT_FIELD = Pattern.compile("\\\"([a-zA-Z0-9_]+)\\\"\\s*:\\s*(\\d+)");

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refresh();
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
    }

    /**
     * Called when the user clicks a notification row.
     * The handler is responsible for navigating to the target view.
     */
    public void setOnOpen(Consumer<Notification> handler) {
        this.onOpen = handler;
    }

    @FXML
    private void initialize() {
        if (btnRefresh != null) btnRefresh.setOnAction(e -> refresh());
        if (btnMarkAll != null) btnMarkAll.setOnAction(e -> markAllAsRead());
    }

    public void refresh() {
        if (listBox == null) return;
        listBox.getChildren().clear();

        if (currentUser == null || currentUser.getId() <= 0) {
            showEmpty("Session non définie.");
            return;
        }

        List<Notification> items;
        try {
            items = notificationService.listNotifications(currentUser.getId(), null, null, 1, 50);
        } catch (Exception e) {
            showEmpty("Erreur chargement notifications: " + safe(e.getMessage()));
            return;
        }

        if (items.isEmpty()) {
            showEmpty("Aucune notification.");
            return;
        }

        hideEmpty();
        for (int i = 0; i < items.size(); i++) {
            Notification n = items.get(i);
            listBox.getChildren().add(renderRow(n));
        }
    }

    private Node renderRow(Notification n) {
        VBox wrapper = new VBox(8);
        wrapper.setPadding(new Insets(12, 12, 12, 12));
        wrapper.setStyle(baseRowStyle(n, false));
        wrapper.setOnMouseEntered(e -> wrapper.setStyle(baseRowStyle(n, true)));
        wrapper.setOnMouseExited(e -> wrapper.setStyle(baseRowStyle(n, false)));
        wrapper.setOnMouseClicked(e -> openNotification(n));

        Label badge = new Label(typeLabel(n.getType()));
        badge.setStyle(typeBadgeStyle(n.getType()));

        Label title = new Label(safe(n.getTitle()));
        title.setStyle(n.isUnread()
            ? "-fx-font-weight: 900; -fx-text-fill: #163a5c; -fx-font-size: 13px;"
            : "-fx-font-weight: 800; -fx-text-fill: rgba(22,58,92,0.85); -fx-font-size: 13px;"
        );

        Label body = new Label(safe(n.getBody()));
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: rgba(22,58,92,0.78); -fx-font-size: 12px;");

        LocalDateTime dt = n.getCreatedAt();
        String when = (dt == null) ? "" : DT_FMT.format(dt);
        Label meta = new Label(when);
        meta.setStyle("-fx-text-fill: rgba(22,58,92,0.60); -fx-font-size: 11px; -fx-font-weight: 800;");

        HBox head = new HBox(8, badge, title);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox texts = new VBox(4, head, body, meta);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Label unreadDot = new Label(n.isUnread() ? "●" : "");
        unreadDot.setStyle("-fx-text-fill: rgba(201,146,15,0.95); -fx-font-weight: 900;");
        unreadDot.setMinWidth(14);

        Button mark = new Button("Marquer lu");
        mark.setVisible(n.isUnread());
        mark.setManaged(n.isUnread());
        mark.setStyle("-fx-background-color: rgba(15,23,42,0.06); -fx-background-radius: 12; -fx-font-weight: 900; -fx-cursor: hand;");
        mark.setOnAction(e -> {
            e.consume();
            try {
                notificationService.markAsRead(n.getId(), currentUser.getId());
                if (onChange != null) onChange.run();
                refresh();
            } catch (Exception ex) {
                alert("Erreur", safe(ex.getMessage()));
            }
        });

        VBox right = new VBox(6, unreadDot, mark);
        right.setAlignment(Pos.TOP_RIGHT);
        right.setMinWidth(90);

        HBox row = new HBox(12, texts, right);
        row.setAlignment(Pos.TOP_LEFT);
        wrapper.getChildren().add(row);

        return wrapper;
    }

    private void openNotification(Notification n) {
        if (n == null) return;
        if (currentUser == null || currentUser.getId() <= 0) return;

        try {
            if (n.isUnread()) {
                notificationService.markAsRead(n.getId(), currentUser.getId());
                if (onChange != null) onChange.run();
            }
        } catch (Exception ignored) {
        }

        try {
            if (onOpen != null) {
                onOpen.accept(n);
            }
        } catch (Exception ignored) {
        }

        closeWindow();
    }

    private void closeWindow() {
        try {
            if (listBox == null || listBox.getScene() == null) return;
            var w = listBox.getScene().getWindow();
            if (w instanceof javafx.stage.Stage s) s.close();
        } catch (Exception ignored) {
        }
    }

    private static String typeLabel(NotificationType t) {
        if (t == null) return "Notification";
        return switch (t) {
            case PARTICIPATION_REQUESTED -> "Participation";
            case PARTICIPATION_ACCEPTED -> "Acceptée";
            case PARTICIPATION_REFUSED -> "Refusée";
            case PARTICIPATION_CANCELLED -> "Annulée";
            case CHAT_MESSAGE -> "Chat";
            case SORTIE_UPDATED, SORTIE_CANCELLED, SORTIE_DELETED -> "Sortie";
        };
    }

    private static String typeBadgeStyle(NotificationType t) {
        // Keep palette aligned with existing UI (no new colors).
        if (t == null) {
            return "-fx-background-color: rgba(15,23,42,0.06); -fx-text-fill: rgba(22,58,92,0.85); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
        }
        return switch (t) {
            case PARTICIPATION_ACCEPTED -> "-fx-background-color: rgba(34,197,94,0.14); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case PARTICIPATION_REFUSED -> "-fx-background-color: rgba(239,68,68,0.12); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case PARTICIPATION_CANCELLED -> "-fx-background-color: rgba(245,158,11,0.14); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case PARTICIPATION_REQUESTED -> "-fx-background-color: rgba(201,146,15,0.18); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case CHAT_MESSAGE -> "-fx-background-color: rgba(15,23,42,0.06); -fx-text-fill: rgba(22,58,92,0.88); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case SORTIE_UPDATED -> "-fx-background-color: rgba(15,23,42,0.06); -fx-text-fill: rgba(22,58,92,0.88); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case SORTIE_CANCELLED -> "-fx-background-color: rgba(245,158,11,0.14); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
            case SORTIE_DELETED -> "-fx-background-color: rgba(239,68,68,0.10); -fx-text-fill: rgba(22,58,92,0.92); -fx-font-weight: 900; -fx-background-radius: 12; -fx-padding: 3 10;";
        };
    }

    private static String baseRowStyle(Notification n, boolean hover) {
        boolean unread = (n != null && n.isUnread());
        String bg = hover
                ? "rgba(15,23,42,0.06)"
                : (unread ? "rgba(201,146,15,0.08)" : "rgba(15,23,42,0.03)");

        return "-fx-background-color: " + bg + ";"
                + " -fx-background-radius: 16;"
                + " -fx-border-color: rgba(22,58,92,0.10);"
                + " -fx-border-radius: 16;"
                + " -fx-cursor: hand;";
    }

    /** Minimal helper for metadata like {\"sortieId\":123,\"demandeId\":456}. */
    public static Integer extractIntField(String json, String key) {
        if (json == null || key == null) return null;
        Matcher m = JSON_INT_FIELD.matcher(json);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                try {
                    return Integer.parseInt(m.group(2));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private void markAllAsRead() {
        if (currentUser == null || currentUser.getId() <= 0) return;
        try {
            notificationService.markAllAsRead(currentUser.getId());
            if (onChange != null) onChange.run();
            refresh();
        } catch (Exception e) {
            alert("Erreur", safe(e.getMessage()));
        }
    }

    private void showEmpty(String msg) {
        if (emptyLabel == null) return;
        emptyLabel.setText(msg);
        emptyLabel.setVisible(true);
        emptyLabel.setManaged(true);
    }

    private void hideEmpty() {
        if (emptyLabel == null) return;
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String safe(String s) { return s == null ? "" : s; }
}
