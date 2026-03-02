package services.lieux;

import java.util.*;

/**
 * Service de suggestions automatiques d'avis.
 * Retourne 3 suggestions selon la note (1-5) et la catégorie du lieu.
 */
public class AvisSuggestionService {

    // ============================================================
    //  STRUCTURE : Map<categorie_normalisee, Map<note, List<suggestions>>>
    // ============================================================

    private static final Map<String, Map<Integer, List<String>>> SUGGESTIONS = new LinkedHashMap<>();

    static {

        // ── RESTAURANT ──────────────────────────────────────────
        Map<Integer, List<String>> resto = new HashMap<>();
        resto.put(1, Arrays.asList(
            "La nourriture était froide et sans saveur, une expérience vraiment décevante.",
            "Service très lent et personnel peu aimable, je ne reviendrai pas.",
            "Rapport qualité-prix catastrophique, les plats ne correspondaient pas à la carte."
        ));
        resto.put(2, Arrays.asList(
            "Cuisine correcte mais sans originalité, on s'attendait à mieux.",
            "Accueil moyen et attente trop longue pour des plats ordinaires.",
            "Quelques plats réussis mais l'ensemble manque de régularité et de soin."
        ));
        resto.put(3, Arrays.asList(
            "Restaurant agréable avec une cuisine honnête, sans grande surprise.",
            "Bon accueil et service correct, la cuisine est satisfaisante dans l'ensemble.",
            "Une adresse convenable pour un repas simple, rien d'exceptionnel mais agréable."
        ));
        resto.put(4, Arrays.asList(
            "Excellente cuisine, les saveurs sont bien dosées et les produits frais.",
            "Personnel attentionné et plats généreux, une belle découverte gastronomique.",
            "Cadre chaleureux et menu varié, nous avons passé un très bon moment."
        ));
        resto.put(5, Arrays.asList(
            "Un vrai coup de cœur ! Cuisine raffinée, service impeccable, à recommander absolument.",
            "Les meilleures saveurs de la ville, chaque plat est une expérience en soi.",
            "Accueil exceptionnel, ambiance parfaite et nourriture délicieuse — on y retourne très vite !"
        ));
        SUGGESTIONS.put("restaurant", resto);

        // ── CAFÉ ────────────────────────────────────────────────
        Map<Integer, List<String>> cafe = new HashMap<>();
        cafe.put(1, Arrays.asList(
            "Café insipide et ambiance froide, on ne s'y sent pas du tout à l'aise.",
            "Service très lent, le personnel ignorait les clients pendant de longues minutes.",
            "Lieu bruyant, mal entretenu et prix excessifs pour la qualité proposée."
        ));
        cafe.put(2, Arrays.asList(
            "Café correct mais sans charme particulier, la déco mériterait d'être rafraîchie.",
            "Accueil peu chaleureux et boissons tièdes, on attendait mieux.",
            "L'endroit est fonctionnel mais manque d'âme et de convivialité."
        ));
        cafe.put(3, Arrays.asList(
            "Café sympa pour une pause rapide, ambiance correcte sans être exceptionnelle.",
            "Bon endroit pour travailler ou lire, connexion Wi-Fi stable et personnel poli.",
            "Boissons de qualité correcte, cadre agréable pour se retrouver entre amis."
        ));
        cafe.put(4, Arrays.asList(
            "Très bon café avec une ambiance cosy, parfait pour une pause détente.",
            "Personnel souriant, boissons excellentes et cadre décoré avec goût.",
            "Endroit idéal pour travailler ou discuter tranquillement, on s'y sent bien."
        ));
        cafe.put(5, Arrays.asList(
            "Le meilleur café de la ville ! Ambiance parfaite, service rapide et boissons délicieuses.",
            "Un endroit magique, on s'y sent immédiatement à l'aise, le café est exceptionnel.",
            "Coup de cœur total ! Déco soignée, personnel adorable et chaque visite est un plaisir."
        ));
        SUGGESTIONS.put("café", cafe);
        SUGGESTIONS.put("cafe", cafe);

        // ── HÔTEL ───────────────────────────────────────────────
        Map<Integer, List<String>> hotel = new HashMap<>();
        hotel.put(1, Arrays.asList(
            "Chambre sale et mal entretenue, rien ne correspondait aux photos de l'annonce.",
            "Personnel indifférent, chambre bruyante et petit-déjeuner de mauvaise qualité.",
            "Une expérience horrible, problèmes de plomberie et climatisation en panne toute la nuit."
        ));
        hotel.put(2, Arrays.asList(
            "Hôtel correct mais vieillissant, la chambre manquait de propreté et de confort.",
            "Accueil froid, literie inconfortable et services de base insuffisants.",
            "Rapport qualité-prix décevant, on s'attendait à beaucoup mieux pour le prix payé."
        ));
        hotel.put(3, Arrays.asList(
            "Séjour correct dans l'ensemble, chambre propre mais sans cachet particulier.",
            "Hôtel fonctionnel avec un personnel poli, idéal pour un court séjour d'affaires.",
            "Bon emplacement et chambres convenables, le petit-déjeuner est correct."
        ));
        hotel.put(4, Arrays.asList(
            "Très bon séjour, chambre confortable, propreté irréprochable et personnel attentionné.",
            "Hôtel très agréable avec un beau cadre, les prestations sont à la hauteur du prix.",
            "Excellent accueil, chambre spacieuse et bien équipée, nous y retournerons."
        ));
        hotel.put(5, Arrays.asList(
            "Séjour parfait ! Chambre luxueuse, service 5 étoiles et petit-déjeuner copieux et délicieux.",
            "Un hôtel d'exception, chaque détail est soigné, le personnel anticipe chaque besoin.",
            "La meilleure expérience hôtelière que j'aie vécue, on s'y sent comme un roi !"
        ));
        SUGGESTIONS.put("hôtel", hotel);
        SUGGESTIONS.put("hotel", hotel);

        // ── MUSÉE ───────────────────────────────────────────────
        Map<Integer, List<String>> musee = new HashMap<>();
        musee.put(1, Arrays.asList(
            "Collections mal mises en valeur et panneaux explicatifs quasi inexistants.",
            "Lieu peu entretenu, éclairage insuffisant et personnel peu disponible pour guider.",
            "Visite décevante, les expositions sont vieillissantes et sans intérêt pédagogique."
        ));
        musee.put(2, Arrays.asList(
            "Musée correct mais les collections gagneraient à être mieux présentées.",
            "Quelques pièces intéressantes mais l'organisation de la visite manque de clarté.",
            "Entrée abordable mais l'expérience globale reste en deçà des attentes."
        ));
        musee.put(3, Arrays.asList(
            "Visite agréable avec des collections variées, idéale pour une sortie culturelle.",
            "Musée bien organisé avec des explications claires, parfait pour les familles.",
            "Bon musée pour découvrir l'histoire locale, quelques expositions vraiment captivantes."
        ));
        musee.put(4, Arrays.asList(
            "Très beau musée avec des collections remarquables et une mise en scène soignée.",
            "Visite très enrichissante, les guides sont passionnés et les œuvres magnifiques.",
            "Lieu culturel incontournable, bien entretenu et accessible à tous les publics."
        ));
        musee.put(5, Arrays.asList(
            "Musée exceptionnel ! Collections fascinantes, scénographie moderne et guides passionnants.",
            "Une visite inoubliable, chaque salle réserve une surprise, à faire absolument.",
            "Le must culturel de la région, nous y sommes retournés deux fois tellement c'est riche."
        ));
        SUGGESTIONS.put("musée", musee);
        SUGGESTIONS.put("musee", musee);

        // ── PARC / ESPACE VERT ──────────────────────────────────
        Map<Integer, List<String>> parc = new HashMap<>();
        parc.put(1, Arrays.asList(
            "Parc mal entretenu, poubelles débordantes et espaces verts à l'abandon.",
            "Aucune infrastructure pour les enfants, bancs cassés et allées dégradées.",
            "Lieu bruyant et peu sécurisé, on ne peut pas s'y promener tranquillement."
        ));
        parc.put(2, Arrays.asList(
            "Parc correct mais manque d'entretien régulier, la végétation est négligée.",
            "Quelques espaces agréables mais les installations pour enfants sont vétustes.",
            "Endroit accessible mais sans grand charme, mériterait plus d'investissement."
        ));
        parc.put(3, Arrays.asList(
            "Parc agréable pour une promenade, espaces verts bien tenus et atmosphère calme.",
            "Bon endroit pour se ressourcer, les allées sont propres et ombragées.",
            "Espace de détente convenable, idéal pour un pique-nique en famille."
        ));
        parc.put(4, Arrays.asList(
            "Très beau parc, la végétation est luxuriante et l'ambiance très apaisante.",
            "Endroit magnifique pour se promener, bien entretenu et sécurisé pour les enfants.",
            "Parc avec beaucoup de charme, parfait pour faire du sport ou se relaxer."
        ));
        parc.put(5, Arrays.asList(
            "Un havre de paix absolu ! Végétation magnifique, propreté exemplaire et atmosphère unique.",
            "Le plus beau parc de la ville, chaque saison lui donne un nouveau visage splendide.",
            "Endroit paradisiaque, on oublie la ville en y entrant, à visiter sans modération."
        ));
        SUGGESTIONS.put("parc", parc);

        // ── MONUMENT / SITE HISTORIQUE ──────────────────────────
        Map<Integer, List<String>> monument = new HashMap<>();
        monument.put(1, Arrays.asList(
            "Site mal entretenu, accès difficile et aucune information historique disponible.",
            "Lieu décevant, la restauration a dénaturé le monument d'origine.",
            "Peu d'intérêt si on n'est pas accompagné d'un guide, le site manque de mise en valeur."
        ));
        monument.put(2, Arrays.asList(
            "Monument intéressant mais la visite est mal balisée et peu informative.",
            "Site historique avec du potentiel mais qui mériterait une meilleure conservation.",
            "Accès correct, quelques panneaux explicatifs mais l'ensemble reste insuffisant."
        ));
        monument.put(3, Arrays.asList(
            "Visite sympa, le monument est bien conservé et offre un aperçu de l'histoire locale.",
            "Site intéressant à découvrir, quelques informations disponibles pour les visiteurs.",
            "Bon endroit pour les amateurs d'histoire, l'ambiance est authentique."
        ));
        monument.put(4, Arrays.asList(
            "Très beau monument, parfaitement conservé avec une mise en valeur soignée.",
            "Site historique impressionnant, les guides sont compétents et passionnants.",
            "Visite très enrichissante, le lieu dégage une atmosphère historique saisissante."
        ));
        monument.put(5, Arrays.asList(
            "Un trésor historique absolu ! Architecture époustouflante et visite guidée exceptionnelle.",
            "Site incontournable, on reste bouche bée devant la beauté et la richesse historique.",
            "Une expérience unique qui marque à vie, ce monument est une fierté pour la région."
        ));
        SUGGESTIONS.put("monument", monument);
        SUGGESTIONS.put("site historique", monument);
        SUGGESTIONS.put("patrimoine", monument);

        // ── PLAGE ───────────────────────────────────────────────
        Map<Integer, List<String>> plage = new HashMap<>();
        plage.put(1, Arrays.asList(
            "Plage sale et surpeuplée, l'eau n'était pas propre à la baignade.",
            "Aucune infrastructure, plage envahie de déchets et sans surveillance.",
            "Expérience désagréable, le sable était brûlant et les services inexistants."
        ));
        plage.put(2, Arrays.asList(
            "Plage correcte mais manque de propreté et d'espaces ombragés.",
            "Eau de mer agréable mais la plage est trop fréquentée en haute saison.",
            "Quelques bons moments mais les installations sanitaires laissent à désirer."
        ));
        plage.put(3, Arrays.asList(
            "Plage agréable avec un beau sable fin, idéale pour une journée en famille.",
            "Bon endroit pour se baigner, eau claire et ambiance sympathique.",
            "Plage bien fréquentée avec des services corrects et un beau panorama."
        ));
        plage.put(4, Arrays.asList(
            "Très belle plage, eaux cristallines et sable blanc d'une propreté irréprochable.",
            "Endroit paradisiaque pour se détendre, les couchers de soleil y sont magnifiques.",
            "Plage avec un cadre naturel exceptionnel, parfaite pour des vacances reposantes."
        ));
        plage.put(5, Arrays.asList(
            "La plus belle plage que j'aie jamais vue ! Eau turquoise et sable immaculé à couper le souffle.",
            "Un coin de paradis absolu, on ne veut plus repartir tellement c'est magnifique.",
            "Plage de rêve avec une nature préservée, chaque moment passé ici est inoubliable."
        ));
        SUGGESTIONS.put("plage", plage);

        // ── SHOPPING / CENTRE COMMERCIAL ────────────────────────
        Map<Integer, List<String>> shopping = new HashMap<>();
        shopping.put(1, Arrays.asList(
            "Centre commercial vétuste, boutiques peu intéressantes et parking insuffisant.",
            "Expérience décevante, les prix sont excessifs et le service client inexistant.",
            "Lieu bondé et mal organisé, on ne s'y retrouve pas et l'ambiance est étouffante."
        ));
        shopping.put(2, Arrays.asList(
            "Centre correct mais le choix de boutiques reste limité pour les besoins.",
            "Quelques bonnes enseignes mais l'ensemble manque de dynamisme et d'originalité.",
            "Accessible et pratique, mais sans grand intérêt comparé à d'autres centres."
        ));
        shopping.put(3, Arrays.asList(
            "Centre commercial agréable avec un bon choix de boutiques pour faire ses courses.",
            "Endroit pratique pour faire du shopping, bien situé et facile d'accès.",
            "Bon rapport entre les enseignes disponibles, idéal pour une sortie shopping en famille."
        ));
        shopping.put(4, Arrays.asList(
            "Très beau centre commercial, grand choix de boutiques et ambiance agréable.",
            "Excellent endroit pour faire du shopping, les offres sont variées et les prix compétitifs.",
            "Centre bien organisé avec des espaces de restauration sympathiques et des boutiques tendance."
        ));
        shopping.put(5, Arrays.asList(
            "Le meilleur centre commercial de la région ! Boutiques de qualité et expérience client parfaite.",
            "Shopping de rêve, on trouve absolument tout avec un service exceptionnel partout.",
            "Centre commercial exceptionnel, une journée ne suffit pas pour tout explorer !"
        ));
        SUGGESTIONS.put("shopping", shopping);
        SUGGESTIONS.put("centre commercial", shopping);

        // ── DÉFAUT (toutes catégories non reconnues) ─────────────
        Map<Integer, List<String>> defaut = new HashMap<>();
        defaut.put(1, Arrays.asList(
            "Lieu très décevant, ne correspond pas du tout aux attentes ni à la description.",
            "Mauvaise expérience globale, je ne recommande pas cet endroit.",
            "Visite ratée, les conditions d'accueil et la qualité du lieu sont insuffisantes."
        ));
        defaut.put(2, Arrays.asList(
            "Endroit correct mais en dessous des espérances, beaucoup de progrès à faire.",
            "Quelques points positifs mais l'ensemble reste décevant pour le prix et l'effort.",
            "Lieu fonctionnel mais sans âme, on est passé à côté de l'expérience attendue."
        ));
        defaut.put(3, Arrays.asList(
            "Lieu agréable dans l'ensemble, une expérience correcte sans être mémorable.",
            "Endroit convenable pour une visite, personnel poli et cadre correct.",
            "Bonne expérience globale, quelques petits bémols mais rien de rédhibitoire."
        ));
        defaut.put(4, Arrays.asList(
            "Très bonne expérience, lieu bien entretenu avec un accueil de qualité.",
            "Endroit vraiment agréable, on passe un excellent moment et on repart satisfait.",
            "Lieu à recommander, les prestations sont à la hauteur et l'ambiance est au rendez-vous."
        ));
        defaut.put(5, Arrays.asList(
            "Expérience absolument parfaite, un lieu exceptionnel qu'on ne peut qu'adorer !",
            "Coup de cœur total, tout était parfait du début à la fin, on y retourne sans hésiter.",
            "Le meilleur endroit que j'aie visité, chaque détail témoigne d'un soin exceptionnel."
        ));
        SUGGESTIONS.put("__default__", defaut);
    }

