package controllers.front.lieux;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import services.lieux.LieuService;

import javax.sound.sampled.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatbotController {

    @FXML private VBox       messagesBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField  inputField;
    @FXML private Button     sendBtn;
    @FXML private Button     voiceBtn;
    @FXML private Label      statusLabel;
    @FXML private VBox       historySidebar;
    @FXML private ScrollPane historyScroll;
    @FXML private Button     newChatBtn;

    // ============================================================
    //  CONFIG GROQ
    // ============================================================
    private static final String GROQ_API_KEY  = "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";
    private static final String GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_WHISPER  = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String GROQ_MODEL    = "llama-3.3-70b-versatile";
    private static final String WHISPER_MODEL = "whisper-large-v3-turbo";

    // ============================================================
    //  HISTORIQUE — fichier JSON local
    // ============================================================
    private static final Path HISTORY_DIR  = Paths.get(System.getProperty("user.home"), ".travel-guide");
    private static final Path HISTORY_FILE = HISTORY_DIR.resolve("chat-history.json");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LieuService     lieuService = new LieuService();
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final List<String[]>  history     = new ArrayList<>();
    private List<Lieu>            allLieux    = new ArrayList<>();

    // Sessions sauvegardées : chaque session = {id, titre, date, messages[]}
    private final List<ChatSession> sessions  = new ArrayList<>();
    private ChatSession             currentSession;

    // Enregistrement vocal
    private TargetDataLine micLine;
    private volatile boolean recording = false;
    private ByteArrayOutputStream audioBuffer;
    
    // Gestion de la parole (TTS)
    private volatile Process currentSpeechProcess = null;

    // ============================================================
    //  INIT
    /** Bypass SSL — nécessaire car la JVM embarquée ne reconnaît pas le cert de api.groq.com */
    private static void disableSSLVerification() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception ignored) {}
    }

    // ============================================================
    @FXML
    public void initialize() {
        disableSSLVerification(); // Fix PKIX: contourne la vérification SSL de la JVM
        inputField.setOnAction(e -> sendMessage());
        executor.submit(() -> {
            try { allLieux = lieuService.getAll(); }
            catch (Exception ignored) {}
        });
        loadHistoryFromDisk();
        refreshHistorySidebar();
        startNewChat();
    }

    // ============================================================
    //  NOUVELLE CONVERSATION
    // ============================================================
    @FXML
    public void startNewChat() {
        if (currentSession != null && !currentSession.messages.isEmpty()) {
            saveCurrentSession();
        }
        history.clear();
        messagesBox.getChildren().clear();
        
        // Ajouter une spacer région pour que les messages se positionnent en bas
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        messagesBox.getChildren().add(spacer);
        
        currentSession = new ChatSession();
        currentSession.id    = UUID.randomUUID().toString();
        currentSession.date  = LocalDateTime.now().format(DT_FMT);
        currentSession.title = "Nouvelle conversation";

        addBotMessage("👋 Bonjour ! Je suis votre assistant Travel Guide propulsé par LLaMA 3.\n\n"
            + "Exemples de questions :\n"
            + "• « lieux à Tunis »\n"
            + "• « restaurants à La Marsa »\n"
            + "• « horaires de [lieu] »\n"
            + "• « lieux moins de 30 TND »\n\n"
            + "Vous pouvez aussi utiliser le 🎤 micro pour parler !");
    }

    // ============================================================
    //  ENVOI TEXTE
    // ============================================================
    @FXML
    public void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();
        processUserMessage(text);
    }

    private void processUserMessage(String text) {
        inputField.setDisable(true);
        sendBtn.setDisable(true);
        if (voiceBtn != null) voiceBtn.setDisable(true);
        setStatus("Réflexion en cours...", true);
        addUserMessage(text);

        // Mettre à jour le titre de la session avec le premier message
        if (currentSession.title.equals("Nouvelle conversation") && !text.isEmpty()) {
            currentSession.title = text.length() > 40 ? text.substring(0, 40) + "..." : text;
            refreshHistorySidebar();
        }

        executor.submit(() -> {
            try {
                if (allLieux.isEmpty()) allLieux = lieuService.getAll();
                String response = callGroqAPI(text);
                Platform.runLater(() -> {
                    addBotMessage(response);
                    // TTS : bouton optionnel pour lire la réponse (plus d'appel automatique)
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    if (voiceBtn != null) voiceBtn.setDisable(false);
                    inputField.requestFocus();
                    setStatus("", false);
                    saveCurrentSession();
                    refreshHistorySidebar();
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
                Platform.runLater(() -> {
                    addBotMessage("❌ Erreur : " + msg);
                    inputField.setDisable(false);
                    sendBtn.setDisable(false);
                    if (voiceBtn != null) voiceBtn.setDisable(false);
                    setStatus("", false);
                });
            }
        });
    }

    // ============================================================
    //  VOICE INPUT — bouton micro
    // ============================================================
    @FXML
    public void toggleVoice() {
        if (!recording) startRecording();
        else stopRecordingAndTranscribe();
    }

    private void startRecording() {
        try {
            AudioFormat fmt = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                addBotMessage("❌ Microphone non disponible sur ce système.");
                return;
            }
            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(fmt);
            micLine.start();
            recording = true;
            audioBuffer = new ByteArrayOutputStream();

            // Style bouton rouge pulsant
            Platform.runLater(() -> {
                if (voiceBtn != null) {
                    voiceBtn.setText("⏹ Stop");
                    voiceBtn.getStyleClass().removeAll("chatVoiceBtn");
                    voiceBtn.getStyleClass().add("chatVoiceBtnRecording");
                }
                setStatus("🎤 Enregistrement...", true);
            });

            // Capture audio dans un thread séparé
            executor.submit(() -> {
                byte[] buf = new byte[4096];
                while (recording) {
                    int n = micLine.read(buf, 0, buf.length);
                    if (n > 0) audioBuffer.write(buf, 0, n);
                }
            });

        } catch (Exception e) {
            addBotMessage("❌ Impossible d'accéder au microphone : " + e.getMessage());
        }
    }

    private void stopRecordingAndTranscribe() {
        recording = false;
        if (micLine != null) { micLine.stop(); micLine.close(); }

        Platform.runLater(() -> {
            if (voiceBtn != null) {
                voiceBtn.setText("🎤");
                voiceBtn.getStyleClass().removeAll("chatVoiceBtnRecording");
                voiceBtn.getStyleClass().add("chatVoiceBtn");
            }
            setStatus("Transcription en cours...", true);
        });

        byte[] audioData = audioBuffer.toByteArray();

        executor.submit(() -> {
            try {
                String transcription = transcribeWithGroqWhisper(audioData);
                Platform.runLater(() -> {
                    if (transcription != null && !transcription.isBlank()) {
                        inputField.setText(transcription);
                        setStatus("", false);
                        // Envoyer automatiquement
                        processUserMessage(transcription);
                        inputField.clear();
                    } else {
                        addBotMessage("❌ Transcription vide. Réessayez en parlant plus clairement.");
                        setStatus("", false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addBotMessage("❌ Erreur transcription : " + e.getMessage());
                    setStatus("", false);
                });
            }
        });
    }

    // ============================================================
    //  GROQ WHISPER — transcription audio
    // ============================================================
    private String transcribeWithGroqWhisper(byte[] pcmData) throws Exception {
        // Convertir PCM raw → WAV avec header
        byte[] wavData = pcmToWav(pcmData, 16000, 16, 1);

        // Construire multipart/form-data manuellement
        String boundary = "----Boundary" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(body, true, StandardCharsets.UTF_8);

        // Champ model
        ps.print("--" + boundary + "\r\n");
        ps.print("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        ps.print(WHISPER_MODEL + "\r\n");

        // Champ language
        ps.print("--" + boundary + "\r\n");
        ps.print("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        ps.print("fr\r\n");

        // Champ fichier audio
        ps.print("--" + boundary + "\r\n");
        ps.print("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n");
        ps.print("Content-Type: audio/wav\r\n\r\n");
        ps.flush();
        body.write(wavData);
        ps.print("\r\n--" + boundary + "--\r\n");
        ps.flush();

        URL url = new URL(GROQ_WHISPER);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            body.writeTo(os);
        }

        int code = conn.getResponseCode();
        Scanner sc = new Scanner(
            code == 200 ? conn.getInputStream() : conn.getErrorStream(),
            StandardCharsets.UTF_8
        );
        StringBuilder resp = new StringBuilder();
        while (sc.hasNextLine()) resp.append(sc.nextLine());
        sc.close();

        if (code != 200) throw new Exception("Whisper HTTP " + code + ": " + resp);

        // Extraire "text" du JSON : {"text":"..."}
        String json = resp.toString();
        int idx = json.indexOf("\"text\":");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 7) + 1;
        int end   = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    /** Convertit des données PCM brutes en fichier WAV (avec header RIFF) */
    private byte[] pcmToWav(byte[] pcm, int sampleRate, int bitsPerSample, int channels) throws IOException {
        int byteRate    = sampleRate * channels * bitsPerSample / 8;
        int blockAlign  = channels * bitsPerSample / 8;
        int dataSize    = pcm.length;
        int chunkSize   = 36 + dataSize;

        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(wav);

        // RIFF header
        dos.writeBytes("RIFF");
        writeIntLE(dos, chunkSize);
        dos.writeBytes("WAVE");
        // fmt chunk
        dos.writeBytes("fmt ");
        writeIntLE(dos, 16);
        writeShortLE(dos, (short) 1);          // PCM
        writeShortLE(dos, (short) channels);
        writeIntLE(dos, sampleRate);
        writeIntLE(dos, byteRate);
        writeShortLE(dos, (short) blockAlign);
        writeShortLE(dos, (short) bitsPerSample);
        // data chunk
        dos.writeBytes("data");
        writeIntLE(dos, dataSize);
        dos.write(pcm);
        dos.flush();
        return wav.toByteArray();
    }

    private void writeIntLE(DataOutputStream dos, int v) throws IOException {
        dos.write(v & 0xFF); dos.write((v >> 8) & 0xFF);
        dos.write((v >> 16) & 0xFF); dos.write((v >> 24) & 0xFF);
    }

    private void writeShortLE(DataOutputStream dos, short v) throws IOException {
        dos.write(v & 0xFF); dos.write((v >> 8) & 0xFF);
    }

    // ============================================================
    //  TTS — synthèse vocale via PowerShell SAPI (Windows)
    // ============================================================
    private void speakText(String text) {
        // Arrêter la parole en cours avant de lancer une nouvelle
        stopSpeech();
        
        // Nettoyer le texte pour la synthèse (enlever emojis et markdown)
        String clean = text
            .replaceAll("[^\\p{L}\\p{N}\\p{P}\\s]", " ")
            .replace("**", "").replace("##", "").replace("• ", "")
            .replaceAll("\\s+", " ").trim();

        if (clean.length() > 300) clean = clean.substring(0, 300) + "...";

        final String ttsText = clean;
        executor.submit(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    // Windows : PowerShell + SAPI (natif, aucune install)
                    String ps = String.format(
                        "Add-Type -AssemblyName System.Speech; " +
                        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$s.Rate = 1; $s.Speak('%s');",
                        ttsText.replace("'", " ")
                    );
                    currentSpeechProcess = new ProcessBuilder("powershell", "-Command", ps)
                        .redirectErrorStream(true).start();
                    currentSpeechProcess.waitFor();
                } else if (os.contains("mac")) {
                    // macOS : commande say (natif)
                    currentSpeechProcess = new ProcessBuilder("say", "-v", "Thomas", ttsText)
                        .start();
                    currentSpeechProcess.waitFor();
                } else {
                    // Linux : espeak (si installé)
                    currentSpeechProcess = new ProcessBuilder("espeak", "-v", "fr", ttsText)
                        .start();
                    currentSpeechProcess.waitFor();
                }
            } catch (Exception ignored) {
                // TTS silencieux si non disponible
            } finally {
                currentSpeechProcess = null;
            }
        });
    }
    
    /**
     * Arrête la parole en cours
     */
    private void stopSpeech() {
        if (currentSpeechProcess != null) {
            try {
                currentSpeechProcess.destroyForcibly();
                currentSpeechProcess = null;
            } catch (Exception ignored) {}
        }
    }

    // ============================================================
    //  GROQ API — LLaMA
    // ============================================================
    private String callGroqAPI(String userMessage) throws Exception {
        history.add(new String[]{"user", userMessage});

        StringBuilder msgs = new StringBuilder("[");
        msgs.append("{\"role\":\"system\",\"content\":\"")
            .append(escapeJson(buildSystemPrompt())).append("\"}");

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            msgs.append(",{\"role\":\"").append(history.get(i)[0])
                .append("\",\"content\":\"").append(escapeJson(history.get(i)[1])).append("\"}");
        }
        msgs.append("]");

        String body = "{\"model\":\"" + GROQ_MODEL + "\",\"messages\":" + msgs
            + ",\"max_tokens\":1024,\"temperature\":0.7}";

        URL url = new URL(GROQ_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
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

        // Sauvegarder dans la session courante
        currentSession.messages.add(new String[]{"user", userMessage});
        currentSession.messages.add(new String[]{"bot", answer});

        return answer;
    }

    // ============================================================
    //  HISTORIQUE — sauvegarde/chargement JSON
    // ============================================================
    private void saveCurrentSession() {
        try {
            Files.createDirectories(HISTORY_DIR);
            // Lire l'existant ou créer
            List<String> lines = new ArrayList<>();
            if (Files.exists(HISTORY_FILE)) {
                lines = Files.readAllLines(HISTORY_FILE, StandardCharsets.UTF_8);
            }

            // Construire JSON de la session courante
            String sessionJson = sessionToJson(currentSession);

            // Remplacer ou ajouter
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("\"id\":\"" + currentSession.id + "\"")) {
                    lines.set(i, sessionJson);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(0, sessionJson);

            // Garder max 20 sessions
            if (lines.size() > 20) lines = lines.subList(0, 20);

            Files.write(HISTORY_FILE, lines, StandardCharsets.UTF_8);

            // Mettre à jour la liste en mémoire
            if (!found) sessions.add(0, currentSession);

        } catch (Exception ignored) {}
    }

    /**
     * Sauvegarde toutes les sessions en mémoire dans le fichier JSON
     */
    private void saveAllSessions() {
        try {
            Files.createDirectories(HISTORY_DIR);
            List<String> lines = new ArrayList<>();
            for (ChatSession s : sessions) {
                lines.add(sessionToJson(s));
            }
            Files.write(HISTORY_FILE, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private void loadHistoryFromDisk() {
        try {
            if (!Files.exists(HISTORY_FILE)) return;
            List<String> lines = Files.readAllLines(HISTORY_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                ChatSession s = jsonToSession(line);
                if (s != null) sessions.add(s);
            }
        } catch (Exception ignored) {}
    }

    private void refreshHistorySidebar() {
        Platform.runLater(() -> {
            if (historySidebar == null) return;
            historySidebar.getChildren().clear();

            // Titre section
            Label titre = new Label("Historique");
            titre.getStyleClass().add("historyTitle");
            historySidebar.getChildren().add(titre);

            if (sessions.isEmpty()) {
                Label empty = new Label("Aucune conversation");
                empty.getStyleClass().add("historyEmpty");
                historySidebar.getChildren().add(empty);
                return;
            }

            for (ChatSession s : sessions) {
                VBox card = new VBox(3);
                card.getStyleClass().add("historyCard");
                if (currentSession != null && s.id.equals(currentSession.id))
                    card.getStyleClass().add("historyCardActive");

                Label titleLbl = new Label(s.title);
                titleLbl.getStyleClass().add("historyCardTitle");
                titleLbl.setWrapText(true);
                titleLbl.setMaxWidth(180);

                Label dateLbl = new Label(s.date + " · " + s.messages.size()/2 + " msg");
                dateLbl.getStyleClass().add("historyCardDate");

                // Container pour le contenu et le bouton supprimer
                VBox contentBox = new VBox(3);
                contentBox.getChildren().addAll(titleLbl, dateLbl);
                HBox.setHgrow(contentBox, Priority.ALWAYS);
                
                HBox cardContent = new HBox(8);
                cardContent.setAlignment(Pos.CENTER_LEFT);
                cardContent.getChildren().add(contentBox);
                
                // Bouton supprimer
                Button deleteBtn = new Button("✕");
                deleteBtn.getStyleClass().add("historyDeleteBtn");
                deleteBtn.setPrefSize(28, 28);
                deleteBtn.setMinSize(28, 28);
                deleteBtn.setMaxSize(28, 28);
                final ChatSession session = s;
                deleteBtn.setOnAction(e -> deleteSession(session));
                
                cardContent.getChildren().add(deleteBtn);
                card.getChildren().add(cardContent);

                // Clic sur la card → recharger la session
                card.setOnMouseClicked(e -> {
                    if (!e.getTarget().toString().contains("Button")) {
                        loadSession(session);
                    }
                });

                historySidebar.getChildren().add(card);
            }
        });
    }

    private void loadSession(ChatSession s) {
        history.clear();
        messagesBox.getChildren().clear();
        
        // Ajouter une spacer région pour que les messages se positionnent en bas
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        messagesBox.getChildren().add(spacer);
        
        currentSession = s;

        for (String[] msg : s.messages) {
            if ("user".equals(msg[0])) {
                history.add(new String[]{"user", msg[1]});
                addUserMessage(msg[1]);
            } else {
                history.add(new String[]{"assistant", msg[1]});
                addBotMessage(msg[1]);
            }
        }
        refreshHistorySidebar();
    }

    /**
     * Supprime une session de l'historique
     */
    private void deleteSession(ChatSession s) {
        sessions.remove(s);
        
        // Si c'est la session courante, créer une nouvelle
        if (currentSession != null && s.id.equals(currentSession.id)) {
            startNewChat();
        } else {
            refreshHistorySidebar();
        }
        
        // Sauvegarder les changements
        saveAllSessions();
    }

    // ============================================================
    //  JSON simplifié pour sessions
    // ============================================================
    private String sessionToJson(ChatSession s) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(escapeJson(s.id)).append("\",");
        sb.append("\"title\":\"").append(escapeJson(s.title)).append("\",");
        sb.append("\"date\":\"").append(escapeJson(s.date)).append("\",");
        sb.append("\"messages\":[");
        for (int i = 0; i < s.messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"r\":\"").append(escapeJson(s.messages.get(i)[0]))
              .append("\",\"c\":\"").append(escapeJson(s.messages.get(i)[1])).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private ChatSession jsonToSession(String json) {
        try {
            ChatSession s = new ChatSession();
            s.id    = extractJsonField(json, "id");
            s.title = extractJsonField(json, "title");
            s.date  = extractJsonField(json, "date");
            // Parser messages
            int msgsStart = json.indexOf("\"messages\":[") + 12;
            int msgsEnd   = json.lastIndexOf("]");
            if (msgsStart > 12 && msgsEnd > msgsStart) {
                String msgsStr = json.substring(msgsStart, msgsEnd);
                String[] parts = msgsStr.split("\\},\\{");
                for (String p : parts) {
                    String r = extractJsonField(p, "r");
                    String c = extractJsonField(p, "c");
                    if (r != null && c != null) s.messages.add(new String[]{r, c});
                }
            }
            return s.id != null ? s : null;
        } catch (Exception e) { return null; }
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end-1) != '\\') break;
            end++;
        }
        return json.substring(start, end)
            .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // ============================================================
    //  SYSTEM PROMPT
    // ============================================================
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant expert en voyages pour l'application Travel Guide. ");
        sb.append("Tu parles TOUJOURS en français. Tu es amical, concis et précis. ");
        sb.append("Utilise des emojis. Quand on te demande un lieu, donne ses infos complètes.\n\n");
        sb.append("BASE DE DONNÉES (").append(allLieux.size()).append(" lieux) :\n");
        for (Lieu l : allLieux) {
            sb.append("NOM:").append(s(l.getNom()))
              .append("|VILLE:").append(s(l.getVille()))
              .append("|CAT:").append(s(l.getCategorie()))
              .append("|ADR:").append(s(l.getAdresse()));
            if (!s(l.getTelephone()).isBlank()) sb.append("|TEL:").append(l.getTelephone());
            if (!s(l.getSiteWeb()).isBlank())   sb.append("|WEB:").append(l.getSiteWeb());
            if (!s(l.getDescription()).isBlank()) sb.append("|DESC:").append(l.getDescription());
            if (l.getBudgetMin() != null) sb.append("|BMIN:").append(l.getBudgetMin());
            if (l.getBudgetMax() != null) sb.append("|BMAX:").append(l.getBudgetMax());
            if (l.getHoraires() != null)
                l.getHoraires().stream().filter(LieuHoraire::isOuvert).forEach(h ->
                    sb.append("|").append(h.getJour()).append(":")
                      .append(s(h.getHeureOuverture1())).append("-").append(s(h.getHeureFermeture1())));
            sb.append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    //  EXTRACTION RÉPONSE GROQ
    // ============================================================
    private String extractGroqText(String json) {
        try {
            int idx   = json.indexOf("\"content\":");
            if (idx < 0) return "Réponse inattendue.";
            int start = json.indexOf("\"", idx + 10) + 1;
            int end   = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '"' && json.charAt(end-1) != '\\') break;
                end++;
            }
            return json.substring(start, end)
                .replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\/", "/").replace("\\\\", "\\")
                .replace("**", "").replace("## ", "").replace("* ", "• ");
        } catch (Exception e) { return "Erreur parsing : " + e.getMessage(); }
    }

    // ============================================================
    //  UI
    // ============================================================
    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(3, 8, 3, 60));
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(420);
        bubble.getStyleClass().add("chatBubbleUser");
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(3, 60, 3, 8));
        Label avatar = new Label("✦");
        avatar.getStyleClass().add("chatAvatar");
        avatar.setMinSize(34, 34);
        avatar.setMaxSize(34, 34);
        
        VBox bubbleContainer = new VBox(6);
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().add("chatBubbleBot");
        bubbleContainer.getChildren().add(bubble);
        
        // Boutons pour lire et arrêter la réponse à voix haute
        HBox buttonBox = new HBox(6);
        Button readBtn = new Button("🔊 Lire la réponse");
        readBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        readBtn.getStyleClass().add("chatReadBtn");
        readBtn.setOnAction(e -> speakText(text));
        
        Button stopBtn = new Button("⏹️ Arrêter");
        stopBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-cursor: hand;");
        stopBtn.getStyleClass().add("chatStopBtn");
        stopBtn.setOnAction(e -> stopSpeech());
        
        buttonBox.getChildren().addAll(readBtn, stopBtn);
        bubbleContainer.getChildren().add(buttonBox);
        
        HBox.setHgrow(bubbleContainer, Priority.SOMETIMES);
        row.getChildren().addAll(avatar, bubbleContainer);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void setStatus(String text, boolean visible) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(text);
                statusLabel.setVisible(visible);
                statusLabel.setManaged(visible);
            }
        });
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    @FXML
    public void clearHistory() {
        saveCurrentSession();
        history.clear();
        messagesBox.getChildren().clear();
        
        // Ajouter une spacer région pour que les messages se positionnent en bas
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        messagesBox.getChildren().add(spacer);
        
        currentSession = new ChatSession();
        currentSession.id    = UUID.randomUUID().toString();
        currentSession.date  = LocalDateTime.now().format(DT_FMT);
        currentSession.title = "Nouvelle conversation";
        addBotMessage("Conversation effacée. Comment puis-je vous aider ? 😊");
    }

    // ============================================================
    //  UTILS
    // ============================================================
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }

    private String s(String v) { return v == null ? "" : v; }

    // ============================================================
    //  MODÈLE SESSION
    // ============================================================
    static class ChatSession {
        String id;
        String title;
        String date;
        List<String[]> messages = new ArrayList<>();
    }
}
