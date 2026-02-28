package utils.ui;

import models.lieux.Lieu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AccessibilityManager — Singleton global pour le mode neurodiversité.
 *
 * Ce mode adapte l'interface pour les personnes avec troubles cognitifs
 * ou neurodiversité (TDAH, autisme, dyslexie, dyscalculie…) :
 *   - Interface ultra-simplifiée (peu de texte, icônes grandes)
 *   - Couleurs très contrastées, pas de surcharge visuelle
 *   - Informations essentielles uniquement (prix, lieu, type)
 *   - Navigation intuitive et rapide
 */
public final class AccessibilityManager {

    // ── Singleton ──────────────────────────────────────────────────────────
    private static final AccessibilityManager INSTANCE = new AccessibilityManager();
    private AccessibilityManager() {}
    public static AccessibilityManager get() { return INSTANCE; }

    // ── State ──────────────────────────────────────────────────────────────
    private boolean neuroMode = false;

    /** Listeners notifiés à chaque changement de mode */
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();

    // ── API publique ───────────────────────────────────────────────────────

    public boolean isNeuroMode() {
        return neuroMode;
    }

    /** Active ou désactive le mode neurodiversité et notifie tous les listeners */
    public void setNeuroMode(boolean enabled) {
        this.neuroMode = enabled;
        notifyAll(enabled);
    }

    /** Bascule le mode et retourne le nouvel état */
    public boolean toggleNeuroMode() {
        setNeuroMode(!neuroMode);
        return neuroMode;
    }

    /**
     * Enregistre un listener appelé quand le mode change.
     * @param listener Consumer<Boolean> — reçoit true si neuro actif
     */
    public void addListener(Consumer<Boolean> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<Boolean> listener) {
        listeners.remove(listener);
    }

    // ── Helpers CSS ────────────────────────────────────────────────────────

    /**
     * Applique ou retire la classe CSS "neuro-mode" sur un nœud racine.
     * À appeler dans initialize() de chaque contrôleur qui veut réagir.
     */
    public void applyClass(javafx.scene.Node root) {
        if (root == null) return;
        if (neuroMode) {
            if (!root.getStyleClass().contains("neuro-mode"))
                root.getStyleClass().add("neuro-mode");
        } else {
            root.getStyleClass().remove("neuro-mode");
        }
    }

    /**
     * Installe un listener qui applique automatiquement la classe CSS
     * sur le nœud à chaque changement de mode.
     */
    public void bindClass(javafx.scene.Node root) {
        if (root == null) return;
        applyClass(root);
        addListener(active -> javafx.application.Platform.runLater(() -> applyClass(root)));
    }

    // ── Formatage du résumé texte simplifié ───────────────────────────────

    /**
     * Retourne un résumé ultra-court pour un lieu en mode neuro.
     * Format : "Musée · Tunis · 5–20 TND"
     */
    public static String buildNeuroSummary(Lieu l) {
        if (l == null) return "";
        StringBuilder sb = new StringBuilder();

        String cat    = safe(l.getCategorie());
        String ville  = safe(l.getVille());
        String budget = buildBudgetText(l.getBudgetMin(), l.getBudgetMax());

        if (!cat.isEmpty())    sb.append(cat);
        if (!ville.isEmpty())  { if (sb.length() > 0) sb.append("  ·  "); sb.append(ville); }
        if (!budget.isEmpty()) { if (sb.length() > 0) sb.append("  ·  "); sb.append(budget); }

        return sb.toString();
    }

    public static String buildBudgetText(Double min, Double max) {
        if (min == null && max == null) return "Gratuit";
        if (min != null && min == 0 && (max == null || max == 0)) return "Gratuit";
        if (min != null && max != null) return String.format("%.0f–%.0f TND", min, max);
        if (min != null) return String.format("dès %.0f TND", min);
        return String.format("max %.0f TND", max);
    }

    /** Emoji icône pour une catégorie de lieu */
    public static String categoryIcon(String categorie) {
        if (categorie == null) return "📍";
        return switch (categorie.toLowerCase().trim()) {
            case "restaurant", "restauration" -> "🍽️";
            case "café", "cafe", "cafétéria"  -> "☕";
            case "musée", "musee"              -> "🏛️";
            case "parc", "jardin"              -> "🌿";
            case "plage", "mer"                -> "🏖️";
            case "sport", "fitness", "gym"     -> "🏃";
            case "shopping", "boutique"        -> "🛍️";
            case "cinéma", "cinema"            -> "🎬";
            case "hôtel", "hotel", "hébergement" -> "🏨";
            case "bar", "boîte", "boite"       -> "🎵";
            case "art", "galerie"              -> "🎨";
            case "éducation", "école", "université" -> "📚";
            case "santé", "clinique", "pharmacie"   -> "⚕️";
            default -> "📍";
        };
    }

    /** Emoji icône ambiance selon le type PUBLIC/PRIVE */
    public static String ambianceIcon(String type, String categorie) {
        if ("PRIVE".equalsIgnoreCase(safe(type))) return "🔒 Privé";
        String cat = safe(categorie).toLowerCase();
        if (cat.contains("plage") || cat.contains("parc")) return "🌤️ En plein air";
        if (cat.contains("restau") || cat.contains("café")) return "🪑 Ambiance calme";
        if (cat.contains("sport")) return "⚡ Dynamique";
        if (cat.contains("musée") || cat.contains("art")) return "🤫 Silencieux";
        return "🏠 Intérieur";
    }

    // ── Private ────────────────────────────────────────────────────────────

    private void notifyAll(boolean active) {
        for (Consumer<Boolean> l : new ArrayList<>(listeners)) {
            try { l.accept(active); } catch (Exception ignored) {}
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
