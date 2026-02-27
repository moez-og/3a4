package models.gamification;

import java.time.LocalDateTime;

public class Badge {
    private int id;
    private String code;
    private String nom;
    private String description;
    private String emoji;
    private String categorie; // EXPLORATEUR, AVIS, FAVORI, SOCIAL, SPECIAL
    private int seuilRequis;  // nombre d'actions requises
    private int pointsBonus;

    // pour un user badge
    private LocalDateTime dateObtenu;

    public Badge() {}

    public Badge(String code, String nom, String description, String emoji,
                 String categorie, int seuilRequis, int pointsBonus) {
        this.code = code;
        this.nom = nom;
        this.description = description;
        this.emoji = emoji;
        this.categorie = categorie;
        this.seuilRequis = seuilRequis;
        this.pointsBonus = pointsBonus;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public int getSeuilRequis() { return seuilRequis; }
    public void setSeuilRequis(int seuilRequis) { this.seuilRequis = seuilRequis; }
    public int getPointsBonus() { return pointsBonus; }
    public void setPointsBonus(int pointsBonus) { this.pointsBonus = pointsBonus; }
    public LocalDateTime getDateObtenu() { return dateObtenu; }
    public void setDateObtenu(LocalDateTime dateObtenu) { this.dateObtenu = dateObtenu; }

    public String getDisplayName() { return emoji + " " + nom; }

    @Override
    public String toString() {
        return "Badge{code='" + code + "', nom='" + nom + "'}";
    }
}
