package utils.geo;

import java.text.Normalizer;
import java.util.*;

public final class TunisiaGeo {

    private TunisiaGeo() {}

    public static final String REGION_OTHER = "Autre…";

    public static List<String> villes() {
        return List.of(
                "Tunis", "Ariana", "Ben Arous", "Manouba",
                "Nabeul", "Zaghouan", "Bizerte", "Béja", "Jendouba", "Le Kef", "Siliana",
                "Sousse", "Monastir", "Mahdia", "Kairouan", "Kasserine", "Sidi Bouzid",
                "Sfax", "Gabès", "Medenine", "Tataouine",
                "Gafsa", "Tozeur", "Kébili"
        );
    }

    public static List<String> regionsForVille(String ville) {
        if (ville == null || ville.trim().isEmpty()) {
            ArrayList<String> out = new ArrayList<>();
            out.add(REGION_OTHER);
            return out;
        }

        // Tolère accents / espaces / variantes ("Beja" vs "Béja", "Kebili" vs "Kébili", ...)
        String key = norm(ville);
        List<String> base = MAP_BY_NORM.getOrDefault(key, List.of());
        ArrayList<String> out = new ArrayList<>(base);
        out.add(REGION_OTHER);
        return out;
    }

    /**
     * Trouve la valeur exacte à sélectionner dans un ComboBox (tolère accents / casse / espaces).
     */
    public static String matchVilleForSelection(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String key = norm(input);
        for (String v : villes()) {
            if (norm(v).equals(key)) return v;
        }
        return null;
    }

    // Gouvernorat -> Délégations / zones (liste large, editable facilement)
    public static final Map<String, List<String>> MAP = build();

    // Lookup normalisé (clé sans accents/casse)
    private static final Map<String, List<String>> MAP_BY_NORM = buildNormalizedLookup();

