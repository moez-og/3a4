package services.sorties;

import models.sorties.SortieRecap;
import utils.Mydb;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SortieRecapService {

    private final Connection cnx;

    public SortieRecapService() {
        cnx = Mydb.getInstance().getConnection();
        SortieAlbumSchema.ensureSchema();
    }

    public List<SortieRecap> listBySortie(int sortieId) {
        if (sortieId <= 0) throw new IllegalArgumentException("sortieId invalide");

        // Keep labels consistent (latest is always "Version Finale").
        normalizeLabels(sortieId);

        String sql = """
            SELECT id, sortie_id, video_path, generated_at, version, version_label
                FROM sortie_recap
                WHERE sortie_id=?
                ORDER BY version DESC, generated_at DESC, id DESC
                """;

        List<SortieRecap> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.listBySortie: " + e.getMessage(), e);
        }
        return out;
    }

    public SortieRecap getLatest(int sortieId) {
        if (sortieId <= 0) return null;

        normalizeLabels(sortieId);
        String sql = """
            SELECT id, sortie_id, video_path, generated_at, version, version_label
                FROM sortie_recap
                WHERE sortie_id=?
                ORDER BY version DESC, generated_at DESC, id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.getLatest: " + e.getMessage(), e);
        }
        return null;
    }

    public int nextVersion(int sortieId) {
        if (sortieId <= 0) return 1;
        String sql = "SELECT COALESCE(MAX(version),0) + 1 AS v FROM sortie_recap WHERE sortie_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("v");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.nextVersion: " + e.getMessage(), e);
        }
        return 1;
    }

    public SortieRecap create(int sortieId, String videoPath) {
        if (sortieId <= 0) throw new IllegalArgumentException("sortieId invalide");
        if (safe(videoPath).isBlank()) throw new IllegalArgumentException("videoPath obligatoire");

        int version = nextVersion(sortieId);
        return create(sortieId, videoPath, version);
    }

    public SortieRecap create(int sortieId, String videoPath, int version) {
        if (sortieId <= 0) throw new IllegalArgumentException("sortieId invalide");
        if (safe(videoPath).isBlank()) throw new IllegalArgumentException("videoPath obligatoire");
        if (version <= 0) throw new IllegalArgumentException("version invalide");

        String sql = "INSERT INTO sortie_recap (sortie_id, video_path, version, version_label) VALUES (?,?,?,NULL)";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sortieId);
            ps.setString(2, videoPath);
            ps.setInt(3, version);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getInt(1);
            }

            SortieRecap r = new SortieRecap();
            r.setId(id);
            r.setSortieId(sortieId);
            r.setVideoPath(videoPath);
            r.setGeneratedAt(LocalDateTime.now());
            r.setVersion(version);

            // Ensure latest is marked as final.
            normalizeLabels(sortieId);
            r.setVersionLabel(getVersionLabel(id));
            return r;
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.create: " + e.getMessage(), e);
        }
    }

    public boolean delete(int recapId) {
        if (recapId <= 0) return false;

        Integer sortieId = null;
        try (PreparedStatement ps = cnx.prepareStatement("SELECT sortie_id FROM sortie_recap WHERE id=?")) {
            ps.setInt(1, recapId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) sortieId = rs.getInt("sortie_id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.delete: " + e.getMessage(), e);
        }

        try (PreparedStatement ps = cnx.prepareStatement("DELETE FROM sortie_recap WHERE id=?")) {
            ps.setInt(1, recapId);
            int n = ps.executeUpdate();
            if (sortieId != null && sortieId > 0) normalizeLabels(sortieId);
            return n > 0;
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.delete: " + e.getMessage(), e);
        }
    }

    public LocalDateTime latestGeneratedAt(int sortieId) {
        if (sortieId <= 0) return null;
        String sql = "SELECT MAX(generated_at) AS t FROM sortie_recap WHERE sortie_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, sortieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("t");
                    return ts == null ? null : ts.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SortieRecapService.latestGeneratedAt: " + e.getMessage(), e);
        }
        return null;
    }

    public Path resolvePath(String videoPath) {
        if (videoPath == null) return null;
        Path p = Paths.get(videoPath);
        if (p.isAbsolute()) return p;
        return Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
    }

    private static SortieRecap map(ResultSet rs) throws SQLException {
        SortieRecap r = new SortieRecap();
        r.setId(rs.getInt("id"));
        r.setSortieId(rs.getInt("sortie_id"));
        r.setVideoPath(rs.getString("video_path"));
        Timestamp ts = rs.getTimestamp("generated_at");
        r.setGeneratedAt(ts == null ? null : ts.toLocalDateTime());
        r.setVersion(rs.getInt("version"));
        try {
            r.setVersionLabel(rs.getString("version_label"));
        } catch (SQLException ignored) {
            r.setVersionLabel(null);
        }
        return r;
    }

    private void normalizeLabels(int sortieId) {
        if (sortieId <= 0) return;
        if (cnx == null) return;

        try (PreparedStatement ps = cnx.prepareStatement(
                "UPDATE sortie_recap " +
                        "SET version_label = CASE WHEN version=1 THEN 'Première version' ELSE CONCAT('Version ', version) END " +
                        "WHERE sortie_id=?")) {
            ps.setInt(1, sortieId);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }

        // Mark latest as final (MySQL supports ORDER BY/LIMIT in UPDATE).
        try (PreparedStatement ps = cnx.prepareStatement(
                "UPDATE sortie_recap SET version_label='Version Finale' WHERE sortie_id=? ORDER BY version DESC, generated_at DESC, id DESC LIMIT 1")) {
            ps.setInt(1, sortieId);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private String getVersionLabel(int recapId) {
        if (recapId <= 0) return "";
        try (PreparedStatement ps = cnx.prepareStatement("SELECT version_label FROM sortie_recap WHERE id=?")) {
            ps.setInt(1, recapId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safe(rs.getString("version_label"));
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
