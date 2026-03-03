package utils.ui;

import javafx.animation.*;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * GestureNavigationManager
 * ─────────────────────────
 * Installs two global "go back" triggers on any JavaFX Scene:
 *
 *  1. TOUCH  — 2-finger swipe RIGHT  (touchmove deltaX > threshold)
 *  2. DESKTOP — Alt + ← (ArrowLeft)
 *  3. MOUSE-SCROLL — 2-finger horizontal scroll (trackpad) : very fast rightward
 *
 * Usage:
 *   GestureNavigationManager.install(scene, historyStack, navigator);
 */
public class GestureNavigationManager {

    // ── Tuning constants ──────────────────────────────────────────────────────
    /** Minimum horizontal swipe distance (px) to trigger back navigation. */
    private static final double SWIPE_THRESHOLD_PX = 80.0;

    /**
     * For trackpad scroll events: horizontal scroll delta accumulator threshold.
     * A single "flick" on a trackpad produces many small ScrollEvents; we sum
     * them and trigger only when the accumulated total crosses this value.
     */
    private static final double SCROLL_THRESHOLD_PX = 120.0;

    /** Cooldown between successive back-navigations (ms) to avoid double-fire. */
    private static final long COOLDOWN_MS = 700;

    // ── State ─────────────────────────────────────────────────────────────────
    private double   touchStartX  = 0;
    private double   touchStartY  = 0;
    private long     lastFireTime = 0;
    private double   scrollAccumX = 0;   // accumulated horizontal scroll (trackpad)

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Deque<String> history;
    private final ShellNavigator navigator;
    private Scene scene;

    // ─────────────────────────────────────────────────────────────────────────

    private GestureNavigationManager(Scene scene,
                                     Deque<String> history,
                                     ShellNavigator navigator) {
        this.scene     = scene;
        this.history   = history;
        this.navigator = navigator;
        installHandlers();
    }

    /**
     * Install gesture navigation on the given scene.
     *
     * @param scene      The primary scene to attach event filters to.
     * @param history    Mutable navigation history stack (top = current route).
     *                   Must be shared with the navigator so it can push/pop correctly.
     * @param navigator  The shell navigator used to perform the actual navigation.
     */
    public static GestureNavigationManager install(Scene scene,
                                                    Deque<String> history,
                                                    ShellNavigator navigator) {
        return new GestureNavigationManager(scene, history, navigator);
    }

    // ── Handler installation ──────────────────────────────────────────────────

    private void installHandlers() {
        // ① Keyboard: Alt + ← → back
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPress);

        // ② Touch events (touch-screen / tablet)
        scene.addEventFilter(TouchEvent.TOUCH_PRESSED,  this::onTouchPressed);
        scene.addEventFilter(TouchEvent.TOUCH_MOVED,    this::onTouchMoved);

