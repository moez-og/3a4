package services.evenements;

import models.evenements.Evenement;
import models.evenements.Inscription;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de recommandation d'événements basé sur l'API Google Gemini (gratuit).
 *
 * Algorithme :
 *  1. Récupérer les événements auxquels le user s'est inscrit (via InscriptionService)
 *  2. Envoyer les titres + descriptions de ces événements à Gemini
 *  3. Gemini analyse et recommande parmi les événements disponibles
 *  4. Retourne une liste triée par pertinence
 *
 * ⚠️ 100% GRATUIT — Google AI Studio, aucune carte bancaire requise.
 *     Quota : 15 req/min, 1500 req/jour (Gemini 2.0 Flash)
 */
public class RecommendationService {

    // ═══════════════════════════════════════════════════════════
    //  CONFIGURATION — Mets ta clé API Gemini ici
    // ═══════════════════════════════════════════════════════════

    /**
     * Clé API Gemini (Google AI Studio).
     * Pour l'obtenir gratuitement :
     *   1. Va sur https://aistudio.google.com
     *   2. Connecte-toi avec ton compte Google
     *   3. Clique sur "Get API Key" → "Create API Key"
     *   4. Colle la clé ici (commence par AIzaSy...)
     */
    private static final String GEMINI_API_KEY = "AIzaSyCS4R_fiIho5cGI5CGAaYTFWWaH7thL0W0";

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private static final int TIMEOUT_MS = 15_000;

    private final EvenementService evenementService = new EvenementService();
    private final InscriptionService inscriptionService = new InscriptionService();

    // Cache simple pour éviter les appels API répétés
    private int lastUserId = -1;
    private List<Integer> lastRecommendedIds = new ArrayList<>();
    private long lastCallTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    // Dernière analyse des intérêts (pour les stats admin)
    private String lastInterestsAnalysis = "";
    private Map<String, Integer> lastInterestsMap = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════
    //  API PRINCIPALE
    // ═══════════════════════════════════════════════════════════

