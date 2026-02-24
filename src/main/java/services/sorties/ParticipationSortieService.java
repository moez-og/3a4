package services.sorties;

import models.sorties.ParticipationSortie;
import utils.Mydb;
import utils.json.JsonStringArray;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParticipationSortieService {

    private final Connection cnx;

    // ✅ NOM TABLE EXACT
    private static final String TABLE = "participation_annonce";

    public ParticipationSortieService() {
        cnx = Mydb.getInstance().getConnection();
    }

    public ParticipationSortie getByAnnonceAndUser(int annonceId, int userId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE annonce_id=? AND user_id=? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByAnnonceAndUser: " + e.getMessage(), e);
        }
        return null;
    }

    public int countAcceptedPlaces(int annonceId) {
        String sql = "SELECT COALESCE(SUM(nb_places),0) AS s FROM " + TABLE + " WHERE annonce_id=? AND UPPER(statut) IN ('CONFIRMEE','ACCEPTEE')";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("s");
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.countAcceptedPlaces: " + e.getMessage(), e);
        }
        return 0;
    }

    public void addRequest(ParticipationSortie p) {
        ParticipationSortie existing = getByAnnonceAndUser(p.getAnnonceId(), p.getUserId());
        if (existing != null) {
            throw new RuntimeException("Une demande existe déjà pour cette annonce.");
        }

        String sql = """
                INSERT INTO %s
                (annonce_id, user_id, statut, contact_prefer, contact_value, commentaire, nb_places, reponses_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE);

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, p.getAnnonceId());
            ps.setInt(2, p.getUserId());
            ps.setString(3, safeDefault(p.getStatut(), "EN_ATTENTE"));
            ps.setString(4, safeDefault(p.getContactPrefer(), "EMAIL"));
            ps.setString(5, safeReq(p.getContactValue(), "Contact"));
            ps.setString(6, emptyToNull(p.getCommentaire()));
            ps.setInt(7, Math.max(1, p.getNbPlaces()));
            ps.setString(8, JsonStringArray.toJson(p.getReponses()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.addRequest: " + e.getMessage(), e);
        }
    }

    public void updateStatus(int id, String statut) {
        String sql = "UPDATE " + TABLE + " SET statut=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, safeDefault(statut, "EN_ATTENTE"));
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.updateStatus: " + e.getMessage(), e);
        }
    }

    public List<ParticipationSortie> getByAnnonce(int annonceId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE annonce_id=? ORDER BY date_demande DESC, id DESC";
        List<ParticipationSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByAnnonce: " + e.getMessage(), e);
        }
        return list;
    }

    public List<ParticipationSortie> getByUser(int userId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE user_id=? ORDER BY date_demande DESC, id DESC";
        List<ParticipationSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByUser: " + e.getMessage(), e);
        }
        return list;
    }

    private ParticipationSortie map(ResultSet rs) throws SQLException {
        ParticipationSortie p = new ParticipationSortie();
        p.setId(rs.getInt("id"));
        p.setAnnonceId(rs.getInt("annonce_id"));
        p.setUserId(rs.getInt("user_id"));

        Timestamp ts = rs.getTimestamp("date_demande");
        if (ts != null) p.setDateDemande(ts.toLocalDateTime());

        p.setStatut(rs.getString("statut"));
        p.setContactPrefer(rs.getString("contact_prefer"));
        p.setContactValue(rs.getString("contact_value"));
        p.setCommentaire(rs.getString("commentaire"));
        p.setNbPlaces(rs.getInt("nb_places"));

        p.setReponses(JsonStringArray.fromJson(rs.getString("reponses_json")));
        return p;
    }

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