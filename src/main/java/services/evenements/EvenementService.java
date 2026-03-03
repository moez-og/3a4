package services.evenements;

import models.evenements.Evenement;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EvenementService {

    // ✅ ADAPTE ICI selon ton template:
    private Connection getConnection() throws SQLException {
        // exemple 1: return utils.Mydb.getInstance().getConnection();
        // exemple 2: return utils.DBConnection.getConnection();
        return utils.Mydb.getInstance().getConnection();
    }

    private LocalDateTime toLDT(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    // ========== READ ==========
    public List<Evenement> getAll() {
        String sql = """
            SELECT id, date_creation, titre, description, date_debut, date_fin,
                   capacite_max, lieu_id, statut, type, image_url, prix
            FROM evenement
            ORDER BY date_debut DESC
        """;

        List<Evenement> list = new ArrayList<>();
        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Evenement e = new Evenement();
                e.setId(rs.getInt("id"));
                e.setDateCreation(toLDT(rs.getTimestamp("date_creation")));
                e.setTitre(rs.getString("titre"));
                e.setDescription(rs.getString("description"));
                e.setDateDebut(toLDT(rs.getTimestamp("date_debut")));
                e.setDateFin(toLDT(rs.getTimestamp("date_fin")));
                e.setCapaciteMax(rs.getInt("capacite_max"));

                int lieu = rs.getInt("lieu_id");
                e.setLieuId(rs.wasNull() ? null : lieu);

                e.setStatut(rs.getString("statut"));
                e.setType(rs.getString("type"));
                e.setImageUrl(rs.getString("image_url"));
                e.setPrix(rs.getDouble("prix"));

                list.add(e);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getAll Evenement: " + ex.getMessage(), ex);
        }
        return list;
    }

    public Evenement getById(int id) {
        String sql = """
            SELECT id, date_creation, titre, description, date_debut, date_fin,
                   capacite_max, lieu_id, statut, type, image_url, prix
            FROM evenement
            WHERE id = ?
        """;

        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Evenement e = new Evenement();
                e.setId(rs.getInt("id"));
                e.setDateCreation(toLDT(rs.getTimestamp("date_creation")));
                e.setTitre(rs.getString("titre"));
                e.setDescription(rs.getString("description"));
                e.setDateDebut(toLDT(rs.getTimestamp("date_debut")));
                e.setDateFin(toLDT(rs.getTimestamp("date_fin")));
                e.setCapaciteMax(rs.getInt("capacite_max"));

                int lieu = rs.getInt("lieu_id");
                e.setLieuId(rs.wasNull() ? null : lieu);

                e.setStatut(rs.getString("statut"));
                e.setType(rs.getString("type"));
                e.setImageUrl(rs.getString("image_url"));
                e.setPrix(rs.getDouble("prix"));

                return e;
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur getById Evenement: " + ex.getMessage(), ex);
        }
    }

    // ========== CREATE ==========
    public int add(Evenement e) {
        validateEvenement(e);

        String sql = """
            INSERT INTO evenement(date_creation, titre, description, date_debut, date_fin,
                                 capacite_max, lieu_id, statut, type, image_url, prix)
            VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, e.getTitre());
            ps.setString(2, e.getDescription());
            ps.setTimestamp(3, Timestamp.valueOf(e.getDateDebut()));
            ps.setTimestamp(4, Timestamp.valueOf(e.getDateFin()));
            ps.setInt(5, e.getCapaciteMax());

            if (e.getLieuId() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, e.getLieuId());

            ps.setString(7, e.getStatut());
            ps.setString(8, e.getType());
            ps.setString(9, e.getImageUrl());
            ps.setDouble(10, e.getPrix());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur add Evenement: " + ex.getMessage(), ex);
        }
    }

    // ========== UPDATE ==========
    public void update(Evenement e) {
        if (e.getId() <= 0) throw new IllegalArgumentException("ID événement invalide.");
        validateEvenement(e);

        String sql = """
            UPDATE evenement
            SET titre=?, description=?, date_debut=?, date_fin=?, capacite_max=?,
                lieu_id=?, statut=?, type=?, image_url=?, prix=?
            WHERE id=?
        """;

        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, e.getTitre());
            ps.setString(2, e.getDescription());
            ps.setTimestamp(3, Timestamp.valueOf(e.getDateDebut()));
            ps.setTimestamp(4, Timestamp.valueOf(e.getDateFin()));
            ps.setInt(5, e.getCapaciteMax());

            if (e.getLieuId() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, e.getLieuId());

            ps.setString(7, e.getStatut());
            ps.setString(8, e.getType());
            ps.setString(9, e.getImageUrl());
            ps.setDouble(10, e.getPrix());
            ps.setInt(11, e.getId());

            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur update Evenement: " + ex.getMessage(), ex);
        }
    }

    // ========== DELETE ==========
    public void delete(int id) {
        String sql = "DELETE FROM evenement WHERE id = ?";

        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur delete Evenement: " + ex.getMessage(), ex);
        }
    }

    // ========== SEARCH / FILTER (DB) ==========
    public List<Evenement> search(String q, String type, String statut) {
        // q peut matcher titre/description
        String sql = """
            SELECT id, date_creation, titre, description, date_debut, date_fin,
                   capacite_max, lieu_id, statut, type, image_url, prix
            FROM evenement
            WHERE (? IS NULL OR titre LIKE ? OR description LIKE ?)
              AND (? IS NULL OR type = ?)
              AND (? IS NULL OR statut = ?)
            ORDER BY date_debut DESC
        """;

        List<Evenement> list = new ArrayList<>();
        String like = (q == null || q.isBlank()) ? null : "%" + q.trim() + "%";

        try (Connection cn = getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);

            ps.setString(4, (type == null || type.isBlank()) ? null : type);
            ps.setString(5, (type == null || type.isBlank()) ? null : type);

            ps.setString(6, (statut == null || statut.isBlank()) ? null : statut);
            ps.setString(7, (statut == null || statut.isBlank()) ? null : statut);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Evenement e = new Evenement();
                    e.setId(rs.getInt("id"));
                    e.setDateCreation(toLDT(rs.getTimestamp("date_creation")));
                    e.setTitre(rs.getString("titre"));
                    e.setDescription(rs.getString("description"));
                    e.setDateDebut(toLDT(rs.getTimestamp("date_debut")));
                    e.setDateFin(toLDT(rs.getTimestamp("date_fin")));
                    e.setCapaciteMax(rs.getInt("capacite_max"));

                    int lieu = rs.getInt("lieu_id");
                    e.setLieuId(rs.wasNull() ? null : lieu);

                    e.setStatut(rs.getString("statut"));
                    e.setType(rs.getString("type"));
                    e.setImageUrl(rs.getString("image_url"));
                    e.setPrix(rs.getDouble("prix"));
                    list.add(e);
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Erreur search Evenement: " + ex.getMessage(), ex);
        }
        return list;
    }

    // ========== VALIDATION ==========
    private void validateEvenement(Evenement e) {
        if (e.getTitre() == null || e.getTitre().isBlank())
            throw new IllegalArgumentException("Titre obligatoire.");
        if (e.getDateDebut() == null || e.getDateFin() == null)
            throw new IllegalArgumentException("Dates obligatoires.");
        if (e.getDateFin().isBefore(e.getDateDebut()))
            throw new IllegalArgumentException("Date fin doit être >= date début.");
        if (e.getCapaciteMax() <= 0)
            throw new IllegalArgumentException("Capacité max doit être > 0.");
        if (e.getType() == null || e.getType().isBlank())
            throw new IllegalArgumentException("Type obligatoire (PRIVE/PUBLIC).");
        if (e.getStatut() == null || e.getStatut().isBlank())
            throw new IllegalArgumentException("Statut obligatoire (OUVERT/FERME/ANNULE).");
        if (e.getPrix() < 0)
            throw new IllegalArgumentException("Prix invalide.");
    }
}
