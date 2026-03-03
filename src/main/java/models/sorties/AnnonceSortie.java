package models.sorties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AnnonceSortie {
    private int id;
    private int userId;

    private String titre;
    private String description;

    private String ville;
    private String lieuTexte;
    private String pointRencontre;

    private String typeActivite;
    private LocalDateTime dateSortie;

    private double budgetMax;   // 0 = aucun budget
    private int nbPlaces;

    private String imageUrl;
    private String statut;      // OUVERTE | CLOTUREE | ANNULEE

    private List<String> questions = new ArrayList<>();

    public AnnonceSortie() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getLieuTexte() {
        return lieuTexte;
    }

    public void setLieuTexte(String lieuTexte) {
        this.lieuTexte = lieuTexte;
    }

    public String getPointRencontre() {
        return pointRencontre;
    }

    public void setPointRencontre(String pointRencontre) {
        this.pointRencontre = pointRencontre;
    }

    public String getTypeActivite() {
        return typeActivite;
    }

    public void setTypeActivite(String typeActivite) {
        this.typeActivite = typeActivite;
    }

    public LocalDateTime getDateSortie() {
        return dateSortie;
    }

    public void setDateSortie(LocalDateTime dateSortie) {
        this.dateSortie = dateSortie;
    }

    public double getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(double budgetMax) {
        this.budgetMax = budgetMax;
    }

    public int getNbPlaces() {
        return nbPlaces;
    }

    public void setNbPlaces(int nbPlaces) {
        this.nbPlaces = nbPlaces;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public List<String> getQuestions() {
        return questions;
    }

    public void setQuestions(List<String> questions) {
        this.questions = (questions == null) ? new ArrayList<>() : new ArrayList<>(questions);
    }

    @Override
    public String toString() {
        return "AnnonceSortie{" +
                "id=" + id +
                ", userId=" + userId +
                ", titre='" + titre + '\'' +
                ", ville='" + ville + '\'' +
                ", typeActivite='" + typeActivite + '\'' +
                ", dateSortie=" + dateSortie +
                ", budgetMax=" + budgetMax +
                ", nbPlaces=" + nbPlaces +
                ", statut='" + statut + '\'' +
                '}';
    }
}
