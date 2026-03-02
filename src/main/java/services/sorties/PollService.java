package services.sorties;

import models.sorties.PollOptionSnapshot;
import models.sorties.PollSnapshot;
import utils.Mydb;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service persistant des sondages liés au chat d'une annonce.
 *
 * Tables créées automatiquement :
 * - poll
 * - poll_option
 * - poll_vote
 */
public class PollService {

    private final Connection cnx = Mydb.getInstance().getConnection();

    // ──────────────────────────────────────────────────────────────────
    //  Schema
    // ──────────────────────────────────────────────────────────────────

    public void ensureSchema() {
        // IMPORTANT: exécuter chaque DDL séparément.
        // Beaucoup de configs MySQL/JDBC refusent les "multi-queries" dans un seul execute().
        String ddlPoll = """
                CREATE TABLE IF NOT EXISTS poll (
                    id                INT         NOT NULL AUTO_INCREMENT,
                    annonce_id        INT         NOT NULL,
                    question          TEXT        NOT NULL,
                    created_by        INT         NOT NULL,
                    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    is_open           TINYINT(1)  NOT NULL DEFAULT 1,
                    allow_multi       TINYINT(1)  NOT NULL DEFAULT 0,
                    allow_add_options TINYINT(1)  NOT NULL DEFAULT 1,
                    is_pinned         TINYINT(1)  NOT NULL DEFAULT 0,
                    closed_at         TIMESTAMP   NULL,
                    PRIMARY KEY (id),
                    KEY idx_poll_annonce (annonce_id, created_at),
                    KEY idx_poll_pinned (annonce_id, is_pinned, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        String ddlOption = """
                CREATE TABLE IF NOT EXISTS poll_option (
                    id         INT          NOT NULL AUTO_INCREMENT,
                    poll_id    INT          NOT NULL,
                    text       VARCHAR(255) NOT NULL,
                    created_by INT          NOT NULL,
                    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_pollopt_poll (poll_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        String ddlVote = """
                CREATE TABLE IF NOT EXISTS poll_vote (
                    poll_id   INT        NOT NULL,
                    option_id INT        NOT NULL,
                    user_id   INT        NOT NULL,
                    voted_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (poll_id, option_id, user_id),
                    KEY idx_pollvote_poll (poll_id),
                    KEY idx_pollvote_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        try (Statement st = cnx.createStatement()) {
            st.execute(ddlPoll);
            st.execute(ddlOption);
            st.execute(ddlVote);

            // Upgrade léger si la table existait avant l'ajout de colonnes
            ensureColumn("poll", "allow_add_options",
                    "ALTER TABLE poll ADD COLUMN allow_add_options TINYINT(1) NOT NULL DEFAULT 1 AFTER allow_multi");
            ensureColumn("poll", "is_pinned",
                    "ALTER TABLE poll ADD COLUMN is_pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER allow_add_options");
            ensureColumn("poll", "closed_at",
                    "ALTER TABLE poll ADD COLUMN closed_at TIMESTAMP NULL AFTER is_pinned");
            ensureIndex("poll", "idx_poll_pinned",
                    "ALTER TABLE poll ADD KEY idx_poll_pinned (annonce_id, is_pinned, created_at)");
        } catch (SQLException e) {
            System.err.println("[PollService] Schema init failed: " + e.getMessage());
        }
    }

    private void ensureColumn(String table, String column, String alterSql) {
        try {
            DatabaseMetaData meta = cnx.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) return;
            }
            try (Statement st = cnx.createStatement()) {
                st.execute(alterSql);
            }
        } catch (SQLException ignored) {
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
    //  Create
    // ──────────────────────────────────────────────────────────────────

    public int createPoll(int annonceId,
                          int createdBy,
                          String question,
                          boolean allowMulti,
                          boolean allowAddOptions,
                          boolean pinned,
                          List<String> options) {
        if (annonceId <= 0) throw new IllegalArgumentException("annonceId invalide");
        if (createdBy <= 0) throw new IllegalArgumentException("createdBy invalide");
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) throw new IllegalArgumentException("Question vide");

        List<String> cleanOptions = new ArrayList<>();
        if (options != null) {
            for (String o : options) {
                if (o == null) continue;
                String v = o.trim();
                if (!v.isEmpty()) cleanOptions.add(v);
            }
        }
        if (cleanOptions.size() < 2) throw new IllegalArgumentException("Ajoutez au moins 2 choix");

        String sqlPoll = "INSERT INTO poll (annonce_id, question, created_by, is_open, allow_multi, allow_add_options, is_pinned) VALUES (?,?,?, 1, ?, ?, ?)";
        String sqlOpt = "INSERT INTO poll_option (poll_id, text, created_by) VALUES (?,?,?)";

        try {
            boolean prevAuto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                int pollId;
                try (PreparedStatement ps = cnx.prepareStatement(sqlPoll, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, annonceId);
                    ps.setString(2, q);
                    ps.setInt(3, createdBy);
                    ps.setInt(4, allowMulti ? 1 : 0);
                    ps.setInt(5, allowAddOptions ? 1 : 0);
                    ps.setInt(6, pinned ? 1 : 0);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new RuntimeException("PollService.createPoll: no generated key");
                        pollId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = cnx.prepareStatement(sqlOpt)) {
                    for (String opt : cleanOptions) {
                        ps.setInt(1, pollId);
                        ps.setString(2, opt);
                        ps.setInt(3, createdBy);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                cnx.commit();
                return pollId;
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(prevAuto);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("PollService.createPoll: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Read
    // ──────────────────────────────────────────────────────────────────

    public List<Integer> listPinnedPollIds(int annonceId) {
        String sql = "SELECT id FROM poll WHERE annonce_id=? AND is_pinned=1 ORDER BY created_at DESC";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("[PollService] listPinnedPollIds: " + e.getMessage());
        }
        return ids;
    }

    public PollSnapshot getSnapshot(int pollId, int currentUserId) {
        if (pollId <= 0) return null;

        String sqlPoll = """
                SELECT p.id, p.annonce_id, p.question, p.created_by,
                       p.is_open, p.allow_multi, p.allow_add_options, p.is_pinned,
                       CONCAT(COALESCE(u.prenom,''), ' ', COALESCE(u.nom,'')) AS created_by_name
                FROM poll p
                LEFT JOIN user u ON u.id = p.created_by
                WHERE p.id = ?
                """;

        PollSnapshot snap = new PollSnapshot();
        try (PreparedStatement ps = cnx.prepareStatement(sqlPoll)) {
            ps.setInt(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                snap.setId(rs.getInt("id"));
                snap.setAnnonceId(rs.getInt("annonce_id"));
                snap.setQuestion(rs.getString("question"));
                snap.setCreatedBy(rs.getInt("created_by"));
                String name = rs.getString("created_by_name");
                snap.setCreatedByName(name == null || name.isBlank() ? "Utilisateur #" + snap.getCreatedBy() : name.trim());
                snap.setOpen(rs.getInt("is_open") == 1);
                snap.setAllowMulti(rs.getInt("allow_multi") == 1);
                snap.setAllowAddOptions(rs.getInt("allow_add_options") == 1);
                snap.setPinned(rs.getInt("is_pinned") == 1);
            }
        } catch (SQLException e) {
            System.err.println("[PollService] getSnapshot poll: " + e.getMessage());
            return null;
        }

        // My votes
        String sqlMine = "SELECT option_id FROM poll_vote WHERE poll_id=? AND user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sqlMine)) {
            ps.setInt(1, pollId);
            ps.setInt(2, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) snap.getMyOptionIds().add(rs.getInt(1));
            }
        } catch (SQLException ignored) {
        }

        // Total voters
        String sqlVoters = "SELECT COUNT(DISTINCT user_id) FROM poll_vote WHERE poll_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sqlVoters)) {
            ps.setInt(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) snap.setTotalVoters(rs.getInt(1));
            }
        } catch (SQLException ignored) {
        }

        // Options with vote counts
        String sqlOptions = """
                SELECT o.id, o.text, COUNT(v.user_id) AS votes
                FROM poll_option o
                LEFT JOIN poll_vote v
                  ON v.poll_id = o.poll_id AND v.option_id = o.id
                WHERE o.poll_id = ?
                GROUP BY o.id, o.text
                ORDER BY o.id ASC
                """;

        List<PollOptionSnapshot> opts = new ArrayList<>();
        int totalVotes = 0;
        try (PreparedStatement ps = cnx.prepareStatement(sqlOptions)) {
            ps.setInt(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String text = rs.getString("text");
                    int votes = rs.getInt("votes");
                    totalVotes += votes;
                    opts.add(new PollOptionSnapshot(id, text, votes, 0d));
                }
            }
        } catch (SQLException e) {
            System.err.println("[PollService] getSnapshot options: " + e.getMessage());
        }

        for (PollOptionSnapshot o : opts) {
            double pct = (totalVotes <= 0) ? 0d : ((double) o.getVotes() / (double) totalVotes);
            o.setPercent(clamp01(pct));
            snap.getOptions().add(o);
        }

        return snap;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Update
    // ──────────────────────────────────────────────────────────────────

    public void vote(int pollId, int userId, boolean allowMulti, Set<Integer> optionIds) {
        if (pollId <= 0) throw new IllegalArgumentException("pollId invalide");
        if (userId <= 0) throw new IllegalArgumentException("userId invalide");
        if (optionIds == null || optionIds.isEmpty()) throw new IllegalArgumentException("Sélection vide");

        PollMeta meta = getMeta(pollId);
        if (!meta.open) throw new IllegalStateException("Sondage clôturé");

        // S'aligne sur l'état DB (au cas où le client a un état obsolète)
        boolean effectiveMulti = meta.allowMulti;
        if (!effectiveMulti && optionIds.size() > 1) {
            throw new IllegalArgumentException("Un seul choix possible");
        }
        if (effectiveMulti != allowMulti) {
            // pas bloquant ; l'UI peut être obsolète. On applique la règle DB.
        }

        Set<Integer> allowed = loadOptionIdsForPoll(pollId);
        Set<Integer> selected = new HashSet<>();
        for (Integer id : optionIds) {
            if (id != null && allowed.contains(id)) selected.add(id);
        }
        if (selected.isEmpty()) throw new IllegalArgumentException("Choix invalide");

        String del = "DELETE FROM poll_vote WHERE poll_id=? AND user_id=?";
        String ins = "INSERT INTO poll_vote (poll_id, option_id, user_id) VALUES (?,?,?)";

        try {
            boolean prevAuto = cnx.getAutoCommit();
            cnx.setAutoCommit(false);
            try {
                try (PreparedStatement ps = cnx.prepareStatement(del)) {
                    ps.setInt(1, pollId);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = cnx.prepareStatement(ins)) {
                    for (Integer oid : selected) {
                        ps.setInt(1, pollId);
                        ps.setInt(2, oid);
                        ps.setInt(3, userId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                cnx.commit();
            } catch (Exception ex) {
                cnx.rollback();
                throw ex;
            } finally {
                cnx.setAutoCommit(prevAuto);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("PollService.vote: " + e.getMessage(), e);
        }
    }

    public void clearVote(int pollId, int userId) {
        String sql = "DELETE FROM poll_vote WHERE poll_id=? AND user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.clearVote: " + e.getMessage(), e);
        }
    }

    public void addOption(int pollId, int userId, String text) {
        PollMeta meta = getMeta(pollId);
        if (!meta.open) throw new IllegalStateException("Sondage clôturé");
        if (!meta.allowAddOptions) throw new IllegalStateException("Ajout d'options désactivé");
        addOptionInternal(pollId, userId, text);
    }

    public void addOptionPrivileged(int pollId, int userId, String text) {
        PollMeta meta = getMeta(pollId);
        if (!meta.open) throw new IllegalStateException("Sondage clôturé");
        addOptionInternal(pollId, userId, text);
    }

    private void addOptionInternal(int pollId, int userId, String text) {
        String v = text == null ? "" : text.trim();
        if (v.isEmpty()) throw new IllegalArgumentException("Option vide");
        if (v.length() > 255) v = v.substring(0, 255);

        String sql = "INSERT INTO poll_option (poll_id, text, created_by) VALUES (?,?,?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setString(2, v);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.addOption: " + e.getMessage(), e);
        }
    }

    public void setPinnedAsAdmin(int pollId, boolean pinned) {
        String sql = "UPDATE poll SET is_pinned=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pinned ? 1 : 0);
            ps.setInt(2, pollId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.setPinnedAsAdmin: " + e.getMessage(), e);
        }
    }

    public void setPinnedAsOwner(int pollId, int ownerId, boolean pinned) {
        String sql = "UPDATE poll SET is_pinned=? WHERE id=? AND created_by=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pinned ? 1 : 0);
            ps.setInt(2, pollId);
            ps.setInt(3, ownerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.setPinnedAsOwner: " + e.getMessage(), e);
        }
    }

    public void closePollAsAdmin(int pollId) {
        String sql = "UPDATE poll SET is_open=0, closed_at=NOW() WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.closePollAsAdmin: " + e.getMessage(), e);
        }
    }

    public void closePollAsOwner(int pollId, int ownerId) {
        String sql = "UPDATE poll SET is_open=0, closed_at=NOW() WHERE id=? AND created_by=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            ps.setInt(2, ownerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("PollService.closePollAsOwner: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────────────────────────

    private record PollMeta(boolean open, boolean allowMulti, boolean allowAddOptions) {}

    private PollMeta getMeta(int pollId) {
        String sql = "SELECT is_open, allow_multi, allow_add_options FROM poll WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Sondage introuvable");
                boolean open = rs.getInt(1) == 1;
                boolean multi = rs.getInt(2) == 1;
                boolean add = rs.getInt(3) == 1;
                return new PollMeta(open, multi, add);
            }
        } catch (SQLException e) {
            throw new RuntimeException("PollService.getMeta: " + e.getMessage(), e);
        }
    }

    private Set<Integer> loadOptionIdsForPoll(int pollId) {
        String sql = "SELECT id FROM poll_option WHERE poll_id=?";
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("PollService.loadOptionIdsForPoll: " + e.getMessage(), e);
        }
        return ids;
    }

    private static double clamp01(double v) {
        if (v < 0d) return 0d;
        if (v > 1d) return 1d;
        return v;
    }
}
