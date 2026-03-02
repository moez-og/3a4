package services.language;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service d'auto-correction grammaticale via l'API LanguageTool (gratuite, open-source).
 * Supporte le français (et d'autres langues).
 * Endpoint public : https://api.languagetool.org/v2/check
 */
public class LanguageToolService {

    private static final String API_URL = "https://api.languagetool.org/v2/check";
    private static final String LANGUAGE = "fr";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Vérifie un texte et renvoie la liste des corrections suggérées.
     *
     * @param text Le texte à vérifier
     * @return Liste de GrammarMatch (offset, longueur, message, suggestions de remplacement)
     */
    public static List<GrammarMatch> check(String text) {
        List<GrammarMatch> matches = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return matches;

        try {
            String body = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&language=" + LANGUAGE
                    + "&disabledRules=WHITESPACE_RULE";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                matches = parseResponse(response.body());
            } else {
                System.err.println("[LanguageTool] HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("[LanguageTool] Erreur: " + e.getMessage());
        }

        return matches;
    }

    /**
     * Applique automatiquement toutes les premières suggestions de correction au texte.
     * Parcourt les matches en sens inverse pour garder les offsets cohérents.
     *
     * @param text    Le texte original
     * @param matches Les corrections détectées
     * @return Le texte corrigé
     */
    public static String autoCorrect(String text, List<GrammarMatch> matches) {
        if (matches == null || matches.isEmpty()) return text;

        // Trier par offset décroissant pour ne pas invalider les positions
        List<GrammarMatch> sorted = new ArrayList<>(matches);
        sorted.sort((a, b) -> Integer.compare(b.offset, a.offset));

        StringBuilder sb = new StringBuilder(text);
        for (GrammarMatch m : sorted) {
            if (!m.replacements.isEmpty()) {
                int end = m.offset + m.length;
                if (end <= sb.length()) {
                    sb.replace(m.offset, end, m.replacements.get(0));
                }
            }
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  JSON parsing (regex-based, sans dépendance externe)
    // ════════════════════════════════════════════════════════════════

    private static List<GrammarMatch> parseResponse(String json) {
        List<GrammarMatch> results = new ArrayList<>();

        // Extraire le tableau "matches":[...]
        Pattern matchesArrayPattern = Pattern.compile("\"matches\"\\s*:\\s*\\[(.*)\\]\\s*\\}", Pattern.DOTALL);
        Matcher mArr = matchesArrayPattern.matcher(json);
        if (!mArr.find()) return results;

        String matchesJson = mArr.group(1);

        // Séparer les objets match individuels — on cherche chaque bloc {...}
        // Stratégie : compter les accolades
        List<String> matchBlocks = extractJsonObjects(matchesJson);

        for (String block : matchBlocks) {
            try {
                int offset = extractInt(block, "\"offset\"\\s*:\\s*(\\d+)");
                int length = extractInt(block, "\"length\"\\s*:\\s*(\\d+)");
                String message = extractStr(block, "\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
                String shortMsg = extractStr(block, "\"shortMessage\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
                String ruleId = extractStr(block, "\"id\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

                // Extraire les replacements
                List<String> replacements = new ArrayList<>();
                Pattern repPattern = Pattern.compile("\"replacements\"\\s*:\\s*\\[([^\\]]*)\\]");
                Matcher repMatcher = repPattern.matcher(block);
                if (repMatcher.find()) {
                    String repsJson = repMatcher.group(1);
                    Pattern valPat = Pattern.compile("\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
                    Matcher valMat = valPat.matcher(repsJson);
                    while (valMat.find()) {
                        replacements.add(unescapeJson(valMat.group(1)));
                    }
                }

                results.add(new GrammarMatch(
                        offset, length,
                        message != null ? unescapeJson(message) : "",
                        shortMsg != null ? unescapeJson(shortMsg) : "",
                        ruleId != null ? ruleId : "",
                        replacements
                ));
            } catch (Exception ignored) {
                // Ignorer les blocs mal formés
            }
        }

        return results;
    }

    /**
     * Extrait les objets JSON de premier niveau depuis un tableau.
     */
    private static List<String> extractJsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static int extractInt(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String extractStr(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    // ════════════════════════════════════════════════════════════════
    //  Data class
    // ════════════════════════════════════════════════════════════════

    public static class GrammarMatch {
        public final int offset;           // Position dans le texte
        public final int length;           // Longueur du mot/passage erroné
        public final String message;       // Explication complète
        public final String shortMessage;  // Message court (type d'erreur)
        public final String ruleId;        // ID de la règle LanguageTool
        public final List<String> replacements; // Suggestions de correction

        public GrammarMatch(int offset, int length, String message, String shortMessage,
                            String ruleId, List<String> replacements) {
            this.offset = offset;
            this.length = length;
            this.message = message;
            this.shortMessage = shortMessage;
            this.ruleId = ruleId;
            this.replacements = replacements != null ? replacements : new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.format("[%d+%d] %s → %s", offset, length, message,
                    replacements.isEmpty() ? "(aucune suggestion)" : replacements.get(0));
        }
    }
}
