package models.sorties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParticipationSortie {

    private int id;
    private int annonceId;
    private int userId;

    private LocalDateTime dateDemande;

    // EN_ATTENTE | ACCEPTEE | REFUSEE
    private String statut;

    // EMAIL | TELEPHONE
    private String contactPrefer;
    private String contactValue;

    private String commentaire;

    // nb places demandées (>=1)
    private int nbPlaces;

    // réponses alignées avec questions_json
    private List<String> reponses = new ArrayList<>();

    public ParticipationSortie() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAnnonceId() { return annonceId; }
    public void setAnnonceId(int annonceId) { this.annonceId = annonceId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public LocalDateTime getDateDemande() { return dateDemande; }
    public void setDateDemande(LocalDateTime dateDemande) { this.dateDemande = dateDemande; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getContactPrefer() { return contactPrefer; }
    public void setContactPrefer(String contactPrefer) { this.contactPrefer = contactPrefer; }

    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public int getNbPlaces() { return nbPlaces; }
    public void setNbPlaces(int nbPlaces) { this.nbPlaces = nbPlaces; }

    public List<String> getReponses() { return reponses; }
    public void setReponses(List<String> reponses) {
        this.reponses = (reponses == null) ? new ArrayList<>() : new ArrayList<>(reponses);
    }
}