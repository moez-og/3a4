package utils.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.util.Duration;
import models.lieux.Lieu;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * LieuSearchAutoComplete
 * ──────────────────────
 * Attaches a live-search dropdown to a TextField.
 *
 * Features:
 *  • Debounced input (200 ms) to avoid flooding
 *  • Max 7 suggestions
 *  • Highlights matching text inline (bold + color)
 *  • Keyboard navigation: ↑ ↓ Enter / Escape
 *  • Click to select
 *  • "Aucun lieu trouvé" fallback
 *  • Smooth fade-in animation
 *
 * Usage:
 *   LieuSearchAutoComplete.attach(searchField, allLieux, lieu -> { ... navigate ... });
 */
public class LieuSearchAutoComplete {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final int    MAX_SUGGESTIONS = 7;
    private static final long   DEBOUNCE_MS     = 200;
    private static final double POPUP_WIDTH     = 320;
    private static final double ROW_HEIGHT      = 52;

    // ── State ─────────────────────────────────────────────────────────────────
    private final TextField        field;
    private final List<Lieu>       source;
    private final Consumer<Lieu>   onSelect;
    private final Popup            popup;
    private final VBox             listBox;
    private final List<VBox>       rows       = new ArrayList<>();
    private int                    selIndex   = -1;
    private Timer                  debouncer  = null;
    private boolean                suppressListener = false;

    // ─────────────────────────────────────────────────────────────────────────

    private LieuSearchAutoComplete(TextField field,
                                   List<Lieu> source,
                                   Consumer<Lieu> onSelect) {
        this.field    = field;
        this.source   = source;
        this.onSelect = onSelect;

        // ── Build popup container ──
        listBox = new VBox(0);
        listBox.setStyle(
            "-fx-background-color:white;" +
            "-fx-background-radius:14;" +
            "-fx-border-color:rgba(15,23,42,0.10);" +
            "-fx-border-radius:14;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.14),18,0,0,6);"
        );
        listBox.setPrefWidth(POPUP_WIDTH);
        listBox.setMaxWidth(POPUP_WIDTH);

        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(listBox);

        installHandlers();
    }

    /**
     * Attach autocomplete to the given TextField.
     *
     * @param field     The search TextField to enhance.
     * @param source    Master list of all lieux (not mutated).
     * @param onSelect  Called when the user picks a suggestion.
     * @return          The created instance (for lifecycle control if needed).
     */
    public static LieuSearchAutoComplete attach(TextField field,
                                                 List<Lieu> source,
                                                 Consumer<Lieu> onSelect) {
        return new LieuSearchAutoComplete(field, source, onSelect);
    }

    /**
     * Refresh the source data (call after reloading lieux from DB).
     * Thread-safe.
     */
    public void refreshSource(List<Lieu> newSource) {
        synchronized (source) {
            source.clear();
            source.addAll(newSource);
        }
    }

    // ── Handler installation ──────────────────────────────────────────────────

    private void installHandlers() {
        // Text change → debounced search
        field.textProperty().addListener((obs, old, nv) -> {
            if (suppressListener) return;
            scheduleSearch(nv == null ? "" : nv.trim());
        });

        // Keyboard: ↑ ↓ Enter Escape
        field.setOnKeyPressed(e -> {
            if (!popup.isShowing()) return;

            switch (e.getCode()) {
                case DOWN -> {
                    e.consume();
                    moveSelection(+1);
                }
                case UP -> {
                    e.consume();
                    moveSelection(-1);
                }
                case ENTER -> {
                    e.consume();
                    if (selIndex >= 0 && selIndex < rows.size()) {
                        activateRow(selIndex);
                    }
                }
                case ESCAPE -> {
                    e.consume();
                    hidePopup();
                }
                default -> {}
            }
        });

        // Hide popup when field loses focus (with tiny delay so click on popup registers first)
        field.focusedProperty().addListener((obs, wasFocused, nowFocused) -> {
            if (!nowFocused) {
                new Timer(true).schedule(new TimerTask() {
                    @Override public void run() {
                        Platform.runLater(() -> hidePopup());
                    }
                }, 180);
            }
        });
    }

