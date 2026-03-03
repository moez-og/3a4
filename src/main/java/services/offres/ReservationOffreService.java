package services.offres;

import models.offres.ReservationOffre;
import utils.Mydb;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de réservation lié aux offres.
 * La table `reservation_offre` est créée automatiquement si elle n'existe pas.
 *
 * Capacité par défaut : MAX_PERSONNES_PAR_JOUR personnes par lieu par jour.
 */
public class ReservationOffreService {

    public static final int MAX_PERSONNES_PAR_JOUR = 50;

    private final Connection cnx;

    public ReservationOffreService() {
        this.cnx = Mydb.getInstance().getConnection();
        initTable();
    }

    // ═══════════════════════════════════════════════════════════════
    //  DDL – création automatique de la table
    // ═══════════════════════════════════════════════════════════════

    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS reservation_offre (
                  id               INT AUTO_INCREMENT PRIMARY KEY,
                  user_id          INT NOT NULL,
                  offre_id         INT NOT NULL,
                  lieu_id          INT NOT NULL,
                  date_reservation DATE NOT NULL,
                  nombre_personnes INT NOT NULL DEFAULT 1,
                  statut           VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',
                  note             TEXT,
                  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Statement st = cnx.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            // Si la table existe déjà ou que la BD n'accepte pas le DDL, on ignore
            System.err.println("ReservationOffreService.initTable: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Disponibilité
    // ═══════════════════════════════════════════════════════════════

    /**
     * Retourne le total de personnes déjà réservées pour un lieu à une date donnée
     * (réservations actives = statut != ANNULÉE).
     */
    public int compterPersonnes(int lieuId, LocalDate date) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(nombre_personnes), 0)
                FROM reservation_offre
                WHERE lieu_id = ? AND date_reservation = ? AND statut != 'ANNULÉE'
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Vérifie si l'utilisateur a déjà une réservation active pour
     * la même offre + lieu + date.
     */
    public boolean dejaReserve(int userId, int offreId, int lieuId, LocalDate date) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM reservation_offre
                WHERE user_id = ? AND offre_id = ? AND lieu_id = ?
                  AND date_reservation = ? AND statut != 'ANNULÉE'
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, offreId);
            ps.setInt(3, lieuId);
            ps.setDate(4, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    //  CRUD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Crée une réservation après avoir vérifié la disponibilité.
     * Lance une {@link IllegalStateException} si la règle n'est pas respectée.
     */
    public ReservationOffre creer(ReservationOffre r) throws SQLException {
        int dejaOccupe = compterPersonnes(r.getLieuId(), r.getDateReservation().toLocalDate());
        int places = MAX_PERSONNES_PAR_JOUR - dejaOccupe;

        if (places <= 0) {
            throw new IllegalStateException(
                    "Ce lieu est complet pour le " + r.getDateReservation()
                    + " (capacité maximale atteinte : " + MAX_PERSONNES_PAR_JOUR + " personnes).");
        }
        if (r.getNombrePersonnes() > places) {
            throw new IllegalStateException(
                    "Seulement " + places + " place(s) disponible(s) pour cette date.");
        }
        if (dejaReserve(r.getUserId(), r.getOffreId(), r.getLieuId(),
                        r.getDateReservation().toLocalDate())) {
            throw new IllegalStateException(
                    "Vous avez déjà une réservation active pour cette offre à cette date.");
        }

        String sql = """
                INSERT INTO reservation_offre
                  (user_id, offre_id, lieu_id, date_reservation, nombre_personnes, statut, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getUserId());
            ps.setInt(2, r.getOffreId());
            ps.setInt(3, r.getLieuId());
            ps.setDate(4, r.getDateReservation());
            ps.setInt(5, r.getNombrePersonnes());
            ps.setString(6, r.getStatut() != null ? r.getStatut() : "EN_ATTENTE");
            if (r.getNote() != null && !r.getNote().isBlank()) {
                ps.setString(7, r.getNote());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setId(keys.getInt(1));
            }
        }
        return r;
    }

    /**
     * Toutes les réservations d'un utilisateur, triées par date décroissante.
     */
    public List<ReservationOffre> mesReservations(int userId) throws SQLException {
        String sql = """
                SELECT * FROM reservation_offre
                WHERE user_id = ?
                ORDER BY date_reservation DESC, created_at DESC
                """;
        List<ReservationOffre> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Toutes les réservations (vue admin), triées par date décroissante.
     */
    public List<ReservationOffre> toutesLesReservations() throws SQLException {
        String sql = """
                SELECT * FROM reservation_offre
                ORDER BY date_reservation DESC, created_at DESC
                """;
        List<ReservationOffre> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /**
     * Change le statut d'une réservation (usage admin : CONFIRMÉE / REFUSÉE / ANNULÉE…).
     */
    public void changerStatut(int reservationId, String nouveauStatut) throws SQLException {
        if (reservationId <= 0) throw new IllegalArgumentException("ID réservation invalide.");
        if (nouveauStatut == null || nouveauStatut.isBlank())
            throw new IllegalArgumentException("Statut vide.");
        String sql = "UPDATE reservation_offre SET statut = ? WHERE id = ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, nouveauStatut);
            ps.setInt(2, reservationId);
            int n = ps.executeUpdate();
            if (n == 0) throw new IllegalStateException("Réservation #" + reservationId + " introuvable.");
        }
    }

    /**
     * Annule une réservation (seul l'utilisateur propriétaire peut annuler).
     */
    public void annuler(int reservationId, int userId) throws SQLException {
        String sql = """
                UPDATE reservation_offre SET statut = 'ANNULÉE'
                WHERE id = ? AND user_id = ?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            ps.setInt(2, userId);
            int n = ps.executeUpdate();
            if (n == 0) throw new IllegalStateException(
                    "Aucune réservation trouvée ou vous n'êtes pas autorisé à l'annuler.");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Mapping ResultSet → objet
    // ═══════════════════════════════════════════════════════════════

    private ReservationOffre map(ResultSet rs) throws SQLException {
        ReservationOffre r = new ReservationOffre();
        r.setId(rs.getInt("id"));
        r.setUserId(rs.getInt("user_id"));
        r.setOffreId(rs.getInt("offre_id"));
        r.setLieuId(rs.getInt("lieu_id"));
        r.setDateReservation(rs.getDate("date_reservation"));
        r.setNombrePersonnes(rs.getInt("nombre_personnes"));
        r.setStatut(rs.getString("statut"));
        r.setNote(rs.getString("note"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }
}
