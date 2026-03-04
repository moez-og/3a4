package services.sorties;

import utils.Mydb;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Schema helper for the Album & Recap feature.
 * Kept separate to stay consistent with other services that self-init their tables.
 */
public final class SortieAlbumSchema {

    private SortieAlbumSchema() {}

    public static void ensureSchema() {
        Connection cnx = Mydb.getInstance().getConnection();
        if (cnx == null) return;

        try (Statement st = cnx.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sortie_media (
                        id           INT AUTO_INCREMENT PRIMARY KEY,
                        sortie_id    INT NOT NULL,
                        user_id      INT NOT NULL,
                        file_path    VARCHAR(500) NOT NULL,
                        media_type   VARCHAR(10) NOT NULL,
                        uploaded_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        KEY idx_sortie_media_sortie (sortie_id, uploaded_at),
                        KEY idx_sortie_media_user (user_id),
                        CONSTRAINT chk_sortie_media_type CHECK (media_type IN ('IMAGE','VIDEO'))
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sortie_recap (
                        id           INT AUTO_INCREMENT PRIMARY KEY,
                        sortie_id    INT NOT NULL,
                        video_path   VARCHAR(500) NOT NULL,
                        generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        version      INT NOT NULL,
                        version_label VARCHAR(60) NULL,
                        KEY idx_sortie_recap_sortie (sortie_id, generated_at),
                        UNIQUE KEY uq_sortie_recap_sortie_version (sortie_id, version)
                    )
                    """);

            // Backward-compatible: add column if DB already exists without it.
            try {
                st.executeUpdate("ALTER TABLE sortie_recap ADD COLUMN version_label VARCHAR(60) NULL");
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            System.err.println("⚠ SortieAlbumSchema init failed: " + e.getMessage());
        }
    }
}
