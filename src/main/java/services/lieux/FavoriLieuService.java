package services.lieux;

import models.lieux.Lieu;
import utils.Mydb;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestion des lieux favoris (table favori_lieu).
 */
public class FavoriLieuService {

    private final Connection cnx;

    public FavoriLieuService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /** Ajoute un lieu en favori (ignore si déjà présent grâce à IGNORE). */
    public void add(int userId, int lieuId) {
        String sql = "INSERT IGNORE INTO favori_lieu (user_id, lieu_id) VALUES (?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lieuId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("FavoriLieuService.add: " + e.getMessage(), e);
        }
    }

    /** Retire un lieu des favoris. */
    public void remove(int userId, int lieuId) {
        String sql = "DELETE FROM favori_lieu WHERE user_id=? AND lieu_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lieuId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("FavoriLieuService.remove: " + e.getMessage(), e);
        }
    }

    /** Vérifie si un lieu est déjà en favori. */
    public boolean isFavori(int userId, int lieuId) {
        String sql = "SELECT 1 FROM favori_lieu WHERE user_id=? AND lieu_id=? LIMIT 1";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("FavoriLieuService.isFavori: " + e.getMessage(), e);
        }
    }

    /** Toggle : ajoute si absent, retire si présent. Retourne le nouvel état (true = favori). */
    public boolean toggle(int userId, int lieuId) {
        if (isFavori(userId, lieuId)) {
            remove(userId, lieuId);
            return false;
        } else {
            add(userId, lieuId);
            return true;
        }
    }

    /** Retourne tous les lieux favoris d'un utilisateur avec les infos complètes. */
    public List<Lieu> getFavorisForUser(int userId) {
        String sql = """
                SELECT l.id, l.nom, l.ville, l.adresse, l.categorie,
                       l.latitude, l.longitude, l.type, l.image_url,
                       l.telephone, l.site_web, l.instagram,
                       l.description, l.budget_min, l.budget_max
                FROM favori_lieu f
                JOIN lieu l ON l.id = f.lieu_id
                WHERE f.user_id = ?
                ORDER BY f.date_ajout DESC
                """;
        List<Lieu> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Lieu l = new Lieu();
                    l.setId(rs.getInt("id"));
                    l.setNom(rs.getString("nom"));
                    l.setVille(rs.getString("ville"));
                    l.setAdresse(rs.getString("adresse"));
                    l.setCategorie(rs.getString("categorie"));
                    l.setLatitude(rs.getObject("latitude", Double.class));
                    l.setLongitude(rs.getObject("longitude", Double.class));
                    l.setType(rs.getString("type"));
                    l.setImageUrl(rs.getString("image_url"));
                    l.setTelephone(rs.getString("telephone"));
                    l.setSiteWeb(rs.getString("site_web"));
                    l.setInstagram(rs.getString("instagram"));
                    l.setDescription(rs.getString("description"));
                    l.setBudgetMin(rs.getObject("budget_min", Double.class));
                    l.setBudgetMax(rs.getObject("budget_max", Double.class));
                    list.add(l);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("FavoriLieuService.getFavorisForUser: " + e.getMessage(), e);
        }
        return list;
    }

    /** Nombre de favoris d'un utilisateur. */
    public int countForUser(int userId) {
        String sql = "SELECT COUNT(*) FROM favori_lieu WHERE user_id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("FavoriLieuService.countForUser: " + e.getMessage(), e);
        }
        return 0;
    }
}