        // ③ Scroll events: 2-finger trackpad horizontal swipe
        //    We use SCROLL (not SwipeEvent) because JavaFX SwipeEvent requires
        //    an actual touch screen; trackpad horizontal scroll fires ScrollEvent.
        scene.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
    }

    // ── ① Keyboard ────────────────────────────────────────────────────────────

    private void onKeyPress(KeyEvent e) {
        if (e.isAltDown() && e.getCode() == KeyCode.LEFT) {
            e.consume();
            triggerBack("Alt + ←");
        }
    }

    // ── ② Touch ───────────────────────────────────────────────────────────────

    private void onTouchPressed(TouchEvent e) {
        if (e.getTouchCount() == 2) {
            // Record start position of the first touch point
            touchStartX = e.getTouchPoint().getX();
            touchStartY = e.getTouchPoint().getY();
        }
    }

    private void onTouchMoved(TouchEvent e) {
        if (e.getTouchCount() != 2) return;

        double dx = e.getTouchPoint().getX() - touchStartX;
        double dy = e.getTouchPoint().getY() - touchStartY;

        // Require mostly horizontal motion (not a vertical scroll attempt)
        if (Math.abs(dx) < Math.abs(dy) * 1.4) return;

        if (dx > SWIPE_THRESHOLD_PX) {
            e.consume();
            triggerBack("swipe ↔");
            // Reset so repeated moves don't fire multiple times
            touchStartX = e.getTouchPoint().getX();
        }
    }

    // ── ③ Trackpad horizontal scroll ─────────────────────────────────────────

    private void onScroll(ScrollEvent e) {
        double dx = e.getDeltaX();
        double dy = e.getDeltaY();

        // Only process if it's a PREDOMINANTLY horizontal movement
        // Ratio 2:1 — horizontal must be at least 2x the vertical component
        if (Math.abs(dx) < Math.abs(dy) * 2.0) {
            // Vertical scroll — reset accumulator and do NOT interfere
            scrollAccumX = 0;
            return;
        }

        // Also ignore tiny values (noise / inertia tails)
        if (Math.abs(dx) < 4.0) return;

        scrollAccumX += dx;

        if (scrollAccumX > SCROLL_THRESHOLD_PX) {
            scrollAccumX = 0;
            e.consume();
            triggerBack("trackpad →");
        } else if (scrollAccumX < -SCROLL_THRESHOLD_PX) {
            scrollAccumX = 0; // leftward — ignore for now
        }
    }

    // ── Core back logic ───────────────────────────────────────────────────────

    private void triggerBack(String source) {
        long now = System.currentTimeMillis();
        if (now - lastFireTime < COOLDOWN_MS) return;
        lastFireTime = now;

        if (history == null || history.isEmpty()) {
            showGestureToast("Vous êtes déjà sur la page d'accueil", false);
            return;
        }

        // Peek at current without removing (history might not have been pushed yet)
        String current = history.peek();

        // Find the previous route — it's the second element
        // We need at least 2 distinct entries in history to go back
        if (history.size() <= 1) {
            showGestureToast("Vous êtes déjà sur la page d'accueil", false);
            return;
        }

        // Pop current page off the stack
        history.pop();

        // Peek at what's now on top (= previous page)
        String prev = history.peek();
        if (prev == null) {
            // Restore current to avoid empty history
            if (current != null) history.push(current);
            showGestureToast("Vous êtes déjà sur la page d'accueil", false);
            return;
        }

        // Pop prev too — navigate() will push it back, avoiding duplication
        history.pop();

        showGestureToast("← Retour", true);
        navigator.navigate(prev);
    }

    // ── Visual feedback toast ─────────────────────────────────────────────────

    private void showGestureToast(String message, boolean success) {
        if (scene == null) return;

        // Build toast label
        Label lbl = new Label((success ? "← " : "⚠ ") + message);
        lbl.setStyle(
            "-fx-background-color:" + (success ? "rgba(15,23,42,0.82)" : "rgba(120,60,0,0.82)") + ";" +
            "-fx-background-radius:30;" +
            "-fx-text-fill:white;" +
            "-fx-font-size:13px;" +
            "-fx-font-weight:800;" +
            "-fx-padding:10 22;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.28),14,0,0,4);"
        );

        Popup popup = new Popup();
        popup.getContent().add(lbl);
        popup.setAutoHide(false);
        popup.show(scene.getWindow());

        // Center-bottom positioning
        double x = scene.getWindow().getX() + scene.getWidth() / 2 - 100;
        double y = scene.getWindow().getY() + scene.getHeight() - 90;
        popup.setX(x);
        popup.setY(y);

        // Animate: fade in → hold → fade out
        lbl.setOpacity(0);
        Timeline tl = new Timeline(
            new KeyFrame(Duration.ZERO,          new KeyValue(lbl.opacityProperty(), 0.0)),
            new KeyFrame(Duration.millis(180),   new KeyValue(lbl.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(1500),  new KeyValue(lbl.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(1900),  new KeyValue(lbl.opacityProperty(), 0.0))
        );
        tl.setOnFinished(e -> popup.hide());
        tl.play();
    }
}