    /**
     * Retourne la liste des événements recommandés pour un user, triés par pertinence.
     * Utilise l'historique d'inscriptions du user pour déduire ses intérêts via Gemini.
     */
    public List<Evenement> getRecommendations(int userId) {
        // Vérifier le cache
        long now = System.currentTimeMillis();
        if (userId == lastUserId && !lastRecommendedIds.isEmpty()
                && (now - lastCallTime) < CACHE_DURATION_MS) {
            return resolveEvents(lastRecommendedIds);
        }

        try {
            // 1. Récupérer tous les événements
            List<Evenement> allEvents = evenementService.getAll();
            if (allEvents.isEmpty()) return Collections.emptyList();

            // 2. Récupérer les événements auxquels le user s'est inscrit
            List<Evenement> userEvents = getUserInscribedEvents(userId, allEvents);

            // 3. Récupérer les événements OUVERTS auxquels le user n'est PAS inscrit
            Set<Integer> userEventIds = userEvents.stream()
                    .map(Evenement::getId).collect(Collectors.toSet());
            List<Evenement> availableEvents = allEvents.stream()
                    .filter(e -> !userEventIds.contains(e.getId()))
                    .filter(e -> "OUVERT".equalsIgnoreCase(e.getStatut()))
                    .collect(Collectors.toList());

            if (availableEvents.isEmpty()) return Collections.emptyList();

            // 4. Si l'user n'a aucune inscription, retourner les plus populaires
            if (userEvents.isEmpty()) {
                lastInterestsAnalysis = "Aucune inscription trouvée — affichage des événements populaires";
                lastInterestsMap.clear();
                lastInterestsMap.put("Populaires", availableEvents.size());
                return getPopularEvents(availableEvents);
            }

            // 5. Si la clé API n'est pas configurée, utiliser le fallback local
            if (GEMINI_API_KEY.startsWith("VOTRE_")) {
                System.out.println("[Recommendation] Clé Gemini non configurée → fallback algorithme local");
                return fallbackLocalRecommendation(userEvents, availableEvents, userId);
            }

            // 6. Appeler Gemini
            List<Integer> recommendedIds = callGemini(userEvents, availableEvents);

            // Mettre en cache
            lastUserId = userId;
            lastRecommendedIds = recommendedIds;
            lastCallTime = now;

            return resolveEvents(recommendedIds);

        } catch (Exception e) {
            System.err.println("[Recommendation] Erreur: " + e.getMessage());
            e.printStackTrace();
            // Fallback : retourner des événements populaires
            try {
                List<Evenement> all = evenementService.getAll().stream()
                        .filter(ev -> "OUVERT".equalsIgnoreCase(ev.getStatut()))
                        .collect(Collectors.toList());
                return getPopularEvents(all);
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    /**
     * Retourne la dernière analyse textuelle des intérêts (pour affichage front).
     */
    public String getLastInterestsAnalysis() {
        return lastInterestsAnalysis;
    }

    /**
     * Retourne la map des intérêts déduits (catégorie → score) pour les stats admin.
     */
    public Map<String, Integer> getLastInterestsMap() {
        return lastInterestsMap;
    }

    /**
     * Analyse les intérêts de TOUS les users (pour le panneau stats admin).
     * Retourne une map globale : catégorie_d'intérêt → nombre_d'utilisateurs.
     */
    public Map<String, Integer> analyzeAllUsersInterests(List<models.users.User> allUsers) {
        Map<String, Integer> globalInterests = new LinkedHashMap<>();

        List<Evenement> allEvents = evenementService.getAll();

        for (models.users.User user : allUsers) {
            List<Evenement> userEvents = getUserInscribedEvents(user.getId(), allEvents);
            if (userEvents.isEmpty()) continue;

            // Extraire les mots-clés des événements de chaque user
            Map<String, Integer> userKeywords = extractKeywords(userEvents);
            for (String keyword : userKeywords.keySet()) {
                globalInterests.merge(keyword, 1, Integer::sum);
            }
        }

        // Trier par popularité décroissante
        return globalInterests.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Vide le cache pour forcer un nouvel appel API.
     */
    public void clearCache() {
        lastUserId = -1;
        lastRecommendedIds.clear();
        lastCallTime = 0;
    }

    /**
     * Vérifie si la clé API Gemini est configurée.
     */
    public boolean isApiKeyConfigured() {
        return !GEMINI_API_KEY.startsWith("VOTRE_");
    }

    // ═══════════════════════════════════════════════════════════
    //  GEMINI API CALL
    // ═══════════════════════════════════════════════════════════

    private List<Integer> callGemini(List<Evenement> userEvents, List<Evenement> availableEvents) {
        try {
            String prompt = buildPrompt(userEvents, availableEvents);
            String response = httpPost(GEMINI_API_URL + GEMINI_API_KEY, buildRequestBody(prompt));

            // Extraire le texte de la réponse Gemini
            String text = extractGeminiText(response);
            if (text == null || text.isEmpty()) {
                System.err.println("[Gemini] Réponse vide");
                return availableEvents.stream().map(Evenement::getId).collect(Collectors.toList());
            }

            System.out.println("[Gemini] Réponse: " + text);

            // Parser les IDs recommandés
            List<Integer> ids = parseRecommendedIds(text);

            // Extraire l'analyse des intérêts depuis la réponse
            parseInterestsFromResponse(text);

            return ids;

        } catch (Exception e) {
            System.err.println("[Gemini] API Error: " + e.getMessage());
            return availableEvents.stream().map(Evenement::getId).collect(Collectors.toList());
        }
    }

    private String buildPrompt(List<Evenement> userEvents, List<Evenement> available) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un système de recommandation d'événements. ");
        sb.append("Analyse les événements auxquels l'utilisateur s'est déjà inscrit ");
        sb.append("pour comprendre ses centres d'intérêt, puis recommande les événements ");
        sb.append("les plus pertinents parmi ceux disponibles.\n\n");

        sb.append("=== ÉVÉNEMENTS AUXQUELS L'UTILISATEUR S'EST INSCRIT ===\n");
        for (Evenement e : userEvents) {
            sb.append("- [").append(e.getId()).append("] ")
              .append(safe(e.getTitre())).append(" : ")
              .append(truncate(safe(e.getDescription()), 150))
              .append(" (type: ").append(safe(e.getType()))
              .append(", prix: ").append(e.getPrix()).append(" TND)\n");
        }

        sb.append("\n=== ÉVÉNEMENTS DISPONIBLES (à recommander) ===\n");
        for (Evenement e : available) {
            sb.append("- [").append(e.getId()).append("] ")
              .append(safe(e.getTitre())).append(" : ")
              .append(truncate(safe(e.getDescription()), 150))
              .append(" (type: ").append(safe(e.getType()))
              .append(", prix: ").append(e.getPrix()).append(" TND)\n");
        }

        sb.append("\n=== INSTRUCTIONS ===\n");
        sb.append("1. Identifie les centres d'intérêt de l'utilisateur (3-5 mots-clés)\n");
        sb.append("2. Classe les événements disponibles du plus pertinent au moins pertinent\n");
        sb.append("3. Réponds EXACTEMENT dans ce format :\n");
        sb.append("INTERETS: mot1, mot2, mot3\n");
        sb.append("RECOMMANDATIONS: id1, id2, id3, id4, ...\n");
        sb.append("EXPLICATION: une phrase courte expliquant pourquoi\n");
        sb.append("\nIMPORTANT: Les RECOMMANDATIONS doivent contenir UNIQUEMENT les IDs numériques ");
        sb.append("des événements disponibles, séparés par des virgules, triés du plus pertinent au moins pertinent.");

        return sb.toString();
    }

    private String buildRequestBody(String prompt) {
        // Escape special characters in the prompt for JSON
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        return "{\"contents\":[{\"parts\":[{\"text\":\"" + escaped + "\"}]}],"
                + "\"generationConfig\":{\"temperature\":0.3,\"maxOutputTokens\":500}}";
    }

    private String extractGeminiText(String json) {
        // Parse: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
        if (json == null) return null;
        int idx = json.indexOf("\"text\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 6);
        if (start < 0) return null;
        start++;
        // Find closing quote (handle escaped quotes)
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') result.append('\n');
                else if (c == 'r') result.append('\r');
                else if (c == 't') result.append('\t');
                else result.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private List<Integer> parseRecommendedIds(String text) {
        List<Integer> ids = new ArrayList<>();
        // Find line starting with "RECOMMANDATIONS:"
        for (String line : text.split("\n")) {
            if (line.toUpperCase().contains("RECOMMANDATION")) {
                String afterColon = line.contains(":") ? line.substring(line.indexOf(":") + 1) : line;
                // Extract all numbers
                String[] parts = afterColon.split("[^0-9]+");
                for (String p : parts) {
                    p = p.trim();
                    if (!p.isEmpty()) {
                        try { ids.add(Integer.parseInt(p)); } catch (NumberFormatException ignored) {}
                    }
                }
                break;
            }
        }
        return ids;
    }

    private void parseInterestsFromResponse(String text) {
        lastInterestsMap.clear();
        for (String line : text.split("\n")) {
            if (line.toUpperCase().contains("INTERET") || line.toUpperCase().contains("INTÉRÊT")) {
                String afterColon = line.contains(":") ? line.substring(line.indexOf(":") + 1).trim() : "";
                lastInterestsAnalysis = afterColon;
                String[] interests = afterColon.split(",");
                for (String interest : interests) {
                    interest = interest.trim();
                    if (!interest.isEmpty()) {
                        lastInterestsMap.put(interest, 1);
                    }
                }
                break;
            }
        }
        // Extract explanation
        for (String line : text.split("\n")) {
            if (line.toUpperCase().contains("EXPLICATION")) {
                String afterColon = line.contains(":") ? line.substring(line.indexOf(":") + 1).trim() : "";
                lastInterestsAnalysis += "\n" + afterColon;
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FALLBACK LOCAL (sans API)
    // ═══════════════════════════════════════════════════════════

    /**
     * Algorithme de recommandation local quand la clé API n'est pas configurée.
     * Utilise la similarité textuelle (mots-clés communs).
     */
    private List<Evenement> fallbackLocalRecommendation(
            List<Evenement> userEvents, List<Evenement> availableEvents, int userId) {

        // Extraire les mots-clés des événements de l'utilisateur
        Map<String, Integer> keywords = extractKeywords(userEvents);

        // Stocker pour les stats
        lastInterestsMap = new LinkedHashMap<>(keywords);
        lastInterestsAnalysis = "Intérêts déduits (algorithme local) : " +
                String.join(", ", keywords.keySet());

        // Calculer un score pour chaque événement disponible
        Map<Evenement, Double> scores = new LinkedHashMap<>();
        for (Evenement ev : availableEvents) {
            double score = calculateSimilarityScore(ev, keywords);

            // Bonus pour les événements populaires
            try {
                int inscCount = inscriptionService.countByEvent(ev.getId());
                score += inscCount * 0.5;
            } catch (Exception ignored) {}

            // Bonus pour les événements gratuits
            if (ev.getPrix() <= 0) score += 1.0;

            // Bonus si même type que les événements de l'user
            boolean sameType = userEvents.stream()
                    .anyMatch(ue -> safe(ue.getType()).equalsIgnoreCase(safe(ev.getType())));
            if (sameType) score += 2.0;

            scores.put(ev, score);
        }

        // Trier par score décroissant
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Evenement, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Extraire les mots-clés significatifs des événements.
     */
    private Map<String, Integer> extractKeywords(List<Evenement> events) {
        // Mots vides à ignorer
        Set<String> stopWords = Set.of(
                "le", "la", "les", "de", "des", "du", "un", "une", "et", "en",
                "à", "au", "aux", "ce", "ces", "cette", "qui", "que", "quoi",
                "pour", "par", "sur", "dans", "avec", "est", "sont", "a", "ont",
                "il", "elle", "nous", "vous", "ils", "elles", "ne", "pas", "se",
                "plus", "très", "bien", "tout", "tous", "toute", "toutes",
                "the", "and", "or", "is", "are", "was", "were", "be", "been",
                "of", "in", "to", "for", "with", "on", "at", "from", "by",
                "événement", "evenement", "event", "notre", "nos", "votre", "vos"
        );

        Map<String, Integer> keywords = new LinkedHashMap<>();
        for (Evenement e : events) {
            String text = (safe(e.getTitre()) + " " + safe(e.getDescription())).toLowerCase();
            String[] words = text.split("[^a-zà-ÿ0-9]+");
            for (String word : words) {
                if (word.length() >= 3 && !stopWords.contains(word)) {
                    keywords.merge(word, 1, Integer::sum);
                }
            }
        }

        // Garder les 10 mots les plus fréquents
        return keywords.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Calculer un score de similarité entre un événement et les mots-clés.
     */
    private double calculateSimilarityScore(Evenement ev, Map<String, Integer> keywords) {
        String text = (safe(ev.getTitre()) + " " + safe(ev.getDescription())).toLowerCase();
        double score = 0;
        for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
            if (text.contains(entry.getKey())) {
                score += entry.getValue() * 3.0; // Chaque occurrence vaut 3 points
            }
        }
        return score;
    }

    // ═══════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ═══════════════════════════════════════════════════════════

    private List<Evenement> getUserInscribedEvents(int userId, List<Evenement> allEvents) {
        Map<Integer, Evenement> eventMap = allEvents.stream()
                .collect(Collectors.toMap(Evenement::getId, e -> e, (a, b) -> a));

        List<Evenement> userEvents = new ArrayList<>();
        for (Evenement ev : allEvents) {
            try {
                if (inscriptionService.existsForUser(ev.getId(), userId)) {
                    userEvents.add(ev);
                }
            } catch (Exception ignored) {}
        }
        return userEvents;
    }

    private List<Evenement> getPopularEvents(List<Evenement> events) {
        // Trier par nombre d'inscriptions (les plus populaires en premier)
        Map<Evenement, Integer> popularity = new LinkedHashMap<>();
        for (Evenement ev : events) {
            try {
                popularity.put(ev, inscriptionService.countByEvent(ev.getId()));
            } catch (Exception e) {
                popularity.put(ev, 0);
            }
        }
        return popularity.entrySet().stream()
                .sorted(Map.Entry.<Evenement, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<Evenement> resolveEvents(List<Integer> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Evenement> allEvents = evenementService.getAll();
        Map<Integer, Evenement> map = allEvents.stream()
                .collect(Collectors.toMap(Evenement::getId, e -> e, (a, b) -> a));
        List<Evenement> result = new ArrayList<>();
        for (int id : ids) {
            Evenement e = map.get(id);
            if (e != null) result.add(e);
        }
        return result;
    }

    private String httpPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        if (code < 200 || code >= 300) {
            System.err.println("[Gemini] HTTP " + code + ": " + sb);
            throw new IOException("Gemini API error " + code + ": " + sb);
        }

        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
