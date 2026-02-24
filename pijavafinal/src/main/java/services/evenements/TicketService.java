package services.evenements;

import models.evenements.Ticket;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TicketService — version mise à jour
 * ═══════════════════════════════════════════════════════
 * CHANGEMENTS vs version originale :
 *
 *  1. getListByInscriptionId(int) → List<Ticket>
 *     Retourne TOUS les tickets d'une inscription
 *     (remplace getByInscriptionId qui ne retournait qu'un seul)
 *
 *  2. countByInscriptionId(int) → int
 *     Compte les tickets d'une inscription
 *     → utilisé pour calculer paiement = prix × count
 *
 *  3. countByEventId(int) → int
 *     Compte le total de tickets d'un événement
 *     → utilisé pour places disponibles = capaciteMax − count
 *
 *  4. createForInscription() — blocage "1 ticket max" SUPPRIMÉ
 *     On peut maintenant créer plusieurs tickets par inscription
 *     La limite est la capaciteMax de l'événement (vérifiée dans le controller)
 * ═══════════════════════════════════════════════════════
 */
public class TicketService {

    private Connection getConnection() throws SQLException {
        return utils.Mydb.getInstance().getConnection();
    }

    // ─────────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────────

    /**
     * ✅ NOUVEAU — tous les tickets d'une inscription (liste)
     */
    public List<Ticket> getListByInscriptionId(int inscriptionId) {
        String sql = """
            SELECT id, inscription_id, date
            FROM ticket
            WHERE inscription_id = ?
            ORDER BY date DESC
        """;
        List<Ticket> list = new ArrayList<>();
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, inscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ticket t = new Ticket();
                    t.setId(rs.getInt("id"));
                    t.setInscriptionId(rs.getInt("inscription_id"));
                    Date d = rs.getDate("date");
                    t.setDate(d == null ? null : d.toLocalDate());
                    list.add(t);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getListByInscriptionId: " + ex.getMessage(), ex);
        }
        return list;
    }

    /**
     * Gardé pour compatibilité — retourne le 1er ticket ou null
     */
    public Ticket getByInscriptionId(int inscriptionId) {
        List<Ticket> list = getListByInscriptionId(inscriptionId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * ✅ NOUVEAU — nombre de tickets d'une inscription
     * Utilisé pour : paiement = prix × countByInscriptionId(inscriptionId)
     */
    public int countByInscriptionId(int inscriptionId) {
        String sql = "SELECT COUNT(*) FROM ticket WHERE inscription_id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, inscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur countByInscriptionId: " + ex.getMessage(), ex);
        }
    }

    /**
     * ✅ NOUVEAU — total tickets d'un événement
     * Utilisé pour : places disponibles = capaciteMax − countByEventId(eventId)
     */
    public int countByEventId(int eventId) {
        String sql = """
            SELECT COUNT(*)
            FROM ticket t
            JOIN inscription i ON i.id = t.inscription_id
            WHERE i.event_id = ?
        """;
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur countByEventId: " + ex.getMessage(), ex);
        }
    }

    public List<Ticket> getByEventId(int eventId) {
        String sql = """
            SELECT t.id, t.inscription_id, t.date
            FROM ticket t
            JOIN inscription i ON i.id = t.inscription_id
            WHERE i.event_id = ?
            ORDER BY t.date DESC
        """;
        List<Ticket> list = new ArrayList<>();
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ticket t = new Ticket();
                    t.setId(rs.getInt("id"));
                    t.setInscriptionId(rs.getInt("inscription_id"));
                    Date d = rs.getDate("date");
                    t.setDate(d == null ? null : d.toLocalDate());
                    list.add(t);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getByEventId: " + ex.getMessage(), ex);
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────

    /**
     * ✅ MODIFIÉ — crée UN ticket pour une inscription.
     * Le blocage "un seul ticket par inscription" est SUPPRIMÉ.
     * La vérification de capacité (places disponibles) est faite
     * dans EvenementsAdminController.onGenererTicket() avant l'appel.
     */
    public int createForInscription(int inscriptionId) {
        String sql = "INSERT INTO ticket(inscription_id, date) VALUES(?, ?)";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, inscriptionId);
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur createForInscription: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DELETE
    // ─────────────────────────────────────────────────────────────

    public void delete(int ticketId) {
        String sql = "DELETE FROM ticket WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur delete Ticket: " + ex.getMessage(), ex);
        }
    }
}
