package services.sorties;

import models.sorties.SortieMedia;
import utils.Mydb;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SortieMediaService {

    private final Connection cnx;

    public SortieMediaService() {
        cnx = Mydb.getInstance().getConnection();
        SortieAlbumSchema.ensureSchema();
    }

    public List<SortieMedia> listBySortie(int sortieId) {
        if (sortieId <= 0) throw new IllegalArgumentException("sortieId invalide");

        String sql = """
                SELECT m.id, m.sortie_id, m.user_id,
                       m.file_path, m.media_type, m.uploaded_at,
                       CONCAT(COALESCE(u.prenom,''), ' ', COALESCE(u.nom,'')) AS author_name
                FROM sortie_media m
                LEFT JOIN user u ON u.id = m.user_id
                WHERE m.sortie_id=?
                ORDER BY m.uploaded_at DESC, m.id DESC
                """;

        List<SortieMedia> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieMediaService.listBySortie: " + e.getMessage(), e);
        }
        return out;
    }

    public SortieMedia add(int sortieId, int userId, String filePath, String mediaType) {
        if (sortieId <= 0) throw new IllegalArgumentException("sortieId invalide");
        if (userId <= 0) throw new IllegalArgumentException("userId invalide");
        if (safe(filePath).isBlank()) throw new IllegalArgumentException("filePath obligatoire");

        String mt = normalizeType(mediaType);

        String sql = "INSERT INTO sortie_media (sortie_id, user_id, file_path, media_type) VALUES (?,?,?,?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sortieId);
            ps.setInt(2, userId);
            ps.setString(3, filePath);
            ps.setString(4, mt);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getInt(1);
            }

            SortieMedia m = new SortieMedia();
            m.setId(id);
            m.setSortieId(sortieId);
            m.setUserId(userId);
            m.setFilePath(filePath);
            m.setMediaType(mt);
            m.setUploadedAt(LocalDateTime.now());
            return m;
        } catch (SQLException e) {
            throw new RuntimeException("SortieMediaService.add: " + e.getMessage(), e);
        }
    }

    public boolean delete(int mediaId, int actorUserId) {
        if (mediaId <= 0) throw new IllegalArgumentException("mediaId invalide");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId invalide");

        // Only owner can delete.
        String sql = "DELETE FROM sortie_media WHERE id=? AND user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, mediaId);
            ps.setInt(2, actorUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("SortieMediaService.delete: " + e.getMessage(), e);
        }
    }

    public long countBySortie(int sortieId) {
        if (sortieId <= 0) return 0;
        String sql = "SELECT COUNT(*) AS c FROM sortie_media WHERE sortie_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieMediaService.countBySortie: " + e.getMessage(), e);
        }
        return 0;
    }

    public LocalDateTime latestUploadAt(int sortieId) {
        if (sortieId <= 0) return null;
        String sql = "SELECT MAX(uploaded_at) AS t FROM sortie_media WHERE sortie_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("t");
                    return ts == null ? null : ts.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieMediaService.latestUploadAt: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Resolves DB stored paths (relative paths are resolved from process working directory).
     */
    public Path resolvePath(String filePath) {
        if (filePath == null) return null;
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
    }

    private static SortieMedia map(ResultSet rs) throws SQLException {
        SortieMedia m = new SortieMedia();
        m.setId(rs.getInt("id"));
        m.setSortieId(rs.getInt("sortie_id"));
        m.setUserId(rs.getInt("user_id"));
        m.setFilePath(rs.getString("file_path"));
        m.setMediaType(rs.getString("media_type"));

        Timestamp ts = rs.getTimestamp("uploaded_at");
        m.setUploadedAt(ts == null ? null : ts.toLocalDateTime());

        String author = safe(rs.getString("author_name")).trim();
        m.setAuthorName(author.isBlank() ? ("Utilisateur #" + m.getUserId()) : author);
        return m;
    }

    private static String normalizeType(String mediaType) {
        String t = safe(mediaType).trim().toUpperCase();
        if (t.equals("IMAGE") || t.equals("VIDEO")) return t;
        return "IMAGE";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
