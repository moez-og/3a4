package services.lieux;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Service de modération des avis — détection bad words + méchanceté
 * Multilingue : français, arabe, anglais, langage SMS/contourné
 */
public class ModerationService {

    public enum Severity { OK, WARNING, BLOCKED }

    public static class ModerationResult {
        public final Severity   severity;
        public final String     reason;       // Message affiché à l'utilisateur
        public final String     detail;       // Détail interne (mot détecté)
        public final String     suggestion;   // Texte suggéré nettoyé
        public final int        score;        // Score de toxicité 0-100

        public ModerationResult(Severity s, String reason, String detail, String suggestion, int score) {
            this.severity   = s;
            this.reason     = reason;
            this.detail     = detail;
            this.suggestion = suggestion;
            this.score      = score;
        }

        public boolean isBlocked()  { return severity == Severity.BLOCKED; }
        public boolean isWarning()  { return severity == Severity.WARNING; }
        public boolean isOk()       { return severity == Severity.OK; }
    }

    // ============================================================
    //  DICTIONNAIRES DE MOTS INTERDITS
    // ============================================================

    // Gros mots / insultes — français
    private static final List<String> BAD_WORDS_FR = Arrays.asList(
        "merde", "putain", "connard", "connasse", "salaud", "salope", "con",
        "conne", "idiot", "idiote", "imbécile", "imbecile", "crétin", "cretin",
        "abruti", "débile", "debile", "nul", "nulle", "fdp", "pd", "pute",
        "enculé", "encule", "fils de pute", "va te faire", "bastard", "bâtard",
        "batard", "niquer", "nique", "baise", "baiser", "foutre", "chier",
        "merdique", "salopard", "ordure", "pourriture", "déchet", "dechet",
        "racaille", "sous-merde", "va chier", "ferme ta gueule", "ta gueule",
        "gueule", "casse-toi", "casse toi", "ferme la", "ferme-la",
        "paysan", "attardé", "attarde", "gogol", "bouffon", "clown",
        "inutile", "dégage", "degage", "dégueulasse", "degueulasse"
    );

    // Insultes — arabe (translittéré)
    private static final List<String> BAD_WORDS_AR = Arrays.asList(
        "kelb", "klab", "hmar", "hmir", "kahba", "kahba", "9ahba", "qahba",
        "zamel", "zomel", "a7a", "ahwa", "weld el9a7ba", "ibn el kahba",
        "weld lekahba", "tb9i", "tbki", "7mar", "bghel", "baghl",
        "3ahir", "ahir", "bled", "ya3bour", "a3bour", "koskos", "far9",
        "khara", "5ara", "3ara", "zebi", "zbi", "9a7be", "kahbe"
    );

    // Bad words — anglais
    private static final List<String> BAD_WORDS_EN = Arrays.asList(
        "fuck", "shit", "bitch", "asshole", "bastard", "damn", "crap",
        "idiot", "stupid", "moron", "loser", "jerk", "dumb", "retard",
        "wtf", "stfu", "fck", "fuk", "fuq", "sht", "bs"
    );

    // Mots de haine / discrimination
    private static final List<String> HATE_WORDS = Arrays.asList(
        "raciste", "racism", "fasciste", "nazi", "terroriste", "terrorist",
        "sous-humain", "sous humain", "sauvage", "barbare", "animal",
        "étrangers dehors", "retournez", "rentrez chez vous"
    );

    // Patterns de contournement SMS / leetspeak
    private static final Map<String, String> LEET_MAP = new LinkedHashMap<>();
    static {
        LEET_MAP.put("@",  "a");
        LEET_MAP.put("4",  "a");
        LEET_MAP.put("3",  "e");
        LEET_MAP.put("€",  "e");
        LEET_MAP.put("1",  "i");
        LEET_MAP.put("!",  "i");
        LEET_MAP.put("0",  "o");
        LEET_MAP.put("5",  "s");
        LEET_MAP.put("$",  "s");
        LEET_MAP.put("7",  "t");
        LEET_MAP.put("ck", "k");
        LEET_MAP.put("ph", "f");
    }