    // ============================================================
    //  MÉTHODE PRINCIPALE
    // ============================================================

    /**
     * Retourne 3 suggestions d'avis selon la note et la catégorie du lieu.
     *
     * @param note      Note de 1 à 5
     * @param categorie Catégorie du lieu (ex: "Restaurant", "Hôtel"...)
     * @return Liste de 3 suggestions (jamais null, jamais vide)
     */
    public List<String> getSuggestions(int note, String categorie) {
        int n = Math.max(1, Math.min(5, note));

        // Normaliser la catégorie : minuscules + trim
        String cat = categorie == null ? "" : categorie.trim().toLowerCase(Locale.ROOT)
            .replace("é","e").replace("è","e").replace("ê","e")
            .replace("ô","o").replace("î","i").replace("û","u")
            .replace("à","a").replace("â","a").replace("ç","c")
            .replace("oe","o");

        // Chercher une correspondance exacte d'abord
        Map<Integer, List<String>> byCat = SUGGESTIONS.get(cat);

        // Si pas trouvé, chercher une correspondance partielle
        if (byCat == null) {
            for (Map.Entry<String, Map<Integer, List<String>>> entry : SUGGESTIONS.entrySet()) {
                if (!entry.getKey().equals("__default__") && cat.contains(entry.getKey())) {
                    byCat = entry.getValue();
                    break;
                }
            }
        }

        // Fallback : suggestions génériques
        if (byCat == null) {
            byCat = SUGGESTIONS.get("__default__");
        }

        List<String> suggestions = byCat.get(n);
        return suggestions != null ? suggestions : SUGGESTIONS.get("__default__").get(n);
    }
}
