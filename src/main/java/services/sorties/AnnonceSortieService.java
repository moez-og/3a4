package services.sorties;

import models.sorties.AnnonceSortie;
import models.notifications.Notification;
import models.notifications.NotificationType;
import services.notifications.NotificationService;
import utils.Mydb;
import utils.json.JsonStringArray;

import services.common.CrudService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AnnonceSortieService implements CrudService<AnnonceSortie, Integer> {

    private final Connection cnx;
    private final NotificationService notificationService = new NotificationService();

    public AnnonceSortieService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /* ===================== READ ===================== */

    public List<AnnonceSortie> getAll() {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url,
                       CASE
                           WHEN UPPER(statut) IN ('ANNULEE','ANNULÉE')
                           THEN 'ANNULEE'

                           WHEN date_sortie IS NOT NULL
                                AND date_sortie < CURRENT_TIMESTAMP
                           THEN 'TERMINEE'

                           WHEN UPPER(statut) = 'OUVERTE'
                                AND nb_places > 0
                                AND nb_places <= (
                                    SELECT COALESCE(SUM(p.nb_places),0)
                                    FROM participation_annonce p
                                    WHERE p.annonce_id = annonce_sortie.id
                                      AND UPPER(p.statut) IN ('CONFIRMEE','ACCEPTEE')
                                )
                           THEN 'CLOTUREE'
                           ELSE statut
                       END AS statut,
                       questions_json
                FROM annonce_sortie
                ORDER BY date_sortie DESC, id DESC
                """;

        List<AnnonceSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(map(rs));

        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getAll: " + e.getMessage(), e);
        }
        return list;
    }

    public List<AnnonceSortie> getByUserId(int userId) {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url,
                       CASE
                           WHEN UPPER(statut) IN ('ANNULEE','ANNULÉE')
                           THEN 'ANNULEE'

                           WHEN date_sortie IS NOT NULL
                                AND date_sortie < CURRENT_TIMESTAMP
                           THEN 'TERMINEE'

                           WHEN UPPER(statut) = 'OUVERTE'
                                AND nb_places > 0
                                AND nb_places <= (
                                    SELECT COALESCE(SUM(p.nb_places),0)
                                    FROM participation_annonce p
                                    WHERE p.annonce_id = annonce_sortie.id
                                      AND UPPER(p.statut) IN ('CONFIRMEE','ACCEPTEE')
                                )
                           THEN 'CLOTUREE'
                           ELSE statut
                       END AS statut,
                       questions_json
                FROM annonce_sortie
                WHERE user_id = ?
                ORDER BY date_sortie DESC, id DESC
                """;

        List<AnnonceSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getByUserId: " + e.getMessage(), e);
        }
        return list;
    }

    public AnnonceSortie getById(int id) {
        String sql = """
                SELECT id, user_id, titre, description, ville, lieu_texte, point_rencontre,
                       type_activite, date_sortie, budget_max, nb_places, image_url,
                       CASE
                           WHEN UPPER(statut) IN ('ANNULEE','ANNULÉE')
                           THEN 'ANNULEE'

                           WHEN date_sortie IS NOT NULL
                                AND date_sortie < CURRENT_TIMESTAMP
                           THEN 'TERMINEE'

                           WHEN UPPER(statut) = 'OUVERTE'
                                AND nb_places > 0
                                AND nb_places <= (
                                    SELECT COALESCE(SUM(p.nb_places),0)
                                    FROM participation_annonce p
                                    WHERE p.annonce_id = annonce_sortie.id
                                      AND UPPER(p.statut) IN ('CONFIRMEE','ACCEPTEE')
                                )
                           THEN 'CLOTUREE'
                           ELSE statut
                       END AS statut,
                       questions_json
                FROM annonce_sortie
                WHERE id = ?
                """;

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.getById: " + e.getMessage(), e);
        }
        return null;
    }

    /* ===================== CREATE ===================== */

    public void add(AnnonceSortie a) {
        String sql = """
                INSERT INTO annonce_sortie
                (user_id, titre, description, ville, lieu_texte, point_rencontre, type_activite,
                 date_sortie, budget_max, nb_places, image_url, statut, questions_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            bind(ps, a, false);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.add: " + e.getMessage(), e);
        }
    }

    /* ===================== UPDATE ===================== */

    public void update(AnnonceSortie a) {
        if (a == null) throw new IllegalArgumentException("AnnonceSortie null");
        AnnonceSortie before = null;
        if (a.getId() > 0) {
            try {
                before = getById(a.getId());
            } catch (Exception ignored) {
            }
        }

        String sql = """
                UPDATE annonce_sortie
                SET user_id=?, titre=?, description=?, ville=?, lieu_texte=?, point_rencontre=?, type_activite=?,
                    date_sortie=?, budget_max=?, nb_places=?, image_url=?, statut=?, questions_json=?
                WHERE id=?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            bind(ps, a, true);
            ps.executeUpdate();

            // Non-bloquant: si notif échoue, on ne casse pas l'update.
            notifyParticipantsOnUpdate(before, a);
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.update: " + e.getMessage(), e);
        }
    }

    /* ===================== DELETE ===================== */

    public void delete(int id) {
        if (id <= 0) throw new IllegalArgumentException("id invalide");

        // Charger annonce + participants avant suppression.
        AnnonceSortie annonce = null;
        try {
            annonce = getById(id);
        } catch (Exception ignored) {
        }

        List<Integer> receivers = new ArrayList<>();
        try {
            receivers = listParticipantUserIdsExcludingRefused(id);
        } catch (Exception ignored) {
        }

        String sql = "DELETE FROM annonce_sortie WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("AnnonceSortieService.delete: " + e.getMessage(), e);
        }

        // Informer participants (non-bloquant).
        try {
            notifyParticipantsSortieDeleted(annonce, id, receivers);
        } catch (Exception ignored) {
        }
    }

    // ===== CrudService<Integer> wrappers (keep existing int API) =====

    @Override
    public AnnonceSortie getById(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        return getById(id.intValue());
    }

    @Override
    public void delete(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        delete(id.intValue());
    }

    /* ===================== MAPPING ===================== */

    private AnnonceSortie map(ResultSet rs) throws SQLException {
        AnnonceSortie a = new AnnonceSortie();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));

        a.setTitre(rs.getString("titre"));
        a.setDescription(rs.getString("description"));

        a.setVille(rs.getString("ville"));
        a.setLieuTexte(rs.getString("lieu_texte"));
        a.setPointRencontre(rs.getString("point_rencontre"));

        a.setTypeActivite(rs.getString("type_activite"));

        Timestamp ts = rs.getTimestamp("date_sortie");
        a.setDateSortie(ts == null ? null : ts.toLocalDateTime());

        a.setBudgetMax(rs.getDouble("budget_max"));
        a.setNbPlaces(rs.getInt("nb_places"));

        a.setImageUrl(rs.getString("image_url"));
        a.setStatut(rs.getString("statut"));

        a.setQuestions(JsonStringArray.fromJson(rs.getString("questions_json")));

        return a;
    }

    private void bind(PreparedStatement ps, AnnonceSortie a, boolean includeIdAtEnd) throws SQLException {
        ps.setInt(1, a.getUserId());
        ps.setString(2, safeReq(a.getTitre(), "Titre"));

        String desc = emptyToNull(a.getDescription());
        ps.setString(3, desc);

        ps.setString(4, safeReq(a.getVille(), "Ville"));
        ps.setString(5, safeReq(a.getLieuTexte(), "Lieu"));
        ps.setString(6, safeReq(a.getPointRencontre(), "Point de rencontre"));
        ps.setString(7, safeReq(a.getTypeActivite(), "Type d'activité"));

        LocalDateTime dt = a.getDateSortie();
        if (dt == null) throw new SQLException("Date de sortie manquante");
        ps.setTimestamp(8, Timestamp.valueOf(dt));

        ps.setDouble(9, a.getBudgetMax());
        ps.setInt(10, a.getNbPlaces());

        ps.setString(11, emptyToNull(a.getImageUrl()));
        ps.setString(12, safeDefault(a.getStatut(), "OUVERTE"));

        ps.setString(13, JsonStringArray.toJson(a.getQuestions()));

        if (includeIdAtEnd) {
            ps.setInt(14, a.getId());
        }
    }

    /* ===================== HELPERS ===================== */

    private static String safeReq(String s, String label) throws SQLException {
        if (s == null || s.trim().isEmpty()) throw new SQLException(label + " obligatoire");
        return s.trim();
    }

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

    /* ===================== NOTIFICATIONS (Sorties) ===================== */

    private void notifyParticipantsOnUpdate(AnnonceSortie before, AnnonceSortie after) {
        try {
            if (after == null) return;
            int sortieId = after.getId();
            if (sortieId <= 0) return;

            List<Integer> receivers = listParticipantUserIdsExcludingRefused(sortieId);
            if (receivers.isEmpty()) return;

            // Skip if no meaningful change (when we have before snapshot).
            if (before != null && !hasMeaningfulChange(before, after)) return;

            String statut = safeDefault(after.getStatut(), "OUVERTE").trim().toUpperCase();
            boolean cancelled = statut.equals("ANNULEE") || statut.equals("ANNULÉE");

            NotificationType type = cancelled ? NotificationType.SORTIE_CANCELLED : NotificationType.SORTIE_UPDATED;
            String title = cancelled ? "Sortie annulée" : "Sortie modifiée";

            String titre = safe(after.getTitre()).trim();
            if (titre.isEmpty()) titre = "Sortie #" + sortieId;

            String body = cancelled
                    ? ("La sortie \"" + titre + "\" a été annulée.")
                    : ("La sortie \"" + titre + "\" a été mise à jour.");

            String meta = "{\"sortieId\":" + sortieId + "}";

            Integer senderId = (after.getUserId() > 0) ? after.getUserId() : null;
            for (Integer uid : receivers) {
                if (uid == null || uid <= 0) continue;
                if (senderId != null && uid.intValue() == senderId.intValue()) continue;

                Notification n = new Notification();
                n.setReceiverId(uid);
                n.setSenderId(senderId);
                n.setType(type);
                n.setTitle(title);
                n.setBody(body);
                n.setEntityType("sortie");
                n.setEntityId(sortieId);
                n.setMetadataJson(meta);

                // Refresh instead of duplicating.
                notificationService.createOrRefreshNotification(n);
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyParticipantsSortieDeleted(AnnonceSortie annonce, int sortieId, List<Integer> receivers) {
        if (sortieId <= 0) return;
        if (receivers == null || receivers.isEmpty()) return;

        int creatorId = (annonce == null) ? 0 : annonce.getUserId();
        Integer senderId = (creatorId > 0) ? creatorId : null;

        String titre = (annonce == null) ? ("Sortie #" + sortieId) : safe(annonce.getTitre()).trim();
        if (titre.isEmpty()) titre = "Sortie #" + sortieId;

        String meta = "{\"sortieId\":" + sortieId + "}";

        for (Integer uid : receivers) {
            if (uid == null || uid <= 0) continue;
            if (senderId != null && uid.intValue() == senderId.intValue()) continue;

            Notification n = new Notification();
            n.setReceiverId(uid);
            n.setSenderId(senderId);
            n.setType(NotificationType.SORTIE_DELETED);
            n.setTitle("Sortie supprimée");
            n.setBody("La sortie \"" + titre + "\" a été supprimée.");
            n.setEntityType("sortie");
            n.setEntityId(sortieId);
            n.setMetadataJson(meta);

            notificationService.createOrRefreshNotification(n);
        }
    }

    private List<Integer> listParticipantUserIdsExcludingRefused(int annonceId) throws SQLException {
        if (annonceId <= 0) return new ArrayList<>();
        String sql = """
                SELECT DISTINCT user_id
                FROM participation_annonce
                WHERE annonce_id=?
                  AND UPPER(statut) NOT IN ('REFUSEE','REFUSÉE')
                """;

        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt("user_id");
                    if (uid > 0) ids.add(uid);
                }
            }
        }
        return ids;
    }

    private static boolean hasMeaningfulChange(AnnonceSortie before, AnnonceSortie after) {
        if (before == null || after == null) return true;

        if (!eq(before.getTitre(), after.getTitre())) return true;
        if (!eq(before.getVille(), after.getVille())) return true;
        if (!eq(before.getLieuTexte(), after.getLieuTexte())) return true;
        if (!eq(before.getPointRencontre(), after.getPointRencontre())) return true;
        if (!eq(before.getTypeActivite(), after.getTypeActivite())) return true;
        if (!eq(before.getStatut(), after.getStatut())) return true;

        if (before.getDateSortie() == null) {
            if (after.getDateSortie() != null) return true;
        } else {
            if (!before.getDateSortie().equals(after.getDateSortie())) return true;
        }

        if (Double.compare(before.getBudgetMax(), after.getBudgetMax()) != 0) return true;
        if (before.getNbPlaces() != after.getNbPlaces()) return true;

        return false;
    }

    private static boolean eq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
