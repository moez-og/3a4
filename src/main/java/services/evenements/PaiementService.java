package services.evenements;

import models.evenements.Paiement;
import utils.Mydb;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaiementService — CRUD et logique métier pour les paiements.
 *
 * Méthodes : CARTE_BANCAIRE | ESPECES | VIREMENT | FLOUCI
 */
public class PaiementService {

    private Connection getConnection() throws SQLException {
        return Mydb.getInstance().getConnection();
    }

    private LocalDateTime toLDT(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    // ─────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Enregistre un paiement pour une inscription donnée.
     * Génère automatiquement un code de référence unique.
     *
     * @return l'ID du paiement créé, ou -1 en cas d'échec
     */
    public int addPaiement(int inscriptionId, double montant, String methode,
                           String nomCarte, String quatreDerniers) {
        String referenceCode = generateReferenceCode();
        String sql = """
            INSERT INTO paiement(inscription_id, montant, methode, statut, reference_code,
                                 nom_carte, quatre_derniers, date_paiement)
            VALUES(?, ?, ?, 'PAYE', ?, ?, ?, NOW())
        """;
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, inscriptionId);
            ps.setDouble(2, montant);
            ps.setString(3, methode);
            ps.setString(4, referenceCode);
            ps.setString(5, nomCarte);
            ps.setString(6, quatreDerniers);
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur addPaiement: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────────

    /**
     * Récupère le paiement associé à une inscription (le dernier si plusieurs).
     * @return le Paiement ou null si aucun paiement n'existe
     */
    public Paiement getByInscriptionId(int inscriptionId) {
        String sql = """
            SELECT id, inscription_id, montant, methode, statut, reference_code,
                   nom_carte, quatre_derniers, date_paiement
            FROM paiement
            WHERE inscription_id = ?
            ORDER BY date_paiement DESC
            LIMIT 1
        """;
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, inscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getByInscriptionId: " + ex.getMessage(), ex);
        }
    }

    public Paiement getById(int paiementId) {
        String sql = """
            SELECT id, inscription_id, montant, methode, statut, reference_code,
                   nom_carte, quatre_derniers, date_paiement
            FROM paiement
            WHERE id = ?
        """;
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, paiementId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getById: " + ex.getMessage(), ex);
        }
    }

    /**
     * Vérifie si une inscription a déjà été payée (au moins un paiement PAYE).
     */
    public boolean isPaid(int inscriptionId) {
        String sql = "SELECT 1 FROM paiement WHERE inscription_id = ? AND statut = 'PAYE' LIMIT 1";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, inscriptionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur isPaid: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DELETE
    // ─────────────────────────────────────────────────────────────

    public void delete(int paiementId) {
        String sql = "DELETE FROM paiement WHERE id = ?";
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, paiementId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Erreur delete paiement: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    private Paiement mapRow(ResultSet rs) throws SQLException {
        Paiement p = new Paiement();
        p.setId(rs.getInt("id"));
        p.setInscriptionId(rs.getInt("inscription_id"));
        p.setMontant(rs.getDouble("montant"));
        p.setMethode(rs.getString("methode"));
        p.setStatut(rs.getString("statut"));
        p.setReferenceCode(rs.getString("reference_code"));
        p.setNomCarte(rs.getString("nom_carte"));
        p.setQuatreDerniers(rs.getString("quatre_derniers"));
        p.setDatePaiement(toLDT(rs.getTimestamp("date_paiement")));
        return p;
    }

    /** Génère un code de référence unique : PAY-XXXXXXXX */
    private String generateReferenceCode() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
