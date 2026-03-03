package services.lieux;

import models.lieux.Lieu;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import utils.TestDbUtils;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class EvaluationLieuServiceTest {

    static Connection cnx;

    static LieuService lieuService;
    static EvaluationLieuService evalService;

    static int userId;
    static int offreId;
    static int lieuId;

    // ✅ valeur "safe" (évite Data truncated)
    private static final String CATEGORIE_OK = "MUSEE";

    @BeforeAll
    static void setup() throws Exception {
        cnx = TestDbUtils.cnx();

        lieuService = new LieuService();
        evalService = new EvaluationLieuService();

        userId = TestDbUtils.ensureUser(cnx);
        offreId = TestDbUtils.createOffre(cnx, userId);

        // créer un lieu test
        String nom = "LieuEvalTest_" + System.currentTimeMillis();

        Lieu l = new Lieu();
        l.setIdOffre(offreId);
        l.setNom(nom);
        l.setVille("Tunis");
        l.setAdresse("Adresse");
        l.setCategorie(CATEGORIE_OK); // ✅ FIX (au lieu de "Parc")
        l.setType("PUBLIC");
        l.setImageUrl(null);

        l.setLatitude(null);
        l.setLongitude(null);
        l.setTelephone(null);
        l.setSiteWeb(null);
        l.setInstagram(null);
        l.setDescription("desc");
        l.setBudgetMin(null);
        l.setBudgetMax(null);

        lieuService.add(l);

        lieuId = lieuService.getAll().stream()
                .filter(x -> nom.equals(x.getNom()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Lieu test non créé"))
                .getId();
    }

    @AfterAll
    static void tearDown() {
        try { evalService.deleteByLieuUser(lieuId, userId); } catch (Exception ignored) {}
        try { lieuService.delete(lieuId); } catch (Exception ignored) {}
        try { TestDbUtils.deleteById(cnx, "offre", offreId); } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    void testUpsertAndGetByLieuId() {
        evalService.upsert(lieuId, userId, 4, "Bon lieu");

        var list = evalService.getByLieuId(lieuId);
        assertFalse(list.isEmpty(), "La liste des évaluations doit contenir au moins une évaluation.");

        var ev = list.stream()
                .filter(x -> x.getUserId() == userId)
                .findFirst()
                .orElse(null);

        assertNotNull(ev, "L'évaluation de l'utilisateur test doit exister.");
        assertEquals(4, ev.getNote());
        assertEquals("Bon lieu", ev.getCommentaire());
    }

    @Test
    @Order(2)
    void testAvgAndCount() {
        evalService.upsert(lieuId, userId, 5, "Excellent");

        int count = evalService.countByLieuId(lieuId);
        assertTrue(count >= 1, "countByLieuId doit être >= 1.");

        double avg = evalService.avgNote(lieuId);
        assertTrue(avg > 0.0, "avgNote doit être > 0.");
    }

    @Test
    @Order(3)
    void testDeleteEvaluation() {
        evalService.upsert(lieuId, userId, 3, "ok");
        evalService.deleteByLieuUser(lieuId, userId);

        var list = evalService.getByLieuId(lieuId);
        boolean exists = list.stream().anyMatch(x -> x.getUserId() == userId);

        assertFalse(exists, "Après delete, l'évaluation ne doit plus exister.");
    }
}
