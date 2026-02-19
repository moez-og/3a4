package services.users;

import models.users.User;
import utils.Mydb;
import utils.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class UserService {
    private Connection connection;

    public UserService() throws SQLException {
        this.connection = Mydb.getInstance().getConnection();
        if (this.connection == null) {
            System.err.println("✗ ERREUR CRITIQUE: Impossible de se connecter à la base de données!");
            System.err.println("  Vérifiez que:");
            System.err.println("  1. MySQL est en cours d'exécution");
            System.err.println("  2. La base de données 'fintokhrej' existe");
            System.err.println("  3. Les identifiants sont corrects (root:vide)");
        }
    }

    /**
     * Ajouter un nouvel utilisateur
     */
    public void ajouter(User user) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connexion à la base de données indisponible");
        }
        String sql = "INSERT INTO user (nom, prenom, email, password_hash, role, telephone, imageUrl) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getNom());
            ps.setString(2, user.getPrenom());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getTelephone());
            ps.setString(7, user.getImageUrl());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    user.setId(rs.getInt(1));
                }
            }
        }
    }

    /**
     * Modifier un utilisateur existant
     */
    public void modifier(User user) throws SQLException {
        String sql = "UPDATE user SET nom = ?, prenom = ?, email = ?, password_hash = ?, role = ?, telephone = ?, imageUrl = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getNom());
            ps.setString(2, user.getPrenom());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getRole());
            ps.setString(6, user.getTelephone());
            ps.setString(7, user.getImageUrl());
            ps.setInt(8, user.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Supprimer un utilisateur
     */
    public void supprimer(int id) throws SQLException {
        String sql = "DELETE FROM user WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Trouver un utilisateur par ID
     */
    public User trouverParId(int id) throws SQLException {
        String sql = "SELECT * FROM user WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return extraireUtilisateur(rs);
                }
            }
        }
        return null;
    }

    /**
     * Trouver un utilisateur par email
     */
    public User trouverParEmail(String email) throws SQLException {
        if (connection == null) {
            throw new SQLException("Connexion à la base de données indisponible");
        }
        String sql = "SELECT * FROM user WHERE email = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return extraireUtilisateur(rs);
                }
            }
        }
        return null;
    }

    /**
     * Authentifier un utilisateur (vérifier email et mot de passe)
     */
    public boolean authentifier(String email, String motDePasse) throws SQLException {
        User user = trouverParEmail(email);
        if (user != null) {
            return PasswordUtil.verifyPassword(motDePasse, user.getPasswordHash());
        }
        return false;
    }

    /**
     * Obtenir l'utilisateur connecté (par email et mot de passe)
     */
    public User obtenirUtilisateurConnecte(String email, String motDePasse) throws SQLException {
        User user = trouverParEmail(email);
        if (user != null) {
            // Vérifier le mot de passe avec PasswordUtil
            if (PasswordUtil.verifyPassword(motDePasse, user.getPasswordHash())) {
                return user;
            }
        }
        return null;
    }

    /**
     * Obtenir tous les utilisateurs
     */
    public List<User> obtenirTous() throws SQLException {
        List<User> utilisateurs = new ArrayList<>();
        String sql = "SELECT * FROM user";
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    utilisateurs.add(extraireUtilisateur(rs));
                }
            }
        }
        return utilisateurs;
    }

    /**
     * Méthode utilitaire pour extraire un utilisateur depuis ResultSet
     */
    private User extraireUtilisateur(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("nom"),
                rs.getString("prenom"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getString("telephone"),
                rs.getString("imageUrl")
        );
    }

    /**
     * Vérifier si un email existe
     */
    public boolean emailExiste(String email) throws SQLException {
        return trouverParEmail(email) != null;
    }
}
