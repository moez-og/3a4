package services.gamification;

import utils.Mydb;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère la génération automatique d'offres personnelles
 * quand un utilisateur obtient un badge avec récompense.
 */
public class RecompenseOffreService {

    private final Connection cnx;

    public RecompenseOffreService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /**
     * Appelé après l'attribution d'un badge.
     * Si ce badge a une récompense offre configurée dans badge_recompense,
     * génère une offre personnelle dans offre_badge_user.
     * @return l'offre générée (pour notification), ou null si pas de récompense.
     */
    public OffreBadgeResult genererOffreSiBadgeRecompense(int userId, String badgeCode) {
        String sql = "SELECT * FROM badge_recompense WHERE badge_code = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, badgeCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String titre       = rs.getString("titre");
                String description = rs.getString("description");
                float  pourcentage = rs.getFloat("pourcentage");
                int    duree       = rs.getInt("duree_jours");

                if (aDejaOffreBadge(userId, badgeCode)) return null;

                LocalDate debut = LocalDate.now();
                LocalDate fin   = debut.plusDays(duree);

                String insertSql = """
                    INSERT IGNORE INTO offre_badge_user
                        (user_id, badge_code, titre, pourcentage, date_debut, date_fin, statut)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                """;
                try (PreparedStatement ins = cnx.prepareStatement(insertSql)) {
                    ins.setInt(1, userId);
                    ins.setString(2, badgeCode);
                    ins.setString(3, titre);
                    ins.setFloat(4, pourcentage);
                    ins.setDate(5, Date.valueOf(debut));
                    ins.setDate(6, Date.valueOf(fin));
                    ins.executeUpdate();
                }

                OffreBadgeResult result = new OffreBadgeResult(titre, description, pourcentage, duree, badgeCode);
                result.setDateFin(fin);
                return result;
            }
        } catch (SQLException e) {
            System.err.println("genererOffreSiBadgeRecompense: " + e.getMessage());
        }
        return null;
    }

    /**
     * Récupère toutes les offres badges ACTIVES et non expirées d'un utilisateur.
     */
    public List<OffreBadgeResult> getOffresActivesUser(int userId) {
        String sql = """
            SELECT obu.*, br.description,
                   DATEDIFF(obu.date_fin, CURDATE()) AS jours_restants
            FROM offre_badge_user obu
            LEFT JOIN badge_recompense br ON br.badge_code = obu.badge_code
            WHERE obu.user_id = ?
              AND obu.statut = 'ACTIVE'
              AND obu.date_fin >= CURDATE()
            ORDER BY obu.date_fin ASC
        """;
        List<OffreBadgeResult> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int joursRestants = rs.getInt("jours_restants");
                    OffreBadgeResult o = new OffreBadgeResult(
                        rs.getString("titre"),
                        rs.getString("description"),
                        rs.getFloat("pourcentage"),
                        joursRestants,
                        rs.getString("badge_code")
                    );
                    o.setDateFin(rs.getDate("date_fin").toLocalDate());
                    list.add(o);
                }
            }
        } catch (SQLException e) {
            System.err.println("getOffresActivesUser: " + e.getMessage());
        }
        return list;
    }

    /**
     * Met à jour le statut des offres expirées.
     * À appeler à chaque ouverture du dashboard gamification.
     */
    public void expireOffresEchues() {
        String sql = """
            UPDATE offre_badge_user
            SET statut = 'EXPIREE'
            WHERE statut = 'ACTIVE' AND date_fin < CURDATE()
        """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("expireOffresEchues: " + e.getMessage());
        }
    }

    public boolean aDejaOffreBadge(int userId, String badgeCode) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "SELECT 1 FROM offre_badge_user WHERE user_id=? AND badge_code=?")) {
            ps.setInt(1, userId);
            ps.setString(2, badgeCode);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    // ══════════════════════════════════════════════════════════════
    //  DTO — Résultat d'une offre badge
    // ══════════════════════════════════════════════════════════════

    public static class OffreBadgeResult {
        private final String    titre;
        private final String    description;
        private final float     pourcentage;
        private final int       joursRestants;
        private final String    badgeCode;
        private       LocalDate dateFin;

        public OffreBadgeResult(String titre, String description,
                                float pourcentage, int joursRestants, String badgeCode) {
            this.titre         = titre;
            this.description   = description;
            this.pourcentage   = pourcentage;
            this.joursRestants = joursRestants;
            this.badgeCode     = badgeCode;
        }

        public String    getTitre()             { return titre; }
        public String    getDescription()       { return description; }
        public float     getPourcentage()       { return pourcentage; }
        public int       getJoursRestants()     { return joursRestants; }
        public String    getBadgeCode()         { return badgeCode; }
        public LocalDate getDateFin()           { return dateFin; }
        public void      setDateFin(LocalDate d){ this.dateFin = d; }

        /** Ex: "-15%" */
        public String getDisplayPourcentage() {
            return "-" + (int) pourcentage + "%";
        }
    }
}
