package services.sorties;

import models.sorties.AnnonceSortie;
import utils.Mydb;
import utils.json.JsonStringArray;

import services.common.CrudService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AnnonceSortieService implements CrudService<AnnonceSortie, Integer> {

    private final Connection cnx;

    public AnnonceSortieService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /* ===================== READ ===================== */

    public List<AnnonceSortie> getAll() {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url, statut, questions_json
                FROM annonce_sortie
                ORDER BY date_sortie DESC, id DESC
                """;

        List<AnnonceSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(map(rs));

        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getAll: " + e.getMessage(), e);
        }
        return list;
    }

    public List<AnnonceSortie> getByUserId(int userId) {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url, statut, questions_json
                FROM annonce_sortie
                WHERE user_id = ?
                ORDER BY date_sortie DESC, id DESC
                """;

        List<AnnonceSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getByUserId: " + e.getMessage(), e);
        }
        return list;
    }

    public AnnonceSortie getById(int id) {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url, statut, questions_json
                FROM annonce_sortie
                WHERE id = ?
                """;

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getById: " + e.getMessage(), e);
        }
        return null;
    }

    /* ===================== CREATE ===================== */

    public void add(AnnonceSortie a) {
        String sql = """
                INSERT INTO annonce_sortie
                (user_id, titre, description, ville, lieu_texte, point_rencontre, type_activite,
                 date_sortie, budget_max, nb_places, image_url, statut, questions_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            bind(ps, a, false);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.add: " + e.getMessage(), e);
        }
    }

    /* ===================== UPDATE ===================== */

    public void update(AnnonceSortie a) {
        String sql = """
                UPDATE annonce_sortie
                SET user_id=?, titre=?, description=?, ville=?, lieu_texte=?, point_rencontre=?, type_activite=?,
                    date_sortie=?, budget_max=?, nb_places=?, image_url=?, statut=?, questions_json=?
                WHERE id=?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            bind(ps, a, true);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.update: " + e.getMessage(), e);
        }
    }

    /* ===================== DELETE ===================== */

    public void delete(int id) {
        String sql = "DELETE FROM annonce_sortie WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.delete: " + e.getMessage(), e);
        }
    }

    // ===== CrudService<Integer> wrappers (keep existing int API) =====

    @Override
    public AnnonceSortie getById(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        return getById(id.intValue());
    }

    @Override
    public void delete(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        delete(id.intValue());
    }

    /* ===================== MAPPING ===================== */

    private AnnonceSortie map(ResultSet rs) throws SQLException {
        AnnonceSortie a = new AnnonceSortie();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));

        a.setTitre(rs.getString("titre"));
        a.setDescription(rs.getString("description"));

        a.setVille(rs.getString("ville"));
        a.setLieuTexte(rs.getString("lieu_texte"));
        a.setPointRencontre(rs.getString("point_rencontre"));

        a.setTypeActivite(rs.getString("type_activite"));

        Timestamp ts = rs.getTimestamp("date_sortie");
        a.setDateSortie(ts == null ? null : ts.toLocalDateTime());

        a.setBudgetMax(rs.getDouble("budget_max"));
        a.setNbPlaces(rs.getInt("nb_places"));

        a.setImageUrl(rs.getString("image_url"));
        a.setStatut(rs.getString("statut"));

        a.setQuestions(JsonStringArray.fromJson(rs.getString("questions_json")));

        return a;
    }

    private void bind(PreparedStatement ps, AnnonceSortie a, boolean includeIdAtEnd) throws SQLException {
        ps.setInt(1, a.getUserId());
        ps.setString(2, safeReq(a.getTitre(), "Titre"));

        String desc = emptyToNull(a.getDescription());
        ps.setString(3, desc);

        ps.setString(4, safeReq(a.getVille(), "Ville"));
        ps.setString(5, safeReq(a.getLieuTexte(), "Lieu"));
        ps.setString(6, safeReq(a.getPointRencontre(), "Point de rencontre"));
        ps.setString(7, safeReq(a.getTypeActivite(), "Type d'activité"));

        LocalDateTime dt = a.getDateSortie();
        if (dt == null) throw new SQLException("Date de sortie manquante");
        ps.setTimestamp(8, Timestamp.valueOf(dt));

        ps.setDouble(9, a.getBudgetMax());
        ps.setInt(10, a.getNbPlaces());

        ps.setString(11, emptyToNull(a.getImageUrl()));
        ps.setString(12, safeDefault(a.getStatut(), "OUVERTE"));

        ps.setString(13, JsonStringArray.toJson(a.getQuestions()));

        if (includeIdAtEnd) {
            ps.setInt(14, a.getId());
        }
    }

    /* ===================== HELPERS ===================== */

    private static String safeReq(String s, String label) throws SQLException {
        if (s == null || s.trim().isEmpty()) throw new SQLException(label + " obligatoire");
        return s.trim();
    }

    private static String safeDefault(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isEmpty() ? def : t;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
