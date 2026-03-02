package services.gamification;

import models.gamification.Badge;
import models.gamification.ClassementEntry;
import models.gamification.UserPoints;
import utils.Mydb;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service central de la gamification.
 * Gère : points fidélité, badges explorateur, classement local, récompenses avis.
 */
public class GamificationService {

    private final Connection cnx;
    private final RecompenseOffreService recompenseService = new RecompenseOffreService();

    // ── Points par action ─────────────────────────────────────────────────
    public static final int PTS_VISITE_LIEU       = 5;
    public static final int PTS_AVIS_LAISSE       = 15;
    public static final int PTS_AVIS_FIABLE       = 25;
    public static final int PTS_FAVORI            = 3;
    public static final int PTS_SORTIE_JOINTE     = 10;
    public static final int PTS_INSCRIPTION_EVENT = 8;

    public GamificationService() {
        cnx = Mydb.getInstance().getConnection();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INITIALISATION DES TABLES
    // ══════════════════════════════════════════════════════════════════════

    public void initTables() {
        try (Statement st = cnx.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_points (
                    user_id            INT PRIMARY KEY,
                    total_points       INT DEFAULT 0,
                    nb_lieux_visites   INT DEFAULT 0,
                    nb_avis_laisses    INT DEFAULT 0,
                    nb_favoris         INT DEFAULT 0,
                    nb_sorties_jointes INT DEFAULT 0,
                    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS badge (
                    id            INT AUTO_INCREMENT PRIMARY KEY,
                    code          VARCHAR(50) UNIQUE NOT NULL,
                    nom           VARCHAR(100) NOT NULL,
                    description   TEXT,
                    emoji         VARCHAR(20) DEFAULT '🏅',
                    categorie     VARCHAR(30) DEFAULT 'GENERAL',
                    seuil_requis  INT DEFAULT 1,
                    points_bonus  INT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS user_badge (
                    user_id     INT NOT NULL,
                    badge_id    INT NOT NULL,
                    date_obtenu TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id, badge_id),
                    FOREIGN KEY (user_id)  REFERENCES user(id)  ON DELETE CASCADE,
                    FOREIGN KEY (badge_id) REFERENCES badge(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS avis_utile (
                    evaluation_id  INT NOT NULL,
                    votant_user_id INT NOT NULL,
                    date_vote      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (evaluation_id, votant_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS badge_recompense (
                    id          INT AUTO_INCREMENT PRIMARY KEY,
                    badge_code  VARCHAR(50) NOT NULL,
                    titre       VARCHAR(140) NOT NULL,
                    description TEXT DEFAULT NULL,
                    pourcentage FLOAT NOT NULL DEFAULT 10,
                    duree_jours INT NOT NULL DEFAULT 7,
                    UNIQUE KEY uq_badge_recompense (badge_code),
                    FOREIGN KEY (badge_code) REFERENCES badge(code) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS offre_badge_user (
                    id           INT AUTO_INCREMENT PRIMARY KEY,
                    user_id      INT NOT NULL,
                    badge_code   VARCHAR(50) NOT NULL,
                    titre        VARCHAR(140) NOT NULL,
                    pourcentage  FLOAT NOT NULL,
                    date_debut   DATE NOT NULL,
                    date_fin     DATE NOT NULL,
                    statut       ENUM('ACTIVE','EXPIREE','UTILISEE') NOT NULL DEFAULT 'ACTIVE',
                    date_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uq_offre_badge_user (user_id, badge_code),
                    FOREIGN KEY (user_id)    REFERENCES user(id)  ON DELETE CASCADE ON UPDATE CASCADE,
                    FOREIGN KEY (badge_code) REFERENCES badge(code) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            seedBadges(st);
            seedRecompenses(st);

            System.out.println("✓ Tables gamification initialisées.");
        } catch (SQLException e) {
            System.err.println("Gamification initTables: " + e.getMessage());
        }
    }

    private void seedBadges(Statement st) throws SQLException {
        String[][] badges = {
            {"PREMIER_PAS",    "Premier Pas",        "Vous avez visité votre premier lieu",           "👣", "EXPLORATEUR", "1",    "10"},
            {"EXPLORATEUR_5",  "Explorateur",        "Vous avez visité 5 lieux différents",           "🧭", "EXPLORATEUR", "5",    "25"},
            {"EXPLORATEUR_20", "Grand Explorateur",  "Vous avez visité 20 lieux différents",          "🗺", "EXPLORATEUR", "20",   "75"},
            {"EXPLORATEUR_50", "Légende Locale",     "50 lieux visités ! Vous connaissez tout !",     "🏆", "EXPLORATEUR", "50",  "200"},
            {"PREMIER_AVIS",   "Critique Débutant",  "Vous avez laissé votre premier avis",           "✍", "AVIS",        "1",    "15"},
            {"AVIS_10",        "Critique Confirmé",  "10 avis laissés, votre opinion compte !",       "📝", "AVIS",        "10",   "50"},
            {"AVIS_FIABLE",    "Avis de Confiance",  "3 de vos avis ont été jugés utiles",            "⭐", "AVIS",        "3",    "60"},
            {"SUPER_CRITIQUE", "Super Critique",     "5 avis fiables reconnus par la communauté",     "🌟", "AVIS",        "5",   "100"},
            {"PREMIER_FAVORI", "Coup de Cœur",       "Vous avez ajouté votre premier lieu favori",    "❤", "FAVORI",      "1",     "5"},
            {"COLLECTION_10",  "Collectionneur",     "10 lieux dans vos favoris",                     "💝", "FAVORI",      "10",   "30"},
            {"SORTIE_JOINTE",  "Esprit Equipe",      "Vous avez rejoint votre première sortie",       "🤝", "SOCIAL",      "1",    "10"},
            {"SORTIE_5",       "Ame Sociale",        "5 sorties rejointes, vous adorez sortir !",     "🎉", "SOCIAL",      "5",    "40"},
            {"NIVEAU_ARGENT",  "Niveau Argent",      "Vous avez atteint le niveau Argent",            "🥈", "SPECIAL",     "100",  "20"},
            {"NIVEAU_OR",      "Niveau Or",          "Vous avez atteint le niveau Or",                "🥇", "SPECIAL",     "500",  "50"},
            {"NIVEAU_PLATINE", "Niveau Platine",     "Elite ! Vous avez atteint le niveau Platine",   "💎", "SPECIAL",    "1500", "150"},
        };
        for (String[] b : badges) {
            try {
                st.execute(String.format(
                    "INSERT IGNORE INTO badge (code,nom,description,emoji,categorie,seuil_requis,points_bonus) VALUES ('%s','%s','%s','%s','%s',%s,%s)",
                    b[0], b[1], b[2], b[3], b[4], b[5], b[6]));
            } catch (SQLException ignored) {}
        }
    }

    private void seedRecompenses(Statement st) throws SQLException {
        String[][] recompenses = {
            {"PREMIER_PAS",    "Bienvenue Explorateur !",      "10% sur tous les lieux partenaires pendant 7 jours",           "10",  "7"},
            {"EXPLORATEUR_5",  "Explorateur Confirmé",         "15% sur les cafés et restos partenaires pendant 7 jours",      "15",  "7"},
            {"EXPLORATEUR_20", "Grand Voyageur",               "20% sur tous les lieux partenaires pendant 7 jours",           "20",  "7"},
            {"EXPLORATEUR_50", "Légende — Offre Platine",      "30% sur tous les lieux partenaires pendant 14 jours",          "30", "14"},
            {"PREMIER_AVIS",   "Merci pour votre avis !",      "5% de réduction sur votre prochain lieu visité",               "5",   "7"},
            {"AVIS_FIABLE",    "Critique de Confiance",        "20% sur les lieux partenaires, votre avis est précieux !",     "20",  "7"},
            {"SUPER_CRITIQUE", "Super Critique — Offre VIP",   "25% exclusif sur tous les lieux partenaires",                  "25", "10"},
            {"NIVEAU_OR",      "Membre Or — Accès Premium",    "20% sur tous les lieux partenaires pendant 7 jours",           "20",  "7"},
            {"NIVEAU_PLATINE", "Membre Platine — Offre Elite", "35% exclusif sur tous les lieux partenaires pendant 30 jours", "35", "30"},
        };
        for (String[] r : recompenses) {
            try {
                st.execute(String.format(
                    "INSERT IGNORE INTO badge_recompense (badge_code,titre,description,pourcentage,duree_jours) VALUES ('%s','%s','%s',%s,%s)",
                    r[0], r[1], r[2], r[3], r[4]));
            } catch (SQLException ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  POINTS
    // ══════════════════════════════════════════════════════════════════════

    public UserPoints getOrCreateUserPoints(int userId) {
        String sql = "SELECT * FROM user_points WHERE user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUserPoints(rs);
            }
        } catch (SQLException e) {
            System.err.println("getOrCreateUserPoints select: " + e.getMessage());
        }
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT IGNORE INTO user_points (user_id) VALUES (?)")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("getOrCreateUserPoints insert: " + e.getMessage());
        }
        return new UserPoints(userId);
    }

    public void ajouterPoints(int userId, String action) {
        ensureUserPointsExist(userId);

        int pts;
        String colonne;
        switch (action) {
            case "VISITE_LIEU"       -> { pts = PTS_VISITE_LIEU;       colonne = "nb_lieux_visites"; }
            case "AVIS_LAISSE"       -> { pts = PTS_AVIS_LAISSE;       colonne = "nb_avis_laisses"; }
            case "AVIS_FIABLE"       -> { pts = PTS_AVIS_FIABLE;       colonne = null; }
            case "FAVORI"            -> { pts = PTS_FAVORI;             colonne = "nb_favoris"; }
            case "SORTIE_JOINTE"     -> { pts = PTS_SORTIE_JOINTE;     colonne = "nb_sorties_jointes"; }
            case "INSCRIPTION_EVENT" -> { pts = PTS_INSCRIPTION_EVENT; colonne = null; }
            default -> { return; }
        }

        String sql = (colonne != null)
            ? "UPDATE user_points SET total_points = total_points + ?, " + colonne + " = " + colonne + " + 1 WHERE user_id = ?"
            : "UPDATE user_points SET total_points = total_points + ? WHERE user_id = ?";

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pts);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("ajouterPoints: " + e.getMessage());
        }

        verifierEtAttribuerBadges(userId);
    }

    private void ensureUserPointsExist(int userId) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT IGNORE INTO user_points (user_id) VALUES (?)")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BADGES + RÉCOMPENSES OFFRES
    // ══════════════════════════════════════════════════════════════════════

    public List<Badge> getBadgesUtilisateur(int userId) {
        String sql = """
            SELECT b.*, ub.date_obtenu
            FROM badge b
            JOIN user_badge ub ON ub.badge_id = b.id
            WHERE ub.user_id = ?
            ORDER BY ub.date_obtenu DESC
        """;
        List<Badge> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Badge b = mapBadge(rs);
                    Timestamp ts = rs.getTimestamp("date_obtenu");
                    if (ts != null) b.setDateObtenu(ts.toLocalDateTime());
                    list.add(b);
                }
            }
        } catch (SQLException e) {
            System.err.println("getBadgesUtilisateur: " + e.getMessage());
        }
        return list;
    }

    public List<Badge> getTousBadges() {
        List<Badge> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement("SELECT * FROM badge ORDER BY categorie, seuil_requis");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapBadge(rs));
        } catch (SQLException e) {
            System.err.println("getTousBadges: " + e.getMessage());
        }
        return list;
    }

    /**
     * Vérifie et attribue automatiquement les badges mérités.
     * Pour chaque nouveau badge : ajoute les points bonus + génère une offre si récompense configurée.
     * Retourne la liste des nouveaux badges obtenus (pour notification popup).
     */
    public List<Badge> verifierEtAttribuerBadges(int userId) {
        UserPoints up = getOrCreateUserPoints(userId);
        List<Badge> tousBadges = getTousBadges();
        List<Badge> nouveaux = new ArrayList<>();

        for (Badge badge : tousBadges) {
            if (aDejaLeBadge(userId, badge.getId())) continue;
            if (meriteLeBadge(up, badge)) {
                attribuerBadge(userId, badge.getId());

                // Bonus points du badge
                if (badge.getPointsBonus() > 0) {
                    try (PreparedStatement ps = cnx.prepareStatement(
                            "UPDATE user_points SET total_points = total_points + ? WHERE user_id = ?")) {
                        ps.setInt(1, badge.getPointsBonus());
                        ps.setInt(2, userId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                }

                // 🎁 Générer automatiquement une offre récompense si ce badge en a une
                recompenseService.genererOffreSiBadgeRecompense(userId, badge.getCode());

                nouveaux.add(badge);
            }
        }
        return nouveaux;
    }

    private boolean meriteLeBadge(UserPoints up, Badge badge) {
        return switch (badge.getCode()) {
            case "PREMIER_PAS"    -> up.getNbLieuxVisites() >= 1;
            case "EXPLORATEUR_5"  -> up.getNbLieuxVisites() >= 5;
            case "EXPLORATEUR_20" -> up.getNbLieuxVisites() >= 20;
            case "EXPLORATEUR_50" -> up.getNbLieuxVisites() >= 50;
            case "PREMIER_AVIS"   -> up.getNbAvisLaisses() >= 1;
            case "AVIS_10"        -> up.getNbAvisLaisses() >= 10;
            case "PREMIER_FAVORI" -> up.getNbFavoris() >= 1;
            case "COLLECTION_10"  -> up.getNbFavoris() >= 10;
            case "SORTIE_JOINTE"  -> up.getNbSortiesJointes() >= 1;
            case "SORTIE_5"       -> up.getNbSortiesJointes() >= 5;
            case "NIVEAU_ARGENT"  -> up.getTotalPoints() >= 100;
            case "NIVEAU_OR"      -> up.getTotalPoints() >= 500;
            case "NIVEAU_PLATINE" -> up.getTotalPoints() >= 1500;
            default               -> up.getTotalPoints() >= badge.getSeuilRequis();
        };
    }

    private boolean aDejaLeBadge(int userId, int badgeId) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "SELECT 1 FROM user_badge WHERE user_id=? AND badge_id=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, badgeId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    private void attribuerBadge(int userId, int badgeId) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT IGNORE INTO user_badge (user_id, badge_id) VALUES (?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, badgeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("attribuerBadge: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CLASSEMENT LOCAL
    // ══════════════════════════════════════════════════════════════════════

    public List<ClassementEntry> getClassementLocal(int limit) {
        String sql = """
            SELECT u.id, u.nom, u.prenom, u.imageUrl,
                   up.total_points,
                   COUNT(ub.badge_id) AS nb_badges
            FROM user_points up
            JOIN user u ON u.id = up.user_id
            LEFT JOIN user_badge ub ON ub.user_id = up.user_id
            GROUP BY u.id, u.nom, u.prenom, u.imageUrl, up.total_points
            ORDER BY up.total_points DESC
            LIMIT ?
        """;
        List<ClassementEntry> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rang = 1;
                while (rs.next()) {
                    ClassementEntry e = new ClassementEntry();
                    e.setRang(rang++);
                    e.setUserId(rs.getInt("id"));
                    e.setNom(rs.getString("nom"));
                    e.setPrenom(rs.getString("prenom"));
                    e.setImageUrl(rs.getString("imageUrl"));
                    e.setTotalPoints(rs.getInt("total_points"));
                    e.setNbBadges(rs.getInt("nb_badges"));
                    e.setNiveau(UserPoints.calcNiveau(e.getTotalPoints()));
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            System.err.println("getClassementLocal: " + e.getMessage());
        }
        return list;
    }

    public int getRangUtilisateur(int userId) {
        String sql = """
            SELECT COUNT(*) + 1 AS rang
            FROM user_points
            WHERE total_points > (SELECT total_points FROM user_points WHERE user_id = ?)
        """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("rang");
            }
        } catch (SQLException e) {
            System.err.println("getRangUtilisateur: " + e.getMessage());
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AVIS FIABLES
    // ══════════════════════════════════════════════════════════════════════

    public void marquerAvisUtile(int evaluationId, int votantUserId) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "INSERT IGNORE INTO avis_utile (evaluation_id, votant_user_id) VALUES (?, ?)")) {
            ps.setInt(1, evaluationId);
            ps.setInt(2, votantUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("marquerAvisUtile: " + e.getMessage());
            return;
        }
        try (PreparedStatement ps = cnx.prepareStatement(
                "SELECT user_id FROM evaluation_lieu WHERE id=?")) {
            ps.setInt(1, evaluationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int auteurId = rs.getInt("user_id");
                    ajouterPoints(auteurId, "AVIS_FIABLE");
                    verifierBadgesAvisFiables(auteurId);
                }
            }
        } catch (SQLException e) {
            System.err.println("marquerAvisUtile lookup: " + e.getMessage());
        }
    }

