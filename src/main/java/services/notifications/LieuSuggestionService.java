package services.notifications;

import models.lieux.Lieu;
import models.notifications.LieuNotification;
import models.notifications.LieuNotification.Type;
import services.lieux.FavoriLieuService;
import services.lieux.LieuService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LieuSuggestionService
 * ──────────────────────
 * Generates up to MAX_SUGGESTIONS notification suggestions based on the user's
 * favorite lieux.
 *
 * Strategy (in priority order):
 *   1. Same category as a favorite, not already favorited
 *   2. Same ville as a favorite, not already favorited
 *   3. Similar budget range, not already favorited
 *
 * Results are de-duplicated and capped at MAX_SUGGESTIONS.
 */
public class LieuSuggestionService {

    private static final int MAX_SUGGESTIONS = 5;

    private final FavoriLieuService favoriService = new FavoriLieuService();
    private final LieuService       lieuService   = new LieuService();

    /**
     * Generate suggestion notifications for a given user.
     * Returns an empty list if the user has no favorites or no matching lieux.
     */
    public List<LieuNotification> generateSuggestions(int userId) {
        List<LieuNotification> result = new ArrayList<>();

        // ── Load user favorites ──────────────────────────────────────────────
        List<Lieu> favoris;
        try {
            favoris = favoriService.getFavorisForUser(userId);
        } catch (Exception e) {
            return result; // DB issue — return empty silently
        }
        if (favoris == null || favoris.isEmpty()) return result;

        // ── Load all lieux ───────────────────────────────────────────────────
        List<Lieu> allLieux;
        try {
            allLieux = lieuService.getAll();
        } catch (Exception e) {
            return result;
        }
        if (allLieux == null || allLieux.isEmpty()) return result;

        // ── Build sets for quick lookup ──────────────────────────────────────
        Set<Integer> favoriIds = favoris.stream()
            .map(Lieu::getId)
            .collect(Collectors.toSet());

        Set<String> favoriCategories = favoris.stream()
            .map(l -> safe(l.getCategorie()).toLowerCase())
            .filter(c -> !c.isEmpty())
            .collect(Collectors.toSet());

        Set<String> favoriVilles = favoris.stream()
            .map(l -> safe(l.getVille()).toLowerCase())
            .filter(v -> !v.isEmpty())
            .collect(Collectors.toSet());

        // Average budget of favorites (for similarity matching)
        double avgBudget = favoris.stream()
            .mapToDouble(l -> {
                if (l.getBudgetMin() != null && l.getBudgetMax() != null)
                    return (l.getBudgetMin() + l.getBudgetMax()) / 2.0;
                if (l.getBudgetMax() != null) return l.getBudgetMax();
                if (l.getBudgetMin() != null) return l.getBudgetMin();
                return 0;
            })
            .average().orElse(0);

        // ── Candidate lieux: not already favorited ───────────────────────────
        List<Lieu> candidates = allLieux.stream()
            .filter(l -> !favoriIds.contains(l.getId()))
            .collect(Collectors.toList());

        // Shuffle to vary suggestions across sessions
        Collections.shuffle(candidates);

        // Track added lieu IDs to avoid duplicates
        Set<Integer> addedIds = new HashSet<>();

        // ── Strategy 1: same category ────────────────────────────────────────
        if (!favoriCategories.isEmpty()) {
            for (Lieu l : candidates) {
                if (result.size() >= MAX_SUGGESTIONS) break;
                if (addedIds.contains(l.getId())) continue;
                String cat = safe(l.getCategorie()).toLowerCase();
                if (!cat.isEmpty() && favoriCategories.contains(cat)) {
                    // Find the matching favorite for the reason string
                    String matchFavName = favoris.stream()
                        .filter(f -> safe(f.getCategorie()).equalsIgnoreCase(l.getCategorie()))
                        .map(f -> safe(f.getNom()))
                        .findFirst().orElse("vos favoris");
                    result.add(new LieuNotification(l, Type.SUGGESTION_SAME_CATEGORY,
                        "Basé sur votre favori : " + matchFavName));
                    addedIds.add(l.getId());
                }
            }
        }

        // ── Strategy 2: same ville ───────────────────────────────────────────
        if (result.size() < MAX_SUGGESTIONS && !favoriVilles.isEmpty()) {
            for (Lieu l : candidates) {
                if (result.size() >= MAX_SUGGESTIONS) break;
                if (addedIds.contains(l.getId())) continue;
                String ville = safe(l.getVille()).toLowerCase();
                if (!ville.isEmpty() && favoriVilles.contains(ville)) {
                    String matchFavName = favoris.stream()
                        .filter(f -> safe(f.getVille()).equalsIgnoreCase(l.getVille()))
                        .map(f -> safe(f.getNom()))
                        .findFirst().orElse("vos favoris");
                    result.add(new LieuNotification(l, Type.SUGGESTION_SAME_VILLE,
                        "Proche de votre favori : " + matchFavName));
                    addedIds.add(l.getId());
                }
            }
        }

        // ── Strategy 3: similar budget ───────────────────────────────────────
        if (result.size() < MAX_SUGGESTIONS && avgBudget > 0) {
            double tolerance = avgBudget * 0.5; // 50% range
            double lo = avgBudget - tolerance;
            double hi = avgBudget + tolerance;

            for (Lieu l : candidates) {
                if (result.size() >= MAX_SUGGESTIONS) break;
                if (addedIds.contains(l.getId())) continue;
                double bud = budgetMoyen(l);
                if (bud > 0 && bud >= lo && bud <= hi) {
                    result.add(new LieuNotification(l, Type.SUGGESTION_SIMILAR_BUDGET,
                        String.format("Budget similaire à vos favoris (~%.0f TND)", avgBudget)));
                    addedIds.add(l.getId());
                }
            }
        }

        return result;
    }

    private double budgetMoyen(Lieu l) {
        if (l.getBudgetMin() != null && l.getBudgetMax() != null)
            return (l.getBudgetMin() + l.getBudgetMax()) / 2.0;
        if (l.getBudgetMax() != null) return l.getBudgetMax();
        if (l.getBudgetMin() != null) return l.getBudgetMin();
        return 0;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
