package services.sorties;

import models.sorties.AnnonceSortie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import utils.TestDbUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class AnnonceSortieServiceTest {

    static Connection cnx;
    static AnnonceSortieService service;

    static int ownerId;
    static int annonceId = -1;
    static String titre;

    @BeforeAll
    static void setup() throws Exception {
        cnx = TestDbUtils.cnx();
        service = new AnnonceSortieService();
        ownerId = TestDbUtils.ensureUser(cnx);
        titre = "SortieSvcTest_" + System.currentTimeMillis();
    }

    @AfterAll
    static void tearDown() {
        try {
            if (annonceId > 0) service.delete(annonceId);
        } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    void testAddAndGetById() {
        AnnonceSortie a = new AnnonceSortie();
        a.setUserId(ownerId);
        a.setTitre(titre);
        a.setDescription("desc test");
        a.setVille("Tunis");
        a.setLieuTexte("Centre");
        a.setPointRencontre("Point A");
        a.setTypeActivite("Marche");
        a.setDateSortie(LocalDateTime.now().plusDays(1));
        a.setBudgetMax(0);
        a.setNbPlaces(5);
        a.setImageUrl(null);
        a.setStatut("OUVERTE");
        a.setQuestions(List.of());

        service.add(a);

        var created = service.getAll().stream()
                .filter(x -> titre.equals(x.getTitre()))
                .findFirst()
                .orElse(null);

        assertNotNull(created, "L'annonce ajoutée doit exister en base.");
        annonceId = created.getId();
        assertTrue(annonceId > 0);

        AnnonceSortie byId = service.getById(annonceId);
        assertNotNull(byId);
        assertEquals(titre, byId.getTitre());
        assertEquals("OUVERTE", byId.getStatut());
    }

    @Test
    @Order(2)
    void testUpdate() {
        assertTrue(annonceId > 0, "Le test update dépend du test add.");

        AnnonceSortie a = service.getById(annonceId);
        assertNotNull(a);

        a.setTitre(titre + "_MOD");
        a.setVille("Sousse");
        a.setNbPlaces(8);
        service.update(a);

        AnnonceSortie updated = service.getById(annonceId);
        assertNotNull(updated);
        assertEquals(titre + "_MOD", updated.getTitre());
        assertEquals("Sousse", updated.getVille());
        assertEquals(8, updated.getNbPlaces());
    }

    @Test
    @Order(3)
    void testDelete() {
        assertTrue(annonceId > 0, "Le test delete dépend du test add.");

        service.delete(annonceId);
        assertNull(service.getById(annonceId));

        annonceId = -1;
    }
}
