package models.notifications;

/**
 * Notification types supported by the app.
 */
public enum NotificationType {
    PARTICIPATION_REQUESTED,
    PARTICIPATION_ACCEPTED,
    PARTICIPATION_REFUSED,

    // ===== Chat =====
    CHAT_MESSAGE,

    // ===== Sorties (gestion sortie) =====
    SORTIE_UPDATED,
    SORTIE_CANCELLED,
    SORTIE_DELETED,

    // ===== Participation (gestion sortie) =====
    PARTICIPATION_CANCELLED
}
