package services.offres;

import models.lieux.Lieu;
import models.offres.Offre;
import models.users.User;
import org.junit.jupiter.api.*;
import services.users.UserService;
import utils.Mydb;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OffreServiceTest {

    private static OffreService service;
    private static UserService userService;
    private static int idOffreTest;
    private static int lieuIdTest;
    private static int userIdTest;
    private static String typeOffreValide;

    @BeforeAll
    static void setup() throws SQLException {
        service = new OffreService();
        userService = new UserService();

        List<User> users = userService.obtenirTous();
        assumeTrue(!users.isEmpty(), "Aucun utilisateur trouvé en base pour exécuter les tests OffreService.");
        userIdTest = users.get(0).getId();

        List<Lieu> lieux = service.obtenirTousLesLieux();
        assumeTrue(!lieux.isEmpty(), "Aucun lieu trouvé en base pour exécuter les tests OffreService.");
        lieuIdTest = lieux.get(0).getId();

        typeOffreValide = detectValidTypeOffre();
        assumeTrue(typeOffreValide != null && !typeOffreValide.isBlank(), "Impossible de déterminer une valeur valide pour la colonne offre.type.");
    }

    @Test
    @Order(1)
    void testAjouterOffre() throws SQLException {
        Offre offre = new Offre();
        offre.setUser_id(userIdTest);
        offre.setEvent_id(0);
        offre.setLieu_id(lieuIdTest);
        offre.setTitre("TestOffre_" + System.currentTimeMillis());
        offre.setDescription("Description test offre");
        offre.setType(typeOffreValide);
        offre.setPourcentage(15.0f);
        offre.setDate_debut(Date.valueOf(LocalDate.now()));
        offre.setDate_fin(Date.valueOf(LocalDate.now().plusDays(7)));
        offre.setStatut("active");

        service.ajouter(offre);
        assertTrue(offre.getId() > 0);

        List<Offre> offres = service.obtenirToutes();
        assertTrue(offres.stream().anyMatch(o -> o.getId() == offre.getId()));

        idOffreTest = offre.getId();
    }

    @Test
    @Order(2)
    void testModifierOffre() throws SQLException {
        assumeTrue(idOffreTest > 0, "Aucune offre de test à modifier.");

        Offre offre = new Offre();
        offre.setId(idOffreTest);
        offre.setUser_id(userIdTest);
        offre.setEvent_id(0);
        offre.setLieu_id(lieuIdTest);
        offre.setTitre("OffreModifiee_" + System.currentTimeMillis());
        offre.setDescription("Description modifiée");
        offre.setType(typeOffreValide);
        offre.setPourcentage(25.0f);
        offre.setDate_debut(Date.valueOf(LocalDate.now()));
        offre.setDate_fin(Date.valueOf(LocalDate.now().plusDays(10)));
        offre.setStatut("active");

        service.modifier(offre);

        List<Offre> offres = service.obtenirToutes();
        Offre trouvee = offres.stream()
                .filter(o -> o.getId() == idOffreTest)
                .findFirst()
                .orElse(null);

        assertNotNull(trouvee);
        assertEquals(offre.getTitre(), trouvee.getTitre());
        assertTrue(trouvee.getStatut() != null && trouvee.getStatut().equalsIgnoreCase(offre.getStatut()));
    }

    @Test
    @Order(3)
    void testSupprimerOffre() throws SQLException {
        assumeTrue(idOffreTest > 0, "Aucune offre de test à supprimer.");

        service.supprimer(idOffreTest);

        List<Offre> offres = service.obtenirToutes();
        assertFalse(offres.stream().anyMatch(o -> o.getId() == idOffreTest));
        idOffreTest = 0;
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (idOffreTest > 0) {
            service.supprimer(idOffreTest);
        }
    }

    private static String detectValidTypeOffre() throws SQLException {
        List<Offre> existing = service.obtenirToutes();
        for (Offre offre : existing) {
            if (offre.getType() != null && !offre.getType().isBlank()) {
                return offre.getType();
            }
        }

        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'offre' AND COLUMN_NAME = 'type'";
        try (PreparedStatement ps = Mydb.getInstance().getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String columnType = rs.getString("COLUMN_TYPE");
                if (columnType != null && columnType.startsWith("enum(")) {
                    int firstQuote = columnType.indexOf('\'');
                    int secondQuote = columnType.indexOf('\'', firstQuote + 1);
                    if (firstQuote >= 0 && secondQuote > firstQuote) {
                        return columnType.substring(firstQuote + 1, secondQuote);
                    }
                }
            }
        }
        return null;
    }
}
