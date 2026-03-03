package models.notifications;

import models.lieux.Lieu;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LieuNotification — one suggestion notification based on user favorites.
 */
public class LieuNotification {

    public enum Type {
        SUGGESTION_SAME_CATEGORY,
        SUGGESTION_SAME_VILLE,
        SUGGESTION_SIMILAR_BUDGET
    }

    private final String        id;
    private final Lieu          lieu;
    private final Type          type;
    private final String        reason;
    private final LocalDateTime createdAt;
    private boolean             read;

    public LieuNotification(Lieu lieu, Type type, String reason) {
        this.id        = UUID.randomUUID().toString();
        this.lieu      = lieu;
        this.type      = type;
        this.reason    = reason;
        this.createdAt = LocalDateTime.now();
        this.read      = false;
    }

    public String        getId()        { return id; }
    public Lieu          getLieu()      { return lieu; }
    public Type          getType()      { return type; }
    public String        getReason()    { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean       isRead()       { return read; }
    public void          markRead()     { this.read = true; }

    public String getTypeLabel() {
        return switch (type) {
            case SUGGESTION_SAME_CATEGORY  -> "Même catégorie";
            case SUGGESTION_SAME_VILLE     -> "Même ville";
            case SUGGESTION_SIMILAR_BUDGET -> "Budget similaire";
        };
    }

    public String getCategoryIcon() {
        String cat = lieu.getCategorie() == null ? "" : lieu.getCategorie().toLowerCase();
        if (cat.contains("resto") || cat.contains("restaurant")) return "🍽️";
        if (cat.contains("café") || cat.contains("cafe") || cat.contains("coffee")) return "☕";
        if (cat.contains("musée") || cat.contains("museum") || cat.contains("art")) return "🏛️";
        if (cat.contains("parc") || cat.contains("park") || cat.contains("jardin")) return "🌿";
        if (cat.contains("plage") || cat.contains("beach") || cat.contains("mer"))  return "🏖️";
        if (cat.contains("hotel") || cat.contains("hôtel"))                          return "🏨";
        if (cat.contains("sport") || cat.contains("gym"))                            return "⚽";
        if (cat.contains("cinéma") || cat.contains("cinema"))                        return "🎬";
        if (cat.contains("shopping") || cat.contains("mall"))                        return "🛍️";
        return "📌";
    }
}
