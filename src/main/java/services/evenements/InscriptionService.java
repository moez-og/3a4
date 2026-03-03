package services.evenements;

import models.evenements.Inscription;
import utils.Mydb;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * InscriptionService — version mise à jour
 * ═══════════════════════════════════════════════════════
 * CHANGEMENTS vs version originale :
 *
 *  1. updatePaiementFloat(int inscriptionId, float paiement)
 *     Met à jour la colonne paiement avec un float.
 *     Appelé automatiquement après chaque ajout ou suppression
 *     de ticket pour garder : paiement = prix × nbTickets
 * ═══════════════════════════════════════════════════════
 */
public class InscriptionService {

    private Connection getConnection() throws SQLException {
        return utils.Mydb.getInstance().getConnection();
    }

    private LocalDateTime toLDT(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    // ─────────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────────

    public List<Inscription> getByEventId(int eventId) {
        String sql = """
            SELECT id, event_id, user_id, statut, paiement, date_creation
            FROM inscription
            WHERE event_id = ?
            ORDER BY date_creation DESC
        """;
        List<Inscription> list = new ArrayList<>();
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Inscription i = new Inscription();
                    i.setId(rs.getInt("id"));
                    i.setEventId(rs.getInt("event_id"));
                    i.setUserId(rs.getInt("user_id"));
                    i.setStatut(rs.getString("statut"));
                    i.setPaiement(rs.getString("paiement"));
                    i.setDateCreation(toLDT(rs.getTimestamp("date_creation")));
                    list.add(i);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getByEventId: " + ex.getMessage(), ex);
        }
        return list;
    }

    public int countByEvent(int eventId) {
        String sql = "SELECT COUNT(*) FROM inscription WHERE event_id = ? AND statut <> 'ANNULEE'";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur countByEvent: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  VALIDATION MÉTIER
    // ─────────────────────────────────────────────────────────────

    public boolean existsForUser(int eventId, int userId) {
        String sql = "SELECT 1 FROM inscription WHERE event_id=? AND user_id=? LIMIT 1";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur existsForUser: " + ex.getMessage(), ex);
        }
    }

    public void ensureCapacityAvailable(int eventId) {
        String sql = """
            SELECT e.capacite_max,
                   (SELECT COUNT(*) FROM inscription i WHERE i.event_id = e.id AND i.statut <> 'ANNULEE') AS used_places
            FROM evenement e
            WHERE e.id = ?
        """;
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Événement introuvable.");
                int max = rs.getInt("capacite_max");
                int used = rs.getInt("used_places");
                if (used >= max) throw new IllegalStateException("Capacité maximale atteinte.");
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur ensureCapacityAvailable: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────

    public int addInscription(int eventId, int userId, float paiement) {
        if (existsForUser(eventId, userId))
            throw new IllegalStateException("Cet utilisateur est déjà inscrit à cet événement.");
        ensureCapacityAvailable(eventId);

        String sql = """
            INSERT INTO inscription(event_id, user_id, statut, paiement, date_creation)
            VALUES(?, ?, 'EN_ATTENTE', ?, NOW())
        """;
        try (Connection cnx = Mydb.getInstance().getConnection();
             PreparedStatement ps = cnx.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, eventId);
            ps.setInt(2, userId);
            ps.setFloat(3, paiement);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur addInscription: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  UPDATE
    // ─────────────────────────────────────────────────────────────

    public void updateStatut(int inscriptionId, String newStatut) {
        if (newStatut == null || newStatut.isBlank())
            throw new IllegalArgumentException("Statut invalide.");
        String sql = "UPDATE inscription SET statut = ? WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, newStatut);
            ps.setInt(2, inscriptionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur updateStatut: " + ex.getMessage(), ex);
        }
    }

    /**
     * ✅ NOUVEAU — met à jour paiement (float) dans la base
     * Signature float pour correspondre exactement à la colonne SQL
     * et à addInscription(int, int, float).
     *
     * Appelé dans le controller après chaque ajout ou suppression
     * de ticket : paiement = prix_evenement × nb_tickets_inscription
     */
    public void updatePaiementFloat(int inscriptionId, float paiement) {
        String sql = "UPDATE inscription SET paiement = ? WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setFloat(1, paiement);
            ps.setInt(2, inscriptionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur updatePaiementFloat: " + ex.getMessage(), ex);
        }
    }

    // Gardé pour compatibilité (String)
    public void updatePaiement(int inscriptionId, String paiement) {
        String sql = "UPDATE inscription SET paiement = ? WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, paiement);
            ps.setInt(2, inscriptionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur updatePaiement: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DELETE
    // ─────────────────────────────────────────────────────────────

    public void delete(int inscriptionId) {
        String sql = "DELETE FROM inscription WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, inscriptionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur delete: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SEARCH
    // ─────────────────────────────────────────────────────────────

    public List<Inscription> searchByUser(int eventId, Integer userId, String statut) {
        String sql = """
            SELECT id, event_id, user_id, statut, paiement, date_creation
            FROM inscription
            WHERE event_id = ?
              AND (? IS NULL OR user_id = ?)
              AND (? IS NULL OR statut = ?)
            ORDER BY date_creation DESC
        """;
        List<Inscription> list = new ArrayList<>();
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            if (userId == null) { ps.setNull(2, Types.INTEGER); ps.setNull(3, Types.INTEGER); }
            else { ps.setInt(2, userId); ps.setInt(3, userId); }
            String st = (statut == null || statut.isBlank()) ? null : statut;
            ps.setString(4, st);
            ps.setString(5, st);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Inscription i = new Inscription();
                    i.setId(rs.getInt("id"));
                    i.setEventId(rs.getInt("event_id"));
                    i.setUserId(rs.getInt("user_id"));
                    i.setStatut(rs.getString("statut"));
                    i.setPaiement(rs.getString("paiement"));
                    i.setDateCreation(toLDT(rs.getTimestamp("date_creation")));
                    list.add(i);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur searchByUser: " + ex.getMessage(), ex);
        }
        return list;
    }
}
