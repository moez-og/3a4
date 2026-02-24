package services.offres;

import models.lieux.Lieu;
import models.offres.CodePromo;
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
public class CodePromoServiceTest {

    private static CodePromoService codePromoService;
    private static OffreService offreService;
    private static UserService userService;

    private static int idCodePromoTest;
    private static int idOffreTest;
    private static int idUserTest;
    private static String typeOffreValide;

    @BeforeAll
    static void setup() throws SQLException {
        codePromoService = new CodePromoService();
        offreService = new OffreService();
        userService = new UserService();

        List<Lieu> lieux = offreService.obtenirTousLesLieux();
        assumeTrue(!lieux.isEmpty(), "Aucun lieu trouvé en base pour exécuter les tests CodePromoService.");

        List<User> users = userService.obtenirTous();
        assumeTrue(!users.isEmpty(), "Aucun utilisateur trouvé en base pour exécuter les tests CodePromoService.");
        idUserTest = users.get(0).getId();

        typeOffreValide = detectValidTypeOffre();
        assumeTrue(typeOffreValide != null && !typeOffreValide.isBlank(), "Impossible de déterminer une valeur valide pour la colonne offre.type.");

        Offre offre = new Offre();
        offre.setUser_id(idUserTest);
        offre.setEvent_id(0);
        offre.setLieu_id(lieux.get(0).getId());
        offre.setTitre("TestOffreCodePromo_" + System.currentTimeMillis());
        offre.setDescription("Offre pour test code promo");
        offre.setType(typeOffreValide);
        offre.setPourcentage(20.0f);
        offre.setDate_debut(Date.valueOf(LocalDate.now()));
        offre.setDate_fin(Date.valueOf(LocalDate.now().plusDays(15)));
        offre.setStatut("active");

        offreService.ajouter(offre);
        idOffreTest = offre.getId();
        assumeTrue(idOffreTest > 0, "Impossible de créer une offre de test pour code promo.");
    }

    @Test
    @Order(1)
    void testGenererCodePromo() throws SQLException {
        CodePromo promo = codePromoService.genererOuRecupererCodePromo(idOffreTest, idUserTest);

        assertNotNull(promo);
        assertTrue(promo.getId() > 0);
        assertEquals(idOffreTest, promo.getOffre_id());
        assertTrue(promo.getQr_image_url() != null && !promo.getQr_image_url().isBlank());

        idCodePromoTest = promo.getId();
    }

    @Test
    @Order(2)
    void testModifierCodePromo() throws SQLException {
        assumeTrue(idCodePromoTest > 0, "Aucun code promo de test à modifier.");

        CodePromo promo = new CodePromo();
        promo.setId(idCodePromoTest);
        promo.setOffre_id(idOffreTest);
        promo.setUser_id(idUserTest);
        promo.setQr_image_url("https://example.com/qr-modifie.png");
        promo.setDate_generation(Date.valueOf(LocalDate.now()));
        promo.setDate_expiration(Date.valueOf(LocalDate.now().plusDays(20)));
        promo.setStatut("DESACTIVE");

        codePromoService.modifierCodePromo(promo);

        List<CodePromo> promos = codePromoService.obtenirTousCodesPromo();
        CodePromo trouve = promos.stream()
                .filter(p -> p.getId() == idCodePromoTest)
                .findFirst()
                .orElse(null);

        assertNotNull(trouve);
        assertEquals("DESACTIVE", trouve.getStatut());
        assertEquals(promo.getQr_image_url(), trouve.getQr_image_url());
    }

    @Test
    @Order(3)
    void testSupprimerCodePromo() throws SQLException {
        assumeTrue(idCodePromoTest > 0, "Aucun code promo de test à supprimer.");

        codePromoService.supprimerCodePromo(idCodePromoTest);

        List<CodePromo> promos = codePromoService.obtenirTousCodesPromo();
        assertFalse(promos.stream().anyMatch(p -> p.getId() == idCodePromoTest));
        idCodePromoTest = 0;
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (idCodePromoTest > 0) {
            codePromoService.supprimerCodePromo(idCodePromoTest);
        }
        if (idOffreTest > 0) {
            offreService.supprimer(idOffreTest);
        }
    }

    private static String detectValidTypeOffre() throws SQLException {
        List<Offre> existing = offreService.obtenirToutes();
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
