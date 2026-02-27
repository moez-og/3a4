package analytics;

import java.util.List;
import java.util.Map;

/**
 * Conteneur immuable de tous les résultats analytiques calculés par StatEngine.
 * Passé en un seul objet au contrôleur JavaFX.
 */
public record AnalyticsResult(

    // ── Heatmap Âge × Catégorie ────────────────────────────────────
    // heatmap.get("18-24").get("CAFE") = nb favoris
    Map<String, Map<String, Long>> heatmap,

    // Listes ordonnées pour les axes
    List<String> ageSlices,
    List<String> categories,

    // Valeur max dans la heatmap (pour normaliser l'intensité des couleurs)
    long heatmapMax,

    // ── Popularité des lieux (score pondéré) ──────────────────────
    List<LieuScore> topLieux,

    // ── Distribution par budget × tranche d'âge ──────────────────
    // budgetAge.get("18-24").get("< 20 TND") = nb favoris
    Map<String, Map<String, Long>> budgetAge,

    // ── KPIs globaux ──────────────────────────────────────────────
    int  totalFavoris,
    int  totalUsersActifs,
    int  totalLieux,
    double moyenneFavorisParUser,

    // ── Catégorie dominante par tranche d'âge ────────────────────
    // dominante.get("18-24") = "CAFE"
    Map<String, String> dominanteParAge,

    // ── Corrélation Pearson : âge vs budget moyen ─────────────────
    double pearsonAgeBudget,

    // ── Entropie de diversité géographique (Shannon) ──────────────
    // entropie.get("18-24") = valeur entre 0 et ln(nb_villes)
    Map<String, Double> entropieParAge,

    // ── Nombre de favoris par rôle ─────────────────────────────────
    Map<String, Long> favorisByRole

) {
    /**
     * Score pondéré d'un lieu.
     * score = 0.55 * nb_favoris_norm + 0.30 * avg_note_norm + 0.15 * recence_norm
     */
    public record LieuScore(
        int    lieuId,
        String nom,
        String categorie,
        String ville,
        long   nbFavoris,
        double avgNote,
        double scoreGlobal,
        double tendance       // ratio (semaine actuelle / semaine précédente), NaN si pas de données
    ) implements Comparable<LieuScore> {
        @Override
        public int compareTo(LieuScore o) {
            return Double.compare(o.scoreGlobal, this.scoreGlobal);
        }
    }
}
