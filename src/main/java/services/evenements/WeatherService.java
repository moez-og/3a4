package services.evenements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Service météo utilisant l'API gratuite Open-Meteo (https://open-meteo.com).
 * ✅ 100% gratuit, sans clé API, sans inscription.
 * Supporte les prévisions jusqu'à 16 jours.
 */
public class WeatherService {

    // ── Résultat météo ──────────────────────────────────────────
    public static class WeatherResult {
        public final double temperature;      // °C
        public final double precipitation;    // mm
        public final double windSpeed;        // km/h
        public final int weatherCode;         // WMO code
        public final String description;      // texte FR
        public final String icon;             // emoji
        public final String advice;           // conseil utilisateur
        public final int attendancePercent;   // estimation participation %

        public WeatherResult(double temperature, double precipitation, double windSpeed,
                             int weatherCode, String description, String icon,
                             String advice, int attendancePercent) {
            this.temperature = temperature;
            this.precipitation = precipitation;
            this.windSpeed = windSpeed;
            this.weatherCode = weatherCode;
            this.description = description;
            this.icon = icon;
            this.advice = advice;
            this.attendancePercent = attendancePercent;
        }
    }

    // ── Coordonnées par défaut (Tunis) si pas de lieu ──────────
    private static final double DEFAULT_LAT = 36.8065;
    private static final double DEFAULT_LON = 10.1815;

    /**
     * Récupère la météo pour une date et un lieu donné.
     * @param lat       latitude (nullable → utilise Tunis)
     * @param lon       longitude (nullable → utilise Tunis)
     * @param eventDate date de l'événement
     * @param isOutdoor true si événement PUBLIC (en extérieur)
     * @return WeatherResult ou null si hors portée / erreur
     */
    public WeatherResult getWeather(Double lat, Double lon, LocalDateTime eventDate, boolean isOutdoor) {
        if (eventDate == null) return null;

        double latitude  = (lat != null)  ? lat : DEFAULT_LAT;
        double longitude = (lon != null) ? lon : DEFAULT_LON;
        LocalDate date = eventDate.toLocalDate();

        // Open-Meteo supporte max 16 jours de prévisions
        long daysAhead = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (daysAhead < 0 || daysAhead > 16) {
            // Si hors portée, retourner une estimation basée sur les moyennes saisonnières
            return getSeasonalEstimate(date, isOutdoor);
        }

        try {
            String urlStr = String.format(
                    "https://api.open-meteo.com/v1/forecast?"
                            + "latitude=%.4f&longitude=%.4f"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
                            + "windspeed_10m_max,weathercode"
                            + "&start_date=%s&end_date=%s"
                            + "&timezone=auto",
                    latitude, longitude, date.toString(), date.toString()
            );

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("User-Agent", "JavaFX-EventApp/1.0");

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return getSeasonalEstimate(date, isOutdoor);
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String json = reader.lines().collect(Collectors.joining());
            reader.close();
            conn.disconnect();

            return parseWeatherResponse(json, isOutdoor);

        } catch (Exception e) {
            System.err.println("WeatherService error: " + e.getMessage());
            return getSeasonalEstimate(date, isOutdoor);
        }
    }

    // ── Parsing JSON (simple, sans dépendance) ─────────────────

    private WeatherResult parseWeatherResponse(String json, boolean isOutdoor) {
        try {
            double tempMax = extractFirstDouble(json, "temperature_2m_max");
            double tempMin = extractFirstDouble(json, "temperature_2m_min");
            double precip  = extractFirstDouble(json, "precipitation_sum");
            double wind    = extractFirstDouble(json, "windspeed_10m_max");
            int wmoCode    = (int) extractFirstDouble(json, "weathercode");

            double avgTemp = (tempMax + tempMin) / 2.0;

            String desc = wmoToDescription(wmoCode);
            String icon = wmoToIcon(wmoCode);
            String advice = generateAdvice(avgTemp, precip, wind, wmoCode, isOutdoor);
            int attendance = estimateAttendance(avgTemp, precip, wind, wmoCode, isOutdoor);

            return new WeatherResult(avgTemp, precip, wind, wmoCode, desc, icon, advice, attendance);

        } catch (Exception e) {
            System.err.println("Weather parse error: " + e.getMessage());
            return null;
        }
    }

    private double extractFirstDouble(String json, String key) {
        // Cherche "key":[val, ...] et extrait val
        String search = "\"" + key + "\":[";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        int start = idx + search.length();
        int end = json.indexOf(']', start);
        if (end < 0) return 0;
        String arrContent = json.substring(start, end).trim();
        // Premier élément
        String first = arrContent.split(",")[0].trim();
        if (first.equals("null") || first.isEmpty()) return 0;
        return Double.parseDouble(first);
    }

    // ── WMO Weather Codes → Description FR ─────────────────────

    private String wmoToDescription(int code) {
        if (code == 0)  return "Ciel dégagé";
        if (code == 1)  return "Principalement dégagé";
        if (code == 2)  return "Partiellement nuageux";
        if (code == 3)  return "Couvert";
        if (code <= 49) return "Brouillard";
        if (code <= 59) return "Bruine";
        if (code <= 69) return "Pluie";
        if (code <= 79) return "Neige";
        if (code <= 84) return "Averses de pluie";
        if (code <= 86) return "Averses de neige";
        if (code == 95) return "Orage";
        if (code >= 96) return "Orage avec grêle";
        return "Conditions variables";
    }

