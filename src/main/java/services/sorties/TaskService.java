package services.sorties;

import models.sorties.TaskSnapshot;
import utils.Mydb;

import java.sql.*;
import java.util.*;

/**
 * Service des tâches (to-do) liées à une annonce de sortie.
 *
 * Objectifs:
 * - Répartition (assignation) des responsabilités
 * - Board dynamique (TODO / DOING / DONE)
 * - Auto-répartition équilibrée
 */
public class TaskService {

    public static final String TODO  = "TODO";
    public static final String DOING = "DOING";
    public static final String DONE  = "DONE";

    private final Connection cnx = Mydb.getInstance().getConnection();

    // ──────────────────────────────────────────────────────────────────
    //  Schema
    // ──────────────────────────────────────────────────────────────────

    public void ensureSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS sortie_task (
                    id          INT          NOT NULL AUTO_INCREMENT,
                    annonce_id  INT          NOT NULL,
                    created_by  INT          NOT NULL,
                    title       VARCHAR(160) NOT NULL,
                    description TEXT         NULL,
                    status      VARCHAR(12)  NOT NULL DEFAULT 'TODO',
                    assigned_to INT          NULL,
                    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    done_at     TIMESTAMP    NULL,
                    PRIMARY KEY (id),
                    KEY idx_task_annonce (annonce_id, status, updated_at),
                    KEY idx_task_assignee (assigned_to, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;

        try (Statement st = cnx.createStatement()) {
            st.execute(ddl);
            ensureColumn("sortie_task", "done_at",
                    "ALTER TABLE sortie_task ADD COLUMN done_at TIMESTAMP NULL AFTER updated_at");
        } catch (SQLException e) {
            System.err.println("[TaskService] Schema init failed: " + e.getMessage());
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

    // ──────────────────────────────────────────────────────────────────
    //  CRUD
    // ──────────────────────────────────────────────────────────────────

    public int createTask(int annonceId, int createdBy, String title, String description, Integer assignedTo) {
        if (annonceId <= 0) throw new IllegalArgumentException("annonceId invalide");
        if (createdBy <= 0) throw new IllegalArgumentException("createdBy invalide");
        String t = safe(title).trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Titre vide");

        String sql = "INSERT INTO sortie_task (annonce_id, created_by, title, description, status, assigned_to) VALUES (?,?,?,?, 'TODO', ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, createdBy);
            ps.setString(3, t);
            ps.setString(4, emptyToNull(description));
            if (assignedTo == null || assignedTo <= 0) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, assignedTo);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("TaskService.createTask: " + e.getMessage(), e);
        }
        throw new RuntimeException("TaskService.createTask: no generated key");
    }

    public void updateTask(int taskId, String title, String description) {
        if (taskId <= 0) throw new IllegalArgumentException("taskId invalide");
        String t = safe(title).trim();
        if (t.isEmpty()) throw new IllegalArgumentException("Titre vide");

        String sql = "UPDATE sortie_task SET title=?, description=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, t);
            ps.setString(2, emptyToNull(description));
            ps.setInt(3, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("TaskService.updateTask: " + e.getMessage(), e);
        }
    }

    public void assignTask(int taskId, Integer assignedTo) {
        throw new UnsupportedOperationException("Utiliser assignTask(taskId, assignedTo, actorUserId, canManage)");
    }

    public void assignTask(int taskId, Integer assignedTo, int actorUserId, boolean canManage) {
        if (taskId <= 0) throw new IllegalArgumentException("taskId invalide");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId invalide");

        Integer target = (assignedTo == null || assignedTo <= 0) ? null : assignedTo;
        TaskAuthRow row = getAuthRow(taskId);
        if (row == null) throw new RuntimeException("Tâche introuvable");

        Integer current = row.assignedTo;

        if (!canManage) {
            if (current == null) {
                // Tâche non assignée: on peut seulement la prendre pour soi (ou la laisser non assignée)
                if (target != null && target != actorUserId) {
                    throw new SecurityException("Tu peux seulement prendre une tâche non assignée pour toi.");
                }
            } else {
                // Tâche déjà assignée: seul l'assigné peut modifier, et il ne peut pas la transférer
                if (current != actorUserId) {
                    throw new SecurityException("Seul l'utilisateur assigné peut modifier cette tâche.");
                }
                if (target != null && target != actorUserId) {
                    throw new SecurityException("Tu ne peux pas transférer la tâche à quelqu'un d'autre.");
                }
            }
        }

        String sql = "UPDATE sortie_task SET assigned_to=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            if (target == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, target);
            }
            ps.setInt(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("TaskService.assignTask: " + e.getMessage(), e);
        }
    }

