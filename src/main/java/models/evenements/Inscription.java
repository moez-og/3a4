package models.evenements;

import java.time.LocalDateTime;

public class Inscription {

    private int id;
    private int eventId;
    private int userId;

    // ex: EN_ATTENTE / CONFIRMEE / ANNULEE (selon ta DB)
    private String statut;

    // ex: PAYE / NON_PAYE (ou boolean)
    private String paiement;

    private LocalDateTime dateCreation;

    /** Nombre de tickets demandés (DEFAULT 1 en DB) */
    private int nbTickets = 1;

    public Inscription() {}

    public Inscription(int id, int eventId, int userId, String statut, String paiement, LocalDateTime dateCreation) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.statut = statut;
        this.paiement = paiement;
        this.dateCreation = dateCreation;
    }

    public Inscription(int id, int eventId, int userId, String statut, String paiement,
                        LocalDateTime dateCreation, int nbTickets) {
        this(id, eventId, userId, statut, paiement, dateCreation);
        this.nbTickets = nbTickets;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getPaiement() { return paiement; }
    public void setPaiement(String paiement) { this.paiement = paiement; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public int getNbTickets() { return nbTickets; }
    public void setNbTickets(int nbTickets) { this.nbTickets = nbTickets; }

    @Override
    public String toString() {
        return "Inscription{" +
                "id=" + id +
                ", eventId=" + eventId +
                ", userId=" + userId +
                ", statut='" + statut + '\'' +
                ", paiement='" + paiement + '\'' +
                ", nbTickets=" + nbTickets +
                ", dateCreation=" + dateCreation +
                '}';
    }
}
