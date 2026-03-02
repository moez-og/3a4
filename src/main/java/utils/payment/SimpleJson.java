package utils.payment;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire minimaliste d'extraction JSON (évite d'ajouter Gson/Jackson).
 * Fonctionne pour des réponses JSON simples / à un niveau.
 */
public final class SimpleJson {
    private SimpleJson() {}

    /**
     * Extrait la valeur string associée à {@code key} dans le JSON.
     * Ex: {@code extractString(json, "url")} → valeur de "url":"..."
     */
    public static String extractString(String json, String key) {
        if (json == null || key == null) return null;
        Matcher m = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*?)\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extrait un nombre (double) associé à {@code key}.
     * Ex: {@code extractDouble(json, "amount")} → valeur numérique
     */
    public static double extractDouble(String json, String key) {
        if (json == null || key == null) return 0;
        Matcher m = Pattern
                .compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([\\d.eE+\\-]+)")
                .matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    /**
     * Extrait un double imbriqué : {"outer": {"inner": 123.45}}
     * Ex: {@code extractNestedDouble(json, "bitcoin", "usd")}
     */
    public static double extractNestedDouble(String json, String outerKey, String innerKey) {
        if (json == null) return 0;
        int idx = json.indexOf("\"" + outerKey + "\"");
        if (idx < 0) return 0;
        int braceStart = json.indexOf("{", idx);
        int braceEnd   = json.indexOf("}", braceStart);
        if (braceStart < 0 || braceEnd < 0) return 0;
        return extractDouble(json.substring(braceStart, braceEnd + 1), innerKey);
    }

    /**
     * Construit un objet JSON simple (pas de tableaux) à partir de paires clé/valeur.
     * Les valeurs null sont ignorées. Les nombres ne sont pas quotés.
     */
    public static String buildObject(Object... kvPairs) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            String k = String.valueOf(kvPairs[i]);
            Object v = kvPairs[i + 1];
            if (v == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(k).append("\":");
            if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(v).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
