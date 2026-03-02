package utils.sorties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue d'activités pour les sorties (sans dépendance "lieux"/"événements").
 * Utilisé par le front + le back pour garantir une liste cohérente.
 */
public final class SortieActivities {

    /** Valeur utilisée quand l'utilisateur choisit une activité hors catalogue. */
    public static final String OTHER = "Autre";

    /**
     * Ordre important: on garde un LinkedHashMap.
     * Chaque catégorie contient OTHER à la fin.
     */
    private static final LinkedHashMap<String, List<String>> CATALOGUE = new LinkedHashMap<>();

    static {
        CATALOGUE.put("Fun / Social", List.of(
                "Karaoké",
                "Quiz & Blind Test",
                "Soirée cinéma (à la maison)",
                "Soirée gaming (console/PC)",
                "Barbecue & Grillade",
                "Afterwork & détente",
                OTHER
        ));

        CATALOGUE.put("Sport / Outdoor", List.of(
                "Balade à vélo",
                "Sortie trottinette",
                "Balade à pied (marche)",
                "Yoga en groupe",
                "Stand-up paddle",
                OTHER
        ));

        CATALOGUE.put("Bien-être", List.of(
                "Journée Spa / Hammam",
                "Massage & relaxation",
                OTHER
        ));

        CATALOGUE.put("Apprentissage / Créatif", List.of(
                "Cours de cuisine",
                "Atelier pâtisserie",
                "Atelier peinture / dessin",
                "Atelier poterie / céramique",
                "Atelier photo",
                "Cours de danse",
                "Club de conversation (langues)",
                OTHER
        ));

        CATALOGUE.put("Sorties « Expérience »", List.of(
                "Dégustation (café / chocolat / pâtisserie)",
                "Marché + cooking à la maison",
                "Chasse au trésor / Rallye urbain",
                "Bénévolat en groupe (clean-up, association)",
                OTHER
        ));

        CATALOGUE.put("Valeur sûre « Groupe »", List.of(
                "Bowling",
                "Billard",
                "Laser game",
                "Paintball",
                OTHER
        ));
    }

    private SortieActivities() {}

    public static List<String> categories() {
        return new ArrayList<>(CATALOGUE.keySet());
    }

    public static List<String> activitiesForCategory(String category) {
        if (category == null) return List.of(OTHER);
        List<String> acts = CATALOGUE.get(category);
        return acts == null ? List.of(OTHER) : acts;
    }

    /**
     * Liste aplatie (unique) des activités (OTHER inclus), en gardant un ordre stable.
     */
    public static List<String> allActivitiesFlat() {
        LinkedHashMap<String, String> uniq = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : CATALOGUE.entrySet()) {
            for (String a : e.getValue()) {
                if (a == null) continue;
                String k = a.trim().toLowerCase();
                if (!uniq.containsKey(k)) uniq.put(k, a);
            }
        }
        return new ArrayList<>(uniq.values());
    }

    /**
     * Pour les filtres: on enlève OTHER, car en DB tu stockes le texte libre.
     */
    public static List<String> activitiesForFilter() {
        ArrayList<String> out = new ArrayList<>(allActivitiesFlat());
        out.removeIf(a -> a != null && a.equalsIgnoreCase(OTHER));
        return out;
    }

    /** Renvoie la catégorie qui contient l'activité (comparaison case-insensitive). */
    public static String findCategoryForActivity(String activity) {
        String act = (activity == null) ? "" : activity.trim();
        if (act.isEmpty()) return null;
        for (Map.Entry<String, List<String>> e : CATALOGUE.entrySet()) {
            for (String a : e.getValue()) {
                if (a != null && a.equalsIgnoreCase(act)) return e.getKey();
            }
        }
        return null;
    }

    /** Renvoie le libellé exact de l'activité dans la catégorie (case-insensitive), sinon null. */
    public static String matchActivityInCategory(String category, String activity) {
        if (category == null || activity == null) return null;
        for (String a : activitiesForCategory(category)) {
            if (a != null && a.equalsIgnoreCase(activity.trim())) return a;
        }
        return null;
    }
}