    // Patterns de méchanceté (phrases agressives)
    private static final List<Pattern> TOXIC_PATTERNS = Arrays.asList(
        Pattern.compile("(je|te).*(?:hais|deteste|haïs)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("aller?\\s*(?:en|au)\\s*(?:enfer|diable)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tu\\s*(?:mérites?|merizes?)\\s*(?:de|d')\\s*(?:mourir|crever)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:nul|nulle|mauvais|horrible|catastrophique|dégoût).*(?:endroit|lieu|place|resto|café|hôtel)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:arnaqu|escroque|voleur|voleuse|menteur|menteuse)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:pourriture|ordure|déchet|rebut)\\s*(?:de|d'|du)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:ferme|shut)\\s*(?:ta|la|your)\\s*(?:gueule|bouche|mouth)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:va|go)\\s*(?:te faire|fuck|chier|crever|mourir)", Pattern.CASE_INSENSITIVE)
    );

    // ============================================================
    //  MÉTHODE PRINCIPALE
    // ============================================================

    public ModerationResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ModerationResult(Severity.OK, "", "", text, 0);
        }

        String original = text.trim();
        String normalized = normalize(original);
        String deLeeted  = deLeet(normalized);

        int score = 0;
        List<String> foundWords  = new ArrayList<>();
        List<String> foundHate   = new ArrayList<>();

        // 1. Vérifier bad words français
        for (String bw : BAD_WORDS_FR) {
            if (containsWord(deLeeted, bw) || containsWord(normalized, bw)) {
                foundWords.add(bw);
                score += 30;
            }
        }

        // 2. Vérifier bad words arabe
        for (String bw : BAD_WORDS_AR) {
            if (normalized.contains(bw)) {
                foundWords.add(bw);
                score += 30;
            }
        }

        // 3. Vérifier bad words anglais
        for (String bw : BAD_WORDS_EN) {
            if (containsWord(deLeeted, bw)) {
                foundWords.add(bw);
                score += 25;
            }
        }

        // 4. Vérifier mots de haine (score plus élevé)
        for (String hw : HATE_WORDS) {
            if (normalized.contains(hw)) {
                foundHate.add(hw);
                score += 50;
            }
        }

        // 5. Vérifier patterns toxiques
        for (Pattern p : TOXIC_PATTERNS) {
            if (p.matcher(normalized).find()) {
                score += 20;
            }
        }

        // 6. Détecter ALL CAPS excessif (cri agressif)
        double capsRatio = getCapsRatio(original);
        if (capsRatio > 0.6 && original.length() > 10) {
            score += 10;
        }

        // 7. Détecter répétition excessive de caractères (!!!! ????)
        if (original.matches(".*[!?]{4,}.*")) score += 5;

        // Plafonner le score à 100
        score = Math.min(score, 100);

        // Construire le résultat
        if (!foundHate.isEmpty() || score >= 60) {
            String detail = !foundHate.isEmpty()
                ? "Discours haineux détecté : " + String.join(", ", foundHate)
                : "Mots inappropriés : " + String.join(", ", foundWords);
            return new ModerationResult(
                Severity.BLOCKED,
                "❌ Votre avis contient du contenu inapproprié et ne peut pas être publié.\n"
                    + "Merci de rester respectueux dans vos commentaires.",
                detail,
                censorText(original, foundWords),
                score
            );
        }

        if (!foundWords.isEmpty() || score >= 30) {
            return new ModerationResult(
                Severity.WARNING,
                "⚠️ Votre commentaire contient des termes qui pourraient être perçus comme irrespectueux.\n"
                    + "Souhaitez-vous le modifier avant de publier ?",
                "Termes détectés : " + String.join(", ", foundWords),
                censorText(original, foundWords),
                score
            );
        }

        return new ModerationResult(Severity.OK, "", "", original, score);
    }

    // ============================================================
    //  HELPERS
    // ============================================================

    /** Normalise le texte : minuscules, accents supprimés */
    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replace("é","e").replace("è","e").replace("ê","e").replace("ë","e")
            .replace("à","a").replace("â","a").replace("ä","a")
            .replace("î","i").replace("ï","i")
            .replace("ô","o").replace("ö","o")
            .replace("ù","u").replace("û","u").replace("ü","u")
            .replace("ç","c")
            .replace("œ","oe").replace("æ","ae");
    }

    /** Décode le leetspeak / SMS : m3rde → merde */
    private String deLeet(String text) {
        String result = text;
        for (Map.Entry<String, String> e : LEET_MAP.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    /** Vérifie si un mot est présent (avec boundaries) */
    private boolean containsWord(String text, String word) {
        if (word.contains(" ")) return text.contains(word);
        // Pour les mots courts, vérifier qu'ils sont isolés
        Pattern p = Pattern.compile("(?<![a-z])" + Pattern.quote(word) + "(?![a-z])",
            Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }

    /** Ratio de majuscules dans le texte */
    private double getCapsRatio(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters == 0) return 0;
        long caps = text.chars().filter(Character::isUpperCase).count();
        return (double) caps / letters;
    }

    /** Censure les mots détectés : remplace par *** */
    public String censorText(String text, List<String> words) {
        String result = text;
        for (String word : words) {
            String stars = "*".repeat(word.length());
            result = result.replaceAll("(?i)" + Pattern.quote(word), stars);
        }
        return result;
    }

    /** Version simplifiée pour appel externe */
    public boolean isClean(String text) {
        return analyze(text).isOk();
    }
}
