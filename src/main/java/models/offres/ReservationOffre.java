package models.offres;

import java.sql.Date;
import java.sql.Timestamp;

public class ReservationOffre {

    private int id;
    private int userId;
    private int offreId;
    private int lieuId;
    private Date dateReservation;
    private int nombrePersonnes;
    private String statut;   // EN_ATTENTE | CONFIRMÉE | ANNULÉE
    private String note;
    private Timestamp createdAt;

    public ReservationOffre() {}

    public ReservationOffre(int userId, int offreId, int lieuId,
                            Date dateReservation, int nombrePersonnes,
                            String statut, String note) {
        this.userId = userId;
        this.offreId = offreId;
        this.lieuId = lieuId;
        this.dateReservation = dateReservation;
        this.nombrePersonnes = nombrePersonnes;
        this.statut = statut;
        this.note = note;
    }

    // ── Getters / Setters ────────────────────────────────────────────────
    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }

    public int getUserId()                      { return userId; }
    public void setUserId(int userId)           { this.userId = userId; }

    public int getOffreId()                     { return offreId; }
    public void setOffreId(int offreId)         { this.offreId = offreId; }

    public int getLieuId()                      { return lieuId; }
    public void setLieuId(int lieuId)           { this.lieuId = lieuId; }

    public Date getDateReservation()            { return dateReservation; }
    public void setDateReservation(Date d)      { this.dateReservation = d; }

    public int getNombrePersonnes()             { return nombrePersonnes; }
    public void setNombrePersonnes(int n)       { this.nombrePersonnes = n; }

    public String getStatut()                   { return statut; }
    public void setStatut(String statut)        { this.statut = statut; }

    public String getNote()                     { return note; }
    public void setNote(String note)            { this.note = note; }

    public Timestamp getCreatedAt()             { return createdAt; }
    public void setCreatedAt(Timestamp t)       { this.createdAt = t; }
}