    public void setStatus(int taskId, String status, int actorUserId) {
        if (taskId <= 0) throw new IllegalArgumentException("taskId invalide");
        if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId invalide");
        String s = normalizeStatus(status);

        // Règle: si assigned_to est renseigné => seul l'assigné peut changer TODO/DOING/DONE.
        // Si non assignée => tout le monde peut, et on "prend" la tâche automatiquement.
        String sql;
        if (DONE.equals(s)) {
            sql = "UPDATE sortie_task SET status=?, done_at=CURRENT_TIMESTAMP, assigned_to=COALESCE(assigned_to, ?) " +
                    "WHERE id=? AND (assigned_to IS NULL OR assigned_to=?)";
        } else {
            sql = "UPDATE sortie_task SET status=?, done_at=NULL, assigned_to=COALESCE(assigned_to, ?) " +
                    "WHERE id=? AND (assigned_to IS NULL OR assigned_to=?)";
        }

        int n;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, s);
            ps.setInt(2, actorUserId);
            ps.setInt(3, taskId);
            ps.setInt(4, actorUserId);
            n = ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("TaskService.setStatus: " + e.getMessage(), e);
        }

        if (n <= 0) {
            TaskAuthRow row = getAuthRow(taskId);
            if (row == null) throw new RuntimeException("Tâche introuvable");
            if (row.assignedTo != null && row.assignedTo != actorUserId) {
                throw new SecurityException("Seul l'utilisateur assigné peut changer le statut de cette tâche.");
            }
            throw new RuntimeException("Changement de statut impossible");
        }
    }

    private static final class TaskAuthRow {
        final Integer assignedTo;

        TaskAuthRow(Integer assignedTo) {
            this.assignedTo = assignedTo;
        }
    }

    private TaskAuthRow getAuthRow(int taskId) {
        if (taskId <= 0) return null;
        String sql = "SELECT id, assigned_to FROM sortie_task WHERE id=? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int at = rs.getInt(2);
                Integer assignedTo = rs.wasNull() ? null : at;
                return new TaskAuthRow(assignedTo);
            }
        } catch (SQLException ignored) {
            return null;
        }
    }

    public void deleteTask(int taskId) {
        if (taskId <= 0) throw new IllegalArgumentException("taskId invalide");
        String sql = "DELETE FROM sortie_task WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("TaskService.deleteTask: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Read
    // ──────────────────────────────────────────────────────────────────

    public List<TaskSnapshot> listTasks(int annonceId) {
        if (annonceId <= 0) return List.of();

        String sql = """
                SELECT t.id, t.annonce_id, t.created_by, t.title, t.description, t.status, t.assigned_to,
                       t.created_at, t.updated_at, t.done_at,
                       CONCAT(COALESCE(cu.prenom,''),' ',COALESCE(cu.nom,'')) AS created_by_name,
                       CONCAT(COALESCE(au.prenom,''),' ',COALESCE(au.nom,'')) AS assigned_to_name
                FROM sortie_task t
                LEFT JOIN user cu ON cu.id = t.created_by
                LEFT JOIN user au ON au.id = t.assigned_to
                WHERE t.annonce_id=?
                ORDER BY
                    CASE UPPER(t.status)
                        WHEN 'TODO' THEN 1
                        WHEN 'DOING' THEN 2
                        WHEN 'DONE' THEN 3
                        ELSE 9
                    END,
                    t.updated_at DESC
                """;

        List<TaskSnapshot> out = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TaskSnapshot t = new TaskSnapshot();
                    t.setId(rs.getInt("id"));
                    t.setAnnonceId(rs.getInt("annonce_id"));
                    t.setCreatedBy(rs.getInt("created_by"));
                    t.setTitle(rs.getString("title"));
                    t.setDescription(rs.getString("description"));
                    t.setStatus(rs.getString("status"));

                    int at = rs.getInt("assigned_to");
                    t.setAssignedTo(rs.wasNull() ? null : at);

                    t.setCreatedByName(normName(rs.getString("created_by_name"), t.getCreatedBy()));
                    t.setAssignedToName(normName(rs.getString("assigned_to_name"), t.getAssignedTo()));

                    Timestamp c = rs.getTimestamp("created_at");
                    Timestamp u = rs.getTimestamp("updated_at");
                    Timestamp d = rs.getTimestamp("done_at");
                    if (c != null) t.setCreatedAt(c.toLocalDateTime());
                    if (u != null) t.setUpdatedAt(u.toLocalDateTime());
                    if (d != null) t.setDoneAt(d.toLocalDateTime());

                    out.add(t);
                }
            }
        } catch (SQLException e) {
            System.err.println("[TaskService] listTasks: " + e.getMessage());
        }

        return out;
    }

    public TaskStats getStats(int annonceId) {
        if (annonceId <= 0) return new TaskStats(0,0,0);
        String sql = """
                SELECT
                    COUNT(*) AS total,
                    SUM(CASE WHEN UPPER(status)='DONE' THEN 1 ELSE 0 END) AS done,
                    SUM(CASE WHEN UPPER(status)<>'DONE' THEN 1 ELSE 0 END) AS open
                FROM sortie_task
                WHERE annonce_id=?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TaskStats(rs.getInt("total"), rs.getInt("done"), rs.getInt("open"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[TaskService] getStats: " + e.getMessage());
        }
        return new TaskStats(0,0,0);
    }

    public long countMyOpenTasks(int annonceId, int userId) {
        if (annonceId <= 0 || userId <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM sortie_task WHERE annonce_id=? AND assigned_to=? AND UPPER(status) <> 'DONE'";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Assignees (membres acceptés + organisateur)
    // ──────────────────────────────────────────────────────────────────

    public List<Assignee> listEligibleAssignees(int annonceId) {
        if (annonceId <= 0) return List.of();
        LinkedHashMap<Integer, String> map = new LinkedHashMap<>();

        // Owner
        String sqlOwner = """
                SELECT a.user_id, CONCAT(COALESCE(u.prenom,''),' ',COALESCE(u.nom,'')) AS name
                FROM annonce_sortie a
                LEFT JOIN user u ON u.id = a.user_id
                WHERE a.id=?
                LIMIT 1
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sqlOwner)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int uid = rs.getInt(1);
                    if (!rs.wasNull() && uid > 0) {
                        map.put(uid, normName(rs.getString(2), uid));
                    }
                }
            }
        } catch (SQLException ignored) {}

        // Accepted members
        String sqlMembers = """
                SELECT p.user_id, CONCAT(COALESCE(u.prenom,''),' ',COALESCE(u.nom,'')) AS name
                FROM participation_annonce p
                LEFT JOIN user u ON u.id = p.user_id
                WHERE p.annonce_id=?
                  AND (
                           UPPER(p.statut) LIKE 'CONFIRM%'
                        OR UPPER(p.statut) LIKE 'ACCEP%'
                  )
                ORDER BY name ASC
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sqlMembers)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt(1);
                    if (uid <= 0) continue;
                    map.putIfAbsent(uid, normName(rs.getString(2), uid));
                }
            }
        } catch (SQLException ignored) {}

        List<Assignee> out = new ArrayList<>();
        for (var e : map.entrySet()) {
            out.add(new Assignee(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Auto assign (équilibré)
    // ──────────────────────────────────────────────────────────────────

    public int autoAssignBalanced(int annonceId) {
        if (annonceId <= 0) return 0;

        List<Assignee> members = listEligibleAssignees(annonceId);
        if (members.isEmpty()) return 0;

        // Load current load per user (open tasks)
        Map<Integer, Integer> load = new HashMap<>();
        for (Assignee a : members) {
            load.put(a.userId, countOpenAssignedTo(annonceId, a.userId));
        }

        // Get unassigned tasks (not DONE)
        String sqlTasks = "SELECT id FROM sortie_task WHERE annonce_id=? AND assigned_to IS NULL AND UPPER(status) <> 'DONE' ORDER BY created_at ASC";
        List<Integer> tasks = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sqlTasks)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tasks.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("[TaskService] autoAssignBalanced tasks: " + e.getMessage());
            return 0;
        }
        if (tasks.isEmpty()) return 0;

        String sqlAssign = "UPDATE sortie_task SET assigned_to=? WHERE id=?";
        int changed = 0;
        try (PreparedStatement ps = cnx.prepareStatement(sqlAssign)) {
            for (int taskId : tasks) {
                int chosen = pickLeastLoaded(load, members);
                ps.setInt(1, chosen);
                ps.setInt(2, taskId);
                changed += ps.executeUpdate();
                load.put(chosen, load.getOrDefault(chosen, 0) + 1);
            }
        } catch (SQLException e) {
            System.err.println("[TaskService] autoAssignBalanced assign: " + e.getMessage());
        }

        return changed;
    }

    private int pickLeastLoaded(Map<Integer, Integer> load, List<Assignee> members) {
        int bestId = members.get(0).userId;
        int best = Integer.MAX_VALUE;
        for (Assignee a : members) {
            int v = load.getOrDefault(a.userId, 0);
            if (v < best) {
                best = v;
                bestId = a.userId;
            }
        }
        return bestId;
    }

    private int countOpenAssignedTo(int annonceId, int userId) {
        String sql = "SELECT COUNT(*) FROM sortie_task WHERE annonce_id=? AND assigned_to=? AND UPPER(status) <> 'DONE'";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Types
    // ──────────────────────────────────────────────────────────────────

    public static class Assignee {
        public final int userId;
        public final String name;

        public Assignee(int userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class TaskStats {
        public final int total;
        public final int done;
        public final int open;

        public TaskStats(int total, int done, int open) {
            this.total = total;
            this.done = done;
            this.open = open;
        }

        public double ratioDone() {
            if (total <= 0) return 0.0;
            return Math.max(0, Math.min(1, (double) done / (double) total));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    private static String normalizeStatus(String status) {
        String s = safe(status).trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case DOING -> DOING;
            case DONE -> DONE;
            default -> TODO;
        };
    }

    private static String emptyToNull(String s) {
        String t = safe(s).trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normName(String name, Integer userId) {
        String n = safe(name).trim();
        if (!n.isBlank()) return n;
        if (userId != null && userId > 0) return "Utilisateur #" + userId;
        return "";
    }
}
