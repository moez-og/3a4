package services.notifications;

import models.notifications.Notification;
import models.notifications.NotificationType;
import utils.Mydb;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {

    private final Connection cnx;

    public NotificationService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /**
     * Creates a notification in DB.
     *
     * Anti-doublon is enforced by DB unique constraint; duplicate inserts are ignored.
     */
    public void createNotification(Notification n) {
        validate(n);

        String sql = """
                INSERT INTO notifications
                (receiver_id, sender_id, type, title, body, entity_type, entity_id, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, n.getReceiverId());
            if (n.getSenderId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, n.getSenderId());

            ps.setString(3, n.getType().name());
            ps.setString(4, n.getTitle());
            ps.setString(5, n.getBody());
            ps.setString(6, n.getEntityType());
            ps.setInt(7, n.getEntityId());
            ps.setString(8, emptyToNull(n.getMetadataJson()));

            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException dup) {
            // Duplicate (unique receiver/type/entity). Ignore.
        } catch (SQLException e) {
            // MySQL may throw a generic SQLException for duplicate key depending on driver.
            if (isDuplicateKey(e)) return;
            throw new RuntimeException("NotificationService.createNotification: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a notification, or refreshes the existing one (same receiver/type/entity)
     * by updating title/body/metadata and bumping created_at + resetting read_at.
     *
     * Useful for recurring events like "Sortie modifiée" where we don't want
     * to spam duplicates but still want the notification to resurface.
     */
    public void createOrRefreshNotification(Notification n) {
        validate(n);

        String sql = """
                INSERT INTO notifications
                (receiver_id, sender_id, type, title, body, entity_type, entity_id, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    sender_id=VALUES(sender_id),
                    title=VALUES(title),
                    body=VALUES(body),
                    metadata_json=VALUES(metadata_json),
                    created_at=NOW(),
                    read_at=NULL
                """;

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, n.getReceiverId());
            if (n.getSenderId() == null) ps.setNull(2, Types.INTEGER);
            else ps.setInt(2, n.getSenderId());

            ps.setString(3, n.getType().name());
            ps.setString(4, n.getTitle());
            ps.setString(5, n.getBody());
            ps.setString(6, n.getEntityType());
            ps.setInt(7, n.getEntityId());
            ps.setString(8, emptyToNull(n.getMetadataJson()));

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("NotificationService.createOrRefreshNotification: " + e.getMessage(), e);
        }
    }

    public List<Notification> listNotifications(int receiverId, Boolean unreadOnly, NotificationType type, int page, int pageSize) {
        if (receiverId <= 0) throw new IllegalArgumentException("receiverId invalide");
        int p = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        int offset = (p - 1) * size;

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM notifications WHERE receiver_id=?"
        );
        List<Object> params = new ArrayList<>();
        params.add(receiverId);

        if (unreadOnly != null && unreadOnly) {
            sql.append(" AND read_at IS NULL");
        }
        if (type != null) {
            sql.append(" AND type=?");
            params.add(type.name());
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        List<Notification> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("NotificationService.listNotifications: " + e.getMessage(), e);
        }
        return out;
    }

    public long countUnread(int receiverId) {
        if (receiverId <= 0) throw new IllegalArgumentException("receiverId invalide");
        String sql = "SELECT COUNT(*) AS c FROM notifications WHERE receiver_id=? AND read_at IS NULL";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("NotificationService.countUnread: " + e.getMessage(), e);
        }
        return 0;
    }

    public boolean markAsRead(long notificationId, int receiverId) {
        if (notificationId <= 0) throw new IllegalArgumentException("notificationId invalide");
        if (receiverId <= 0) throw new IllegalArgumentException("receiverId invalide");

        String sql = """
                UPDATE notifications
                SET read_at=NOW()
                WHERE id=? AND receiver_id=? AND read_at IS NULL
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setInt(2, receiverId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("NotificationService.markAsRead: " + e.getMessage(), e);
        }
    }

    public int markAllAsRead(int receiverId) {
        if (receiverId <= 0) throw new IllegalArgumentException("receiverId invalide");
        String sql = "UPDATE notifications SET read_at=NOW() WHERE receiver_id=? AND read_at IS NULL";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("NotificationService.markAllAsRead: " + e.getMessage(), e);
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setReceiverId(rs.getInt("receiver_id"));

        int sid = rs.getInt("sender_id");
        if (rs.wasNull()) n.setSenderId(null);
        else n.setSenderId(sid);

        String type = rs.getString("type");
        n.setType(type == null ? null : NotificationType.valueOf(type));

        n.setTitle(rs.getString("title"));
        n.setBody(rs.getString("body"));

        n.setEntityType(rs.getString("entity_type"));
        n.setEntityId(rs.getInt("entity_id"));

        Timestamp created = rs.getTimestamp("created_at");
        n.setCreatedAt(created == null ? null : created.toLocalDateTime());

        Timestamp read = rs.getTimestamp("read_at");
        n.setReadAt(read == null ? null : read.toLocalDateTime());

        n.setMetadataJson(rs.getString("metadata_json"));
        return n;
    }

    private static void validate(Notification n) {
        if (n == null) throw new IllegalArgumentException("notification null");
        if (n.getReceiverId() <= 0) throw new IllegalArgumentException("receiverId invalide");
        if (n.getType() == null) throw new IllegalArgumentException("type obligatoire");
        if (safe(n.getTitle()).isBlank()) throw new IllegalArgumentException("title obligatoire");
        if (safe(n.getBody()).isBlank()) throw new IllegalArgumentException("body obligatoire");
        if (safe(n.getEntityType()).isBlank()) throw new IllegalArgumentException("entityType obligatoire");
        if (n.getEntityId() <= 0) throw new IllegalArgumentException("entityId invalide");
    }

    private static boolean isDuplicateKey(SQLException e) {
        // MySQL duplicate key: SQLState 23000, vendor code 1062.
        if (e == null) return false;
        if ("23000".equals(e.getSQLState()) && e.getErrorCode() == 1062) return true;
        String msg = safe(e.getMessage()).toLowerCase();
        return msg.contains("duplicate") && msg.contains("uq_notifications_receiver_type_entity");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
