package services.lieux;

import models.lieux.Lieu;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import utils.TestDbUtils;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class LieuServiceTest {

    static Connection cnx;
    static LieuService service;

    static int userId;
    static int offreId;

    static int lieuIdTest = -1;
    static String nomTest;

    // ✅ valeur "safe" (compatible avec ton schéma: on a vu MUSEE dans le retour)
    private static final String CATEGORIE_OK = "MUSEE";

    @BeforeAll
    static void setup() throws Exception {
        cnx = TestDbUtils.cnx();
        service = new LieuService();

        userId = TestDbUtils.ensureUser(cnx);
        offreId = TestDbUtils.createOffre(cnx, userId);

        nomTest = "LieuTest_" + System.currentTimeMillis();
    }

    @AfterAll
    static void tearDown() {
        try {
            if (lieuIdTest > 0) service.delete(lieuIdTest);
        } catch (Exception ignored) {}

        try {
            if (offreId > 0) TestDbUtils.deleteById(cnx, "offre", offreId);
        } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    void testAddLieu() {
        Lieu l = new Lieu();
        l.setIdOffre(offreId);
        l.setNom(nomTest);
        l.setVille("Tunis");
        l.setAdresse("Adresse test");
        l.setCategorie(CATEGORIE_OK); // ✅ FIX
        l.setLatitude(null);
        l.setLongitude(null);
        l.setType("PUBLIC");
        l.setImageUrl(null);

        l.setTelephone(null);
        l.setSiteWeb(null);
        l.setInstagram(null);
        l.setDescription("desc test");
        l.setBudgetMin(10.0);
        l.setBudgetMax(50.0);

        service.add(l);

        List<Lieu> all = service.getAll();
        assertFalse(all.isEmpty(), "La liste des lieux ne doit pas être vide après insertion.");

        Lieu created = all.stream()
                .filter(x -> nomTest.equals(x.getNom()))
                .findFirst()
                .orElse(null);

        assertNotNull(created, "Le lieu ajouté doit exister en base.");
        lieuIdTest = created.getId();
        assertTrue(lieuIdTest > 0, "L'id du lieu doit être généré (>0).");
        assertEquals("Tunis", created.getVille());
        assertEquals(CATEGORIE_OK, created.getCategorie()); // ✅ cohérence
    }

    @Test
    @Order(2)
    void testUpdateLieu() {
        assertTrue(lieuIdTest > 0, "Le test update dépend du test add.");

        Lieu l = service.getById(lieuIdTest);
        assertNotNull(l, "getById doit retourner un lieu existant.");

        l.setNom(nomTest + "_MOD");
        l.setVille("Sousse");
        l.setCategorie(CATEGORIE_OK); // ✅ FIX: au lieu de "Musée"
        l.setBudgetMax(99.0);

        service.update(l);

        Lieu updated = service.getById(lieuIdTest);
        assertNotNull(updated);
        assertEquals(nomTest + "_MOD", updated.getNom());
        assertEquals("Sousse", updated.getVille());

        // ✅ FIX: la DB/service renvoie en MAJUSCULE
        assertEquals(CATEGORIE_OK, updated.getCategorie());

        assertEquals(99.0, updated.getBudgetMax());
    }

    @Test
    @Order(3)
    void testDeleteLieu() {
        assertTrue(lieuIdTest > 0, "Le test delete dépend du test add.");

        service.delete(lieuIdTest);

        Lieu deleted = service.getById(lieuIdTest);
        assertNull(deleted, "Après suppression, getById doit retourner null.");

        lieuIdTest = -1;
    }
}
