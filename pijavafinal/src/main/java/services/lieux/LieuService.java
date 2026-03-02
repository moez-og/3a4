package services.lieux;

import models.lieux.Lieu;
import utils.Mydb;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LieuService {

    private final Connection cnx;

    public LieuService() throws SQLException {
        cnx = Mydb.getInstance().getConnection();
    }

    /* ===================== READ ===================== */

    public List<Lieu> getAll() {
        String sql = """
                SELECT id, id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                       telephone, site_web, instagram, description, budget_min, budget_max
                FROM lieu
                ORDER BY id DESC
                """;
        List<Lieu> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(map(rs));

        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getAll: " + e.getMessage(), e);
        }
        return list;
    }

    public Lieu getById(int id) {
        String sql = """
                SELECT id, id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                       telephone, site_web, instagram, description, budget_min, budget_max
                FROM lieu
                WHERE id = ?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getById: " + e.getMessage(), e);
        }
        return null;
    }

    public List<String> getDistinctVilles() {
        String sql = "SELECT DISTINCT ville FROM lieu WHERE ville IS NOT NULL AND ville<>'' ORDER BY ville";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getDistinctVilles: " + e.getMessage(), e);
        }
        return list;
    }

    public List<String> getDistinctCategories() {
        String sql = "SELECT DISTINCT categorie FROM lieu WHERE categorie IS NOT NULL AND categorie<>'' ORDER BY categorie";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getDistinctCategories: " + e.getMessage(), e);
        }
        return list;
    }

    /* ===================== CREATE ===================== */

    public void add(Lieu l) {
        String sql = """
                INSERT INTO lieu (id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                                  telephone, site_web, instagram, description, budget_min, budget_max)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, l.getIdOffre());
            ps.setString(2, l.getNom());
            ps.setString(3, l.getVille());
            ps.setString(4, l.getAdresse());
            ps.setString(5, l.getCategorie());

            setNullableDouble(ps, 6, l.getLatitude());
            setNullableDouble(ps, 7, l.getLongitude());

            ps.setString(8, safeDefault(l.getType(), "PUBLIC"));
            ps.setString(9, emptyToNull(l.getImageUrl()));

            ps.setString(10, emptyToNull(l.getTelephone()));
            ps.setString(11, emptyToNull(l.getSiteWeb()));
            ps.setString(12, emptyToNull(l.getInstagram()));
            ps.setString(13, emptyToNull(l.getDescription()));

            setNullableBigDecimal(ps, 14, l.getBudgetMin());
            setNullableBigDecimal(ps, 15, l.getBudgetMax());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.add: " + e.getMessage(), e);
        }
    }

    /* ===================== UPDATE ===================== */

    public void update(Lieu l) {
        String sql = """
                UPDATE lieu
                SET id_offre=?, nom=?, ville=?, adresse=?, categorie=?, latitude=?, longitude=?, type=?, image_url=?,
                    telephone=?, site_web=?, instagram=?, description=?, budget_min=?, budget_max=?
                WHERE id=?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, l.getIdOffre());
            ps.setString(2, l.getNom());
            ps.setString(3, l.getVille());
            ps.setString(4, l.getAdresse());
            ps.setString(5, l.getCategorie());

            setNullableDouble(ps, 6, l.getLatitude());
            setNullableDouble(ps, 7, l.getLongitude());

            ps.setString(8, safeDefault(l.getType(), "PUBLIC"));
            ps.setString(9, emptyToNull(l.getImageUrl()));

            ps.setString(10, emptyToNull(l.getTelephone()));
            ps.setString(11, emptyToNull(l.getSiteWeb()));
            ps.setString(12, emptyToNull(l.getInstagram()));
            ps.setString(13, emptyToNull(l.getDescription()));

            setNullableBigDecimal(ps, 14, l.getBudgetMin());
            setNullableBigDecimal(ps, 15, l.getBudgetMax());

            ps.setInt(16, l.getId());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.update: " + e.getMessage(), e);
        }
    }

    /* ===================== DELETE ===================== */

    public void delete(int id) {
        String sql = "DELETE FROM lieu WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.delete: " + e.getMessage(), e);
        }
    }

    /* ===================== MAPPING ===================== */

    private Lieu map(ResultSet rs) throws SQLException {
        Lieu l = new Lieu();
        l.setId(rs.getInt("id"));
        l.setIdOffre(rs.getInt("id_offre"));

        l.setNom(rs.getString("nom"));
        l.setVille(rs.getString("ville"));
        l.setAdresse(rs.getString("adresse"));
        l.setCategorie(rs.getString("categorie"));

        l.setLatitude(getNullableDouble(rs, "latitude"));
        l.setLongitude(getNullableDouble(rs, "longitude"));

        l.setType(rs.getString("type"));
        l.setImageUrl(rs.getString("image_url"));

        l.setTelephone(rs.getString("telephone"));
        l.setSiteWeb(rs.getString("site_web"));
        l.setInstagram(rs.getString("instagram"));

        l.setDescription(rs.getString("description"));
        l.setBudgetMin(getNullableDouble(rs, "budget_min"));
        l.setBudgetMax(getNullableDouble(rs, "budget_max"));

        return l;
    }

    /* ===================== HELPERS ===================== */

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

    private static Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        if (o instanceof Double d) return d;
        if (o instanceof Float f) return (double) f;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        if (o instanceof Number n) return n.doubleValue();
        return Double.valueOf(o.toString());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.DOUBLE);
        else ps.setDouble(idx, v);
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.DECIMAL);
        else ps.setBigDecimal(idx, BigDecimal.valueOf(v));
    }
}