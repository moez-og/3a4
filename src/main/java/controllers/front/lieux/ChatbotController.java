package controllers.front.lieux;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import models.lieux.Lieu;
import services.lieux.LieuService;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatbotController {

    @FXML private VBox messagesBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField inputField;
    @FXML private Button sendBtn;
    @FXML private Label statusLabel;

    private final LieuService lieuService = new LieuService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Historique de conversation pour le contexte
    private final List<String[]> history = new ArrayList<>();

    // Clé API Anthropic - à remplacer par la vraie clé
    private static final String API_KEY = "VOTRE_CLE_API_ICI";
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-3-haiku-20240307";

    @FXML
    public void initialize() {
        // Permettre envoi avec Entrée
        inputField.setOnAction(e -> sendMessage());

        // Message de bienvenue
        addBotMessage("👋 Bonjour ! Je suis votre assistant Travel Guide.\n\n"
            + "Je connais tous les lieux de notre base de données et je peux vous aider à :\n"
            + "• Trouver un lieu par ville ou catégorie\n"
            + "• Obtenir les horaires d'un lieu\n"
            + "• Comparer des lieux entre eux\n"
            + "• Recommander des endroits selon vos envies\n\n"
            + "Que puis-je faire pour vous ? 🗺️");
    }

    @FXML
    public void sendMessage() {
        String userText = inputField.getText().trim();
        if (userText.isEmpty()) return;

        inputField.clear();
        inputField.setDisable(true);
        sendBtn.setDisable(true);
        setStatus("En train de répondre...", true);

        // Afficher le message utilisateur
        addUserMessage(userText);

        // Envoyer à l'API en arrière-plan
        executor.submit(() -> {
            try {
                String context = buildLieuxContext();
                String response = callClaudeAPI(userText, context);
                Platform.runLater(() -> {
                    addBotMessage(response);
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    inputField.requestFocus();
                    setStatus("", false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addBotMessage("❌ Erreur de connexion à l'API. Vérifiez votre clé API.\n\nDétail: " + e.getMessage());
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    setStatus("", false);
                });
            }
        });
    }

    // ===================== CONSTRUCTION DU CONTEXTE =====================

    private String buildLieuxContext() {
        try {
            List<Lieu> lieux = lieuService.getAll();
            if (lieux == null || lieux.isEmpty()) return "Aucun lieu disponible.";

            StringBuilder sb = new StringBuilder();
            sb.append("BASE DE DONNÉES DES LIEUX (").append(lieux.size()).append(" lieux) :\n\n");

            for (Lieu l : lieux) {
                sb.append("---\n");
                sb.append("ID: ").append(l.getId()).append("\n");
                sb.append("Nom: ").append(safe(l.getNom())).append("\n");
                sb.append("Ville: ").append(safe(l.getVille())).append("\n");
                sb.append("Adresse: ").append(safe(l.getAdresse())).append("\n");
                sb.append("Catégorie: ").append(safe(l.getCategorie())).append("\n");
                sb.append("Type: ").append(safe(l.getType())).append("\n");
                if (!safe(l.getTelephone()).isEmpty())
                    sb.append("Téléphone: ").append(l.getTelephone()).append("\n");
                if (!safe(l.getSiteWeb()).isEmpty())
                    sb.append("Site web: ").append(l.getSiteWeb()).append("\n");
                if (!safe(l.getInstagram()).isEmpty())
                    sb.append("Instagram: ").append(l.getInstagram()).append("\n");
                if (l.getLatitude() != null && l.getLongitude() != null)
                    sb.append("Coordonnées: ").append(l.getLatitude()).append(", ").append(l.getLongitude()).append("\n");
                if (!safe(l.getDescription()).isEmpty())
                    sb.append("Description: ").append(l.getDescription()).append("\n");
                if (l.getBudgetMin() != null || l.getBudgetMax() != null) {
                    sb.append("Budget: ");
                    if (l.getBudgetMin() != null) sb.append("à partir de ").append(l.getBudgetMin()).append(" TND");
                    if (l.getBudgetMax() != null) sb.append(" jusqu'à ").append(l.getBudgetMax()).append(" TND");
                    sb.append("\n");
                }
                if (l.getHoraires() != null && !l.getHoraires().isEmpty()) {
                    sb.append("Horaires: ");
                    l.getHoraires().forEach(h -> {
                        if (h.isOuvert()) {
                            sb.append(h.getJour()).append(" (")
                              .append(safe(h.getHeureOuverture1())).append("-")
                              .append(safe(h.getHeureFermeture1())).append(") ");
                        }
                    });
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Impossible de charger les lieux: " + e.getMessage();
        }
    }

    // ===================== APPEL API CLAUDE =====================

    private String callClaudeAPI(String userMessage, String lieuxContext) throws Exception {
        // Ajouter au historique
        history.add(new String[]{"user", userMessage});

        // Construire les messages avec historique (max 10 derniers échanges)
        StringBuilder messagesJson = new StringBuilder("[");
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            String[] msg = history.get(i);
            if (i > start) messagesJson.append(",");
            messagesJson.append("{\"role\":\"").append(escape(msg[0]))
                        .append("\",\"content\":\"").append(escape(msg[1])).append("\"}");
        }
        messagesJson.append("]");

        String systemPrompt = "Tu es un assistant expert en voyages et découverte de lieux pour l'application Travel Guide. "
            + "Tu connais parfaitement la base de données des lieux suivante et tu aides les utilisateurs à trouver "
            + "les meilleurs endroits selon leurs besoins. Réponds toujours en français, de manière amicale et concise. "
            + "Si on te demande un lieu précis, donne ses détails complets. "
            + "Si on te demande des recommandations, propose les plus pertinents avec une courte description. "
            + "Utilise des emojis pour rendre tes réponses plus vivantes.\n\n"
            + lieuxContext;

        String requestBody = "{"
            + "\"model\":\"" + MODEL + "\","
            + "\"max_tokens\":1024,"
            + "\"system\":\"" + escape(systemPrompt) + "\","
            + "\"messages\":" + messagesJson
            + "}";

        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", API_KEY);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        Scanner scanner;
        if (code == 200) {
            scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
        } else {
            scanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
        }

        StringBuilder response = new StringBuilder();
        while (scanner.hasNextLine()) response.append(scanner.nextLine()).append("\n");
        scanner.close();

        String body = response.toString();

        if (code != 200) {
            throw new Exception("API error " + code + ": " + body);
        }

        // Extraire le texte de la réponse JSON
        String answer = extractTextFromResponse(body);

        // Ajouter la réponse à l'historique
        history.add(new String[]{"assistant", answer});

        return answer;
    }

    /** Extrait le champ text du JSON de réponse Anthropic */
    private String extractTextFromResponse(String json) {
        try {
            int idx = json.indexOf("\"text\":");
            if (idx < 0) return "Réponse inattendue de l'API.";
            int start = json.indexOf("\"", idx + 7) + 1;
            int end = json.lastIndexOf("\"", json.indexOf("\"type\":", start) - 3);
            if (start <= 0 || end <= start) return json;
            return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
        } catch (Exception e) {
            return "Erreur parsing réponse.";
        }
    }

    // ===================== UI HELPERS =====================

    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 8, 4, 48));

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(380);
        bubble.getStyleClass().add("chatBubbleUser");

        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 48, 4, 8));

        // Avatar bot
        Label avatar = new Label("🤖");
        avatar.getStyleClass().add("chatAvatar");
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.getStyleClass().add("chatBubbleBot");
        HBox.setHgrow(bubble, Priority.SOMETIMES);

        row.getChildren().addAll(avatar, bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void setStatus(String text, boolean visible) {
        if (statusLabel != null) {
            statusLabel.setText(text);
            statusLabel.setVisible(visible);
            statusLabel.setManaged(visible);
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            messagesScroll.setVvalue(1.0);
        });
    }

    public void clearHistory() {
        history.clear();
        messagesBox.getChildren().clear();
        addBotMessage("Conversation réinitialisée. Comment puis-je vous aider ? 😊");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String safe(String s) { return s == null ? "" : s; }
}
