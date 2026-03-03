package controllers.front.gamification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.gamification.Badge;

import java.util.List;

/**
 * Popup de notification qui s'affiche quand l'utilisateur gagne un nouveau badge.
 * Design inspiré des toasts modernes (coin bas droit).
 */
public class BadgeNotificationPopup {

    /**
     * Affiche une notification pour chaque badge nouvellement obtenu.
     * Les notifications sont empilées avec un délai.
     *
     * @param stage    La fenêtre principale de l'application
     * @param badges   Liste des nouveaux badges à notifier
     */
    public static void show(Stage stage, List<Badge> badges) {
        if (badges == null || badges.isEmpty() || stage == null) return;

        Platform.runLater(() -> {
            for (int i = 0; i < badges.size(); i++) {
                Badge badge = badges.get(i);
                int delay = i * 600; // 600ms entre chaque notification
                Timeline delayTl = new Timeline(new KeyFrame(
                    Duration.millis(delay),
                    e -> showSingleBadgeNotification(stage, badge)
                ));
                delayTl.play();
            }
        });
    }

    private static void showSingleBadgeNotification(Stage stage, Badge badge) {
        Popup popup = new Popup();
        popup.setAutoFix(true);
        popup.setAutoHide(false);

        // ── Contenu du popup ──────────────────────────────────────────
        HBox container = new HBox(14);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(14, 18, 14, 18));
        container.setStyle("""
            -fx-background-color: linear-gradient(to right, #0D2137, #1A3A5C);
            -fx-background-radius: 12;
            -fx-border-color: #4FC3F7;
            -fx-border-radius: 12;
            -fx-border-width: 1.5;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 16, 0, 0, 6);
            -fx-min-width: 300px;
            -fx-max-width: 360px;
        """);

        // Emoji badge
        Label emojiLbl = new Label(badge.getEmoji() != null ? badge.getEmoji() : "🏅");
        emojiLbl.setStyle("-fx-font-size: 34px;");

        // Texte
        VBox textBox = new VBox(3);
        Label titleLbl = new Label("🎉 Nouveau badge obtenu !");
        titleLbl.setStyle("-fx-text-fill: #4FC3F7; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label badgeNameLbl = new Label(badge.getNom());
        badgeNameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label descLbl = new Label(badge.getDescription() != null ? badge.getDescription() : "");
        descLbl.setStyle("-fx-text-fill: #90A4AE; -fx-font-size: 11px; -fx-wrap-text: true;");
        descLbl.setMaxWidth(230);

        Label bonusLbl = new Label("+" + badge.getPointsBonus() + " pts bonus !");
        bonusLbl.setStyle("-fx-text-fill: #FFD700; -fx-font-size: 11px; -fx-font-weight: bold;");

        textBox.getChildren().addAll(titleLbl, badgeNameLbl, descLbl);
        if (badge.getPointsBonus() > 0) {
            textBox.getChildren().add(bonusLbl);
        }

        container.getChildren().addAll(emojiLbl, textBox);
        popup.getContent().add(container);

        // ── Positionnement ────────────────────────────────────────────
        popup.show(stage);

        double x = stage.getX() + stage.getWidth() - 390;
        double y = stage.getY() + stage.getHeight() - 160;
        popup.setX(x);
        popup.setY(y);

        // ── Animations ────────────────────────────────────────────────
        container.setOpacity(0);
        container.setTranslateY(30);

        // Entrée
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), container);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), container);
        slideIn.setFromY(30);
        slideIn.setToY(0);

        ParallelTransition enterAnim = new ParallelTransition(fadeIn, slideIn);

        // Maintien puis sortie
        PauseTransition pause = new PauseTransition(Duration.seconds(4));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), container);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());

        SequentialTransition fullAnim = new SequentialTransition(enterAnim, pause, fadeOut);
        fullAnim.play();
    }
}
