package services.offres;

import models.offres.CodePromo;
import utils.Mydb;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CodePromoService {
    private final Connection connection;

    private final String codePromoTable;
    private final String codePromoIdCol;
    private final String codePromoOffreFkCol;
    private final String codePromoUserFkCol;
    private final String codePromoQrCol;
    private final String codePromoDateGenCol;
    private final String codePromoDateExpCol;
    private final String codePromoStatutCol;

    public CodePromoService() {
        this.connection = Mydb.getInstance().getConnection();
        try {
            this.codePromoTable = resolveExistingTable("codepromo", "code_promo");
            this.codePromoIdCol = resolveExistingColumn(codePromoTable, "id", "id_codepromo", "id_code_promo");
            this.codePromoOffreFkCol = resolveExistingColumn(codePromoTable, "offre_id", "id_offre");
            this.codePromoUserFkCol = resolveExistingColumnOrNull(codePromoTable, "user_id", "id_user");
            this.codePromoQrCol = resolveExistingColumn(codePromoTable, "qr_image_url", "qr_url", "qrImageUrl");
            this.codePromoDateGenCol = resolveExistingColumn(codePromoTable, "date_generation", "generated_at", "date_gen");
            this.codePromoDateExpCol = resolveExistingColumn(codePromoTable, "date_expiration", "expires_at", "date_exp");
            this.codePromoStatutCol = resolveExistingColumn(codePromoTable, "statut", "status", "etat");
        } catch (SQLException e) {
            throw new RuntimeException("Impossible de résoudre le schéma CodePromo: " + e.getMessage(), e);
        }
    }

    public CodePromo genererOuRecupererCodePromo(int offreId, int userId) throws SQLException {
        if (offreId <= 0) {
            throw new IllegalArgumentException("Offre invalide pour génération du code promo.");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Utilisateur invalide pour génération du code promo.");
        }

        CodePromo existing = findExistingCodePromo(offreId, userId);
        if (existing != null) {
            return existing;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiration = today.plusDays(30);
        String qrUrl = buildQrUrl(offreId, userId);
        String statut = "ACTIF";

        String sql;
        if (codePromoUserFkCol != null) {
            sql = "INSERT INTO " + codePromoTable + " (" + codePromoOffreFkCol + ", " + codePromoUserFkCol + ", " + codePromoQrCol + ", " + codePromoDateGenCol + ", " + codePromoDateExpCol + ", " + codePromoStatutCol + ") VALUES (?, ?, ?, ?, ?, ?)";
        } else {
            sql = "INSERT INTO " + codePromoTable + " (" + codePromoOffreFkCol + ", " + codePromoQrCol + ", " + codePromoDateGenCol + ", " + codePromoDateExpCol + ", " + codePromoStatutCol + ") VALUES (?, ?, ?, ?, ?)";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setInt(idx++, offreId);
            if (codePromoUserFkCol != null) {
                ps.setInt(idx++, userId);
            }
            ps.setString(idx++, qrUrl);
            ps.setDate(idx++, Date.valueOf(today));
            ps.setDate(idx++, Date.valueOf(expiration));
            ps.setString(idx, statut);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }

            CodePromo promo = new CodePromo();
            promo.setId(id);
            promo.setOffre_id(offreId);
            promo.setQr_image_url(qrUrl);
            promo.setDate_generation(Date.valueOf(today));
            promo.setDate_expiration(Date.valueOf(expiration));
            promo.setStatut(statut);
            return promo;
        }
    }

    public List<CodePromo> obtenirTousCodesPromo() throws SQLException {
        List<CodePromo> list = new ArrayList<>();
        String sql = "SELECT * FROM " + codePromoTable + " ORDER BY " + codePromoDateGenCol + " DESC, " + codePromoIdCol + " DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCodePromo(rs));
            }
        }
        return list;
    }

    public void modifierCodePromo(CodePromo codePromo) throws SQLException {
        if (codePromo == null || codePromo.getId() <= 0) {
            throw new IllegalArgumentException("Code promo invalide.");
        }
        if (codePromo.getOffre_id() <= 0) {
            throw new IllegalArgumentException("Offre associée invalide.");
        }
        if (codePromo.getDate_generation() == null || codePromo.getDate_expiration() == null) {
            throw new IllegalArgumentException("Dates génération/expiration obligatoires.");
        }
        if (codePromo.getDate_expiration().before(codePromo.getDate_generation())) {
            throw new IllegalArgumentException("La date d'expiration doit être postérieure à la date de génération.");
        }

        String sql;
        if (codePromoUserFkCol != null) {
            sql = "UPDATE " + codePromoTable + " SET " + codePromoOffreFkCol + " = ?, " + codePromoUserFkCol + " = ?, " + codePromoQrCol + " = ?, " + codePromoDateGenCol + " = ?, " + codePromoDateExpCol + " = ?, " + codePromoStatutCol + " = ? WHERE " + codePromoIdCol + " = ?";
        } else {
            sql = "UPDATE " + codePromoTable + " SET " + codePromoOffreFkCol + " = ?, " + codePromoQrCol + " = ?, " + codePromoDateGenCol + " = ?, " + codePromoDateExpCol + " = ?, " + codePromoStatutCol + " = ? WHERE " + codePromoIdCol + " = ?";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            ps.setInt(idx++, codePromo.getOffre_id());
            if (codePromoUserFkCol != null) {
                if (codePromo.getUser_id() > 0) {
                    ps.setInt(idx++, codePromo.getUser_id());
                } else {
                    ps.setNull(idx++, java.sql.Types.INTEGER);
                }
            }
            ps.setString(idx++, codePromo.getQr_image_url());
            ps.setDate(idx++, codePromo.getDate_generation());
            ps.setDate(idx++, codePromo.getDate_expiration());
            ps.setString(idx++, codePromo.getStatut());
            ps.setInt(idx, codePromo.getId());
            ps.executeUpdate();
        }
    }

    public void supprimerCodePromo(int id) throws SQLException {
        if (id <= 0) {
            throw new IllegalArgumentException("Identifiant code promo invalide.");
        }
        String sql = "DELETE FROM " + codePromoTable + " WHERE " + codePromoIdCol + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private CodePromo findExistingCodePromo(int offreId, int userId) throws SQLException {
        String sql;
        if (codePromoUserFkCol != null) {
            sql = "SELECT * FROM " + codePromoTable + " WHERE " + codePromoOffreFkCol + " = ? AND " + codePromoUserFkCol + " = ? ORDER BY " + codePromoIdCol + " DESC LIMIT 1";
        } else {
            sql = "SELECT * FROM " + codePromoTable + " WHERE " + codePromoOffreFkCol + " = ? ORDER BY " + codePromoIdCol + " DESC LIMIT 1";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, offreId);
            if (codePromoUserFkCol != null) {
                ps.setInt(2, userId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CodePromo promo = new CodePromo();
                    promo.setId(rs.getInt(codePromoIdCol));
                    promo.setOffre_id(rs.getInt(codePromoOffreFkCol));
                    if (codePromoUserFkCol != null) {
                        promo.setUser_id(rs.getInt(codePromoUserFkCol));
                        if (rs.wasNull()) {
                            promo.setUser_id(0);
                        }
                    }
                    promo.setQr_image_url(rs.getString(codePromoQrCol));
                    promo.setDate_generation(rs.getDate(codePromoDateGenCol));
                    promo.setDate_expiration(rs.getDate(codePromoDateExpCol));
                    promo.setStatut(rs.getString(codePromoStatutCol));
                    return promo;
                }
            }
        }
        return null;
    }

    private CodePromo mapCodePromo(ResultSet rs) throws SQLException {
        CodePromo promo = new CodePromo();
        promo.setId(rs.getInt(codePromoIdCol));
        promo.setOffre_id(rs.getInt(codePromoOffreFkCol));
        if (codePromoUserFkCol != null) {
            promo.setUser_id(rs.getInt(codePromoUserFkCol));
            if (rs.wasNull()) {
                promo.setUser_id(0);
            }
        } else {
            promo.setUser_id(0);
        }
        promo.setQr_image_url(rs.getString(codePromoQrCol));
        promo.setDate_generation(rs.getDate(codePromoDateGenCol));
        promo.setDate_expiration(rs.getDate(codePromoDateExpCol));
        promo.setStatut(rs.getString(codePromoStatutCol));
        return promo;
    }

    private String buildQrUrl(int offreId, int userId) {
        String raw = "OFFRE=" + offreId + "|USER=" + userId + "|TS=" + System.currentTimeMillis();
        String encoded = URLEncoder.encode(raw, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" + encoded;
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

    private String resolveExistingColumnOrNull(String tableName, String... candidates) throws SQLException {
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
        return null;
    }

    private String resolveExistingTable(String... candidates) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> existing = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                existing.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        for (String candidate : candidates) {
            if (existing.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }
        throw new SQLException("Aucune table trouvée parmi: " + String.join(", ", candidates));
    }
}
