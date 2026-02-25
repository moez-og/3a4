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
import javafx.scene.layout.VBox;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
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

    @FXML private VBox       messagesBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField  inputField;
    @FXML private Button     sendBtn;
    @FXML private Label      statusLabel;

    // ============================================================
    //  CONFIGURATION GROQ
    //  - Creer une cle sur https://console.groq.com
    //  - Exporter GROQ_API_KEY dans votre environment
    // ============================================================
    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");
    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL   = "llama-3.3-70b-versatile";

    private final LieuService     lieuService = new LieuService();
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final List<String[]>  history     = new ArrayList<>();
    private List<Lieu>            allLieux    = new ArrayList<>();

    @FXML
    public void initialize() {
        inputField.setOnAction(e -> sendMessage());
        executor.submit(() -> {
            try { allLieux = lieuService.getAll(); }
            catch (Exception ignored) {}
        });
        addBotMessage("👋 Bonjour ! Je suis votre assistant Travel Guide propulsé par LLaMA 3 (Groq).\n\n"
            + "Je connais tous les lieux de votre base de données.\n\n"
            + "Exemples :\n"
            + "• « lieux à Tunis »\n"
            + "• « restaurants à La Marsa »\n"
            + "• « horaires de [lieu] »\n"
            + "• « lieux moins de 30 TND »\n\n"
            + "Que puis-je faire pour vous ? 🗺️");
    }

    @FXML
    public void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();
        inputField.setDisable(true);
        sendBtn.setDisable(true);
        setStatus("Réflexion en cours...", true);
        addUserMessage(text);

        executor.submit(() -> {
            try {
                if (allLieux.isEmpty()) allLieux = lieuService.getAll();
                String response = callGroqAPI(text);
                Platform.runLater(() -> {
                    addBotMessage(response);
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    inputField.requestFocus();
                    setStatus("", false);
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
                Platform.runLater(() -> {
                    addBotMessage("❌ Erreur : " + msg
                        + "\n\n🔍 Vérifications :\n"
                        + "• Clé commence par gsk_ ?\n"
                        + "• Connexion internet active ?\n"
                        + "• VPN désactivé ?");
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    setStatus("", false);
                });
            }
        });
    }

    // ============================================================
    //  APPEL API GROQ (compatible OpenAI)
    // ============================================================

    private String callGroqAPI(String userMessage) throws Exception {
        String apiKey = getGroqApiKey();
        history.add(new String[]{"user", userMessage});

        // Construire le tableau messages JSON avec system + historique + question
        StringBuilder msgs = new StringBuilder("[");

        // Message système avec toutes les données BDD
        msgs.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(buildSystemPrompt())).append("\"}");

        // Historique (max 10 derniers échanges)
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            msgs.append(",{\"role\":\"").append(history.get(i)[0])
                .append("\",\"content\":\"").append(escapeJson(history.get(i)[1])).append("\"}");
        }
        msgs.append("]");

        String body = "{"
            + "\"model\":\"" + GROQ_MODEL + "\","
            + "\"messages\":" + msgs + ","
            + "\"max_tokens\":1024,"
            + "\"temperature\":0.7"
            + "}";

        URL url = new URL(GROQ_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        Scanner sc = new Scanner(
            code == 200 ? conn.getInputStream() : conn.getErrorStream(),
            StandardCharsets.UTF_8
        );
        StringBuilder resp = new StringBuilder();
        while (sc.hasNextLine()) resp.append(sc.nextLine()).append("\n");
        sc.close();

        if (code != 200) throw new Exception("HTTP " + code + " — " + resp.toString().trim());

        String answer = extractGroqText(resp.toString());
        history.add(new String[]{"assistant", answer});
        return answer;
    }

    private String getGroqApiKey() {
        if (GROQ_API_KEY == null || GROQ_API_KEY.trim().isEmpty()) {
            throw new IllegalStateException("Missing GROQ_API_KEY environment variable");
        }
        return GROQ_API_KEY.trim();
    }

    // ============================================================
    //  SYSTEM PROMPT AVEC DONNÉES BDD
    // ============================================================

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant expert en voyages pour l'application Travel Guide. ");
        sb.append("Tu parles TOUJOURS en français. Tu es amical, concis et précis. ");
        sb.append("Utilise des emojis pour rendre tes réponses vivantes. ");
        sb.append("Quand on te demande un lieu, donne ses informations complètes. ");
        sb.append("Quand on te demande des recommandations, propose les plus pertinents.\n\n");

        sb.append("BASE DE DONNÉES DES LIEUX (").append(allLieux.size()).append(" lieux au total) :\n\n");
        for (Lieu l : allLieux) {
            sb.append("NOM: ").append(s(l.getNom()))
              .append(" | VILLE: ").append(s(l.getVille()))
              .append(" | CATEGORIE: ").append(s(l.getCategorie()))
              .append(" | ADRESSE: ").append(s(l.getAdresse()));
            if (!s(l.getTelephone()).isBlank())
                sb.append(" | TEL: ").append(l.getTelephone());
            if (!s(l.getSiteWeb()).isBlank())
                sb.append(" | WEB: ").append(l.getSiteWeb());
            if (!s(l.getDescription()).isBlank())
                sb.append(" | DESC: ").append(l.getDescription());
            if (l.getBudgetMin() != null)
                sb.append(" | BUDGET_MIN: ").append(l.getBudgetMin()).append("TND");
            if (l.getBudgetMax() != null)
                sb.append(" | BUDGET_MAX: ").append(l.getBudgetMax()).append("TND");
            if (l.getHoraires() != null) {
                l.getHoraires().stream().filter(LieuHoraire::isOuvert).forEach(h ->
                    sb.append(" | ").append(h.getJour()).append(": ")
                      .append(s(h.getHeureOuverture1())).append("-").append(s(h.getHeureFermeture1()))
                );
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    //  EXTRACTION TEXTE RÉPONSE GROQ (format OpenAI)
    // ============================================================

    private String extractGroqText(String json) {
        try {
            // Format: {"choices":[{"message":{"content":"..."}}]}
            int idx = json.indexOf("\"content\":");
            if (idx < 0) return "Réponse inattendue : " + json.substring(0, Math.min(300, json.length()));
            int start = json.indexOf("\"", idx + 10) + 1;
            int end = start;
            // Trouver la fin en gérant les échappements
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '"' && json.charAt(end - 1) != '\\') break;
                end++;
            }
            if (start <= 0 || end <= start) return json;
            return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .replace("**", "")
                .replace("## ", "")
                .replace("* ", "• ");
        } catch (Exception e) {
            return "Erreur parsing : " + e.getMessage();
        }
    }

    // ============================================================
    //  UI
    // ============================================================

    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 8, 4, 60));
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.getStyleClass().add("chatBubbleUser");
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(4, 60, 4, 8));
        Label avatar = new Label("🤖");
        avatar.getStyleClass().add("chatAvatar");
        avatar.setMinSize(34, 34);
        avatar.setMaxSize(34, 34);
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(500);
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
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    @FXML
    public void clearHistory() {
        history.clear();
        messagesBox.getChildren().clear();
        addBotMessage("Conversation réinitialisée. Comment puis-je vous aider ? 😊");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String s(String v) { return v == null ? "" : v; }
}
