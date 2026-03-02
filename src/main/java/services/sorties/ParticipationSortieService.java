package services.sorties;

import models.sorties.ParticipationSortie;
import models.sorties.AnnonceSortie;
import models.notifications.Notification;
import models.notifications.NotificationType;
import services.notifications.NotificationService;
import services.common.ServiceBase;
import utils.Mydb;
import utils.json.JsonStringArray;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParticipationSortieService implements ServiceBase {

    private final Connection cnx;
    private final NotificationService notificationService = new NotificationService();
    private final AnnonceSortieService annonceSortieService = new AnnonceSortieService();
    private final NotificationEmailSmsService emailSmsService = new NotificationEmailSmsService();

    // ✅ NOM TABLE EXACT
    private static final String TABLE = "participation_annonce";

    public ParticipationSortieService() {
        cnx = Mydb.getInstance().getConnection();
    }

    public ParticipationSortie getByAnnonceAndUser(int annonceId, int userId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE annonce_id=? AND user_id=? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByAnnonceAndUser: " + e.getMessage(), e);
        }
        return null;
    }

    public int countAcceptedPlaces(int annonceId) {
        String sql = "SELECT COALESCE(SUM(nb_places),0) AS s FROM " + TABLE + " WHERE annonce_id=? AND UPPER(statut) IN ('CONFIRMEE','ACCEPTEE')";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("s");
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.countAcceptedPlaces: " + e.getMessage(), e);
        }
        return 0;
    }

    public void addRequest(ParticipationSortie p) {
        ParticipationSortie existing = getByAnnonceAndUser(p.getAnnonceId(), p.getUserId());
        if (existing != null) {
            throw new RuntimeException("Une demande existe déjà pour cette annonce.");
        }

        String sql = """
                INSERT INTO %s
                (annonce_id, user_id, statut, contact_prefer, contact_value, commentaire, nb_places, reponses_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE);

        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getAnnonceId());
            ps.setInt(2, p.getUserId());
            ps.setString(3, safeDefault(p.getStatut(), "EN_ATTENTE"));
            ps.setString(4, safeDefault(p.getContactPrefer(), "EMAIL"));
            ps.setString(5, safeReq(p.getContactValue(), "Contact"));
            ps.setString(6, emptyToNull(p.getCommentaire()));
            ps.setInt(7, Math.max(1, p.getNbPlaces()));
            ps.setString(8, JsonStringArray.toJson(p.getReponses()));
            ps.executeUpdate();

            // Grab generated id (participation request id) so we can use it as entityId.
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    if (id > 0) p.setId(id);
                }
            }

            // Notifier le créateur de la sortie.
            createParticipationRequestedNotification(p);
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.addRequest: " + e.getMessage(), e);
        }
    }

    public void updateStatus(int id, String statut) {
        if (id <= 0) throw new IllegalArgumentException("ID participation invalide");

        // Load current row first (to detect transitions and build notification payload).
        ParticipationSortie before = getById(id);
        if (before == null) throw new RuntimeException("Participation introuvable. ID=" + id);

        String newStatus = safeDefault(statut, "EN_ATTENTE");
        String oldStatus = safeDefault(before.getStatut(), "");
        if (!oldStatus.isBlank() && oldStatus.equalsIgnoreCase(newStatus)) {
            return; // no change => no duplicate notification
        }

        String sql = "UPDATE " + TABLE + " SET statut=? WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            ps.executeUpdate();

            // Keep annonce status consistent globally (home/list/details/admin).
            // If capacity is full -> CLOTUREE. If places free again -> OUVERTE.
            // Never override ANNULEE.
            boolean oldAccepted = isAcceptedStatus(oldStatus);
            boolean newAccepted = isAcceptedStatus(newStatus);
            if (oldAccepted || newAccepted) {
                reconcileAnnonceStatusByCapacity(before.getAnnonceId());
            }

            // Notifier le participant (accept/refus). senderId est optionnel => null.
            createParticipationDecisionNotification(before, newStatus);

            // ✅ Envoi email / SMS au participant (acceptation OU refus)
            String statusUpper = safeDefault(newStatus, "").trim().toUpperCase();
            boolean isAccepted = isAcceptedStatus(newStatus);
            boolean isRefused  = statusUpper.equals("REFUSEE") || statusUpper.equals("REFUSÉE");

            if (isAccepted || isRefused) {
                try {
                    AnnonceSortie annonce = annonceSortieService.getById(before.getAnnonceId());
                    if (annonce != null) {
                        emailSmsService.envoyerNotification(before, annonce, isAccepted);
                    } else {
                        System.err.println("[NotifService] Annonce introuvable pour ID=" + before.getAnnonceId());
                    }
                } catch (Exception e) {
                    System.err.println("[NotifService] ❌ Erreur lors de l'envoi : " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.updateStatus: " + e.getMessage(), e);
        }
    }

    private boolean isAcceptedStatus(String statut) {
        String s = safeDefault(statut, "").trim().toUpperCase();
        return s.equals("CONFIRMEE") || s.equals("ACCEPTEE");
    }

    private void reconcileAnnonceStatusByCapacity(int annonceId) {
        if (annonceId <= 0) return;

        String sqlInfo = "SELECT nb_places, statut FROM annonce_sortie WHERE id=? LIMIT 1";
        int capacity = 0;
        String currentStatus = "";

        try (PreparedStatement ps = cnx.prepareStatement(sqlInfo)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                capacity = rs.getInt("nb_places");
                currentStatus = safeDefault(rs.getString("statut"), "").trim().toUpperCase();
            }
        } catch (SQLException e) {
            // Don't fail the whole updateStatus for a secondary consistency update.
            return;
        }

        if (currentStatus.equals("ANNULEE") || currentStatus.equals("ANNULÉE")) return;
        if (capacity <= 0) return;

        int accepted = 0;
        try {
            accepted = countAcceptedPlaces(annonceId);
        } catch (Exception ignored) {
        }

        String desired = (accepted >= capacity) ? "CLOTUREE" : "OUVERTE";
        if (desired.equals(currentStatus)) return;

        // Only toggle between OUVERTE <-> CLOTUREE here.
        if (!(currentStatus.equals("OUVERTE") || currentStatus.equals("CLOTUREE"))) return;

        String sqlUpd = "UPDATE annonce_sortie SET statut=? WHERE id=? AND UPPER(statut)=?";
        try (PreparedStatement ps = cnx.prepareStatement(sqlUpd)) {
            ps.setString(1, desired);
            ps.setInt(2, annonceId);
            ps.setString(3, currentStatus);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public ParticipationSortie getById(int id) {
        String sql = "SELECT * FROM " + TABLE + " WHERE id=? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getById: " + e.getMessage(), e);
        }
        return null;
    }

    private void createParticipationRequestedNotification(ParticipationSortie p) {
        try {
            if (p == null) return;
            if (p.getId() <= 0) return; // entityId must be valid
            if (p.getAnnonceId() <= 0 || p.getUserId() <= 0) return;

            AnnonceSortie annonce = annonceSortieService.getById(p.getAnnonceId());
            if (annonce == null) return;

            int creatorId = annonce.getUserId();
            if (creatorId <= 0) return;
            if (creatorId == p.getUserId()) return; // safety

            Notification n = new Notification();
            n.setReceiverId(creatorId);
            n.setSenderId(p.getUserId());
            n.setType(NotificationType.PARTICIPATION_REQUESTED);
            n.setTitle("Nouvelle demande de participation");
            String titre = safe(annonce.getTitre()).trim();
            if (titre.isEmpty()) titre = "Sortie #" + annonce.getId();
            n.setBody("Un participant a demandé à rejoindre: " + titre);
            n.setEntityType("participation");
            n.setEntityId(p.getId());
            n.setMetadataJson("{\"sortieId\":" + annonce.getId() + ",\"demandeId\":" + p.getId() + "}");

            notificationService.createNotification(n);
        } catch (Exception ignored) {
            // Notifications are non-blocking for core participation flow.
        }
    }

    private void createParticipationDecisionNotification(ParticipationSortie participation, String newStatus) {
        try {
            if (participation == null) return;
            if (participation.getId() <= 0) return;
            if (participation.getAnnonceId() <= 0 || participation.getUserId() <= 0) return;

            String s = safeDefault(newStatus, "").trim().toUpperCase();
            NotificationType type;
            String title;

            if (s.equals("CONFIRMEE") || s.equals("ACCEPTEE")) {
                type = NotificationType.PARTICIPATION_ACCEPTED;
                title = "Demande acceptée";
            } else if (s.equals("REFUSEE") || s.equals("REFUSÉE")) {
                type = NotificationType.PARTICIPATION_REFUSED;
                title = "Demande refusée";
            } else {
                return; // only these transitions generate notifications
            }

            AnnonceSortie annonce = annonceSortieService.getById(participation.getAnnonceId());
            String titre = (annonce == null) ? ("Sortie #" + participation.getAnnonceId()) : safe(annonce.getTitre()).trim();
            if (titre.isEmpty()) titre = "Sortie #" + participation.getAnnonceId();

            Notification n = new Notification();
            n.setReceiverId(participation.getUserId());
            n.setSenderId(null); // optional (creator/admin depends on UI path)
            n.setType(type);
            n.setTitle(title);
            n.setBody(title + " : " + titre);
            n.setEntityType("participation");
            n.setEntityId(participation.getId());
            n.setMetadataJson("{\"sortieId\":" + participation.getAnnonceId() + ",\"demandeId\":" + participation.getId() + "}");

            notificationService.createNotification(n);
        } catch (Exception ignored) {
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public void updatePendingRequest(ParticipationSortie p) {
        if (p == null) throw new IllegalArgumentException("Participation null");
        if (p.getId() <= 0) throw new IllegalArgumentException("ID participation invalide");

        String sql = """
                UPDATE %s
                SET contact_prefer=?, contact_value=?, commentaire=?, nb_places=?, reponses_json=?
                WHERE id=? AND UPPER(statut)='EN_ATTENTE'
                """.formatted(TABLE);

        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, safeDefault(p.getContactPrefer(), "EMAIL"));
            ps.setString(2, safeReq(p.getContactValue(), "Contact"));
            ps.setString(3, emptyToNull(p.getCommentaire()));
            ps.setInt(4, Math.max(1, p.getNbPlaces()));
            ps.setString(5, JsonStringArray.toJson(p.getReponses()));
            ps.setInt(6, p.getId());
            int n = ps.executeUpdate();
            if (n <= 0) {
                throw new SQLException("Aucune demande EN_ATTENTE à modifier");
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.updatePendingRequest: " + e.getMessage(), e);
        }
    }

    public void deleteById(int id) {
        if (id <= 0) throw new IllegalArgumentException("ID participation invalide");

        // Charger avant suppression pour pouvoir notifier.
        ParticipationSortie before = null;
        try {
            before = getById(id);
        } catch (Exception ignored) {
        }

        String sql = "DELETE FROM " + TABLE + " WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.deleteById: " + e.getMessage(), e);
        }

        // Non-bloquant.
        try {
            createParticipationCancelledNotification(before);
        } catch (Exception ignored) {
        }
    }

    private void createParticipationCancelledNotification(ParticipationSortie p) {
        try {
            if (p == null) return;
            if (p.getId() <= 0) return;
            if (p.getAnnonceId() <= 0 || p.getUserId() <= 0) return;

            AnnonceSortie annonce = annonceSortieService.getById(p.getAnnonceId());
            if (annonce == null) return;

            int creatorId = annonce.getUserId();
            if (creatorId <= 0) return;
            if (creatorId == p.getUserId()) return;

            String titre = safe(annonce.getTitre()).trim();
            if (titre.isEmpty()) titre = "Sortie #" + annonce.getId();

            Notification n = new Notification();
            n.setReceiverId(creatorId);
            n.setSenderId(p.getUserId());
            n.setType(NotificationType.PARTICIPATION_CANCELLED);
            n.setTitle("Participation annulée");
            n.setBody("Un participant a annulé sa demande/participation: " + titre);
            n.setEntityType("participation");
            n.setEntityId(p.getId());
            n.setMetadataJson("{\"sortieId\":" + annonce.getId() + ",\"demandeId\":" + p.getId() + "}");

            notificationService.createNotification(n);
        } catch (Exception ignored) {
        }
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) AS c FROM " + TABLE;
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong("c");
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.countAll: " + e.getMessage(), e);
        }
        return 0;
    }

    public long countByStatus(String statut) {
        String sql = "SELECT COUNT(*) AS c FROM " + TABLE + " WHERE UPPER(statut)=UPPER(?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, safeDefault(statut, ""));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.countByStatus: " + e.getMessage(), e);
        }
        return 0;
    }

    public long countByStatuses(String... statuts) {
        if (statuts == null || statuts.length == 0) return 0;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < statuts.length; i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }
        String sql = "SELECT COUNT(*) AS c FROM " + TABLE + " WHERE UPPER(statut) IN (" + in + ")";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            for (int i = 0; i < statuts.length; i++) {
                ps.setString(i + 1, safeDefault(statuts[i], "").trim().toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("c");
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.countByStatuses: " + e.getMessage(), e);
        }
        return 0;
    }

    public List<ParticipationSortie> getByAnnonce(int annonceId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE annonce_id=? ORDER BY date_demande DESC, id DESC";
        List<ParticipationSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, annonceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByAnnonce: " + e.getMessage(), e);
        }
        return list;
    }

    public List<ParticipationSortie> getByUser(int userId) {
        String sql = "SELECT * FROM " + TABLE + " WHERE user_id=? ORDER BY date_demande DESC, id DESC";
        List<ParticipationSortie> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("ParticipationSortieService.getByUser: " + e.getMessage(), e);
        }
        return list;
    }

    private ParticipationSortie map(ResultSet rs) throws SQLException {
        ParticipationSortie p = new ParticipationSortie();
        p.setId(rs.getInt("id"));
        p.setAnnonceId(rs.getInt("annonce_id"));
        p.setUserId(rs.getInt("user_id"));

        Timestamp ts = rs.getTimestamp("date_demande");
        if (ts != null) p.setDateDemande(ts.toLocalDateTime());

        p.setStatut(rs.getString("statut"));
        p.setContactPrefer(rs.getString("contact_prefer"));
        p.setContactValue(rs.getString("contact_value"));
        p.setCommentaire(rs.getString("commentaire"));
        p.setNbPlaces(rs.getInt("nb_places"));

        p.setReponses(JsonStringArray.fromJson(rs.getString("reponses_json")));
        return p;
    }

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
}