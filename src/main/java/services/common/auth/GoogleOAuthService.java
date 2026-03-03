package services.common.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleOAuthService {
    private static final String ENV_CLIENT_ID = "GOOGLE_CLIENT_ID";
    private static final String ENV_CLIENT_SECRET = "GOOGLE_CLIENT_SECRET";
    private static final String ENV_REDIRECT_URI = "GOOGLE_REDIRECT_URI";

    private static final String FILE_CLIENT_ID = "google.clientId";
    private static final String FILE_CLIENT_SECRET = "google.clientSecret";
    private static final String FILE_REDIRECT_URI = "google.redirectUri";

    private static final String LOCAL_SECRETS_FILE = "local-secrets.properties";

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";

    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    public GoogleUserProfile authenticate() throws Exception {
        OAuthConfig config = loadConfig();
        String state = UUID.randomUUID().toString();

        try (OAuthCodeReceiver receiver = OAuthCodeReceiver.start(config.redirectUri)) {
            String authUrl = buildAuthorizationUrl(config, state);
            openSystemBrowser(authUrl);

            String code = receiver.awaitCode(state, Duration.ofMinutes(2));
            String accessToken = exchangeCodeForAccessToken(config, code);
            return fetchUserProfile(accessToken);
        }
    }

    private OAuthConfig loadConfig() {
        Properties localSecrets = loadLocalSecrets();

        String clientId = resolveValue(System.getenv(ENV_CLIENT_ID), localSecrets.getProperty(FILE_CLIENT_ID));
        String clientSecret = resolveValue(System.getenv(ENV_CLIENT_SECRET), localSecrets.getProperty(FILE_CLIENT_SECRET));
        String redirectUriRaw = resolveValue(System.getenv(ENV_REDIRECT_URI), localSecrets.getProperty(FILE_REDIRECT_URI));
        if (isBlank(redirectUriRaw)) {
            redirectUriRaw = "http://localhost:8765/oauth2/callback";
        }

        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new IllegalStateException("Configuration Google OAuth manquante: renseigner GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET ou local-secrets.properties");
        }

        URI redirectUri = URI.create(redirectUriRaw.trim());
        if (redirectUri.getHost() == null || redirectUri.getPort() < 0) {
            throw new IllegalStateException("google.redirectUri invalide: host/port requis, ex: http://localhost:8765/oauth2/callback");
        }

        return new OAuthConfig(clientId.trim(), clientSecret.trim(), redirectUri);
    }

    private String buildAuthorizationUrl(OAuthConfig config, String state) {
        String scope = "openid email profile";
        return AUTH_ENDPOINT
                + "?client_id=" + urlEncode(config.clientId)
                + "&redirect_uri=" + urlEncode(config.redirectUri.toString())
                + "&response_type=code"
                + "&scope=" + urlEncode(scope)
                + "&access_type=offline"
                + "&prompt=select_account"
                + "&state=" + urlEncode(state);
    }

    private String exchangeCodeForAccessToken(OAuthConfig config, String code) throws Exception {
        String form = "code=" + urlEncode(code)
                + "&client_id=" + urlEncode(config.clientId)
                + "&client_secret=" + urlEncode(config.clientSecret)
                + "&redirect_uri=" + urlEncode(config.redirectUri.toString())
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_ENDPOINT))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Échec échange token Google: HTTP " + response.statusCode());
        }

        String accessToken = readJsonString(response.body(), "access_token");
        if (isBlank(accessToken)) {
            throw new IllegalStateException("Réponse Google invalide: access_token manquant");
        }
        return accessToken;
    }

    private GoogleUserProfile fetchUserProfile(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USERINFO_ENDPOINT))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Échec récupération profil Google: HTTP " + response.statusCode());
        }

        String body = response.body();
        String email = readJsonString(body, "email");
        String name = readJsonString(body, "name");
        String givenName = readJsonString(body, "given_name");
        String familyName = readJsonString(body, "family_name");
        String pictureUrl = readJsonString(body, "picture");

        if (isBlank(email)) {
            throw new IllegalStateException("Le compte Google ne retourne pas d'email exploitable");
        }

        return new GoogleUserProfile(email, name, givenName, familyName, pictureUrl);
    }

    private String readJsonString(String json, String key) {
        Pattern pattern = Pattern.compile(String.format(JSON_STRING_PATTERN.pattern(), Pattern.quote(key)));
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private void openSystemBrowser(String url) throws Exception {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IllegalStateException("Ouverture navigateur non supportée. Ouvrir manuellement: " + url);
        }
        Desktop.getDesktop().browse(URI.create(url));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveValue(String envValue, String fileValue) {
        if (!isBlank(envValue)) {
            return envValue.trim();
        }
        return isBlank(fileValue) ? "" : fileValue.trim();
    }

    private Properties loadLocalSecrets() {
        Properties properties = new Properties();
        Path filePath = Paths.get(LOCAL_SECRETS_FILE);
        if (!Files.exists(filePath)) {
            return properties;
        }

        try (InputStream in = Files.newInputStream(filePath)) {
            properties.load(in);
        } catch (IOException ignored) {
        }
        return properties;
    }

    public static final class GoogleUserProfile {
        private final String email;
        private final String name;
        private final String givenName;
        private final String familyName;
        private final String pictureUrl;

        public GoogleUserProfile(String email, String name, String givenName, String familyName, String pictureUrl) {
            this.email = email;
            this.name = name;
            this.givenName = givenName;
            this.familyName = familyName;
            this.pictureUrl = pictureUrl;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }

        public String getGivenName() {
            return givenName;
        }

        public String getFamilyName() {
            return familyName;
        }

        public String getPictureUrl() {
            return pictureUrl;
        }
    }

    private static final class OAuthConfig {
        private final String clientId;
        private final String clientSecret;
        private final URI redirectUri;

        private OAuthConfig(String clientId, String clientSecret, URI redirectUri) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
        }
    }

    private static final class OAuthCodeReceiver implements AutoCloseable {
        private final HttpServer server;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<String> code = new AtomicReference<>("");
        private final AtomicReference<String> state = new AtomicReference<>("");
        private final AtomicReference<String> error = new AtomicReference<>("");

        private OAuthCodeReceiver(HttpServer server) {
            this.server = server;
        }

        static OAuthCodeReceiver start(URI redirectUri) throws IOException {
            InetSocketAddress address = new InetSocketAddress(redirectUri.getHost(), redirectUri.getPort());
            HttpServer server = HttpServer.create(address, 0);
            OAuthCodeReceiver receiver = new OAuthCodeReceiver(server);
            String path = redirectUri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            server.createContext(path, receiver.new CallbackHandler());
            server.start();
            return receiver;
        }

        String awaitCode(String expectedState, Duration timeout) throws Exception {
            boolean done = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                throw new IllegalStateException("Timeout OAuth Google: aucune réponse reçue");
            }

            if (!isBlank(error.get())) {
                throw new IllegalStateException("Erreur Google OAuth: " + error.get());
            }

            if (!isBlank(expectedState) && !expectedState.equals(state.get())) {
                throw new IllegalStateException("État OAuth invalide (state mismatch)");
            }

            if (isBlank(code.get())) {
                throw new IllegalStateException("Code OAuth Google manquant");
            }
            return code.get();
        }

        @Override
        public void close() {
            if (server != null) {
                server.stop(0);
            }
        }

        private final class CallbackHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
                code.set(params.getOrDefault("code", ""));
                state.set(params.getOrDefault("state", ""));
                error.set(params.getOrDefault("error", ""));

                String html = "<html><head><meta charset='UTF-8'/></head><body><h3>Connexion Google réussie.</h3><p>Vous pouvez fermer cette fenêtre.</p></body></html>";
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }

                latch.countDown();
            }

            private Map<String, String> parseQuery(String rawQuery) {
                Map<String, String> map = new HashMap<>();
                if (rawQuery == null || rawQuery.isBlank()) {
                    return map;
                }
                String[] parts = rawQuery.split("&");
                for (String part : parts) {
                    if (part == null || part.isBlank()) {
                        continue;
                    }
                    String[] keyValue = part.split("=", 2);
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                    map.put(key, value);
                }
                return map;
            }
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
