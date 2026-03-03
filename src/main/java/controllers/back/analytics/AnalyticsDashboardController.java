package controllers.back.analytics;

import analytics.AnalyticsResult;
import analytics.StatEngine;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  AnalyticsDashboardController                                   ║
 * ║  Dashboard Data Science — Back-Office                           ║
 * ║                                                                  ║
 * ║  Sections :                                                     ║
 * ║   1. KPI Cards (4 métriques clés, animation compteur)           ║
 * ║   2. Heatmap Âge × Catégorie (custom JavaFX, gradient couleur)  ║
 * ║   3. Top 10 Lieux (barres animées + indicateur tendance)        ║
 * ║   4. Budget × Âge (barres groupées custom)                      ║
 * ║   5. Pearson + Shannon (métriques avancées avec gauge)          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public class AnalyticsDashboardController {

    // ── FXML ────────────────────────────────────────────────────────
    @FXML private VBox     rootBox;
    @FXML private StackPane loadingOverlay;
    @FXML private Label    loadingLabel;

    // KPIs
    @FXML private Label kpiFavoris;
    @FXML private Label kpiUsers;
    @FXML private Label kpiLieux;
    @FXML private Label kpiMoyenne;

    // Heatmap
    @FXML private GridPane    heatmapGrid;
    @FXML private Label       heatmapLegendMin;
    @FXML private Label       heatmapLegendMax;
    @FXML private VBox        heatmapSection;

    // Top lieux
    @FXML private VBox        topLieuxBars;
    @FXML private Label       topLieuxSubtitle;

    // Budget × Âge
    @FXML private VBox        budgetAgeChart;

    // Métriques avancées
    @FXML private Label       pearsonLabel;
    @FXML private Label       pearsonInterp;
    @FXML private HBox        pearsonGauge;
    @FXML private VBox        shannonList;
    @FXML private Label       dominanteInfo;

    // ── Couleurs heatmap ────────────────────────────────────────────
    private static final Color HEATMAP_LOW  = Color.rgb(239, 246, 255);   // bleu très pâle
    private static final Color HEATMAP_MID  = Color.rgb(59,  130, 246);   // bleu vif
    private static final Color HEATMAP_HIGH = Color.rgb(30,  64,  175);   // bleu foncé

    // Palette barres Top Lieux
    private static final String[] BAR_COLORS = {
        "#d4af37", "#c9920f", "#b87333",
        "#a16207", "#92400e", "#78350f",
        "#6b7280", "#4b5563", "#374151", "#1f2937"
    };

    private AnalyticsResult result;

    // ════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        showLoading(true);
        Task<AnalyticsResult> task = new Task<>() {
            @Override
            protected AnalyticsResult call() {
                return new StatEngine().compute();
            }
        };
        task.setOnSucceeded(e -> {
            result = task.getValue();
            Platform.runLater(() -> {
                showLoading(false);
                renderAll();
            });
        });
        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                showLoading(false);
                showError(task.getException().getMessage());
            });
        });
        new Thread(task, "analytics-engine").start();
    }

    // ════════════════════════════════════════════════════════════════
    //  RENDU COMPLET
    // ════════════════════════════════════════════════════════════════

    private void renderAll() {
        renderKPIs();
        renderHeatmap();
        renderTopLieux();
        renderBudgetAge();
        renderMetriquesAvancees();
    }

    // ════════════════════════════════════════════════════════════════
    //  1. KPI CARDS
    // ════════════════════════════════════════════════════════════════

    private void renderKPIs() {
        animateCounter(kpiFavoris, 0, result.totalFavoris(), "", 800);
        animateCounter(kpiUsers,   0, result.totalUsersActifs(), "", 900);
        animateCounter(kpiLieux,   0, result.totalLieux(), "", 700);
        // Moyenne avec 1 décimale
        animateCounterDouble(kpiMoyenne, 0.0, result.moyenneFavorisParUser(), " fav/user", 1000);
    }

    private void animateCounter(Label label, int from, int to, String suffix, int durationMs) {
        Timeline tl = new Timeline();
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(durationMs), e -> {}));
        tl.setCycleCount(1);

        // Interpolation frame par frame
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            final int idx = i;
            double t = (double) i / steps;
            double eased = easeOutCubic(t);
            int val = (int) (from + (to - from) * eased);
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis((double) durationMs * i / steps),
                    ev -> label.setText(val + suffix)
            ));
        }
        label.setText(to + suffix);
        tl.play();
    }

    private void animateCounterDouble(Label label, double from, double to,
                                       String suffix, int durationMs) {
        Timeline tl = new Timeline();
        int steps = 40;
        for (int i = 0; i <= steps; i++) {
            final int idx = i;
            double t   = (double) i / steps;
            double val = from + (to - from) * easeOutCubic(t);
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis((double) durationMs * i / steps),
                    ev -> label.setText(String.format("%.1f", val) + suffix)
            ));
        }
        tl.play();
    }

    private double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    // ════════════════════════════════════════════════════════════════
    //  2. HEATMAP ÂGE × CATÉGORIE
    // ════════════════════════════════════════════════════════════════

    private void renderHeatmap() {
        if (heatmapGrid == null) return;
        heatmapGrid.getChildren().clear();
        heatmapGrid.getColumnConstraints().clear();
        heatmapGrid.getRowConstraints().clear();

        List<String> ages = result.ageSlices();
        List<String> cats = result.categories();

        if (ages.isEmpty() || cats.isEmpty()) {
            heatmapGrid.add(makeLabel("Données insuffisantes", "#6b7280", 12, false), 0, 0);
            return;
        }

        int CELL_W = 72, CELL_H = 52;

        // Colonne vide (labels lignes)
        heatmapGrid.getColumnConstraints().add(colConstraint(90));
        for (String cat : cats)
            heatmapGrid.getColumnConstraints().add(colConstraint(CELL_W + 4));

        // En-tête colonnes (catégories)
        heatmapGrid.add(new Label(""), 0, 0);
        for (int c = 0; c < cats.size(); c++) {
            Label lbl = makeLabel(cats.get(c), "#1e3a5f", 10, true);
            lbl.setRotate(-35);
            lbl.setMaxWidth(CELL_W);
            StackPane wrap = new StackPane(lbl);
            wrap.setPrefHeight(48);
            heatmapGrid.add(wrap, c + 1, 0);
        }

        // Lignes (tranches d'âge)
        for (int r = 0; r < ages.size(); r++) {
            String age = ages.get(r);
            Map<String, Long> row = result.heatmap().getOrDefault(age, Map.of());

            // Label ligne
            Label ageLabel = makeLabel(age, "#1e3a5f", 11, true);
            ageLabel.setMinWidth(85);
            heatmapGrid.add(ageLabel, 0, r + 1);

            for (int c = 0; c < cats.size(); c++) {
                String cat  = cats.get(c);
                long   val  = row.getOrDefault(cat, 0L);
                double norm = (double) val / result.heatmapMax(); // 0 → 1

                Rectangle rect = new Rectangle(CELL_W, CELL_H);
                rect.setArcWidth(10); rect.setArcHeight(10);
                rect.setFill(heatmapColor(norm));

                // Valeur dans la cellule
                Label valLabel = makeLabel(val > 0 ? String.valueOf(val) : "",
                        norm > 0.55 ? "white" : "#1e3a5f", 12, true);

                StackPane cell = new StackPane(rect, valLabel);
                cell.setCursor(Cursor.HAND);

                // Tooltip enrichi
                Tooltip tt = new Tooltip(
                        age + "  ×  " + cat + "\n" +
                        "Favoris : " + val + "\n" +
                        "Part :    " + String.format("%.1f%%", norm * 100)
                );
                tt.setStyle("-fx-font-size:11px; -fx-background-color:#1e3a5f; -fx-text-fill:white;");
                Tooltip.install(cell, tt);

                // Animation entrée (fade + scale)
                cell.setOpacity(0);
                cell.setScaleX(0.7); cell.setScaleY(0.7);
                final int delay = (r * cats.size() + c) * 22;
                PauseTransition pause = new PauseTransition(Duration.millis(delay));
                pause.setOnFinished(ev -> {
                    FadeTransition ft = new FadeTransition(Duration.millis(220), cell);
                    ft.setFromValue(0); ft.setToValue(1);
                    ScaleTransition st = new ScaleTransition(Duration.millis(220), cell);
                    st.setFromX(0.7); st.setToX(1);
                    st.setFromY(0.7); st.setToY(1);
                    new ParallelTransition(ft, st).play();
                });
                pause.play();

                GridPane.setMargin(cell, new Insets(2));
                heatmapGrid.add(cell, c + 1, r + 1);
            }
        }

        // Légende gradient
        if (heatmapLegendMin != null) heatmapLegendMin.setText("0");
        if (heatmapLegendMax != null) heatmapLegendMax.setText(String.valueOf(result.heatmapMax()));
    }

    /**
     * Interpolation couleur trilinéaire : blanc → bleu → bleu foncé
     * norm ∈ [0, 1]
     */
    private Color heatmapColor(double norm) {
        if (norm <= 0) return HEATMAP_LOW;
        if (norm >= 1) return HEATMAP_HIGH;
        if (norm < 0.5) {
            double t = norm / 0.5;
            return HEATMAP_LOW.interpolate(HEATMAP_MID, t);
        } else {
            double t = (norm - 0.5) / 0.5;
            return HEATMAP_MID.interpolate(HEATMAP_HIGH, t);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  3. TOP 10 LIEUX (barres horizontales animées)
    // ════════════════════════════════════════════════════════════════

    private void renderTopLieux() {
        if (topLieuxBars == null) return;
        topLieuxBars.getChildren().clear();

        List<AnalyticsResult.LieuScore> top = result.topLieux();
        if (top.isEmpty()) {
            topLieuxBars.getChildren().add(makeLabel("Aucune donnée", "#6b7280", 12, false));
            return;
        }

        if (topLieuxSubtitle != null)
            topLieuxSubtitle.setText("Score = 55% popularité + 30% note + 15% récence");

        double maxScore = top.stream().mapToDouble(AnalyticsResult.LieuScore::scoreGlobal)
                             .max().orElse(1.0);

        for (int i = 0; i < top.size(); i++) {
            AnalyticsResult.LieuScore lieu = top.get(i);
            double pct = maxScore > 0 ? lieu.scoreGlobal() / maxScore : 0;
            String color = BAR_COLORS[Math.min(i, BAR_COLORS.length - 1)];

            // Rang
            Label rankLbl = makeLabel(String.format("#%d", i + 1), "#9ca3af", 11, true);
            rankLbl.setMinWidth(28);

            // Nom + meta
            Label nomLbl  = makeLabel(lieu.nom(), "#0f172a", 12, true);
            nomLbl.setMinWidth(130); nomLbl.setMaxWidth(160); nomLbl.setWrapText(true);
            Label metaLbl = makeLabel(lieu.categorie() + " · " + lieu.ville(),
                    "#6b7280", 10, false);

            VBox nomBox = new VBox(2, nomLbl, metaLbl);
            nomBox.setMinWidth(160);

            // Barre
            double barW = 260;
            Rectangle bg   = new Rectangle(barW, 20);
            bg.setArcWidth(8); bg.setArcHeight(8);
            bg.setFill(Color.web("#f1f5f9"));

            Rectangle bar  = new Rectangle(0, 20);
            bar.setArcWidth(8); bar.setArcHeight(8);
            bar.setFill(Color.web(color));

            // Score
            Label scoreLbl = makeLabel(String.format("%.3f", lieu.scoreGlobal()),
                    color, 11, true);
            scoreLbl.setMinWidth(48);

            // Tendance ↑ ↓ →
            String tendStr = "→";
            String tendColor = "#6b7280";
            if (!Double.isNaN(lieu.tendance())) {
                if (lieu.tendance() > 1.15)      { tendStr = "↑"; tendColor = "#16a34a"; }
                else if (lieu.tendance() < 0.85) { tendStr = "↓"; tendColor = "#dc2626"; }
            }
            Label tendLbl = makeLabel(tendStr, tendColor, 16, true);
            tendLbl.setMinWidth(22);

            StackPane barPane = new StackPane(bg, bar);
            StackPane.setAlignment(bar, Pos.CENTER_LEFT);
            barPane.setAlignment(Pos.CENTER_LEFT);

            HBox row = new HBox(8, rankLbl, nomBox, barPane, scoreLbl, tendLbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));

            topLieuxBars.getChildren().add(row);

            // Animation barre
            final int delayMs = i * 80;
            final double targetW = barW * pct;
            PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
            pause.setOnFinished(ev -> {
                Timeline tl = new Timeline(new KeyFrame(Duration.millis(500),
                        new KeyValue(bar.widthProperty(), targetW,
                                Interpolator.EASE_BOTH)));
                tl.play();
            });
            pause.play();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  4. BUDGET × ÂGE (barres groupées custom)
    // ════════════════════════════════════════════════════════════════

    private void renderBudgetAge() {
        if (budgetAgeChart == null) return;
        budgetAgeChart.getChildren().clear();

        Map<String, Map<String, Long>> data = result.budgetAge();
        if (data.isEmpty()) return;

        List<String> budgetSlices = List.of("Gratuit", "< 20 TND", "20-80 TND", "> 80 TND");
        String[] bColors = {"#22c55e", "#3b82f6", "#f59e0b", "#ef4444"};

        // Légende
        HBox legend = new HBox(14);
        legend.setPadding(new Insets(0, 0, 10, 0));
        for (int b = 0; b < budgetSlices.size(); b++) {
            Rectangle dot = new Rectangle(12, 12);
            dot.setArcWidth(4); dot.setArcHeight(4);
            dot.setFill(Color.web(bColors[b]));
            legend.getChildren().addAll(dot, makeLabel(budgetSlices.get(b), "#374151", 11, false));
        }
        budgetAgeChart.getChildren().add(legend);

        List<String> ages = result.ageSlices();

        // Valeur max pour normaliser
        long maxVal = data.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToLong(Long::longValue).max().orElse(1L);

        for (String age : ages) {
            Map<String, Long> row = data.getOrDefault(age, Map.of());

            Label ageLbl = makeLabel(age, "#1e3a5f", 11, true);
            ageLbl.setMinWidth(55);

            HBox bars = new HBox(4);
            bars.setAlignment(Pos.BOTTOM_LEFT);

            double maxBarH = 60.0;

            for (int b = 0; b < budgetSlices.size(); b++) {
                long val = row.getOrDefault(budgetSlices.get(b), 0L);
                double h = maxVal > 0 ? (double) val / maxVal * maxBarH : 0;

                Rectangle bar = new Rectangle(28, 4); // démarre petit
                bar.setArcWidth(4); bar.setArcHeight(4);
                bar.setFill(Color.web(bColors[b]));

                Tooltip tt = new Tooltip(age + " · " + budgetSlices.get(b) + " : " + val);
                tt.setStyle("-fx-font-size:11px;");
                Tooltip.install(bar, tt);

                VBox barWrap = new VBox(bar);
                barWrap.setAlignment(Pos.BOTTOM_CENTER);
                barWrap.setMinHeight(maxBarH + 4);
                bars.getChildren().add(barWrap);

                // Animation hauteur
                final double targetH = Math.max(4, h);
                PauseTransition pause = new PauseTransition(Duration.millis(b * 60));
                pause.setOnFinished(ev -> {
                    Timeline tl = new Timeline(new KeyFrame(Duration.millis(450),
                            new KeyValue(bar.heightProperty(), targetH, Interpolator.EASE_OUT)));
                    tl.play();
                });
                pause.play();
            }

            Label valSumLbl = makeLabel(
                    row.values().stream().mapToLong(Long::longValue).sum() + " fav",
                    "#9ca3af", 10, false);

            HBox ageRow = new HBox(10, ageLbl, bars, valSumLbl);
            ageRow.setAlignment(Pos.CENTER_LEFT);
            ageRow.setPadding(new Insets(3, 0, 3, 0));
            budgetAgeChart.getChildren().add(ageRow);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  5. MÉTRIQUES AVANCÉES (Pearson + Shannon + Dominante)
    // ════════════════════════════════════════════════════════════════

    private void renderMetriquesAvancees() {
        // ── Pearson ────────────────────────────────────────────────
        double r = result.pearsonAgeBudget();
        if (pearsonLabel != null)
            pearsonLabel.setText(String.format("r = %.3f", r));

        if (pearsonInterp != null) {
            String interp;
            String color;
            double absR = Math.abs(r);
            if      (absR < 0.10) { interp = "Aucune corrélation";       color = "#6b7280"; }
            else if (absR < 0.30) { interp = "Corrélation faible";       color = "#f59e0b"; }
            else if (absR < 0.50) { interp = "Corrélation modérée";      color = "#3b82f6"; }
            else if (absR < 0.70) { interp = "Corrélation forte";        color = "#8b5cf6"; }
            else                  { interp = "Corrélation très forte";   color = "#d4af37"; }
            if (r < 0) interp = "↘ " + interp + " (inverse)";
            else       interp = "↗ " + interp;
            pearsonInterp.setText(interp);
            pearsonInterp.setStyle("-fx-text-fill:" + color + "; -fx-font-weight:900;");
        }

        // Jauge Pearson (barre -1 → +1)
        if (pearsonGauge != null) {
            pearsonGauge.getChildren().clear();
            double barW = 280;
            Rectangle bg = new Rectangle(barW, 10);
            bg.setArcWidth(5); bg.setArcHeight(5);
            bg.setFill(Color.web("#e2e8f0"));

            // Position du curseur
            double pos = ((r + 1) / 2.0) * barW;
            Rectangle cursor = new Rectangle(4, 18);
            cursor.setFill(Color.web("#d4af37"));

            StackPane gauge = new StackPane(bg, cursor);
            StackPane.setAlignment(cursor, Pos.CENTER_LEFT);
            cursor.setTranslateX(pos - barW / 2.0);
            gauge.setPrefWidth(barW);

            HBox gaugeRow = new HBox(6,
                    makeLabel("-1", "#6b7280", 10, false),
                    gauge,
                    makeLabel("+1", "#6b7280", 10, false));
            gaugeRow.setAlignment(Pos.CENTER);
            pearsonGauge.getChildren().add(gaugeRow);
        }

        // ── Shannon (entropie géographique) ─────────────────────
        if (shannonList != null) {
            shannonList.getChildren().clear();
            Map<String, Double> entropie = result.entropieParAge();
            double maxH = entropie.values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(1.0);

            result.ageSlices().forEach(age -> {
                double h = entropie.getOrDefault(age, 0.0);
                double norm = maxH > 0 ? h / maxH : 0;
                String label = h < 0.5  ? "Mono-ville"
                             : h < 1.5  ? "Peu diversifié"
                             : h < 2.5  ? "Diversifié"
                             : "Explorateur";

                Label ageLbl = makeLabel(age, "#1e3a5f", 11, true);
                ageLbl.setMinWidth(55);

                Rectangle barBg = new Rectangle(180, 12);
                barBg.setArcWidth(6); barBg.setArcHeight(6);
                barBg.setFill(Color.web("#e2e8f0"));

                Rectangle barFg = new Rectangle(0, 12);
                barFg.setArcWidth(6); barFg.setArcHeight(6);
                barFg.setFill(norm > 0.6 ? Color.web("#8b5cf6") : Color.web("#a78bfa"));

                StackPane barStack = new StackPane(barBg, barFg);
                StackPane.setAlignment(barFg, Pos.CENTER_LEFT);

                Label hLbl  = makeLabel(String.format("H=%.2f", h), "#6b7280", 10, false);
                hLbl.setMinWidth(50);
                Label catLbl = makeLabel(label,
                        norm > 0.6 ? "#7c3aed" : "#6b7280", 10, true);
                catLbl.setMinWidth(90);

                HBox row = new HBox(8, ageLbl, barStack, hLbl, catLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(2, 0, 2, 0));
                shannonList.getChildren().add(row);

                // Animer
                Timeline tl = new Timeline(new KeyFrame(Duration.millis(500),
                        new KeyValue(barFg.widthProperty(), 180 * norm, Interpolator.EASE_OUT)));
                tl.play();
            });
        }

        // ── Catégorie dominante par âge ──────────────────────────
        if (dominanteInfo != null) {
            StringBuilder sb = new StringBuilder();
            result.ageSlices().forEach(age -> {
                String cat = result.dominanteParAge().getOrDefault(age, "—");
                sb.append(age).append(" → ").append(cat).append("   ");
            });
            dominanteInfo.setText(sb.toString().trim());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UTILITAIRES UI
    // ════════════════════════════════════════════════════════════════

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(show);
            loadingOverlay.setManaged(show);
        }
    }

    private void showError(String msg) {
        if (rootBox != null) {
            Label err = makeLabel("Erreur : " + msg, "#dc2626", 13, true);
            rootBox.getChildren().add(0, err);
        }
    }

    private Label makeLabel(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        l.setStyle("-fx-text-fill:" + color + ";");
        return l;
    }

    private ColumnConstraints colConstraint(double w) {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setMinWidth(w); cc.setPrefWidth(w);
        return cc;
    }
}