    // ── Debounced search ──────────────────────────────────────────────────────

    private void scheduleSearch(String query) {
        if (debouncer != null) debouncer.cancel();
        if (query.length() < 1) {
            Platform.runLater(this::hidePopup);
            return;
        }
        debouncer = new Timer(true);
        debouncer.schedule(new TimerTask() {
            @Override public void run() {
                Platform.runLater(() -> performSearch(query));
            }
        }, DEBOUNCE_MS);
    }

    // ── Search logic ──────────────────────────────────────────────────────────

    private void performSearch(String query) {
        String q = query.toLowerCase();
        List<Lieu> matches = new ArrayList<>();

        synchronized (source) {
            for (Lieu l : source) {
                if (matches.size() >= MAX_SUGGESTIONS) break;
                String blob = (safe(l.getNom()) + " " + safe(l.getVille()) + " " + safe(l.getCategorie())).toLowerCase();
                if (blob.contains(q)) matches.add(l);
            }
        }

        buildDropdown(matches, query);
    }

    // ── Dropdown builder ──────────────────────────────────────────────────────

    private void buildDropdown(List<Lieu> matches, String query) {
        listBox.getChildren().clear();
        rows.clear();
        selIndex = -1;

        if (matches.isEmpty()) {
            VBox emptyRow = buildEmptyRow();
            listBox.getChildren().add(emptyRow);
        } else {
            for (int i = 0; i < matches.size(); i++) {
                VBox row = buildRow(matches.get(i), query, i);
                rows.add(row);
                listBox.getChildren().add(row);
                // Add divider (except after last)
                if (i < matches.size() - 1) {
                    Region div = new Region();
                    div.setStyle("-fx-background-color:rgba(15,23,42,0.06);-fx-min-height:1;-fx-max-height:1;");
                    div.setMaxWidth(Double.MAX_VALUE);
                    listBox.getChildren().add(div);
                }
            }
        }

        showPopup();
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    private VBox buildRow(Lieu l, String query, int index) {
        // Highlighted name
        TextFlow nameFlow = buildHighlight(safe(l.getNom()), query);

        // Subtitle: ville + categorie
        String sub = buildSubtitle(l);
        Label subLbl = new Label(sub);
        subLbl.setStyle("-fx-font-size:11px;-fx-text-fill:rgba(15,23,42,0.48);-fx-font-weight:600;");

        // Category badge
        String cat = safe(l.getCategorie()).trim();
        HBox rightBox = new HBox();
        if (!cat.isEmpty()) {
            Label badge = new Label(cat);
            badge.setStyle(
                "-fx-font-size:9px;-fx-font-weight:800;-fx-text-fill:#1e3a5f;" +
                "-fx-background-color:rgba(30,58,95,0.09);" +
                "-fx-background-radius:999;-fx-padding:2 8;"
            );
            rightBox.getChildren().add(badge);
        }
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox textCol = new VBox(2, nameFlow, subLbl);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox content = new HBox(12, textCol, rightBox);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox row = new VBox(content);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setMinHeight(ROW_HEIGHT);
        row.setPrefWidth(POPUP_WIDTH);
        row.setStyle(rowStyle(false));

        // Hover
        row.setOnMouseEntered(e -> {
            setSelectionAt(index);
        });
        row.setOnMouseExited(e -> {
            if (selIndex == index) {
                row.setStyle(rowStyle(true));
            }
        });

        // Click
        row.setOnMouseClicked(e -> activateRow(index));

        return row;
    }

    private VBox buildEmptyRow() {
        Label lbl = new Label("Aucun lieu trouvé");
        lbl.setStyle(
            "-fx-font-size:13px;-fx-text-fill:rgba(15,23,42,0.45);-fx-font-weight:700;" +
            "-fx-padding:14 14;"
        );
        VBox row = new VBox(lbl);
        row.setStyle("-fx-background-color:white;");
        return row;
    }

    // ── Highlight builder ─────────────────────────────────────────────────────

    private TextFlow buildHighlight(String text, String query) {
        TextFlow flow = new TextFlow();
        String lower = text.toLowerCase();
        String q     = query.toLowerCase();

        int start = 0;
        int idx;
        while ((idx = lower.indexOf(q, start)) >= 0) {
            if (idx > start) {
                Text normal = new Text(text.substring(start, idx));
                normal.setStyle("-fx-font-size:13px;-fx-fill:#0f172a;");
                flow.getChildren().add(normal);
            }
            Text matched = new Text(text.substring(idx, idx + q.length()));
            matched.setStyle("-fx-font-size:13px;-fx-fill:#1e3a5f;-fx-font-weight:900;");
            flow.getChildren().add(matched);
            start = idx + q.length();
        }
        if (start < text.length()) {
            Text rest = new Text(text.substring(start));
            rest.setStyle("-fx-font-size:13px;-fx-fill:#0f172a;");
            flow.getChildren().add(rest);
        }
        if (flow.getChildren().isEmpty()) {
            Text t = new Text(text);
            t.setStyle("-fx-font-size:13px;-fx-fill:#0f172a;");
            flow.getChildren().add(t);
        }
        return flow;
    }

    private String buildSubtitle(Lieu l) {
        String v = safe(l.getVille()).trim();
        String a = safe(l.getAdresse()).trim();
        if (!v.isEmpty() && !a.isEmpty()) return "📍 " + v + " · " + a;
        if (!v.isEmpty()) return "📍 " + v;
        if (!a.isEmpty()) return a;
        return "";
    }

    private String rowStyle(boolean selected) {
        return selected
            ? "-fx-background-color:rgba(30,58,95,0.07);-fx-cursor:hand;"
            : "-fx-background-color:white;-fx-cursor:hand;";
    }

    // ── Keyboard selection ────────────────────────────────────────────────────

    private void moveSelection(int delta) {
        if (rows.isEmpty()) return;
        int next = selIndex + delta;
        next = Math.max(0, Math.min(next, rows.size() - 1));
        setSelectionAt(next);
    }

    private void setSelectionAt(int index) {
        // De-highlight previous
        if (selIndex >= 0 && selIndex < rows.size()) {
            rows.get(selIndex).setStyle(rowStyle(false));
        }
        selIndex = index;
        if (selIndex >= 0 && selIndex < rows.size()) {
            rows.get(selIndex).setStyle(rowStyle(true));
        }
    }

    private void activateRow(int index) {
        // We need to know which Lieu this index corresponds to.
        // The rows list mirrors the matches list built in buildDropdown.
        // We re-search to get the same list (same query is still in the field).
        String query = safe(field.getText()).trim();
        List<Lieu> matches = new ArrayList<>();
        synchronized (source) {
            for (Lieu l : source) {
                if (matches.size() >= MAX_SUGGESTIONS) break;
                String blob = (safe(l.getNom()) + " " + safe(l.getVille()) + " " + safe(l.getCategorie())).toLowerCase();
                if (blob.contains(query.toLowerCase())) matches.add(l);
            }
        }
        if (index >= 0 && index < matches.size()) {
            Lieu chosen = matches.get(index);
            suppressListener = true;
            field.setText(safe(chosen.getNom()));
            suppressListener = false;
            hidePopup();
            if (onSelect != null) onSelect.accept(chosen);
        }
    }

    // ── Popup show/hide ───────────────────────────────────────────────────────

    private void showPopup() {
        if (field.getScene() == null) return;

        // Position under the TextField
        javafx.geometry.Bounds b = field.localToScreen(field.getBoundsInLocal());
        if (b == null) return;

        popup.show(field.getScene().getWindow(), b.getMinX(), b.getMaxY() + 4);
        listBox.setPrefWidth(Math.max(POPUP_WIDTH, b.getWidth()));

        // Fade in
        listBox.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(140), listBox);
        ft.setToValue(1);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ft.play();
    }

    private void hidePopup() {
        if (popup.isShowing()) {
            FadeTransition ft = new FadeTransition(Duration.millis(100), listBox);
            ft.setToValue(0);
            ft.setOnFinished(e -> popup.hide());
            ft.play();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String safe(String s) { return s == null ? "" : s; }
}