    private String wmoToIcon(int code) {
        if (code == 0)  return "☀️";
        if (code <= 2)  return "⛅";
        if (code == 3)  return "☁️";
        if (code <= 49) return "🌫️";
        if (code <= 59) return "🌦️";
        if (code <= 69) return "🌧️";
        if (code <= 79) return "❄️";
        if (code <= 86) return "🌨️";
        if (code == 95) return "⛈️";
        if (code >= 96) return "🌩️";
        return "🌤️";
    }

    // ── Conseil utilisateur ─────────────────────────────────────

    private String generateAdvice(double temp, double precip, double wind, int wmoCode, boolean isOutdoor) {
        StringBuilder sb = new StringBuilder();

        // Météo
        if (wmoCode >= 95) {
            sb.append("⚠️ Orage prévu ! ");
            if (isOutdoor) sb.append("Événement en extérieur fortement déconseillé.");
            else sb.append("Prévoyez un plan B en intérieur.");
        } else if (precip > 10) {
            sb.append("🌧️ Pluie importante prévue (").append(String.format("%.0f", precip)).append(" mm). ");
            if (isOutdoor) sb.append("Apportez un imperméable ou envisagez un report.");
            else sb.append("Prévoyez un parapluie pour le trajet.");
        } else if (precip > 2) {
            sb.append("🌦️ Pluie légère possible (").append(String.format("%.0f", precip)).append(" mm). ");
            sb.append("Prenez un parapluie par précaution.");
        } else if (temp > 35) {
            sb.append("🥵 Forte chaleur prévue (").append(String.format("%.0f", temp)).append("°C). ");
            sb.append("Restez hydraté et protégez-vous du soleil.");
        } else if (temp < 5) {
            sb.append("🥶 Froid prévu (").append(String.format("%.0f", temp)).append("°C). ");
            sb.append("Habillez-vous chaudement !");
        } else if (wind > 50) {
            sb.append("💨 Vents forts (").append(String.format("%.0f", wind)).append(" km/h). ");
            if (isOutdoor) sb.append("Les installations extérieures pourraient être affectées.");
        } else {
            sb.append("✅ Conditions météo favorables ! ");
            if (temp >= 18 && temp <= 28 && precip < 1) {
                sb.append("Temps idéal pour y aller.");
            } else {
                sb.append("Le temps devrait être agréable.");
            }
        }

        return sb.toString();
    }

    // ── Estimation de présence ──────────────────────────────────

    /**
     * Estime le pourcentage de présence des inscrits selon la météo.
     * Base : 85% (il y a toujours des absences).
     * Pénalités selon conditions météo.
     */
    private int estimateAttendance(double temp, double precip, double wind, int wmoCode, boolean isOutdoor) {
        double base = 85.0;

        // Facteur extérieur/intérieur
        double outdoorMultiplier = isOutdoor ? 1.0 : 0.4; // intérieur = impact réduit

        // Pénalité pluie
        if (precip > 20)       base -= 35 * outdoorMultiplier;
        else if (precip > 10)  base -= 25 * outdoorMultiplier;
        else if (precip > 5)   base -= 15 * outdoorMultiplier;
        else if (precip > 2)   base -= 8  * outdoorMultiplier;

        // Pénalité vent
        if (wind > 60)         base -= 20 * outdoorMultiplier;
        else if (wind > 40)    base -= 10 * outdoorMultiplier;

        // Pénalité température extrême
        if (temp > 40)         base -= 20 * outdoorMultiplier;
        else if (temp > 35)    base -= 10 * outdoorMultiplier;
        else if (temp < 0)     base -= 15 * outdoorMultiplier;
        else if (temp < 5)     base -= 8  * outdoorMultiplier;

        // Pénalité orage
        if (wmoCode >= 95)     base -= 25 * outdoorMultiplier;

        // Bonus beau temps
        if (wmoCode <= 1 && temp >= 18 && temp <= 28 && precip < 1) {
            base += 5; // conditions parfaites
        }

        return (int) Math.max(20, Math.min(95, base));
    }

    // ── Estimation saisonnière (quand prévisions indisponibles) ─

    private WeatherResult getSeasonalEstimate(LocalDate date, boolean isOutdoor) {
        int month = date.getMonthValue();

        // Moyennes approximatives pour la Tunisie
        double temp;
        double precip;
        double wind = 15;
        int wmoCode;
        String desc;
        String icon;

        if (month >= 6 && month <= 8) { // été
            temp = 32; precip = 2; wmoCode = 0;
            desc = "Estimation : été chaud et ensoleillé";
            icon = "☀️";
        } else if (month >= 12 || month <= 2) { // hiver
            temp = 12; precip = 40; wmoCode = 61;
            desc = "Estimation : hiver frais et pluvieux possible";
            icon = "🌧️";
        } else if (month >= 3 && month <= 5) { // printemps
            temp = 20; precip = 20; wmoCode = 2;
            desc = "Estimation : printemps doux et variable";
            icon = "⛅";
        } else { // automne
            temp = 22; precip = 30; wmoCode = 2;
            desc = "Estimation : automne doux avec risques de pluie";
            icon = "🌤️";
        }

        String advice = generateAdvice(temp, precip, wind, wmoCode, isOutdoor);
        int attendance = estimateAttendance(temp, precip, wind, wmoCode, isOutdoor);

        // Ajouter une note claire que c'est une estimation saisonnière
        desc += " (moyenne saisonnière — prévisions exactes disponibles 16 jours avant)";

        return new WeatherResult(temp, precip, wind, wmoCode, desc, icon, advice, attendance);
    }
}
