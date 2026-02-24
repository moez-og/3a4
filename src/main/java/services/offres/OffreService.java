package services.offres;

import models.lieux.Lieu;
import models.offres.Offre;
import utils.Mydb;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OffreService {
    private final Connection connection;
    private final String offreLieuFkCol;
    private final String lieuPkCol;

    public OffreService() {
        this.connection = Mydb.getInstance().getConnection();
        try {
            this.offreLieuFkCol = resolveExistingColumn("offre", "lieu_id", "id_lieu");
            this.lieuPkCol = resolveExistingColumn("lieu", "id", "id_lieu", "lieu_id");
        } catch (SQLException e) {
            throw new RuntimeException("Impossible de résoudre le schéma Offres: " + e.getMessage(), e);
        }
    }

    public List<Offre> obtenirToutes() throws SQLException {
        List<Offre> offres = new ArrayList<>();
        String sql = "SELECT * FROM offre ORDER BY date_debut DESC, id DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                offres.add(mapOffre(rs));
            }
        }
        return offres;
    }

    public List<Lieu> obtenirTousLesLieux() throws SQLException {
        List<Lieu> lieux = new ArrayList<>();
        String sql = "SELECT * FROM lieu ORDER BY " + lieuPkCol + " DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt(lieuPkCol);
                String nom = extractLieuLabel(rs);
                Lieu lieu = new Lieu();
                lieu.setId(id);
                lieu.setNom(nom);
                lieux.add(lieu);
            }
        }
        return lieux;
    }

    public Map<Integer, String> obtenirMapNomsLieux() throws SQLException {
        Map<Integer, String> map = new HashMap<>();
        for (Lieu lieu : obtenirTousLesLieux()) {
            map.put(lieu.getId(), lieu.toString());
        }
        return map;
    }

    public List<Offre> obtenirOffresDisponibles() throws SQLException {
        LocalDate today = LocalDate.now();
        List<Offre> all = obtenirToutes();
        List<Offre> filtered = new ArrayList<>();

        for (Offre offre : all) {
            String statut = safe(offre.getStatut()).trim().toLowerCase();
            LocalDate start = offre.getDate_debut() != null ? offre.getDate_debut().toLocalDate() : null;
            LocalDate end = offre.getDate_fin() != null ? offre.getDate_fin().toLocalDate() : null;

            boolean statusOk = "active".equals(statut);
            boolean dateOk = start != null && end != null && !today.isBefore(start) && !today.isAfter(end);
            if (statusOk && dateOk) {
                filtered.add(offre);
            }
        }
        return filtered;
    }

    public List<Offre> obtenirOffresParLieu(int lieuId) throws SQLException {
        List<Offre> all = obtenirToutes();
        List<Offre> filtered = new ArrayList<>();
        for (Offre offre : all) {
            if (offre.getLieu_id() == lieuId) {
                filtered.add(offre);
            }
        }
        return filtered;
    }

    public void ajouter(Offre offre) throws SQLException {
        validate(offre);
        String sql = "INSERT INTO offre (user_id, event_id, " + offreLieuFkCol + ", titre, description, type, pourcentage, date_debut, date_fin, statut) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            applyOffreParams(ps, offre);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    offre.setId(keys.getInt(1));
                }
            }
        }
    }

    public void modifier(Offre offre) throws SQLException {
        if (offre == null || offre.getId() <= 0) {
            throw new IllegalArgumentException("Offre invalide pour modification.");
        }
        validate(offre);
        String sql = "UPDATE offre SET user_id = ?, event_id = ?, " + offreLieuFkCol + " = ?, titre = ?, description = ?, type = ?, pourcentage = ?, date_debut = ?, date_fin = ?, statut = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            applyOffreParams(ps, offre);
            ps.setInt(11, offre.getId());
            ps.executeUpdate();
        }
    }

    public void supprimer(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Identifiant offre invalide.");
        }
        String sql = "DELETE FROM offre WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void validate(Offre offre) {
        if (offre == null) {
            throw new IllegalArgumentException("Offre obligatoire.");
        }
        if (offre.getLieu_id() <= 0) {
            throw new IllegalArgumentException("Le lieu est obligatoire.");
        }
        if (offre.getTitre() == null || offre.getTitre().isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire.");
        }
        if (offre.getType() == null || offre.getType().isBlank()) {
            throw new IllegalArgumentException("Le type d'offre est obligatoire.");
        }
        if (offre.getPourcentage() < 0 || offre.getPourcentage() > 100) {
            throw new IllegalArgumentException("Le pourcentage doit être entre 0 et 100.");
        }
        if (offre.getDate_debut() == null || offre.getDate_fin() == null) {
            throw new IllegalArgumentException("Les dates début et fin sont obligatoires.");
        }
        if (offre.getDate_fin().before(offre.getDate_debut())) {
            throw new IllegalArgumentException("La date fin doit être postérieure ou égale à la date début.");
        }
        List<String> allowed = Arrays.asList("active", "inactive", "expirée");
        String statut = offre.getStatut() == null ? "" : offre.getStatut().trim().toLowerCase();
        if (!allowed.contains(statut)) {
            throw new IllegalArgumentException("Statut invalide. Valeurs autorisées: active, inactive, expirée.");
        }
        offre.setStatut(statut);
    }

    private void applyOffreParams(PreparedStatement ps, Offre offre) throws SQLException {
        if (offre.getUser_id() > 0) {
            ps.setInt(1, offre.getUser_id());
        } else {
            ps.setNull(1, Types.INTEGER);
        }

        if (offre.getEvent_id() > 0) {
            ps.setInt(2, offre.getEvent_id());
        } else {
            ps.setNull(2, Types.INTEGER);
        }

        ps.setInt(3, offre.getLieu_id());
        ps.setString(4, offre.getTitre());
        ps.setString(5, offre.getDescription());
        ps.setString(6, offre.getType());
        ps.setFloat(7, offre.getPourcentage());
        ps.setDate(8, offre.getDate_debut());
        ps.setDate(9, offre.getDate_fin());
        ps.setString(10, offre.getStatut());
    }

    private Offre mapOffre(ResultSet rs) throws SQLException {
        Offre offre = new Offre();
        offre.setId(rs.getInt("id"));
        offre.setUser_id(rs.getInt("user_id"));
        if (rs.wasNull()) {
            offre.setUser_id(0);
        }
        offre.setEvent_id(rs.getInt("event_id"));
        if (rs.wasNull()) {
            offre.setEvent_id(0);
        }
        offre.setLieu_id(rs.getInt(offreLieuFkCol));
        offre.setTitre(rs.getString("titre"));
        offre.setDescription(rs.getString("description"));
        offre.setType(rs.getString("type"));
        offre.setPourcentage(rs.getFloat("pourcentage"));
        offre.setDate_debut(rs.getDate("date_debut"));
        offre.setDate_fin(rs.getDate("date_fin"));
        offre.setStatut(rs.getString("statut"));
        return offre;
    }

    private String extractLieuLabel(ResultSet rs) throws SQLException {
        String[] preferredColumns = {"nom", "titre", "name", "libelle"};
        for (String col : preferredColumns) {
            try {
                String value = rs.getString(col);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (SQLException ignored) {
            }
        }

        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnName(i);
            int type = metaData.getColumnType(i);
            if ("id".equalsIgnoreCase(columnName) || columnName.equalsIgnoreCase(lieuPkCol)) {
                continue;
            }
            if (type == Types.VARCHAR || type == Types.CHAR || type == Types.LONGVARCHAR) {
                String value = rs.getString(i);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }

        int id = rs.getInt(lieuPkCol);
        return "Lieu #" + id;
    }

    private String resolveExistingColumn(String tableName, String... candidates) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> existing = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, "%")) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }

        for (String candidate : candidates) {
            if (existing.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }
        throw new SQLException("Aucune colonne trouvée dans " + tableName + " parmi: " + String.join(", ", candidates));
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
