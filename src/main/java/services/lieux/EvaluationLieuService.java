package services.lieux;

import models.lieux.EvaluationLieu;
import services.common.ServiceBase;
import utils.Mydb;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EvaluationLieuService implements ServiceBase {

    private final Connection cnx;

    public EvaluationLieuService() {
        cnx = Mydb.getInstance().getConnection();
    }

    public List<EvaluationLieu> getByLieuId(int lieuId) {
        String sql = """
                SELECT e.id, e.lieu_id, e.user_id, e.note, e.commentaire, e.date_evaluation, e.updated_at,
                       u.nom, u.prenom, u.imageUrl
                FROM evaluation_lieu e
                JOIN user u ON u.id = e.user_id
                WHERE e.lieu_id = ?
                ORDER BY e.date_evaluation DESC
                """;

        List<EvaluationLieu> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvaluationLieu ev = new EvaluationLieu();
                    ev.setId(rs.getInt("id"));
                    ev.setLieuId(rs.getInt("lieu_id"));
                    ev.setUserId(rs.getInt("user_id"));
                    ev.setNote(rs.getInt("note"));
                    ev.setCommentaire(rs.getString("commentaire"));
                    ev.setDateEvaluation(toLdt(rs.getTimestamp("date_evaluation")));
                    ev.setUpdatedAt(toLdt(rs.getTimestamp("updated_at")));

                    ev.setUserNom(rs.getString("nom"));
                    ev.setUserPrenom(rs.getString("prenom"));
                    ev.setUserImageUrl(rs.getString("imageUrl"));

                    list.add(ev);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("EvaluationLieuService.getByLieuId: " + e.getMessage(), e);
        }
        return list;
    }

    public double avgNote(int lieuId) {
        String sql = "SELECT AVG(note) AS avg_note FROM evaluation_lieu WHERE lieu_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("avg_note");
            }
        } catch (SQLException e) {
            throw new RuntimeException("EvaluationLieuService.avgNote: " + e.getMessage(), e);
        }
        return 0.0;
    }

    public int countByLieuId(int lieuId) {
        String sql = "SELECT COUNT(*) AS c FROM evaluation_lieu WHERE lieu_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("EvaluationLieuService.countByLieuId: " + e.getMessage(), e);
        }
        return 0;
    }

    // respecte uq_eval_unique(lieu_id, user_id)
    public void upsert(int lieuId, int userId, int note, String commentaire) {
        String sql = """
                INSERT INTO evaluation_lieu(lieu_id, user_id, note, commentaire)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    note = VALUES(note),
                    commentaire = VALUES(commentaire),
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            ps.setInt(2, userId);
            ps.setInt(3, note);
            ps.setString(4, commentaire);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EvaluationLieuService.upsert: " + e.getMessage(), e);
        }
    }

    public void deleteByLieuUser(int lieuId, int userId) {
        String sql = "DELETE FROM evaluation_lieu WHERE lieu_id=? AND user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("EvaluationLieuService.deleteByLieuUser: " + e.getMessage(), e);
        }
    }

    private LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}