package models.lieux;

import java.time.LocalDateTime;

public class EvaluationLieu {
    private int id;
    private int lieuId;
    private int userId;
    private int note;
    private String commentaire;
    private LocalDateTime dateEvaluation;
    private LocalDateTime updatedAt;

    private String userNom;
    private String userPrenom;
    private String userImageUrl;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getLieuId() { return lieuId; }
    public void setLieuId(int lieuId) { this.lieuId = lieuId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getNote() { return note; }
    public void setNote(int note) { this.note = note; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public LocalDateTime getDateEvaluation() { return dateEvaluation; }
    public void setDateEvaluation(LocalDateTime dateEvaluation) { this.dateEvaluation = dateEvaluation; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUserNom() { return userNom; }
    public void setUserNom(String userNom) { this.userNom = userNom; }

    public String getUserPrenom() { return userPrenom; }
    public void setUserPrenom(String userPrenom) { this.userPrenom = userPrenom; }

    public String getUserImageUrl() { return userImageUrl; }
    public void setUserImageUrl(String userImageUrl) { this.userImageUrl = userImageUrl; }

    public String getAuteur() {
        String a = ((userPrenom == null ? "" : userPrenom) + " " + (userNom == null ? "" : userNom)).trim();
        return a.isEmpty() ? "Utilisateur" : a;
    }
}