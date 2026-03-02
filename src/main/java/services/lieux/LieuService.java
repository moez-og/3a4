package services.lieux;

import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import utils.Mydb;

import services.common.CrudService;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LieuService implements CrudService<Lieu, Integer> {

    private final Connection cnx;

    public LieuService() {
        cnx = Mydb.getInstance().getConnection();
    }

    /* ===================== READ ===================== */

    public List<Lieu> getAll() {
        String sql = """
                SELECT id, id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                       telephone, site_web, instagram, description, budget_min, budget_max
                FROM lieu
                ORDER BY id DESC
                """;
        List<Lieu> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Lieu l = map(rs);
                l.setHoraires(getHorairesByLieuId(l.getId()));
                l.setImagesPaths(getImagesByLieuId(l.getId()));
                list.add(l);
            }

        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getAll: " + e.getMessage(), e);
        }
        return list;
    }

    public Lieu getById(int id) {
        String sql = """
                SELECT id, id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                       telephone, site_web, instagram, description, budget_min, budget_max
                FROM lieu
                WHERE id = ?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Lieu l = map(rs);
                    l.setHoraires(getHorairesByLieuId(l.getId()));
                    l.setImagesPaths(getImagesByLieuId(l.getId()));
                    return l;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getById: " + e.getMessage(), e);
        }
        return null;
    }

    public List<String> getDistinctVilles() {
        String sql = "SELECT DISTINCT ville FROM lieu WHERE ville IS NOT NULL AND ville<>'' ORDER BY ville";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getDistinctVilles: " + e.getMessage(), e);
        }
        return list;
    }

    public List<String> getDistinctCategories() {
        String sql = "SELECT DISTINCT categorie FROM lieu WHERE categorie IS NOT NULL AND categorie<>'' ORDER BY categorie";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.getDistinctCategories: " + e.getMessage(), e);
        }
        return list;
    }

    /* ===================== CREATE ===================== */

    public void add(Lieu l) {
        String sql = """
                INSERT INTO lieu (id_offre, nom, ville, adresse, categorie, latitude, longitude, type, image_url,
                                  telephone, site_web, instagram, description, budget_min, budget_max)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, l.getIdOffre());
            ps.setString(2, l.getNom());
            ps.setString(3, l.getVille());
            ps.setString(4, l.getAdresse());
            ps.setString(5, l.getCategorie());

            setNullableDouble(ps, 6, l.getLatitude());
            setNullableDouble(ps, 7, l.getLongitude());

            ps.setString(8, safeDefault(l.getType(), "PUBLIC"));
            ps.setString(9, emptyToNull(l.getImageUrl()));

            ps.setString(10, emptyToNull(l.getTelephone()));
            ps.setString(11, emptyToNull(l.getSiteWeb()));
            ps.setString(12, emptyToNull(l.getInstagram()));
            ps.setString(13, emptyToNull(l.getDescription()));

            setNullableBigDecimal(ps, 14, l.getBudgetMin());
            setNullableBigDecimal(ps, 15, l.getBudgetMax());

            ps.executeUpdate();

            // Récupérer l'ID généré
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int generatedId = keys.getInt(1);
                    l.setId(generatedId);

                    // Sauvegarder horaires
                    saveHoraires(generatedId, l.getHoraires());

                    // Copier et sauvegarder les images uploadées
                    saveImages(generatedId, l.getImagesPaths());
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("LieuService.add: " + e.getMessage(), e);
        }
    }

    /* ===================== UPDATE ===================== */

    public void update(Lieu l) {
        String sql = """
                UPDATE lieu
                SET id_offre=?, nom=?, ville=?, adresse=?, categorie=?, latitude=?, longitude=?, type=?, image_url=?,
                    telephone=?, site_web=?, instagram=?, description=?, budget_min=?, budget_max=?
                WHERE id=?
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, l.getIdOffre());
            ps.setString(2, l.getNom());
            ps.setString(3, l.getVille());
            ps.setString(4, l.getAdresse());
            ps.setString(5, l.getCategorie());

            setNullableDouble(ps, 6, l.getLatitude());
            setNullableDouble(ps, 7, l.getLongitude());

            ps.setString(8, safeDefault(l.getType(), "PUBLIC"));
            ps.setString(9, emptyToNull(l.getImageUrl()));

            ps.setString(10, emptyToNull(l.getTelephone()));
            ps.setString(11, emptyToNull(l.getSiteWeb()));
            ps.setString(12, emptyToNull(l.getInstagram()));
            ps.setString(13, emptyToNull(l.getDescription()));

            setNullableBigDecimal(ps, 14, l.getBudgetMin());
            setNullableBigDecimal(ps, 15, l.getBudgetMax());

            ps.setInt(16, l.getId());

            ps.executeUpdate();

            // Mettre à jour horaires (supprimer anciens + réinsérer)
            deleteHorairesByLieuId(l.getId());
            saveHoraires(l.getId(), l.getHoraires());

            // Mettre à jour images (supprimer anciens + réinsérer)
            deleteImagesByLieuId(l.getId());
            saveImages(l.getId(), l.getImagesPaths());

        } catch (SQLException e) {
            throw new RuntimeException("LieuService.update: " + e.getMessage(), e);
        }
    }


    /* ===================== VALIDATION DOUBLON ===================== */

    /**
     * Vérifie si un lieu avec le même nom et la même ville existe déjà.
     * @param nom   nom du lieu
     * @param ville ville du lieu
     * @param excludeId  ID à exclure (pour la modification, passer l'ID existant ; pour ajout passer -1)
     * @return true si un doublon existe
     */
    public boolean existsByNomVille(String nom, String ville, int excludeId) {
        String sql = "SELECT COUNT(*) FROM lieu WHERE LOWER(TRIM(nom))=LOWER(TRIM(?)) AND LOWER(TRIM(ville))=LOWER(TRIM(?)) AND id != ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, nom == null ? "" : nom.trim());
            ps.setString(2, ville == null ? "" : ville.trim());
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.existsByNomVille: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * Vérifie si un lieu avec le même nom et la même adresse existe déjà.
     * @param nom    nom du lieu
     * @param adresse adresse du lieu
     * @param excludeId  ID à exclure (-1 pour ajout)
     */
    public boolean existsByNomAdresse(String nom, String adresse, int excludeId) {
        if (adresse == null || adresse.trim().isEmpty()) return false;
        String sql = "SELECT COUNT(*) FROM lieu WHERE LOWER(TRIM(nom))=LOWER(TRIM(?)) AND LOWER(TRIM(adresse))=LOWER(TRIM(?)) AND id != ?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setString(1, nom == null ? "" : nom.trim());
            ps.setString(2, adresse.trim());
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.existsByNomAdresse: " + e.getMessage(), e);
        }
        return false;
    }

    /* ===================== DELETE ===================== */

    public void delete(int id) {
        deleteHorairesByLieuId(id);
        deleteImagesByLieuId(id);
        String sql = "DELETE FROM lieu WHERE id=?";
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.delete: " + e.getMessage(), e);
        }
    }

    // ===== CrudService<Integer> wrappers (keep existing int API) =====

    @Override
    public Lieu getById(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        return getById(id.intValue());
    }

    @Override
    public void delete(Integer id) {
        if (id == null) throw new IllegalArgumentException("id null");
        delete(id.intValue());
    }

    /* ===================== HORAIRES ===================== */

    public List<LieuHoraire> getHorairesByLieuId(int lieuId) {
        String sql = """
                SELECT id, lieu_id, jour, ouvert,
                       heure_ouverture_1, heure_fermeture_1,
                       heure_ouverture_2, heure_fermeture_2
                FROM lieu_horaire
                WHERE lieu_id = ?
                ORDER BY FIELD(jour, 'LUNDI','MARDI','MERCREDI','JEUDI','VENDREDI','SAMEDI','DIMANCHE')
                """;
        List<LieuHoraire> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LieuHoraire h = new LieuHoraire();
                    h.setId(rs.getInt("id"));
                    h.setLieuId(rs.getInt("lieu_id"));
                    h.setJour(rs.getString("jour"));
                    h.setOuvert(rs.getBoolean("ouvert"));
                    h.setHeureOuverture1(timeToStr(rs.getString("heure_ouverture_1")));
                    h.setHeureFermeture1(timeToStr(rs.getString("heure_fermeture_1")));
                    h.setHeureOuverture2(timeToStr(rs.getString("heure_ouverture_2")));
                    h.setHeureFermeture2(timeToStr(rs.getString("heure_fermeture_2")));
                    list.add(h);
                }
            }
        } catch (SQLException e) {
            // Si la table n'existe pas encore, on retourne liste vide
            return new ArrayList<>();
        }
        return list;
    }

    private void saveHoraires(int lieuId, List<LieuHoraire> horaires) {
        if (horaires == null || horaires.isEmpty()) return;
        String sql = """
                INSERT INTO lieu_horaire (lieu_id, jour, ouvert,
                    heure_ouverture_1, heure_fermeture_1,
                    heure_ouverture_2, heure_fermeture_2)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            for (LieuHoraire h : horaires) {
                ps.setInt(1, lieuId);
                ps.setString(2, h.getJour());
                ps.setBoolean(3, h.isOuvert());
                ps.setString(4, emptyToNull(h.getHeureOuverture1()));
                ps.setString(5, emptyToNull(h.getHeureFermeture1()));
                ps.setString(6, emptyToNull(h.getHeureOuverture2()));
                ps.setString(7, emptyToNull(h.getHeureFermeture2()));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.saveHoraires: " + e.getMessage(), e);
        }
    }

    private void deleteHorairesByLieuId(int lieuId) {
        try (PreparedStatement ps = cnx.prepareStatement("DELETE FROM lieu_horaire WHERE lieu_id=?")) {
            ps.setInt(1, lieuId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // ignore si table n'existe pas
        }
    }

    /* ===================== IMAGES ===================== */

    public List<String> getImagesByLieuId(int lieuId) {
        String sql = "SELECT image_url FROM lieu_image WHERE lieu_id=? ORDER BY ordre";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = cnx.prepareStatement(sql)) {
            ps.setInt(1, lieuId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            }
        } catch (SQLException e) {
            return new ArrayList<>();
        }
        return list;
    }

    /**
     * Copie les fichiers image vers le dossier images/lieux/ et insère les chemins en BD.
     * @param lieuId  ID du lieu
     * @param filePaths  liste de chemins absolus des fichiers sélectionnés par l'utilisateur
     */
    private void saveImages(int lieuId, List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return;

        String insertSql = "INSERT INTO lieu_image (lieu_id, image_url, ordre) VALUES (?, ?, ?)";
        try (PreparedStatement ps = cnx.prepareStatement(insertSql)) {
            int ordre = 1;
            for (String filePath : filePaths) {
                if (filePath == null || filePath.isBlank()) continue;

                // Copier le fichier dans resources/images/lieux/ et construire l'URL relative
                String savedPath = copyImageFile(lieuId, filePath, ordre);

                ps.setInt(1, lieuId);
                ps.setString(2, savedPath);
                ps.setInt(3, ordre);
                ps.addBatch();
                ordre++;
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("LieuService.saveImages: " + e.getMessage(), e);
        }
    }

    /**
     * Copie un fichier image dans le dossier images/lieux/ du projet.
     * @return chemin relatif de la forme /images/lieux/lieu_<id>_<ordre>.ext
     */
    public String copyImageFile(int lieuId, String sourceFilePath, int ordre) {
        try {
            File sourceFile = new File(sourceFilePath);
            if (!sourceFile.exists()) return sourceFilePath;

            // Déterminer l'extension
            String fileName = sourceFile.getName();
            String ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx >= 0) ext = fileName.substring(dotIdx); // ex: ".jpg"

            // Nom de destination
            String destFileName = "lieu_" + lieuId + "_" + ordre + ext;

            // Chemin absolu destination
            String destDirPath = getImagesLieuxDir();
            File destDir = new File(destDirPath);
            if (!destDir.exists()) destDir.mkdirs();

            Path destPath = Paths.get(destDirPath, destFileName);
            Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);

            // Retourner le chemin relatif (utilisé pour affichage)
            return "/images/lieux/" + destFileName;

        } catch (IOException e) {
            // En cas d'erreur de copie, on garde le chemin original
            return sourceFilePath;
        }
    }

    private void deleteImagesByLieuId(int lieuId) {
        try (PreparedStatement ps = cnx.prepareStatement("DELETE FROM lieu_image WHERE lieu_id=?")) {
            ps.setInt(1, lieuId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // ignore si table n'existe pas
        }
    }

    /**
     * Retourne le chemin absolu vers le dossier images/lieux/.
     * Cherche d'abord dans les resources compilées, sinon fallback src/main/resources.
     */
    private static String getImagesLieuxDir() {
        try {
            java.net.URL res = LieuService.class.getResource("/images/lieux/");
            if (res != null) {
                String path = res.toExternalForm();
                if (path.startsWith("file:")) path = path.substring(5);
                // Windows: "/C:/..." -> "C:/..."
                if (path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') path = path.substring(1);
                return path;
            }
        } catch (Exception ignored) {}
        return System.getProperty("user.dir") + File.separator + "src" + File.separator
                + "main" + File.separator + "resources" + File.separator + "images" + File.separator + "lieux";
    }

    /* ===================== MAPPING ===================== */

    private Lieu map(ResultSet rs) throws SQLException {
        Lieu l = new Lieu();
        l.setId(rs.getInt("id"));
        l.setIdOffre(rs.getInt("id_offre"));

        l.setNom(rs.getString("nom"));
        l.setVille(rs.getString("ville"));
        l.setAdresse(rs.getString("adresse"));
        l.setCategorie(rs.getString("categorie"));

        l.setLatitude(getNullableDouble(rs, "latitude"));
        l.setLongitude(getNullableDouble(rs, "longitude"));

        l.setType(rs.getString("type"));
        l.setImageUrl(rs.getString("image_url"));

        l.setTelephone(rs.getString("telephone"));
        l.setSiteWeb(rs.getString("site_web"));
        l.setInstagram(rs.getString("instagram"));

        l.setDescription(rs.getString("description"));
        l.setBudgetMin(getNullableDouble(rs, "budget_min"));
        l.setBudgetMax(getNullableDouble(rs, "budget_max"));

        return l;
    }

    /* ===================== HELPERS ===================== */

    private static String timeToStr(String t) {
        if (t == null) return "";
        // MySQL TIME peut retourner "HH:mm:ss", on prend HH:mm
        if (t.length() >= 5) return t.substring(0, 5);
        return t;
    }

    private static String safeDefault(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isEmpty() ? def : t;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        if (o instanceof Double d) return d;
        if (o instanceof Float f) return (double) f;
        if (o instanceof BigDecimal bd) return bd.doubleValue();
        if (o instanceof Number n) return n.doubleValue();
        return Double.valueOf(o.toString());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.DOUBLE);
        else ps.setDouble(idx, v);
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.DECIMAL);
        else ps.setBigDecimal(idx, BigDecimal.valueOf(v));
    }
}
