package models.users;

public class User {
    private int id;
    private String nom;
    private String prenom;
    private String email;
    private String passwordHash;
    private String role;
    private String telephone;
    private String imageUrl;

    // Constructeur vide
    public User() {
    }

    // Constructeur complet
    public User(int id, String nom, String prenom, String email, String passwordHash, String role, String telephone, String imageUrl) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.telephone = telephone;
        this.imageUrl = imageUrl;
    }

    // Constructeur sans ID (pour l'insertion)
    public User(String nom, String prenom, String email, String passwordHash, String role, String telephone, String imageUrl) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.telephone = telephone;
        this.imageUrl = imageUrl;
    }

    // Getters et Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", nom='" + nom + '\'' +
                ", prenom='" + prenom + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", telephone='" + telephone + '\'' +
                '}';
    }
}
