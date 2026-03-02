package services.weather;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service pour récupérer les données météo via OpenWeatherMap API.
 * Utilise les coordonnées GPS (latitude, longitude) pour obtenir la météo actuelle.
 * Parse le JSON manuellement (aucune dépendance externe requise).
 */
public class WeatherService {

    private static final String API_KEY = "e2cd7474a04693143ae50e42eca0738c";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";

    /**
     * Récupère les données météo pour un lieu donné.
     * 
     * @param latitude   Latitude du lieu
     * @param longitude  Longitude du lieu
     * @return WeatherData avec température, description, icône, etc.
     * @throws Exception Si l'appel API échoue
     */
    public static WeatherData getWeather(double latitude, double longitude) throws Exception {
        String url = String.format(
            "%s?lat=%.4f&lon=%.4f&appid=%s&units=metric&lang=fr",
            BASE_URL, latitude, longitude, API_KEY
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(java.time.Duration.ofSeconds(5))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Erreur API météo: " + response.statusCode());
        }

        return parseWeatherResponse(response.body());
    }

    /**
     * Parse la réponse JSON d'OpenWeatherMap avec regex (sans bibliothèque JSON).
     *
     * Exemple de réponse :
     * {"weather":[{"main":"Clear","description":"ciel dégagé","icon":"01d"}],
     *  "main":{"temp":22.5,"feels_like":21.8,"humidity":65},
     *  "wind":{"speed":3.2}}
     */
    private static WeatherData parseWeatherResponse(String json) {
        try {
            double temperature = extractDouble(json, "\"temp\"\\s*:\\s*([\\-0-9.]+)");
            double feelsLike   = extractDouble(json, "\"feels_like\"\\s*:\\s*([\\-0-9.]+)");
            int    humidity    = (int) extractDouble(json, "\"humidity\"\\s*:\\s*([0-9.]+)");
            double windSpeed   = extractDouble(json, "\"speed\"\\s*:\\s*([0-9.]+)");
            String mainWeather = extractString(json, "\"main\"\\s*:\\s*\"([^\"]+)\"");
            String description = extractString(json, "\"description\"\\s*:\\s*\"([^\"]+)\"");
            String icon        = extractString(json, "\"icon\"\\s*:\\s*\"([^\"]+)\"");

            // Capitaliser la première lettre de la description
            if (description != null && !description.isEmpty()) {
                description = description.substring(0, 1).toUpperCase() + description.substring(1);
            }

            return new WeatherData(
                temperature,
                feelsLike,
                humidity,
                windSpeed,
                description != null ? description : "Inconnu",
                mainWeather != null ? mainWeather : "Unknown",
                icon != null ? icon : ""
            );
        } catch (Exception e) {
            throw new RuntimeException("Erreur parsing météo: " + e.getMessage(), e);
        }
    }

    // ── Utilitaires parsing ─────────────────────────────────────────

    private static double extractDouble(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private static String extractString(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Data class pour la météo
    // ════════════════════════════════════════════════════════════════

    public static class WeatherData {
        public final double temperature;      // °C
        public final double feelsLike;        // °C (ressenti)
        public final int humidity;            // %
        public final double windSpeed;        // m/s
        public final String description;      // "Dégagé", "Nuageux", etc.
        public final String mainWeather;      // "Clear", "Clouds", "Rain"
        public final String icon;             // Code icône OpenWeatherMap

        public WeatherData(double temperature, double feelsLike, int humidity,
                          double windSpeed, String description, String mainWeather, String icon) {
            this.temperature = temperature;
            this.feelsLike = feelsLike;
            this.humidity = humidity;
            this.windSpeed = windSpeed;
            this.description = description;
            this.mainWeather = mainWeather;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return String.format("🌡️ %.1f°C (%s) | 💨 %.1f m/s | 💧 %d%%", 
                temperature, description, windSpeed, humidity);
        }

        /**
         * Retourne une icône emoji basée sur la météo.
         */
        public String getEmoji() {
            return switch (mainWeather.toLowerCase()) {
                case "clear" -> "☀️";
                case "clouds" -> "☁️";
                case "rain" -> "🌧️";
                case "thunderstorm" -> "⛈️";
                case "snow" -> "❄️";
                case "mist", "smoke", "haze", "dust", "fog", "sand", "ash", "squall", "tornado" -> "🌫️";
                case "drizzle" -> "🌦️";
                default -> "🌤️";
            };
        }
    }
}
