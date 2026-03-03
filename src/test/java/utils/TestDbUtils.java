package utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestDbUtils {

    private TestDbUtils() {}

    public static Connection cnx() {
        Connection c = Mydb.getInstance().getConnection();
        if (c == null) {
            throw new IllegalStateException(
                    "Connexion DB null. Vérifie Mydb (url/user/password) et que MySQL est lancé."
            );
        }
        return c;
    }

    public static boolean hasColumn(Connection cnx, String table, String col) throws SQLException {
        DatabaseMetaData md = cnx.getMetaData();

        // Essai direct
        try (ResultSet rs = md.getColumns(cnx.getCatalog(), null, table, col)) {
            if (rs.next()) return true;
        }

        // Fallback (certains setups ont des noms sensibles à la casse)
        try (ResultSet rs = md.getColumns(cnx.getCatalog(), null, table.toLowerCase(), col)) {
            return rs.next();
        }
    }

    /**
     * Retourne un user_id existant, sinon crée un user minimal compatible avec le schéma.
     * (Gère les colonnes variables comme imageUrl, password, role, etc.)
     */
    public static int ensureUser(Connection cnx) throws SQLException {
        // 1) récupérer un user existant
        try (PreparedStatement ps = cnx.prepareStatement("SELECT id FROM user LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }

        // 2) sinon créer un user
        Map<String, Object> values = new LinkedHashMap<>();

        // Champs "classiques"
        if (hasColumn(cnx, "user", "nom")) values.put("nom", "TestNom");
        if (hasColumn(cnx, "user", "prenom")) values.put("prenom", "TestPrenom");
        if (hasColumn(cnx, "user", "email")) values.put("email", "test_" + System.currentTimeMillis() + "@example.com");

        // password (selon schéma)
        if (hasColumn(cnx, "user", "password_hash")) values.put("password_hash", "hashed");
        if (hasColumn(cnx, "user", "password")) values.put("password", "password");

        // role (selon schéma)
        if (hasColumn(cnx, "user", "role")) values.put("role", "admin");

        // autres champs potentiels
        if (hasColumn(cnx, "user", "telephone")) values.put("telephone", "00000000");

        // IMPORTANT: ton projet a déjà eu l’erreur imageUrl NOT NULL
        if (hasColumn(cnx, "user", "imageUrl")) values.put("imageUrl", "default.png");
        if (hasColumn(cnx, "user", "image_url")) values.put("image_url", "default.png");

        if (hasColumn(cnx, "user", "date_creation")) values.put("date_creation", new Timestamp(System.currentTimeMillis()));

        return insertAndReturnId(cnx, "user", values);
    }

    /**
     * Retourne un user_id différent de excludeId; sinon crée un user minimal.
     */
    public static int ensureUserOtherThan(Connection cnx, int excludeId) throws SQLException {
        try (PreparedStatement ps = cnx.prepareStatement("SELECT id FROM user WHERE id<>? LIMIT 1")) {
            ps.setInt(1, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        if (hasColumn(cnx, "user", "nom")) values.put("nom", "TestNom2");
        if (hasColumn(cnx, "user", "prenom")) values.put("prenom", "TestPrenom2");
        if (hasColumn(cnx, "user", "email")) values.put("email", "test2_" + System.currentTimeMillis() + "@example.com");

        if (hasColumn(cnx, "user", "password_hash")) values.put("password_hash", "hashed");
        if (hasColumn(cnx, "user", "password")) values.put("password", "password");

        if (hasColumn(cnx, "user", "role")) values.put("role", "abonne");
        if (hasColumn(cnx, "user", "telephone")) values.put("telephone", "11111111");

        if (hasColumn(cnx, "user", "imageUrl")) values.put("imageUrl", "default.png");
        if (hasColumn(cnx, "user", "image_url")) values.put("image_url", "default.png");

        if (hasColumn(cnx, "user", "date_creation")) values.put("date_creation", new Timestamp(System.currentTimeMillis()));
        return insertAndReturnId(cnx, "user", values);
    }

    /**
     * Crée une offre minimale et retourne son id (car souvent Lieu.id_offre est NOT NULL).
     * Ce code s’adapte aux colonnes existantes.
     */
    public static int createOffre(Connection cnx, int userId) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();

        // Foreign key typique
        if (hasColumn(cnx, "offre", "user_id")) values.put("user_id", userId);

        // Champs classiques
        if (hasColumn(cnx, "offre", "titre")) values.put("titre", "OffreTest_" + System.currentTimeMillis());
        if (hasColumn(cnx, "offre", "description")) values.put("description", "desc test");

        // type / pourcentage (selon schéma)
        if (hasColumn(cnx, "offre", "type")) values.put("type", "REDUCTION");
        if (hasColumn(cnx, "offre", "pourcentage")) values.put("pourcentage", 10f);

        LocalDate d1 = LocalDate.now();
        LocalDate d2 = d1.plusDays(10);

        // ✅ FIX: utiliser java.sql.Date (import java.sql.Date)
        if (hasColumn(cnx, "offre", "date_debut")) values.put("date_debut", Date.valueOf(d1));
        if (hasColumn(cnx, "offre", "date_fin")) values.put("date_fin", Date.valueOf(d2));

        if (hasColumn(cnx, "offre", "statut")) values.put("statut", "ACTIVE");

        // optionnels
        if (hasColumn(cnx, "offre", "event_id")) values.put("event_id", null);

        return insertAndReturnId(cnx, "offre", values);
    }

    public static void deleteById(Connection cnx, String table, int id) {
        try (PreparedStatement ps = cnx.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Crée une annonce_sortie minimale et retourne son id (adaptatif au schéma).
     */
    public static int createAnnonceSortie(Connection cnx, int userId) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();

        if (hasColumn(cnx, "annonce_sortie", "user_id")) values.put("user_id", userId);
        if (hasColumn(cnx, "annonce_sortie", "titre")) values.put("titre", "SortieTest_" + System.currentTimeMillis());
        if (hasColumn(cnx, "annonce_sortie", "description")) values.put("description", "desc test");
        if (hasColumn(cnx, "annonce_sortie", "ville")) values.put("ville", "Tunis");
        if (hasColumn(cnx, "annonce_sortie", "lieu_texte")) values.put("lieu_texte", "Centre");
        if (hasColumn(cnx, "annonce_sortie", "point_rencontre")) values.put("point_rencontre", "Point A");
        if (hasColumn(cnx, "annonce_sortie", "type_activite")) values.put("type_activite", "Marche");

        Timestamp dt = Timestamp.valueOf(java.time.LocalDateTime.now().plusDays(1));
        if (hasColumn(cnx, "annonce_sortie", "date_sortie")) values.put("date_sortie", dt);

        if (hasColumn(cnx, "annonce_sortie", "budget_max")) values.put("budget_max", 0);
        if (hasColumn(cnx, "annonce_sortie", "nb_places")) values.put("nb_places", 5);
        if (hasColumn(cnx, "annonce_sortie", "image_url")) values.put("image_url", null);

        if (hasColumn(cnx, "annonce_sortie", "statut")) values.put("statut", "OUVERTE");
        if (hasColumn(cnx, "annonce_sortie", "questions_json")) values.put("questions_json", "[]");

        return insertAndReturnId(cnx, "annonce_sortie", values);
    }

    private static int insertAndReturnId(Connection cnx, String table, Map<String, Object> values) throws SQLException {
        if (values.isEmpty()) {
            throw new SQLException("Aucune colonne trouvée pour INSERT dans " + table + ". Vérifie ton schéma.");
        }

        String cols = String.join(", ", values.keySet());
        String qs = String.join(", ", Collections.nCopies(values.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ")";

        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            for (Object v : values.values()) {
                ps.setObject(i++, v);
            }
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        // fallback si getGeneratedKeys non supporté
        try (PreparedStatement ps2 = cnx.prepareStatement("SELECT MAX(id) FROM " + table);
             ResultSet rs = ps2.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }

        throw new SQLException("Impossible de récupérer l'id généré pour " + table);
    }
}
