package models.evenements;

import java.time.LocalDateTime;

public class Evenement {

    private int id;

    private LocalDateTime dateCreation;
    private String titre;
    private String description;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;

    private int capaciteMax;

    private Integer lieuId; // nullable si événement public sans lieu

    // enum en String pour simplicité (OUVERT/FERME/ANNULE)
    private String statut;

    // enum en String (PRIVE/PUBLIC)
    private String type;

    private String imageUrl;

    private double prix;

    public Evenement() {}

    public Evenement(int id, LocalDateTime dateCreation, String titre, String description,
                     LocalDateTime dateDebut, LocalDateTime dateFin,
                     int capaciteMax, Integer lieuId,
                     String statut, String type,
                     String imageUrl, double prix) {
        this.id = id;
        this.dateCreation = dateCreation;
        this.titre = titre;
        this.description = description;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.capaciteMax = capaciteMax;
        this.lieuId = lieuId;
        this.statut = statut;
        this.type = type;
        this.imageUrl = imageUrl;
        this.prix = prix;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public int getCapaciteMax() { return capaciteMax; }
    public void setCapaciteMax(int capaciteMax) { this.capaciteMax = capaciteMax; }

    public Integer getLieuId() { return lieuId; }
    public void setLieuId(Integer lieuId) { this.lieuId = lieuId; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public double getPrix() { return prix; }
    public void setPrix(double prix) { this.prix = prix; }

    @Override
    public String toString() {
        return "Evenement{" +
                "id=" + id +
                ", dateCreation=" + dateCreation +
                ", titre='" + titre + '\'' +
                ", dateDebut=" + dateDebut +
                ", dateFin=" + dateFin +
                ", capaciteMax=" + capaciteMax +
                ", lieuId=" + lieuId +
                ", statut='" + statut + '\'' +
                ", type='" + type + '\'' +
                ", prix=" + prix +
                '}';
    }
}
