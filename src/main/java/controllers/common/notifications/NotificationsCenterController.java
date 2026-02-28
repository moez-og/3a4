package controllers.common.notifications;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import models.notifications.Notification;
import models.users.User;
import services.notifications.NotificationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotificationsCenterController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private VBox listBox;
    @FXML private Label emptyLabel;

    @FXML private Button btnRefresh;
    @FXML private Button btnMarkAll;

    private final NotificationService notificationService = new NotificationService();

    private User currentUser;
    private Runnable onChange;

    public void setCurrentUser(User u) {
        this.currentUser = u;
        refresh();
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
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
            if (i < items.size() - 1) listBox.getChildren().add(new Separator());
        }
    }

    private Node renderRow(Notification n) {
        VBox wrapper = new VBox(6);
        wrapper.setPadding(new Insets(10, 10, 10, 10));

        Label title = new Label(safe(n.getTitle()));
        title.setStyle(n.isUnread()
                ? "-fx-font-weight: 900; -fx-text-fill: #163a5c;"
                : "-fx-font-weight: 800; -fx-text-fill: rgba(22,58,92,0.85);"
        );

        Label body = new Label(safe(n.getBody()));
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: rgba(22,58,92,0.78);");

        LocalDateTime dt = n.getCreatedAt();
        String when = (dt == null) ? "" : DT_FMT.format(dt);
        Label meta = new Label(when);
        meta.setStyle("-fx-text-fill: rgba(22,58,92,0.60); -fx-font-size: 11px; -fx-font-weight: 800;");

        VBox texts = new VBox(3, title, body, meta);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Label unreadDot = new Label(n.isUnread() ? "●" : "");
        unreadDot.setStyle("-fx-text-fill: rgba(201,146,15,0.95); -fx-font-weight: 900;");
        unreadDot.setMinWidth(14);

        Button mark = new Button("Marquer lu");
        mark.setVisible(n.isUnread());
        mark.setManaged(n.isUnread());
        mark.setStyle("-fx-background-color: rgba(15,23,42,0.06); -fx-background-radius: 12; -fx-font-weight: 900; -fx-cursor: hand;");
        mark.setOnAction(e -> {
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
