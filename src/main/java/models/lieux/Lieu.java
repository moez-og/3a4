package models.lieux;

import java.util.ArrayList;
import java.util.List;

public class Lieu {

    private int id;
    private int idOffre;

    private String nom;
    private String ville;
    private String adresse;
    private String categorie;

    private Double latitude;
    private Double longitude;

    private String type;      // PUBLIC / PRIVE
    private String imageUrl;

    private String telephone;
    private String siteWeb;
    private String instagram;

    private String description;
    private Double budgetMin;
    private Double budgetMax;

    // Nouveaux champs : horaires et images multiples
    private List<LieuHoraire> horaires = new ArrayList<>();
    private List<String> imagesPaths = new ArrayList<>(); // chemins fichiers locaux/BD

    public Lieu() {}

    public Lieu(int id, String nom) {
        this.id = id;
        this.nom = nom;
    }

    public Lieu(int idOffre, String nom, String ville, String adresse, String categorie,
                Double latitude, Double longitude, String type, String imageUrl) {
        this.idOffre = idOffre;
        this.nom = nom;
        this.ville = ville;
        this.adresse = adresse;
        this.categorie = categorie;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.imageUrl = imageUrl;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdOffre() { return idOffre; }
    public void setIdOffre(int idOffre) { this.idOffre = idOffre; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String siteWeb) { this.siteWeb = siteWeb; }

    public String getInstagram() { return instagram; }
    public void setInstagram(String instagram) { this.instagram = instagram; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getBudgetMin() { return budgetMin; }
    public void setBudgetMin(Double budgetMin) { this.budgetMin = budgetMin; }

    public Double getBudgetMax() { return budgetMax; }
    public void setBudgetMax(Double budgetMax) { this.budgetMax = budgetMax; }

    public List<LieuHoraire> getHoraires() { return horaires; }
    public void setHoraires(List<LieuHoraire> horaires) { this.horaires = horaires != null ? horaires : new ArrayList<>(); }

    public List<String> getImagesPaths() { return imagesPaths; }
    public void setImagesPaths(List<String> imagesPaths) { this.imagesPaths = imagesPaths != null ? imagesPaths : new ArrayList<>(); }

    @Override
    public String toString() {
        return nom == null ? "" : nom;
    }
}