    private static Map<String, List<String>> buildNormalizedLookup() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : MAP.entrySet()) {
            out.put(norm(e.getKey()), e.getValue());
        }

        // Aliases fréquents (orthographes sans accents)
        if (MAP.containsKey("Béja")) out.put(norm("Beja"), MAP.get("Béja"));
        if (MAP.containsKey("Kébili")) out.put(norm("Kebili"), MAP.get("Kébili"));
        if (MAP.containsKey("Gabès")) out.put(norm("Gabes"), MAP.get("Gabès"));
        if (MAP.containsKey("Medenine")) out.put(norm("Médenine"), MAP.get("Medenine"));

        // Variantes espaces
        if (MAP.containsKey("Sidi Bouzid")) out.put(norm("Sidi Bou Zid"), MAP.get("Sidi Bouzid"));

        return out;
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replace('’', '\'');
        n = n.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return n;
    }

    private static Map<String, List<String>> build() {
        Map<String, List<String>> m = new LinkedHashMap<>();

        // TUNIS
        m.put("Tunis", List.of(
                "Bab Bhar", "Bab Souika", "Carthage", "Cité El Khadra", "Djebel Jelloud",
                "El Kram", "El Menzah", "El Omrane", "El Omrane Supérieur", "Ettahrir",
                "Ezzouhour", "Hrairia", "La Goulette", "La Marsa", "Le Bardo", "Médina",
                "Séjoumi", "Sidi El Bechir", "Sidi Hassine"
        ));

        // ARIANA
        m.put("Ariana", List.of(
                "Ariana Ville", "Ettadhamen", "Kalaat el Andalous", "Mnihla",
                "Raoued", "Sidi Thabet", "Soukra"
        ));

        // BEN AROUS
        m.put("Ben Arous", List.of(
                "Ben Arous", "Bou Mhel El Bassatine", "El Mourouj", "Ezzahra",
                "Fouchana", "Hammam Chott", "Hammam Lif", "M'hamdia", "Megrine",
                "Mohamedia", "Nouvelle Médina", "Radès"
        ));

        // MANOUBA
        m.put("Manouba", List.of(
                "Manouba", "Borj El Amri", "Douar Hicher", "El Battan",
                "Jedaida", "Mornaguia", "Oued Ellil", "Tebourba"
        ));

        // NABEUL
        m.put("Nabeul", List.of(
                "Nabeul", "Béni Khiar", "Bou Argoub", "Dar Chaabane El Fehri",
                "El Haouaria", "El Mida", "Grombalia", "Hammam Ghezèze",
                "Hammamet", "Kélibia", "Korba", "Menzel Bouzelfa",
                "Menzel Temime", "Soliman", "Takelsa"
        ));

        // ZAGHOUAN
        m.put("Zaghouan", List.of(
                "Zaghouan", "Bir Mcherga", "El Fahs", "Nadhour", "Saouaf", "Zriba"
        ));

        // BIZERTE
        m.put("Bizerte", List.of(
                "Bizerte Nord", "Bizerte Sud", "El Alia", "Ghar El Melh", "Joumine",
                "Mateur", "Menzel Bourguiba", "Menzel Jemil", "Ras Jebel", "Sejnane",
                "Tinja", "Utique"
        ));

        // BEJA
        m.put("Béja", List.of(
                "Amdoun", "Béja Nord", "Béja Sud", "Goubellat", "Medjez El Bab",
                "Nefza", "Téboursouk", "Testour", "Thibar"
        ));

        // JENDOUBA
        m.put("Jendouba", List.of(
                "Jendouba", "Jendouba Nord", "Aïn Draham", "Balta-Bou Aouane",
                "Bou Salem", "Fernana", "Ghardimaou", "Oued Mliz", "Tabarka"
        ));

        // LE KEF
        m.put("Le Kef", List.of(
                "El Kef Est", "El Kef Ouest", "Dahmani", "Jerissa", "Kalaat Senan",
                "Kalaat Khasba", "Nebeur", "Sers", "Tajerouine", "Touiref"
        ));

        // SILIANA
        m.put("Siliana", List.of(
                "Siliana Nord", "Siliana Sud", "Bargou", "Bou Arada", "El Aroussa",
                "Gaâfour", "Kesra", "Le Krib", "Makthar", "Rouhia",
                "Sidi Bou Rouis"
        ));

        // SOUSSE
        m.put("Sousse", List.of(
                "Sousse Ville", "Sousse Jaouhara", "Sousse Riadh", "Sousse Sidi Abdelhamid",
                "Akouda", "Bouficha", "Enfidha", "Hammam Sousse", "Hergla",
                "Kalaa Kebira", "Kalaa Sghira", "Kondar", "Msaken",
                "Sidi Bou Ali", "Sidi El Hani"
        ));

        // MONASTIR
        m.put("Monastir", List.of(
                "Monastir Centre", "Jemmel", "Ksar Hellal", "Ksibet el Mediouni", "Sahline",
                "Moknine", "Bembla", "Téboulba", "Sayada-Lamta-Bou Hajar",
                "Khnis", "Ouerdanine", "Zéramdine"
        ));

        // MAHDIA
        m.put("Mahdia", List.of(
                "Mahdia", "Bou Merdes", "Chebba", "Chorbane", "El Jem",
                "Essouassi", "Ksour Essef", "Melloulèche", "Ouled Chamekh", "Sidi Alouane"
        ));

        // KAIROUAN
        m.put("Kairouan", List.of(
                "Kairouan Nord", "Kairouan Sud", "Bou Hajla", "Chebika", "Chrarda",
                "El Ala", "Haffouz", "Hajeb El Ayoun", "Nasrallah", "Oueslatia", "Sbikha"
        ));

        // KASSERINE
        m.put("Kasserine", List.of(
                "Kasserine Nord", "Kasserine Sud", "Ezzouhour", "Feriana", "Foussana",
                "Haidra", "Hassi El Ferid", "Jedeliane", "Mejel Bel Abbès",
                "Sbeitla", "Sbiba", "Thala"
        ));

        // SIDI BOUZID
        m.put("Sidi Bouzid", List.of(
                "Sidi Bouzid Est", "Sidi Bouzid Ouest", "Bir El Hafey", "Cebbala Ouled Asker",
                "Jilma", "Meknassy", "Menzel Bouzaiane", "Mezzouna", "Ouled Haffouz",
                "Regueb", "Sidi Ali Ben Aoun", "Souk Jedid"
        ));

        // SFAX
        m.put("Sfax", List.of(
                "Sfax Ville", "Sfax Ouest", "Sfax Sud", "Agareb", "Bir Ali Ben Khalifa",
                "El Hencha", "Ghriba", "Jebeniana", "Kerkennah", "Mahares",
                "Menzel Chaker", "Sakiet Eddaier", "Sakiet Ezzit", "Skhira",
                "Thyna", "Chihia"
        ));

        // GABES
        m.put("Gabès", List.of(
                "Gabès Ville", "Gabès Ouest", "Gabès Sud", "Ghannouche", "Hamma",
                "Mareth", "Matmata", "Matmata Nouvelle", "Metouia", "Menzel Habib"
        ));

        // MEDENINE
        m.put("Medenine", List.of(
                "Medenine Nord", "Medenine Sud", "Ben Guerdane", "Béni Khedache",
                "Djerba Ajim", "Djerba Houmt Souk", "Djerba Midoun", "Sidi Makhlouf"
        ));

        // TATAOUINE
        m.put("Tataouine", List.of(
                "Tataouine Nord", "Tataouine Sud", "Bir Lahmar", "Dhehiba",
                "Ghomrassen", "Remada", "Smar"
        ));

        // GAFSA
        m.put("Gafsa", List.of(
                "Gafsa Nord", "Gafsa Sud", "Belkhir", "El Guettar", "El Ksar",
                "Mdhilla", "Metlaoui", "Moularès", "Redeyef", "Sidi Aïch", "Sned"
        ));

        // TOZEUR
        m.put("Tozeur", List.of(
                "Tozeur", "Degache", "Hammet Jerid", "Nefta", "Tamaghza", "Hezoua"
        ));

        // KEBILI
        m.put("Kébili", List.of(
                "Kébili Nord", "Kébili Sud", "Douz Nord", "Douz Sud",
                "Faouar", "Souk Lahad"
        ));

        return m;
    }
}