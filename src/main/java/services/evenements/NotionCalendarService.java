package services.evenements;

import models.evenements.Evenement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * NotionCalendarService — Synchronisation des événements vers Notion (API gratuite).
 *
 * ════════════════════════════════════════════════════════════════
 *  100% GRATUIT — Aucune carte bancaire requise.
 *  0 DÉPENDANCE — Utilise java.net.http.HttpClient natif (Java 11+).
 *
 *  CONFIGURATION (une seule fois) :
 *
 *  1. Créer un compte gratuit sur https://www.notion.so/
 *  2. Aller sur https://www.notion.so/my-integrations
 *  3. Cliquer "New integration" → nommer "FinTokhrej Calendar"
 *  4. Copier le "Internal Integration Token" (commence par ntn_...)
 *  5. Dans Notion, créer une nouvelle page "Full page database"
 *     avec ces propriétés (colonnes) :
 *       - Titre        (Title)       ← par défaut
 *       - Date Début   (Date)
 *       - Date Fin     (Date)
 *       - Statut       (Select)      → options: OUVERT, FERME, ANNULE
 *       - Type         (Select)      → options: CONFERENCE, ATELIER, etc.
 *       - Lieu         (Rich text)
 *       - Prix         (Number)
 *       - Capacité     (Number)
 *       - Description  (Rich text)
 *  6. Partager la base avec l'intégration :
 *     → "..." en haut → "Connections" → choisir "FinTokhrej Calendar"
 *  7. Copier l'ID de la base depuis l'URL :
 *     https://notion.so/xxxxx?v=... → xxxxx est le Database ID (32 chars)
 *
 *  Coller le token et l'ID dans l'interface de l'application.
 * ════════════════════════════════════════════════════════════════
 */
public class NotionCalendarService {

    private static final String NOTION_API_URL = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final HttpClient httpClient;
    private String apiToken;
    private String databaseId;
    private boolean configured = false;
    private String lastError = null;
    /** The actual name of the title property in the Notion database */
    private String titlePropertyName = "Titre";

    // ─────────────────────────────────────────────────────────────
    //  SINGLETON
    // ─────────────────────────────────────────────────────────────

    private static NotionCalendarService instance;

    public static NotionCalendarService getInstance() {
        if (instance == null) {
            instance = new NotionCalendarService();
        }
        return instance;
    }

    private NotionCalendarService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    //  CONFIGURATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Configure le service avec le token API et l'ID de la base Notion.
     *
     * @param token      le token d'intégration Notion (ntn_...)
     * @param databaseId l'ID de la base de données Notion (32 caractères)
     */
    public void configure(String token, String databaseId) {
        this.apiToken = token != null ? token.trim() : "";
        this.databaseId = databaseId != null ? databaseId.trim().replace("-", "") : "";
        this.configured = !this.apiToken.isEmpty() && !this.databaseId.isEmpty();
        this.lastError = null;
    }

