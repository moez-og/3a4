package utils.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Minimal DB bootstrap for this JavaFX + JDBC app.
 *
 * The project does not use Flyway/Liquibase, so we create/upgrade small tables
 * opportunistically on startup.
 */
public final class DbSchema {

    private DbSchema() {}

    public static void ensureNotificationsSchema(Connection cnx) {
        if (cnx == null) return;

        // MySQL DDL (idempotent).
        // - Unique anti-doublon: (receiver_id, type, entity_type, entity_id)
        // - Indexes: receiver_id, read_at, created_at, type
        String ddl = """
                CREATE TABLE IF NOT EXISTS notifications (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    receiver_id INT NOT NULL,
                    sender_id INT NULL,
                    type VARCHAR(64) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    body TEXT NOT NULL,
                    entity_type VARCHAR(64) NOT NULL,
                    entity_id INT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    read_at TIMESTAMP NULL DEFAULT NULL,
                    metadata_json TEXT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uq_notifications_receiver_type_entity (receiver_id, type, entity_type, entity_id),
                    KEY idx_notifications_receiver_created (receiver_id, created_at),
                    KEY idx_notifications_receiver_read (receiver_id, read_at),
                    KEY idx_notifications_receiver_type (receiver_id, type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        try (Statement st = cnx.createStatement()) {
            st.execute(ddl);
        } catch (SQLException e) {
            // Don't crash the whole app for a non-critical table.
            System.err.println("⚠ Notifications schema init failed: " + e.getMessage());
        }
    }
}
