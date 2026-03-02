package services.sorties;

import models.sorties.ParticipationSortie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import utils.TestDbUtils;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class ParticipationSortieServiceTest {

    static Connection cnx;
    static AnnonceSortieService annonceService;
    static ParticipationSortieService participationService;

    static int ownerId;
    static int participantId;
    static int annonceId;

    static int participationId = -1;

    @BeforeAll
    static void setup() throws Exception {
        cnx = TestDbUtils.cnx();
        annonceService = new AnnonceSortieService();
        participationService = new ParticipationSortieService();

        ownerId = TestDbUtils.ensureUser(cnx);
        participantId = TestDbUtils.ensureUserOtherThan(cnx, ownerId);

        annonceId = TestDbUtils.createAnnonceSortie(cnx, ownerId);
    }

    @AfterAll
    static void tearDown() {
        try {
            if (participationId > 0) participationService.deleteById(participationId);
        } catch (Exception ignored) {}

        try {
            if (annonceId > 0) annonceService.delete(annonceId);
        } catch (Exception ignored) {}
    }

    @Test
    @Order(1)
    void testAddRequestAndGetByAnnonceAndUser() {
        ParticipationSortie p = new ParticipationSortie();
        p.setAnnonceId(annonceId);
        p.setUserId(participantId);
        p.setStatut("EN_ATTENTE");
        p.setContactPrefer("EMAIL");
        p.setContactValue("participant_" + System.currentTimeMillis() + "@example.com");
        p.setCommentaire("Je veux participer");
        // Certains schémas imposent nb_places=1 (constraint/trigger/default).
        p.setNbPlaces(1);
        p.setReponses(List.of("rep1"));

        participationService.addRequest(p);

        ParticipationSortie created = participationService.getByAnnonceAndUser(annonceId, participantId);
        assertNotNull(created);
        assertEquals("EN_ATTENTE", created.getStatut());
        assertEquals(1, created.getNbPlaces());
        participationId = created.getId();
        assertTrue(participationId > 0);
    }

    @Test
    @Order(2)
    void testUpdatePendingRequest() {
        assertTrue(participationId > 0, "Le test update dépend du test add.");

        ParticipationSortie upd = new ParticipationSortie();
        upd.setId(participationId);
        upd.setContactPrefer("EMAIL");
        upd.setContactValue("updated_" + System.currentTimeMillis() + "@example.com");
        upd.setCommentaire("Commentaire modifié");
        upd.setNbPlaces(1);
        upd.setReponses(List.of("rep1_mod"));

        participationService.updatePendingRequest(upd);

        ParticipationSortie after = participationService.getByAnnonceAndUser(annonceId, participantId);
        assertNotNull(after);
        assertEquals("Commentaire modifié", after.getCommentaire());
        assertEquals(1, after.getNbPlaces());
        assertNotNull(after.getReponses());
        assertFalse(after.getReponses().isEmpty());
    }

    @Test
    @Order(3)
    void testUpdateStatusAndCountAcceptedPlaces() {
        assertTrue(participationId > 0);

        participationService.updateStatus(participationId, "CONFIRMEE");

        int acceptedPlaces = participationService.countAcceptedPlaces(annonceId);
        assertTrue(acceptedPlaces >= 1, "Après CONFIRMEE, les places acceptées doivent être >= 1");

        long confirmed = participationService.countByStatuses("CONFIRMEE", "ACCEPTEE");
        assertTrue(confirmed >= 1);
    }

    @Test
    @Order(4)
    void testDeleteById() {
        assertTrue(participationId > 0);

        participationService.deleteById(participationId);
        assertNull(participationService.getByAnnonceAndUser(annonceId, participantId));

        participationId = -1;
    }
}
