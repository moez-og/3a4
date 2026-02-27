package models.gamification;

public class ClassementEntry {
    private int rang;
    private int userId;
    private String nom;
    private String prenom;
    private String imageUrl;
    private int totalPoints;
    private String niveau;
    private int nbBadges;

    public ClassementEntry() {}

    public int getRang() { return rang; }
    public void setRang(int rang) { this.rang = rang; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }
    public int getNbBadges() { return nbBadges; }
    public void setNbBadges(int nbBadges) { this.nbBadges = nbBadges; }

    public String getNomComplet() {
        return ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
    }

    public String getNiveauEmoji() {
        if (niveau == null) return "🥉";
        return switch (niveau) {
            case "ARGENT" -> "🥈";
            case "OR" -> "🥇";
            case "PLATINE" -> "💎";
            default -> "🥉";
        };
    }

    public String getRangEmoji() {
        return switch (rang) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "#" + rang;
        };
    }
}
