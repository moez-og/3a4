package services.sorties;

import models.sorties.ChatMessage;
import models.notifications.Notification;
import models.notifications.NotificationType;
import services.notifications.NotificationService;
import utils.Mydb;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Service de chat de groupe pour une annonce de sortie.
 *
 * Table auto-créée au premier appel :
 *   chat_message (id, annonce_id, sender_id, content, sent_at)
 *
 * Accès autorisé : créateur de l'annonce + participants CONFIRMEE/ACCEPTEE.
 */
public class ChatService {

    private final Connection cnx = Mydb.getInstance().getConnection();
    private final NotificationService notificationService = new NotificationService();

    // ──────────────────────────────────────────────────────────────────
    //  Init table
    // ──────────────────────────────────────────────────────────────────

    public void ensureSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS chat_message (
                    id           INT          NOT NULL AUTO_INCREMENT,
                    annonce_id   INT          NOT NULL,
                    sender_id    INT          NOT NULL,
                    content      TEXT         NOT NULL,
                    message_type VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
                    poll_id      INT          NULL,
                    meta_json    TEXT         NULL,
                    sent_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_chat_annonce (annonce_id, sent_at),
                    KEY idx_chat_poll (poll_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Statement st = cnx.createStatement()) {
            st.execute(ddl);

            // Upgrade léger (si la table existait déjà avant l'ajout des colonnes)
            ensureColumn("chat_message", "message_type",
                    "ALTER TABLE chat_message ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' AFTER content");
            ensureColumn("chat_message", "poll_id",
                    "ALTER TABLE chat_message ADD COLUMN poll_id INT NULL AFTER message_type");
            ensureColumn("chat_message", "meta_json",
                    "ALTER TABLE chat_message ADD COLUMN meta_json TEXT NULL AFTER poll_id");
            ensureIndex("chat_message", "idx_chat_poll",
                    "ALTER TABLE chat_message ADD KEY idx_chat_poll (poll_id)");
        } catch (SQLException e) {
            System.err.println("[ChatService] Schema init failed: " + e.getMessage());
        }
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            DatabaseMetaData meta = cnx.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) return; // déjà présent
            }
            try (Statement st = cnx.createStatement()) {
                st.execute(alterSql);
            }
        } catch (SQLException ignored) {
            // on reste tolérant : l'app doit continuer.
        }
    }

    private void ensureIndex(String table, String indexName, String alterSql) {
        try {
            DatabaseMetaData meta = cnx.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
                while (rs.next()) {
                    String idx = rs.getString("INDEX_NAME");
                    if (idx != null && idx.equalsIgnoreCase(indexName)) return;
                }
            }
            try (Statement st = cnx.createStatement()) {
                st.execute(alterSql);
            }
        } catch (SQLException ignored) {
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Vérifier si un utilisateur a le droit d'accéder au chat
    // ──────────────────────────────────────────────────────────────────

    /**
     * @return true si userId est le créateur OU un participant accepté de l'annonce
     */
    public boolean canAccess(int annonceId, int userId) {
        // Créateur ?
        String sqlOwner = "SELECT COUNT(*) FROM annonce_sortie WHERE id=? AND user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sqlOwner)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return true;
        } catch (SQLException e) {
            System.err.println("[ChatService] canAccess owner check: " + e.getMessage());
        }

        // Participant accepté ?
        String sqlMember = """
                SELECT COUNT(*) FROM participation_annonce
                WHERE annonce_id=? AND user_id=?
                                    AND (
                                                UPPER(statut) LIKE 'CONFIRM%'
                                         OR UPPER(statut) LIKE 'ACCEP%'
                                    )
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sqlMember)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return true;
        } catch (SQLException e) {
            System.err.println("[ChatService] canAccess member check: " + e.getMessage());
        }

        return false;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Envoyer un message
    // ──────────────────────────────────────────────────────────────────

    public ChatMessage send(int annonceId, int senderId, String content) {
        if (content == null || content.trim().isEmpty())
            throw new IllegalArgumentException("Message vide");

        String sql = "INSERT INTO chat_message (annonce_id, sender_id, content, message_type) VALUES (?,?,?, 'TEXT')";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, senderId);
            ps.setString(3, content.trim());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int messageId = keys.getInt(1);
                ChatMessage msg = getById(messageId);
                if (msg != null) {
                    createChatMessageNotifications(annonceId, senderId, messageId, msg.getSenderName(), msg.getContent(), false);
                }
                return msg;
            }
        } catch (SQLException e) {
            throw new RuntimeException("ChatService.send: " + e.getMessage(), e);
        }
        throw new RuntimeException("ChatService.send: no generated key");
    }

    /**
     * Insère un message de type POLL dans le chat (référence vers un sondage).
     */
    public ChatMessage sendPoll(int annonceId, int senderId, int pollId, String question) {
        if (pollId <= 0) throw new IllegalArgumentException("pollId invalide");
        String q = (question == null) ? "" : question.trim();
        if (q.isEmpty()) q = "Sondage";

        String sql = "INSERT INTO chat_message (annonce_id, sender_id, content, message_type, poll_id) VALUES (?,?,?, 'POLL', ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, senderId);
            ps.setString(3, q);
            ps.setInt(4, pollId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int messageId = keys.getInt(1);
                    ChatMessage msg = getById(messageId);
                    if (msg != null) {
                        createChatMessageNotifications(annonceId, senderId, messageId, msg.getSenderName(), msg.getContent(), true);
                    }
                    return msg;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ChatService.sendPoll: " + e.getMessage(), e);
        }
        throw new RuntimeException("ChatService.sendPoll: no generated key");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Notifications
    // ──────────────────────────────────────────────────────────────────

    private void createChatMessageNotifications(int annonceId, int senderId, int messageId, String senderName, String content, boolean isPoll) {
        try {
            Set<Integer> receivers = listChatReceivers(annonceId);
            receivers.remove(senderId);
            if (receivers.isEmpty()) return;

            String name = safe(senderName).isBlank() ? resolveUserDisplayName(senderId) : safe(senderName).trim();
            if (name.isBlank()) name = "Utilisateur #" + senderId;

            String title = "Nouveau message";
            String body;
            if (isPoll) {
                body = name + " a partagé un sondage: " + excerpt(safe(content), 80);
            } else {
                body = name + " : " + excerpt(safe(content), 90);
            }

            String meta = "{\"sortieId\":" + annonceId + ",\"chatMessageId\":" + messageId + "}";

            for (int rid : receivers) {
                Notification n = new Notification();
                n.setReceiverId(rid);
                n.setSenderId(senderId);
                n.setType(NotificationType.CHAT_MESSAGE);
                n.setTitle(title);
                n.setBody(body);
                n.setEntityType("chat");
                n.setEntityId(annonceId);
                n.setMetadataJson(meta);

                notificationService.createOrRefreshNotification(n);
            }
        } catch (Exception ignored) {
            // Tolérant: une erreur de notif ne doit pas bloquer l'envoi du message.
        }
    }

    private Set<Integer> listChatReceivers(int annonceId) {
        Set<Integer> out = new HashSet<>();

        String sqlOwner = "SELECT user_id FROM annonce_sortie WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sqlOwner)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int oid = rs.getInt(1);
                    if (!rs.wasNull() && oid > 0) out.add(oid);
                }
            }
        } catch (SQLException ignored) {
        }

        String sqlMembers = """
                SELECT user_id FROM participation_annonce
                WHERE annonce_id=?
                  AND (
                           UPPER(statut) LIKE 'CONFIRM%'
                        OR UPPER(statut) LIKE 'ACCEP%'
                  )
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sqlMembers)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    if (!rs.wasNull() && uid > 0) out.add(uid);
                }
            }
        } catch (SQLException ignored) {
        }

        return out;
    }

    private String resolveUserDisplayName(int userId) {
        if (userId <= 0) return "";
        String sql = "SELECT prenom, nom FROM user WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String prenom = safe(rs.getString(1)).trim();
                    String nom = safe(rs.getString(2)).trim();
                    String full = (prenom + " " + nom).trim();
                    return full;
                }
            }
        } catch (SQLException ignored) {
        }
        return "";
    }

    private static String excerpt(String s, int max) {
        String t = safe(s).replaceAll("\\s+", " ").trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ──────────────────────────────────────────────────────────────────
    //  Charger les messages d'une annonce (avec nom expéditeur)
    // ──────────────────────────────────────────────────────────────────

    public List<ChatMessage> getMessages(int annonceId) {
        String sql = """
                SELECT cm.id, cm.annonce_id, cm.sender_id, cm.content, cm.message_type, cm.poll_id, cm.meta_json, cm.sent_at,
                       CONCAT(COALESCE(u.prenom,''), ' ', COALESCE(u.nom,'')) AS sender_name
                FROM chat_message cm
                LEFT JOIN user u ON u.id = cm.sender_id
                WHERE cm.annonce_id = ?
                ORDER BY cm.sent_at ASC
                """;
        List<ChatMessage> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ChatMessage m = new ChatMessage();
                m.setId(rs.getInt("id"));
                m.setAnnonceId(rs.getInt("annonce_id"));
                m.setSenderId(rs.getInt("sender_id"));
                m.setContent(rs.getString("content"));
                m.setMessageType(rs.getString("message_type"));
                int pid = rs.getInt("poll_id");
                m.setPollId(rs.wasNull() ? null : pid);
                m.setMetaJson(rs.getString("meta_json"));
                m.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                String name = rs.getString("sender_name");
                m.setSenderName(name == null || name.isBlank() ? "Utilisateur #" + m.getSenderId() : name.trim());
                list.add(m);
            }
        } catch (SQLException e) {
            throw new RuntimeException("ChatService.getMessages: " + e.getMessage(), e);
        }
        return list;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Charger les nouveaux messages après un certain id
    // ──────────────────────────────────────────────────────────────────

    public List<ChatMessage> getMessagesAfter(int annonceId, int lastId) {
        String sql = """
                SELECT cm.id, cm.annonce_id, cm.sender_id, cm.content, cm.message_type, cm.poll_id, cm.meta_json, cm.sent_at,
                       CONCAT(COALESCE(u.prenom,''), ' ', COALESCE(u.nom,'')) AS sender_name
                FROM chat_message cm
                LEFT JOIN user u ON u.id = cm.sender_id
                WHERE cm.annonce_id = ? AND cm.id > ?
                ORDER BY cm.sent_at ASC
                """;
        List<ChatMessage> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, lastId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ChatMessage m = new ChatMessage();
                m.setId(rs.getInt("id"));
                m.setAnnonceId(rs.getInt("annonce_id"));
                m.setSenderId(rs.getInt("sender_id"));
                m.setContent(rs.getString("content"));
                m.setMessageType(rs.getString("message_type"));
                int pid = rs.getInt("poll_id");
                m.setPollId(rs.wasNull() ? null : pid);
                m.setMetaJson(rs.getString("meta_json"));
                m.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                String name = rs.getString("sender_name");
                m.setSenderName(name == null || name.isBlank() ? "Utilisateur #" + m.getSenderId() : name.trim());
                list.add(m);
            }
        } catch (SQLException e) {
            System.err.println("[ChatService] getMessagesAfter: " + e.getMessage());
        }
        return list;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    private ChatMessage getById(int id) {
        String sql = """
                SELECT cm.id, cm.annonce_id, cm.sender_id, cm.content, cm.message_type, cm.poll_id, cm.meta_json, cm.sent_at,
                       CONCAT(COALESCE(u.prenom,''), ' ', COALESCE(u.nom,'')) AS sender_name
                FROM chat_message cm
                LEFT JOIN user u ON u.id = cm.sender_id
                WHERE cm.id = ?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ChatMessage m = new ChatMessage();
                m.setId(rs.getInt("id"));
                m.setAnnonceId(rs.getInt("annonce_id"));
                m.setSenderId(rs.getInt("sender_id"));
                m.setContent(rs.getString("content"));
                m.setMessageType(rs.getString("message_type"));
                int pid = rs.getInt("poll_id");
                m.setPollId(rs.wasNull() ? null : pid);
                m.setMetaJson(rs.getString("meta_json"));
                m.setSentAt(rs.getTimestamp("sent_at").toLocalDateTime());
                String name = rs.getString("sender_name");
                m.setSenderName(name == null || name.isBlank() ? "Utilisateur #" + m.getSenderId() : name.trim());
                return m;
            }
        } catch (SQLException e) {
            System.err.println("[ChatService] getById: " + e.getMessage());
        }
        return null;
    }
}