    /**
     * Teste la connexion en interrogeant la base Notion.
     *
     * @return true si la connexion est OK
     */
    public boolean testConnection() {
        if (!configured) {
            lastError = "Service non configuré. Veuillez entrer le token et l'ID de la base.";
            return false;
        }

        try {
            System.out.println("[Notion] 🔗 Testing connection... DB=" + databaseId + ", Token=" + apiToken.substring(0, Math.min(10, apiToken.length())) + "...");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[Notion] 📥 Test response status: " + response.statusCode());

            if (response.statusCode() == 200) {
                String body = response.body();

                // Detect the title property name (every DB has exactly one title property)
                detectTitleProperty(body);
                System.out.println("[Notion] 📌 Title property name: \"" + titlePropertyName + "\"");

                // List all existing property names
                List<String> existingProps = extractPropertyNames(body);
                System.out.println("[Notion] 📋 Existing properties: " + existingProps);

                // Auto-create missing properties
                ensureDatabaseSchema(existingProps);

                lastError = null;
                System.out.println("[Notion] ✅ Connexion réussie à la base de données.");
                return true;
            } else {
                lastError = "Erreur " + response.statusCode() + ": " + extractNotionError(response.body());
                System.err.println("[Notion] ❌ " + lastError);
                System.err.println("[Notion] 📥 Full response: " + response.body());
                return false;
            }

        } catch (Exception e) {
            lastError = "Erreur de connexion: " + e.getMessage();
            System.err.println("[Notion] ❌ " + lastError);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Detects the name of the title property in the database.
     * Notion databases always have exactly one title property.
     */
    private void detectTitleProperty(String dbJson) {
        // Look for "type":"title" in the properties and extract the property name
        int idx = 0;
        while (true) {
            int typeIdx = dbJson.indexOf("\"type\":\"title\"", idx);
            if (typeIdx == -1) break;

            // Search backward from this position to find the property name
            // Properties are formatted as: "PropertyName": { ... "type":"title" ... }
            int searchStart = Math.max(0, typeIdx - 500);
            String segment = dbJson.substring(searchStart, typeIdx);

            // Find the last property name before "type":"title"
            // Look for the pattern "}," or the start of "properties":{
            int lastPropEnd = segment.lastIndexOf("\":{");
            if (lastPropEnd == -1) lastPropEnd = segment.lastIndexOf("\": {");

            if (lastPropEnd != -1) {
                // Find the opening quote of the property name
                String beforeBrace = segment.substring(0, lastPropEnd);
                int nameStart = beforeBrace.lastIndexOf("\"");
                if (nameStart != -1) {
                    String propName = beforeBrace.substring(nameStart + 1);
                    if (!propName.isEmpty() && !propName.contains("{") && !propName.contains("}")) {
                        titlePropertyName = propName;
                        System.out.println("[Notion] 🔍 Detected title property: \"" + titlePropertyName + "\"");
                        return;
                    }
                }
            }
            idx = typeIdx + 1;
        }
        // Default fallback
        System.out.println("[Notion] ⚠️ Could not detect title property, using default: \"" + titlePropertyName + "\"");
    }

    /**
     * Extracts all property names from the database JSON response.
     */
    private List<String> extractPropertyNames(String dbJson) {
        List<String> names = new ArrayList<>();
        // Look for "properties":{...} and extract keys
        int propsIdx = dbJson.indexOf("\"properties\":{");
        if (propsIdx == -1) propsIdx = dbJson.indexOf("\"properties\": {");
        if (propsIdx == -1) return names;

        int braceStart = dbJson.indexOf("{", propsIdx + 12);
        if (braceStart == -1) return names;

        // Simple extraction: look for "name":"xxx" patterns inside properties
        int searchFrom = braceStart;
        int depth = 0;
        boolean inProps = false;

        for (int i = braceStart; i < dbJson.length(); i++) {
            char c = dbJson.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1) inProps = true;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
        }

        // Alternative: look for "name":"xxx" at depth 2 within properties
        int nameIdx = braceStart;
        while (true) {
            nameIdx = dbJson.indexOf("\"name\":\"", nameIdx);
            if (nameIdx == -1 || nameIdx > braceStart + 10000) break;
            int s = nameIdx + 8;
            int e = dbJson.indexOf("\"", s);
            if (e != -1) {
                String name = dbJson.substring(s, e);
                if (!name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
            nameIdx = (e != -1 ? e : nameIdx) + 1;
        }

        return names;
    }

    /**
     * Ensures the database has all required properties.
     * Creates missing properties via the Notion API.
     */
    private void ensureDatabaseSchema(List<String> existingProps) {
        StringBuilder propsToAdd = new StringBuilder();
        boolean needsUpdate = false;

        // Check each required property (excluding the title property which always exists)
        String[][] required = {
                {"Date Début", "date"},
                {"Date Fin", "date"},
                {"Statut", "select"},
                {"Type", "select"},
                {"Lieu", "rich_text"},
                {"Prix", "number"},
                {"Capacité", "number"},
                {"EventID", "number"},
                {"Description", "rich_text"}
        };

        for (String[] prop : required) {
            if (!existingProps.contains(prop[0])) {
                System.out.println("[Notion] ➕ Missing property: \"" + prop[0] + "\" (" + prop[1] + ") — will create");
                if (needsUpdate) propsToAdd.append(",");
                propsToAdd.append("\"").append(prop[0]).append("\": {\"").append(prop[1]).append("\": {}}");
                needsUpdate = true;
            }
        }

        if (!needsUpdate) {
            System.out.println("[Notion] ✅ All required properties exist.");
            return;
        }

        try {
            String json = "{\"properties\": {" + propsToAdd.toString() + "}}";
            System.out.println("[Notion] 📤 Creating missing properties: " + json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[Notion] ✅ Database schema updated successfully.");
            } else {
                System.err.println("[Notion] ❌ Failed to update schema: " + response.statusCode() + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("[Notion] ❌ Error updating schema: " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getLastError() {
        return lastError;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    // ─────────────────────────────────────────────────────────────
    //  CRÉER un événement dans Notion
    // ─────────────────────────────────────────────────────────────

    /**
     * Crée un événement dans la base de données Notion.
     *
     * @param ev       l'événement à créer
     * @param lieuName nom du lieu
     * @return l'ID Notion de la page créée, ou null en cas d'erreur
     */
    public String createEvent(Evenement ev, String lieuName) {
        if (!configured) {
            lastError = "Service non configuré.";
            return null;
        }

        try {
            String json = buildCreatePageJson(ev, lieuName);
            System.out.println("[Notion] 📤 CREATE request for: " + safeStr(ev.getTitre()) + " (ID=" + ev.getId() + ")");
            System.out.println("[Notion] 📤 JSON: " + (json.length() > 500 ? json.substring(0, 500) + "..." : json));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/pages"))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[Notion] 📥 Response status: " + response.statusCode());
            if (response.statusCode() != 200) {
                System.err.println("[Notion] 📥 Response body: " + response.body());
            }

            if (response.statusCode() == 200) {
                String pageId = extractJsonField(response.body(), "id");
                System.out.println("[Notion] ✅ Événement créé: " + safeStr(ev.getTitre()) + " → " + pageId);
                lastError = null;
                return pageId;
            } else {
                lastError = "Erreur création: " + response.statusCode() + " - " + extractNotionError(response.body());
                System.err.println("[Notion] ❌ " + lastError);
                return null;
            }

        } catch (Exception e) {
            lastError = "Erreur: " + e.getMessage();
            System.err.println("[Notion] ❌ " + lastError);
            e.printStackTrace();
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  METTRE À JOUR un événement existant dans Notion
    // ─────────────────────────────────────────────────────────────

    /**
     * Met à jour une page Notion existante avec les données de l'événement.
     *
     * @param notionPageId l'ID de la page Notion à mettre à jour
     * @param ev           l'événement source
     * @param lieuName     nom du lieu
     * @return true si la mise à jour a réussi
     */
    public boolean updateEvent(String notionPageId, Evenement ev, String lieuName) {
        if (!configured) {
            lastError = "Service non configuré.";
            return false;
        }

        try {
            String json = buildUpdatePageJson(ev, lieuName);
            System.out.println("[Notion] 📤 UPDATE request for: " + safeStr(ev.getTitre()) + " (PageID=" + notionPageId + ")");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/pages/" + notionPageId))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            System.out.println("[Notion] 📥 Update response status: " + response.statusCode());
            if (response.statusCode() != 200) {
                System.err.println("[Notion] 📥 Update response body: " + response.body());
            }

            if (response.statusCode() == 200) {
                System.out.println("[Notion] ✏️ Événement mis à jour: " + safeStr(ev.getTitre()));
                lastError = null;
                return true;
            } else {
                lastError = "Erreur update: " + response.statusCode() + " - " + extractNotionError(response.body());
                System.err.println("[Notion] ❌ " + lastError);
                return false;
            }

        } catch (Exception e) {
            lastError = "Erreur: " + e.getMessage();
            System.err.println("[Notion] ❌ " + lastError);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Archive (supprime) une page Notion par son ID.
     *
     * @param notionPageId l'ID de la page à archiver
     * @return true si l'archivage a réussi
     */
    public boolean archivePage(String notionPageId) {
        if (!configured) return false;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/pages/" + notionPageId))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"archived\": true}"))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  QUERY — Récupérer toutes les pages avec leur EventID
    // ─────────────────────────────────────────────────────────────

    /**
     * Récupère toutes les pages de la base Notion et construit un mapping
     * EventID (local) → Notion Page ID.
     * Cela permet l'upsert (update or insert) et la suppression des orphelins.
     *
     * @return Map où la clé est l'EventID local et la valeur est le Notion Page ID
     */
    public java.util.Map<Integer, String> queryAllEventMappings() {
        java.util.Map<Integer, String> map = new java.util.LinkedHashMap<>();
        if (!configured) return map;

        String startCursor = null;
        boolean hasMore = true;
        int maxPages = 10; // Safety limit — max 1000 pages (10 × 100)
        int page = 0;

        while (hasMore && page < maxPages) {
            page++;
            try {
                String json = startCursor != null
                        ? "{\"page_size\": 100, \"start_cursor\": \"" + startCursor + "\"}"
                        : "{\"page_size\": 100}";

                System.out.println("[Notion] 🔍 queryMappings page " + page + "...");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId + "/query"))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Notion-Version", NOTION_VERSION)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("[Notion] 📥 queryMappings response: " + response.statusCode());

                if (response.statusCode() != 200) {
                    System.err.println("[Notion] ❌ queryMappings error: " + response.body());
                    break;
                }

                String body = response.body();
                parseEventMappings(body, map);

                // Check for more pages
                hasMore = body.contains("\"has_more\":true") || body.contains("\"has_more\": true");
                if (hasMore) {
                    startCursor = extractJsonField(body, "next_cursor");
                    if (startCursor == null || startCursor.equals("null")) {
                        hasMore = false;
                        break;
                    }
                }

                Thread.sleep(350);

            } catch (Exception e) {
                System.err.println("[Notion] ❌ Erreur query mappings: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }

        System.out.println("[Notion] 📋 " + map.size() + " page(s) Notion trouvée(s) avec EventID (pages scannées: " + page + ").");
        return map;
    }

    /**
     * Parse la réponse de query Notion pour extraire les paires EventID → PageID.
     * Cherche la propriété "EventID" (number) dans chaque page.
     */
    private void parseEventMappings(String body, java.util.Map<Integer, String> map) {
        // On parcourt les résultats page par page
        // Chaque page contient "id":"xxx" et "properties":{"EventID":{"number":123}}
        int searchFrom = 0;
        while (true) {
            int objIdx = body.indexOf("\"object\":\"page\"", searchFrom);
            if (objIdx == -1) break;

            // Trouver l'id de la page (juste avant ou après "object":"page")
            String pageId = null;
            int idSearchStart = Math.max(0, objIdx - 200);
            int idIdx = body.indexOf("\"id\":\"", idSearchStart);
            if (idIdx != -1 && idIdx < objIdx + 50) {
                int s = idIdx + 6;
                int e = body.indexOf("\"", s);
                if (e != -1) pageId = body.substring(s, e);
            }

            // Trouver EventID dans les propriétés de cette page
            // On cherche "EventID" suivi de "number": dans un rayon raisonnable
            int eventIdPropIdx = body.indexOf("\"EventID\"", objIdx);
            if (eventIdPropIdx != -1 && eventIdPropIdx < objIdx + 5000) {
                // Chercher "number": après "EventID"
                int numIdx = body.indexOf("\"number\":", eventIdPropIdx);
                if (numIdx != -1 && numIdx < eventIdPropIdx + 200) {
                    int numStart = numIdx + 9; // length of "number":
                    // Skip whitespace
                    while (numStart < body.length() && body.charAt(numStart) == ' ') numStart++;

                    if (numStart < body.length() && body.charAt(numStart) != 'n') { // not null
                        int numEnd = numStart;
                        while (numEnd < body.length() && (Character.isDigit(body.charAt(numEnd)) || body.charAt(numEnd) == '.')) {
                            numEnd++;
                        }
                        if (numEnd > numStart) {
                            try {
                                int eventId = (int) Double.parseDouble(body.substring(numStart, numEnd));
                                if (pageId != null && pageId.length() > 10 && eventId > 0) {
                                    map.put(eventId, pageId);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }

            searchFrom = objIdx + 1;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SYNCHRONISER TOUS — UPSERT + DELETE ORPHELINS
    // ─────────────────────────────────────────────────────────────

    /**
     * Synchronise intelligemment les événements vers Notion :
     * • Crée les nouveaux événements (absents de Notion)
     * • Met à jour les événements existants (déjà dans Notion)
     * • Archive les pages orphelines (supprimées localement)
     * • Évite toute redondance grâce à la propriété EventID
     *
     * @param evenements   liste complète des événements locaux
     * @param lieuResolver fonction qui résout le nom du lieu
     * @return résultat de la synchronisation
     */
    public SyncResult syncAll(List<Evenement> evenements,
                              Function<Integer, String> lieuResolver) {
        SyncResult result = new SyncResult();

        if (!configured) {
            result.failed = evenements.size();
            result.errors.add("Service non configuré.");
            return result;
        }

        System.out.println("[Notion] ═══════════════════════════════════════════");
        System.out.println("[Notion] 🔄 SYNC START — " + evenements.size() + " événement(s) local(s)");
        System.out.println("[Notion] DB ID: " + databaseId);
        System.out.println("[Notion] Token: " + apiToken.substring(0, Math.min(10, apiToken.length())) + "...");

        // ── ÉTAPE 1 : Récupérer les pages existantes dans Notion ──
        System.out.println("[Notion] 🔍 Récupération des pages existantes...");
        java.util.Map<Integer, String> existingPages = queryAllEventMappings();
        System.out.println("[Notion] 📋 Pages existantes: " + existingPages.size() + " → " + existingPages.keySet());
        java.util.Set<Integer> localIds = new java.util.HashSet<>();

        // ── ÉTAPE 2 : Upsert — créer ou mettre à jour ──
        System.out.println("[Notion] 🔄 STEP 2: Upsert " + evenements.size() + " event(s)...");
        int idx = 0;
        for (Evenement ev : evenements) {
            idx++;
            localIds.add(ev.getId());
            try {
                String lieuName = lieuResolver.apply(ev.getLieuId());
                String existingPageId = existingPages.get(ev.getId());
                System.out.println("[Notion] [" + idx + "/" + evenements.size() + "] Event ID=" + ev.getId()
                        + " \"" + safeStr(ev.getTitre()) + "\" → " + (existingPageId != null ? "UPDATE" : "CREATE"));

                if (existingPageId != null) {
                    // L'événement existe déjà → MISE À JOUR
                    boolean updated = updateEvent(existingPageId, ev, lieuName);
                    if (updated) {
                        result.updated++;
                        System.out.println("[Notion]   → ✅ Updated OK");
                    } else {
                        result.failed++;
                        result.errors.add(safeStr(ev.getTitre()) + ": " + lastError);
                        System.out.println("[Notion]   → ❌ Update FAILED: " + lastError);
                    }
                } else {
                    // Nouveau → CRÉATION
                    String pageId = createEvent(ev, lieuName);
                    if (pageId != null) {
                        result.created++;
                        System.out.println("[Notion]   → ✅ Created OK: " + pageId);
                    } else {
                        result.failed++;
                        result.errors.add(safeStr(ev.getTitre()) + ": " + lastError);
                        System.out.println("[Notion]   → ❌ Create FAILED: " + lastError);
                    }
                }

                // Rate limit Notion API (3 req/sec)
                Thread.sleep(350);

            } catch (Exception e) {
                result.failed++;
                result.errors.add(safeStr(ev.getTitre()) + ": " + e.getMessage());
                System.err.println("[Notion]   → ❌ Exception: " + e.getMessage());
            }
        }

        // ── ÉTAPE 3 : Supprimer les orphelins (événements dans Notion
        //    qui n'existent plus localement) ──
        for (java.util.Map.Entry<Integer, String> entry : existingPages.entrySet()) {
            int notionEventId = entry.getKey();
            if (!localIds.contains(notionEventId)) {
                try {
                    boolean archived = archivePage(entry.getValue());
                    if (archived) {
                        result.deleted++;
                        System.out.println("[Notion] 🗑️ Orphelin archivé: EventID=" + notionEventId);
                    }
                    Thread.sleep(350);
                } catch (Exception ignored) {}
            }
        }

        System.out.println("[Notion] ✅ Sync terminée: " + result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  LIRE les événements depuis Notion
    // ─────────────────────────────────────────────────────────────

    /**
     * Récupère le nombre d'événements dans la base Notion.
     *
     * @return le nombre de pages, ou -1 en cas d'erreur
     */
    public int countEvents() {
        if (!configured) return -1;

        try {
            String json = "{\"page_size\": 1}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId + "/query"))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Notion-Version", NOTION_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Compter le nombre total (pas fiable via une seule query, mais indicatif)
                // On fait une query complète
                return countAllPages();
            }
            return -1;

        } catch (Exception e) {
            return -1;
        }
    }

    private int countAllPages() {
        int count = 0;
        String startCursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String json = startCursor != null
                        ? "{\"page_size\": 100, \"start_cursor\": \"" + startCursor + "\"}"
                        : "{\"page_size\": 100}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId + "/query"))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Notion-Version", NOTION_VERSION)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) break;

                String body = response.body();
                // Count "object":"page" occurrences
                int idx = 0;
                while ((idx = body.indexOf("\"object\":\"page\"", idx)) != -1) {
                    count++;
                    idx++;
                }

                hasMore = body.contains("\"has_more\":true");
                if (hasMore) {
                    startCursor = extractJsonField(body, "next_cursor");
                    if (startCursor == null) break;
                }

                Thread.sleep(350);

            } catch (Exception e) {
                break;
            }
        }

        return count;
    }

    /**
     * Supprime (archive) tous les événements de la base Notion.
     *
     * @return nombre de pages archivées
     */
    public int clearAll() {
        if (!configured) return 0;

        int archived = 0;
        String startCursor = null;
        boolean hasMore = true;

        while (hasMore) {
            try {
                String queryJson = startCursor != null
                        ? "{\"page_size\": 100, \"start_cursor\": \"" + startCursor + "\"}"
                        : "{\"page_size\": 100}";

                HttpRequest queryReq = HttpRequest.newBuilder()
                        .uri(URI.create(NOTION_API_URL + "/databases/" + databaseId + "/query"))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Notion-Version", NOTION_VERSION)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> queryResp = httpClient.send(queryReq,
                        HttpResponse.BodyHandlers.ofString());

                if (queryResp.statusCode() != 200) break;

                // Extract page IDs
                String body = queryResp.body();
                List<String> pageIds = extractAllPageIds(body);

                for (String pid : pageIds) {
                    try {
                        HttpRequest archiveReq = HttpRequest.newBuilder()
                                .uri(URI.create(NOTION_API_URL + "/pages/" + pid))
                                .header("Authorization", "Bearer " + apiToken)
                                .header("Notion-Version", NOTION_VERSION)
                                .header("Content-Type", "application/json")
                                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                                        "{\"archived\": true}"))
                                .timeout(Duration.ofSeconds(10))
                                .build();

                        HttpResponse<String> archiveResp = httpClient.send(archiveReq,
                                HttpResponse.BodyHandlers.ofString());
                        if (archiveResp.statusCode() == 200) archived++;
                        Thread.sleep(350);
                    } catch (Exception ignored) {}
                }

                hasMore = body.contains("\"has_more\":true");
                if (hasMore) {
                    startCursor = extractJsonField(body, "next_cursor");
                    if (startCursor == null) break;
                }

            } catch (Exception e) {
                break;
            }
        }

        System.out.println("[Notion] 🗑️ " + archived + " page(s) archivée(s).");
        return archived;
    }

    // ─────────────────────────────────────────────────────────────
    //  RÉSULTAT DE SYNC
    // ─────────────────────────────────────────────────────────────

    public static class SyncResult {
        public int created;
        public int updated;
        public int deleted;
        public int failed;
        public List<String> errors = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Sync: ");
            if (created > 0) sb.append(created).append(" créé(s), ");
            if (updated > 0) sb.append(updated).append(" mis à jour, ");
            if (deleted > 0) sb.append(deleted).append(" supprimé(s), ");
            if (failed > 0) sb.append(failed).append(" échec(s), ");
            if (sb.toString().endsWith(", ")) sb.setLength(sb.length() - 2);
            if (created == 0 && updated == 0 && deleted == 0 && failed == 0)
                sb.append("Aucun changement");
            return sb.toString();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CONSTRUCTION JSON (sans bibliothèque externe)
    // ─────────────────────────────────────────────────────────────

    private String buildCreatePageJson(Evenement ev, String lieuName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"parent\": {\"database_id\": \"").append(databaseId).append("\"},");
        sb.append("\"properties\": {");

        // Titre (Title property — uses detected name: could be "Titre", "Name", etc.)
        sb.append("\"" + escapeJson(titlePropertyName) + "\": {\"title\": [{\"text\": {\"content\": \"")
                .append(escapeJson(safeStr(ev.getTitre())))
                .append("\"}}]},");

        // Date Début (Date property)
        if (ev.getDateDebut() != null) {
            sb.append("\"Date D\u00e9but\": {\"date\": {\"start\": \"")
                    .append(ev.getDateDebut().format(ISO_FMT))
                    .append("\"}},");
        }

        // Date Fin (Date property)
        if (ev.getDateFin() != null) {
            sb.append("\"Date Fin\": {\"date\": {\"start\": \"")
                    .append(ev.getDateFin().format(ISO_FMT))
                    .append("\"}},");
        }

        // Statut (Select property)
        String statut = safeStr(ev.getStatut());
        if (!statut.isEmpty()) {
            sb.append("\"Statut\": {\"select\": {\"name\": \"")
                    .append(escapeJson(statut))
                    .append("\"}},");
        }

        // Type (Select property)
        String type = safeStr(ev.getType());
        if (!type.isEmpty()) {
            sb.append("\"Type\": {\"select\": {\"name\": \"")
                    .append(escapeJson(type))
                    .append("\"}},");
        }

        // Lieu (Rich text property)
        String lieu = safeStr(lieuName);
        if (!lieu.isEmpty() && !"Sans lieu".equals(lieu)) {
            sb.append("\"Lieu\": {\"rich_text\": [{\"text\": {\"content\": \"")
                    .append(escapeJson(lieu))
                    .append("\"}}]},");
        }

        // Prix (Number property)
        sb.append("\"Prix\": {\"number\": ").append(ev.getPrix()).append("},");

        // Capacité (Number property)
        sb.append("\"Capacité\": {\"number\": ").append(ev.getCapaciteMax()).append("},");

        // EventID — clé unique pour l'upsert (évite les doublons)
        sb.append("\"EventID\": {\"number\": ").append(ev.getId()).append("},");

        // Description (Rich text property)
        String desc = safeStr(ev.getDescription());
        if (!desc.isEmpty()) {
            // Notion limite le rich_text à 2000 chars
            if (desc.length() > 1900) desc = desc.substring(0, 1900) + "…";
            sb.append("\"Description\": {\"rich_text\": [{\"text\": {\"content\": \"")
                    .append(escapeJson(desc))
                    .append("\"}}]},");
        }

        // Supprimer la dernière virgule
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }

        sb.append("}}");
        return sb.toString();
    }

    /**
     * Construit le JSON pour mettre à jour une page existante (PATCH).
     * Même structure de propriétés mais sans le parent.
     */
    private String buildUpdatePageJson(Evenement ev, String lieuName) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"properties\": {");

        // Titre (uses detected title property name)
        sb.append("\"" + escapeJson(titlePropertyName) + "\": {\"title\": [{\"text\": {\"content\": \"")
                .append(escapeJson(safeStr(ev.getTitre())))
                .append("\"}}]},");

        // Date Début
        if (ev.getDateDebut() != null) {
            sb.append("\"Date D\u00e9but\": {\"date\": {\"start\": \"")
                    .append(ev.getDateDebut().format(ISO_FMT))
                    .append("\"}},");
        }

        // Date Fin
        if (ev.getDateFin() != null) {
            sb.append("\"Date Fin\": {\"date\": {\"start\": \"")
                    .append(ev.getDateFin().format(ISO_FMT))
                    .append("\"}},");
        }

        // Statut
        String statut = safeStr(ev.getStatut());
        if (!statut.isEmpty()) {
            sb.append("\"Statut\": {\"select\": {\"name\": \"")
                    .append(escapeJson(statut))
                    .append("\"}},");
        }

        // Type
        String type = safeStr(ev.getType());
        if (!type.isEmpty()) {
            sb.append("\"Type\": {\"select\": {\"name\": \"")
                    .append(escapeJson(type))
                    .append("\"}},");
        }

        // Lieu
        String lieu = safeStr(lieuName);
        if (!lieu.isEmpty() && !"Sans lieu".equals(lieu)) {
            sb.append("\"Lieu\": {\"rich_text\": [{\"text\": {\"content\": \"")
                    .append(escapeJson(lieu))
                    .append("\"}}]},");
        } else {
            sb.append("\"Lieu\": {\"rich_text\": []},");
        }

        // Prix
        sb.append("\"Prix\": {\"number\": ").append(ev.getPrix()).append("},");

        // Capacité
        sb.append("\"Capacité\": {\"number\": ").append(ev.getCapaciteMax()).append("},");

        // EventID (keep same)
        sb.append("\"EventID\": {\"number\": ").append(ev.getId()).append("},");

        // Description
        String desc = safeStr(ev.getDescription());
        if (!desc.isEmpty()) {
            if (desc.length() > 1900) desc = desc.substring(0, 1900) + "…";
            sb.append("\"Description\": {\"rich_text\": [{\"text\": {\"content\": \"")
                    .append(escapeJson(desc))
                    .append("\"}}]},");
        } else {
            sb.append("\"Description\": {\"rich_text\": []},");
        }

        // Supprimer la dernière virgule
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }

        sb.append("}}");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    //  HELPERS JSON (sans dépendance)
    // ─────────────────────────────────────────────────────────────

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }

    /**
     * Extrait un champ simple d'un JSON (sans parser complet).
     * Cherche "field":"value" et retourne value.
     */
    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    /**
     * Extrait l'erreur du corps de réponse Notion.
     */
    private String extractNotionError(String body) {
        String msg = extractJsonField(body, "message");
        if (msg != null) return msg;
        // Tronquer la réponse si trop longue
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    /**
     * Extrait tous les IDs de pages d'une réponse query Notion.
     */
    private List<String> extractAllPageIds(String json) {
        List<String> ids = new ArrayList<>();
        // Pattern: "object":"page" suivi de "id":"xxxx"
        // On cherche les blocs {"object":"page","id":"..."}
        int searchFrom = 0;
        while (true) {
            int objIdx = json.indexOf("\"object\":\"page\"", searchFrom);
            if (objIdx == -1) break;

            // Chercher l'id le plus proche avant ou après
            int idIdx = json.indexOf("\"id\":\"", Math.max(0, objIdx - 100));
            if (idIdx == -1 || idIdx > objIdx + 200) {
                searchFrom = objIdx + 1;
                continue;
            }

            int start = idIdx + 6; // length of "id":"
            int end = json.indexOf("\"", start);
            if (end != -1) {
                String id = json.substring(start, end);
                if (id.length() > 10 && !ids.contains(id)) { // Notion IDs are UUID-like
                    ids.add(id);
                }
            }
            searchFrom = objIdx + 1;
        }
        return ids;
    }

    private static String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
