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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class OffreService {
    private final Connection connection;

    private final String offreTable;
    private final String lieuTable;

    private final String offreIdCol;
    private final String offreUserIdCol;
    private final String offreEventIdCol;
    private final String offreLieuFkCol;
    private final String offreTitreCol;
    private final String offreDescriptionCol;
    private final String offreTypeCol;
    private final String offrePourcentageCol;
    private final String offreDateDebutCol;
    private final String offreDateFinCol;
    private final String offreStatutCol;

    private final String lieuPkCol;
    private final Set<String> offreColsLower;
    private final Set<String> lieuColsLower;

    public OffreService() {
        this.connection = Mydb.getInstance().getConnection();
        try {
            this.offreTable = resolveExistingTable("offre", "offres", "offer", "offers");
            this.lieuTable = resolveExistingTable("lieu", "lieux");

            this.offreColsLower = listColumnsLower(offreTable);
            this.lieuColsLower = listColumnsLower(lieuTable);

            this.offreIdCol = resolveExistingColumn(offreTable, "id", "id_offre", "offre_id");
            this.offreUserIdCol = resolveExistingColumnOrNull(offreTable, "user_id", "id_user", "utilisateur_id", "id_utilisateur");
            this.offreEventIdCol = resolveExistingColumnOrNull(offreTable, "event_id", "id_event", "evenement_id", "id_evenement");

                this.offreLieuFkCol = resolveLieuFkColumnOrNull(offreTable,
                    "lieu_id", "id_lieu", "lieux_id", "id_lieux", "lieu", "lieuId", "idLieu", "idlieu");

            this.offreTitreCol = resolveExistingColumn(offreTable, "titre", "title", "nom", "libelle");
            this.offreDescriptionCol = resolveExistingColumnOrNull(offreTable, "description", "desc", "details");
            this.offreTypeCol = resolveExistingColumn(offreTable, "type", "categorie", "category");
            this.offrePourcentageCol = resolveExistingColumn(offreTable, "pourcentage", "percentage", "reduction", "discount");
            this.offreDateDebutCol = resolveExistingColumn(offreTable, "date_debut", "dateDebut", "start_date", "date_start");
            this.offreDateFinCol = resolveExistingColumn(offreTable, "date_fin", "dateFin", "end_date", "date_end");
            this.offreStatutCol = resolveExistingColumn(offreTable, "statut", "status", "etat");

            this.lieuPkCol = resolveExistingColumn(lieuTable, "id", "id_lieu", "lieu_id");
        } catch (SQLException e) {
            throw new RuntimeException("Impossible de résoudre le schéma Offres: " + e.getMessage(), e);
        }
    }

    public boolean supportsLieuAssociation() {
        return offreLieuFkCol != null && offreColsLower.contains(offreLieuFkCol.toLowerCase(Locale.ROOT));
    }

    public List<Offre> obtenirToutes() throws SQLException {
        List<Offre> offres = new ArrayList<>();
        String sql = "SELECT * FROM " + offreTable + " ORDER BY " + offreDateDebutCol + " DESC, " + offreIdCol + " DESC";

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
        String sql = "SELECT * FROM " + lieuTable + " ORDER BY " + lieuPkCol + " DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt(lieuPkCol);
                String nom = extractLieuLabel(rs);
                lieux.add(new Lieu(id, nom));
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
        if (!supportsLieuAssociation()) {
            return obtenirToutes();
        }
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
        if (!supportsLieuAssociation()) {
            throw new SQLException("La table '" + offreTable + "' ne contient aucune colonne de liaison vers '" + lieuTable + "'. "
                    + "Ajoute une colonne (ex: lieu_id) puis relance. Exemple SQL: ALTER TABLE " + offreTable + " ADD COLUMN lieu_id INT;");
        }
        validate(offre);
        ensureWriteColumns();

        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();

        if (offreUserIdCol != null) { cols.add(offreUserIdCol); vals.add(offre.getUser_id() > 0 ? offre.getUser_id() : null); }
        if (offreEventIdCol != null) { cols.add(offreEventIdCol); vals.add(offre.getEvent_id() > 0 ? offre.getEvent_id() : null); }

        cols.add(offreLieuFkCol); vals.add(offre.getLieu_id());
        cols.add(offreTitreCol); vals.add(offre.getTitre());
        if (offreDescriptionCol != null) { cols.add(offreDescriptionCol); vals.add(offre.getDescription()); }
        cols.add(offreTypeCol); vals.add(offre.getType());
        cols.add(offrePourcentageCol); vals.add(offre.getPourcentage());
        cols.add(offreDateDebutCol); vals.add(offre.getDate_debut());
        cols.add(offreDateFinCol); vals.add(offre.getDate_fin());
        cols.add(offreStatutCol); vals.add(offre.getStatut());

        String placeholders = String.join(", ", cols.stream().map(c -> "?").toList());
        String sql = "INSERT INTO " + offreTable + " (" + String.join(", ", cols) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            applyParams(ps, vals);
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
        if (!supportsLieuAssociation()) {
            throw new SQLException("La table '" + offreTable + "' ne contient aucune colonne de liaison vers '" + lieuTable + "'. "
                    + "Ajoute une colonne (ex: lieu_id) puis relance. Exemple SQL: ALTER TABLE " + offreTable + " ADD COLUMN lieu_id INT;");
        }
        validate(offre);
        ensureWriteColumns();

        List<String> sets = new ArrayList<>();
        List<Object> vals = new ArrayList<>();

        if (offreUserIdCol != null) { sets.add(offreUserIdCol + " = ?"); vals.add(offre.getUser_id() > 0 ? offre.getUser_id() : null); }
        if (offreEventIdCol != null) { sets.add(offreEventIdCol + " = ?"); vals.add(offre.getEvent_id() > 0 ? offre.getEvent_id() : null); }

        sets.add(offreLieuFkCol + " = ?"); vals.add(offre.getLieu_id());
        sets.add(offreTitreCol + " = ?"); vals.add(offre.getTitre());
        if (offreDescriptionCol != null) { sets.add(offreDescriptionCol + " = ?"); vals.add(offre.getDescription()); }
        sets.add(offreTypeCol + " = ?"); vals.add(offre.getType());
        sets.add(offrePourcentageCol + " = ?"); vals.add(offre.getPourcentage());
        sets.add(offreDateDebutCol + " = ?"); vals.add(offre.getDate_debut());
        sets.add(offreDateFinCol + " = ?"); vals.add(offre.getDate_fin());
        sets.add(offreStatutCol + " = ?"); vals.add(offre.getStatut());

        String sql = "UPDATE " + offreTable + " SET " + String.join(", ", sets) + " WHERE " + offreIdCol + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            applyParams(ps, vals);
            ps.setInt(vals.size() + 1, offre.getId());
            ps.executeUpdate();
        }
    }

    public void supprimer(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Identifiant offre invalide.");
        }
        String sql = "DELETE FROM " + offreTable + " WHERE " + offreIdCol + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private void validate(Offre offre) {
        if (offre == null) {
            throw new IllegalArgumentException("Offre obligatoire.");
        }
        if (supportsLieuAssociation() && offre.getLieu_id() <= 0) {
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

    private void applyParams(PreparedStatement ps, List<Object> vals) throws SQLException {
        for (int i = 0; i < vals.size(); i++) {
            Object v = vals.get(i);
            int idx = i + 1;
            if (v == null) {
                ps.setNull(idx, Types.NULL);
            } else if (v instanceof Integer vi) {
                ps.setInt(idx, vi);
            } else if (v instanceof Float vf) {
                ps.setFloat(idx, vf);
            } else if (v instanceof java.sql.Date vd) {
                ps.setDate(idx, vd);
            } else if (v instanceof String vs) {
                ps.setString(idx, vs);
            } else {
                ps.setObject(idx, v);
            }
        }
    }

    private Offre mapOffre(ResultSet rs) throws SQLException {
        Offre offre = new Offre();
        offre.setId(rs.getInt(offreIdCol));

        if (offreUserIdCol != null && offreColsLower.contains(offreUserIdCol.toLowerCase(Locale.ROOT))) {
            offre.setUser_id(rs.getInt(offreUserIdCol));
            if (rs.wasNull()) offre.setUser_id(0);
        } else {
            offre.setUser_id(0);
        }

        if (offreEventIdCol != null && offreColsLower.contains(offreEventIdCol.toLowerCase(Locale.ROOT))) {
            offre.setEvent_id(rs.getInt(offreEventIdCol));
            if (rs.wasNull()) offre.setEvent_id(0);
        } else {
            offre.setEvent_id(0);
        }

        if (supportsLieuAssociation()) {
            offre.setLieu_id(rs.getInt(offreLieuFkCol));
        } else {
            offre.setLieu_id(0);
        }
        offre.setTitre(rs.getString(offreTitreCol));
        offre.setDescription(offreDescriptionCol == null ? null : rs.getString(offreDescriptionCol));
        offre.setType(rs.getString(offreTypeCol));
        offre.setPourcentage(rs.getFloat(offrePourcentageCol));
        offre.setDate_debut(rs.getDate(offreDateDebutCol));
        offre.setDate_fin(rs.getDate(offreDateFinCol));
        offre.setStatut(rs.getString(offreStatutCol));
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

    private void ensureWriteColumns() throws SQLException {
        // These columns must exist to allow inserting/updating offers.
        List<String> required = Arrays.asList(
                offreTitreCol,
                offreTypeCol,
                offrePourcentageCol,
                offreDateDebutCol,
                offreDateFinCol,
                offreStatutCol
        );
        if (supportsLieuAssociation()) {
            required = new ArrayList<>(required);
            required.add(0, offreLieuFkCol);
        }
        for (String col : required) {
            if (col == null || !offreColsLower.contains(col.toLowerCase(Locale.ROOT))) {
                throw new SQLException("Colonne requise manquante dans " + offreTable + ": " + col + " (colonnes: " + String.join(", ", offreColsLower) + ")");
            }
        }
    }

    private Set<String> listColumnsLower(String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> existing = new HashSet<>();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, "%")) {
            while (rs.next()) {
                String c = rs.getString("COLUMN_NAME");
                if (c != null) existing.add(c.toLowerCase(Locale.ROOT));
            }
        }
        return existing;
    }

    private String resolveExistingTable(String... candidates) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> existing = new HashSet<>();
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String t = rs.getString("TABLE_NAME");
                if (t != null) existing.add(t.toLowerCase(Locale.ROOT));
            }
        }
        for (String candidate : candidates) {
            if (candidate != null && existing.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        throw new SQLException("Aucune table trouvée parmi: " + String.join(", ", candidates));
    }

    private String resolveExistingColumn(String tableName, String... candidates) throws SQLException {
        String col = resolveExistingColumnOrNull(tableName, candidates);
        if (col != null) return col;
        throw new SQLException("Aucune colonne trouvée dans " + tableName + " parmi: " + String.join(", ", candidates) + " (colonnes: " + String.join(", ", listColumnsLower(tableName)) + ")");
    }

    private String resolveExistingColumnOrNull(String tableName, String... candidates) throws SQLException {
        Set<String> existing = listColumnsLower(tableName);
        for (String candidate : candidates) {
            if (candidate != null && existing.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveLieuFkColumnOrNull(String tableName, String... candidates) throws SQLException {
        String direct = resolveExistingColumnOrNull(tableName, candidates);
        if (direct != null) return direct;

        // Heuristics: pick a column that mentions "lieu".
        Set<String> cols = listColumnsLower(tableName);

        for (String c : cols) {
            if (c.contains("lieu") && (c.endsWith("id") || c.contains("_id") || c.contains("id_"))) {
                return c;
            }
        }
        for (String c : cols) {
            if (c.contains("lieu")) {
                return c;
            }
        }

        return null;
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
