package models.gamification;

public class UserPoints {
    private int userId;
    private int totalPoints;
    private int nbLieuxVisites;
    private int nbAvisLaisses;
    private int nbFavoris;
    private int nbSortiesJointes;
    private int rang;            // classement local
    private String niveau;       // BRONZE, ARGENT, OR, PLATINE

    public UserPoints() {}

    public UserPoints(int userId) {
        this.userId = userId;
        this.totalPoints = 0;
        this.nbLieuxVisites = 0;
        this.nbAvisLaisses = 0;
        this.nbFavoris = 0;
        this.nbSortiesJointes = 0;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
        this.niveau = calcNiveau(totalPoints);
    }
    public int getNbLieuxVisites() { return nbLieuxVisites; }
    public void setNbLieuxVisites(int nbLieuxVisites) { this.nbLieuxVisites = nbLieuxVisites; }
    public int getNbAvisLaisses() { return nbAvisLaisses; }
    public void setNbAvisLaisses(int nbAvisLaisses) { this.nbAvisLaisses = nbAvisLaisses; }
    public int getNbFavoris() { return nbFavoris; }
    public void setNbFavoris(int nbFavoris) { this.nbFavoris = nbFavoris; }
    public int getNbSortiesJointes() { return nbSortiesJointes; }
    public void setNbSortiesJointes(int nbSortiesJointes) { this.nbSortiesJointes = nbSortiesJointes; }
    public int getRang() { return rang; }
    public void setRang(int rang) { this.rang = rang; }
    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }

    public String getNiveauEmoji() {
        if (niveau == null) return "🥉";
        return switch (niveau) {
            case "ARGENT" -> "🥈";
            case "OR" -> "🥇";
            case "PLATINE" -> "💎";
            default -> "🥉";
        };
    }

    public int getPointsPourNiveauSuivant() {
        if (totalPoints < 100) return 100 - totalPoints;
        if (totalPoints < 500) return 500 - totalPoints;
        if (totalPoints < 1500) return 1500 - totalPoints;
        return 0;
    }

    public double getProgressionNiveau() {
        if (totalPoints < 100) return (double) totalPoints / 100;
        if (totalPoints < 500) return (double) (totalPoints - 100) / 400;
        if (totalPoints < 1500) return (double) (totalPoints - 500) / 1000;
        return 1.0;
    }

    public static String calcNiveau(int points) {
        if (points >= 1500) return "PLATINE";
        if (points >= 500) return "OR";
        if (points >= 100) return "ARGENT";
        return "BRONZE";
    }
}
