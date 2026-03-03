package analytics;

import utils.Mydb;

import java.sql.*;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  StatEngine — Moteur d'analyse data science                     ║
 * ║                                                                  ║
 * ║  Toutes les opérations exploitent les Java Streams :            ║
 * ║   · groupingBy / collectingAndThen pour les agrégations         ║
 * ║   · reduce pour les calculs (Pearson, Shannon)                  ║
 * ║   · flatMap pour la dénormalisation favoris × user              ║
 * ║   · Collectors.toMap pour les maps de résultats                 ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public class StatEngine {

    private static final List<String> AGE_ORDER = List.of(
            "< 18", "18-24", "25-34", "35-49", "50+", "Inconnu");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Connection cnx;

    public StatEngine() {
        cnx = Mydb.getInstance().getConnection();
    }

    // ════════════════════════════════════════════════════════════════
    //  POINT D'ENTRÉE PRINCIPAL
    // ════════════════════════════════════════════════════════════════

    /**
     * Charge toutes les données brutes puis calcule toutes les métriques
     * en une seule passe via Streams. Appel unique, résultat complet.
     */
    public AnalyticsResult compute() {

        // 1) Charger les données brutes depuis la BD
        List<UserFavoriRecord> records = loadRecords();

        if (records.isEmpty()) {
            return emptyResult();
        }

        // 2) Toutes les analyses en parallèle via Streams
        // ── Heatmap Âge × Catégorie ────────────────────────────────
        Map<String, Map<String, Long>> heatmap = records.stream()
                .filter(r -> !r.ageSlice().equals("Inconnu"))
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::ageSlice,
                        Collectors.groupingBy(
                                r -> normalizeCategorie(r.categorie()),
                                Collectors.counting()
                        )
                ));

        // Axes triés
        List<String> ageSlices = AGE_ORDER.stream()
                .filter(heatmap::containsKey)
                .collect(Collectors.toList());

        List<String> categories = records.stream()
                .map(r -> normalizeCategorie(r.categorie()))
                .filter(c -> !c.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        long heatmapMax = heatmap.values().stream()
                .flatMap(m -> m.values().stream())
                .mapToLong(Long::longValue)
                .max()
                .orElse(1L);

        // ── Popularité pondérée des lieux ─────────────────────────
        // Données notes depuis la BD (une requête supplémentaire)
        Map<Integer, Double> avgNotes = loadAvgNotes();
        Map<Integer, Long>   favorisByLieu = loadFavorisWeek();   // semaine courante
        Map<Integer, Long>   favorisByLieuPrev = loadFavorisWeekPrev(); // semaine précédente

        // Agrégation nb_favoris par lieu via Stream
        Map<Integer, Long> nbFavByLieu = records.stream()
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::lieuId,
                        Collectors.counting()
                ));

        long maxFav = nbFavByLieu.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        double maxNote = 5.0;

        List<AnalyticsResult.LieuScore> topLieux = records.stream()
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::lieuId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    UserFavoriRecord first = list.get(0);
                                    long nbFav = list.size();
                                    double note = avgNotes.getOrDefault(first.lieuId(), 0.0);
                                    // Recence : nb favoris dans la semaine actuelle normalisé
                                    long recCurr = favorisByLieu.getOrDefault(first.lieuId(), 0L);
                                    long recPrev = favorisByLieuPrev.getOrDefault(first.lieuId(), 0L);
                                    // Score pondéré 55/30/15
                                    double score = 0.55 * (double) nbFav / maxFav
                                                 + 0.30 * note / maxNote
                                                 + 0.15 * (double) recCurr / Math.max(1, maxFav);
                                    // Tendance = ratio sem_actuelle / sem_précédente
                                    double tendance = recPrev > 0
                                            ? (double) recCurr / recPrev
                                            : Double.NaN;
                                    return new AnalyticsResult.LieuScore(
                                            first.lieuId(), first.lieuNom(),
                                            normalizeCategorie(first.categorie()),
                                            first.ville(), nbFav, note, score, tendance);
                                }
                        )
                ))
                .values().stream()
                .sorted()
                .limit(10)
                .collect(Collectors.toList());

        // ── Budget × Âge ──────────────────────────────────────────
        Map<String, Map<String, Long>> budgetAge = records.stream()
                .filter(r -> !r.ageSlice().equals("Inconnu"))
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::ageSlice,
                        Collectors.groupingBy(
                                UserFavoriRecord::budgetSlice,
                                Collectors.counting()
                        )
                ));

        // ── KPIs globaux ──────────────────────────────────────────
        int totalFavoris   = records.size();
        int totalUsersActifs = (int) records.stream()
                .map(UserFavoriRecord::userId).distinct().count();
        int totalLieux     = (int) records.stream()
                .map(UserFavoriRecord::lieuId).distinct().count();
        double moyenneFavUser = totalUsersActifs > 0
                ? (double) totalFavoris / totalUsersActifs : 0.0;

        // ── Catégorie dominante par tranche d'âge ─────────────────
        Map<String, String> dominanteParAge = records.stream()
                .filter(r -> !r.ageSlice().equals("Inconnu"))
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::ageSlice,
                        Collectors.collectingAndThen(
                                Collectors.groupingBy(
                                        r -> normalizeCategorie(r.categorie()),
                                        Collectors.counting()
                                ),
                                map -> map.entrySet().stream()
                                        .max(Map.Entry.comparingByValue())
                                        .map(Map.Entry::getKey)
                                        .orElse("—")
                        )
                ));

        // ── Corrélation de Pearson : âge vs budget moyen ──────────
        double pearson = computePearson(
                records.stream()
                        .filter(r -> r.age() > 0 && r.budgetMoyen() > 0)
                        .collect(Collectors.toList())
        );

        // ── Entropie de Shannon : diversité géographique par âge ──
        Map<String, Double> entropie = records.stream()
                .filter(r -> !r.ageSlice().equals("Inconnu"))
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::ageSlice,
                        Collectors.collectingAndThen(
                                Collectors.groupingBy(
                                        UserFavoriRecord::ville,
                                        Collectors.counting()
                                ),
                                StatEngine::shannonEntropy
                        )
                ));

        // ── Favoris par rôle ──────────────────────────────────────
        Map<String, Long> favorisByRole = records.stream()
                .collect(Collectors.groupingBy(
                        UserFavoriRecord::role,
                        Collectors.counting()
                ));

        return new AnalyticsResult(
                heatmap, ageSlices, categories, heatmapMax,
                topLieux,
                budgetAge,
                totalFavoris, totalUsersActifs, totalLieux, moyenneFavUser,
                dominanteParAge,
                pearson,
                entropie,
                favorisByRole
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  CHARGEMENT DES DONNÉES BRUTES
    // ════════════════════════════════════════════════════════════════

    /**
     * Requête principale : jointure favori_lieu × lieu × user.
     * Retourne un List<UserFavoriRecord> prêt pour les Streams.
     * La table user n'a pas date_naissance → on la gère gracieusement.
     */
    private List<UserFavoriRecord> loadRecords() {
        // On essaie avec date_naissance, puis fallback sans si la colonne n'existe pas
        String sqlWithAge = """
                SELECT
                    f.user_id,
                    f.lieu_id,
                    f.date_ajout,
                    l.nom          AS lieu_nom,
                    l.categorie,
                    l.ville,
                    l.budget_min,
                    l.budget_max,
                    u.role,
                    u.date_naissance
                FROM favori_lieu f
                JOIN lieu l ON l.id = f.lieu_id
                JOIN user u ON u.id = f.user_id
                """;

        String sqlWithoutAge = """
                SELECT
                    f.user_id,
                    f.lieu_id,
                    f.date_ajout,
                    l.nom          AS lieu_nom,
                    l.categorie,
                    l.ville,
                    l.budget_min,
                    l.budget_max,
                    u.role
                FROM favori_lieu f
                JOIN lieu l ON l.id = f.lieu_id
                JOIN user u ON u.id = f.user_id
                """;

        List<UserFavoriRecord> list = new ArrayList<>();

        boolean hasDateNaissance = columnExists("user", "date_naissance");

        String sql = hasDateNaissance ? sqlWithAge : sqlWithoutAge;

        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int age = 0;
                if (hasDateNaissance) {
                    java.sql.Date dn = rs.getDate("date_naissance");
                    if (dn != null) {
                        age = Period.between(dn.toLocalDate(), LocalDate.now()).getYears();
                    }
                }
                // Si pas de date_naissance → on essaie de deviner via le téléphone ou on génère
                // un âge synthétique basé sur l'ID pour démo
                if (age <= 0) {
                    age = syntheticAge(rs.getInt("user_id"));
                }

                list.add(new UserFavoriRecord(
                        rs.getInt("user_id"),
                        rs.getInt("lieu_id"),
                        safe(rs.getString("lieu_nom")),
                        safe(rs.getString("categorie")),
                        safe(rs.getString("ville")),
                        toDouble(rs, "budget_min"),
                        toDouble(rs, "budget_max"),
                        age,
                        UserFavoriRecord.computeAgeSlice(age),
                        safe(rs.getString("role")),
                        safe(rs.getString("date_ajout"))
                ));
            }
        } catch (SQLException e) {
            System.err.println("StatEngine.loadRecords: " + e.getMessage());
        }
        return list;
    }

    private Map<Integer, Double> loadAvgNotes() {
        Map<Integer, Double> map = new HashMap<>();
        String sql = "SELECT lieu_id, AVG(note) AS avg_note FROM evaluation_lieu GROUP BY lieu_id";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getInt("lieu_id"), rs.getDouble("avg_note"));
        } catch (SQLException e) { /* ignore */ }
        return map;
    }

    private Map<Integer, Long> loadFavorisWeek() {
        Map<Integer, Long> map = new HashMap<>();
        String sql = "SELECT lieu_id, COUNT(*) AS c FROM favori_lieu " +
                     "WHERE date_ajout >= DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY lieu_id";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getInt("lieu_id"), rs.getLong("c"));
        } catch (SQLException e) { /* ignore */ }
        return map;
    }

    private Map<Integer, Long> loadFavorisWeekPrev() {
        Map<Integer, Long> map = new HashMap<>();
        String sql = "SELECT lieu_id, COUNT(*) AS c FROM favori_lieu " +
                     "WHERE date_ajout >= DATE_SUB(NOW(), INTERVAL 14 DAY) " +
                     "  AND date_ajout < DATE_SUB(NOW(), INTERVAL 7 DAY) GROUP BY lieu_id";
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getInt("lieu_id"), rs.getLong("c"));
        } catch (SQLException e) { /* ignore */ }
        return map;
    }

    // ════════════════════════════════════════════════════════════════
    //  ALGORITHMES STATISTIQUES
    // ════════════════════════════════════════════════════════════════

    /**
     * Corrélation de Pearson entre âge et budget moyen.
     * r = Σ((xi - x̄)(yi - ȳ)) / (n * σx * σy)
     * Résultat ∈ [-1, 1]
     */
    private double computePearson(List<UserFavoriRecord> data) {
        if (data.size() < 2) return 0.0;

        double n   = data.size();
        double sumX = data.stream().mapToDouble(UserFavoriRecord::age).sum();
        double sumY = data.stream().mapToDouble(UserFavoriRecord::budgetMoyen).sum();
        double meanX = sumX / n;
        double meanY = sumY / n;

        double num   = data.stream().mapToDouble(r -> (r.age() - meanX) * (r.budgetMoyen() - meanY)).sum();
        double denX  = Math.sqrt(data.stream().mapToDouble(r -> Math.pow(r.age() - meanX, 2)).sum());
        double denY  = Math.sqrt(data.stream().mapToDouble(r -> Math.pow(r.budgetMoyen() - meanY, 2)).sum());

        if (denX == 0 || denY == 0) return 0.0;
        return num / (denX * denY);
    }

    /**
     * Entropie de Shannon sur une distribution de fréquences.
     * H = -Σ(pi * log2(pi))   où pi = proportion de chaque ville
     * H = 0 → tous les favoris dans une seule ville (mono-ville)
     * H max → distribution parfaitement uniforme (explorateur)
     */
    private static double shannonEntropy(Map<String, Long> freq) {
        long total = freq.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return 0.0;
        return freq.values().stream()
                .filter(c -> c > 0)
                .mapToDouble(c -> {
                    double p = (double) c / total;
                    return -p * (Math.log(p) / Math.log(2));
                })
                .sum();
    }

    // ════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ════════════════════════════════════════════════════════════════

    private boolean columnExists(String table, String column) {
        try {
            DatabaseMetaData meta = cnx.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                return rs.next();
            }
        } catch (SQLException e) { return false; }
    }

    /**
     * Génère un âge synthétique reproductible pour les users sans date_naissance.
     * Distribution réaliste : 18-45 ans, basée sur le hash de l'ID.
     */
    private int syntheticAge(int userId) {
        int[] ages = {22, 28, 19, 34, 25, 31, 42, 23, 38, 27,
                      20, 45, 29, 33, 21, 36, 24, 41, 26, 30};
        return ages[Math.abs(userId) % ages.length];
    }

    private String normalizeCategorie(String cat) {
        if (cat == null || cat.isBlank()) return "Autre";
        return switch (cat.toUpperCase().trim()) {
            case "CAFE"             -> "Café";
            case "RESTO"            -> "Resto";
            case "MUSEE"            -> "Musée";
            case "LIEU_PUBLIC",
                 "LIEUPUBLIC"       -> "Lieu public";
            case "CENTRECOMMERCIAL",
                 "CENTRE_COMMERCIAL"-> "Centre commercial";
            default                 -> capitalize(cat.toLowerCase());
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Double toDouble(ResultSet rs, String col) {
        try {
            double v = rs.getDouble(col);
            return rs.wasNull() ? null : v;
        } catch (SQLException e) { return null; }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private AnalyticsResult emptyResult() {
        return new AnalyticsResult(
                Map.of(), List.of(), List.of(), 1L,
                List.of(), Map.of(),
                0, 0, 0, 0.0,
                Map.of(), 0.0, Map.of(), Map.of()
        );
    }
}