    private void verifierBadgesAvisFiables(int userId) {
        String sql = """
            SELECT COUNT(*) AS total FROM avis_utile au
            JOIN evaluation_lieu e ON e.id = au.evaluation_id
            WHERE e.user_id = ?
        """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    if (total >= 3 && !aDejaLeBadgeParCode(userId, "AVIS_FIABLE"))
                        attribuerBadgeParCode(userId, "AVIS_FIABLE");
                    if (total >= 5 && !aDejaLeBadgeParCode(userId, "SUPER_CRITIQUE"))
                        attribuerBadgeParCode(userId, "SUPER_CRITIQUE");
                }
            }
        } catch (SQLException e) {
            System.err.println("verifierBadgesAvisFiables: " + e.getMessage());
        }
    }

    public boolean aDejaLeBadgeParCode(int userId, String code) {
        try (PreparedStatement ps = cnx.prepareStatement(
                "SELECT 1 FROM user_badge ub JOIN badge b ON b.id=ub.badge_id WHERE ub.user_id=? AND b.code=?")) {
            ps.setInt(1, userId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    public void attribuerBadgeParCode(int userId, String code) {
        try (PreparedStatement ps = cnx.prepareStatement("SELECT id FROM badge WHERE code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) attribuerBadge(userId, rs.getInt("id"));
            }
        } catch (SQLException e) {
            System.err.println("attribuerBadgeParCode: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MAPPERS
    // ══════════════════════════════════════════════════════════════════════

    private UserPoints mapUserPoints(ResultSet rs) throws SQLException {
        UserPoints up = new UserPoints();
        up.setUserId(rs.getInt("user_id"));
        up.setTotalPoints(rs.getInt("total_points"));
        up.setNbLieuxVisites(rs.getInt("nb_lieux_visites"));
        up.setNbAvisLaisses(rs.getInt("nb_avis_laisses"));
        up.setNbFavoris(rs.getInt("nb_favoris"));
        up.setNbSortiesJointes(rs.getInt("nb_sorties_jointes"));
        return up;
    }

    private Badge mapBadge(ResultSet rs) throws SQLException {
        Badge b = new Badge();
        b.setId(rs.getInt("id"));
        b.setCode(rs.getString("code"));
        b.setNom(rs.getString("nom"));
        b.setDescription(rs.getString("description"));
        b.setEmoji(rs.getString("emoji"));
        b.setCategorie(rs.getString("categorie"));
        b.setSeuilRequis(rs.getInt("seuil_requis"));
        b.setPointsBonus(rs.getInt("points_bonus"));
        return b;
    }
}
