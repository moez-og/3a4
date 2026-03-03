package utils.json;

import java.util.ArrayList;
import java.util.List;

public final class JsonStringArray {

    private JsonStringArray() {}

    public static String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (String s : list) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(s == null ? "" : s)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public static List<String> fromJson(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;

        String t = json.trim();
        if (t.isEmpty() || t.equals("[]")) return out;

        // parse simple: ["a","b"] avec escapes
        int i = 0;
        int n = t.length();

        // doit commencer par [
        while (i < n && Character.isWhitespace(t.charAt(i))) i++;
        if (i >= n || t.charAt(i) != '[') return out;
        i++;

        while (i < n) {
            while (i < n && (Character.isWhitespace(t.charAt(i)) || t.charAt(i) == ',')) i++;
            if (i < n && t.charAt(i) == ']') break;

            if (i >= n || t.charAt(i) != '"') break;
            i++;

            StringBuilder s = new StringBuilder();
            boolean esc = false;
            while (i < n) {
                char c = t.charAt(i++);
                if (esc) {
                    switch (c) {
                        case '"': s.append('"'); break;
                        case '\\': s.append('\\'); break;
                        case '/': s.append('/'); break;
                        case 'b': s.append('\b'); break;
                        case 'f': s.append('\f'); break;
                        case 'n': s.append('\n'); break;
                        case 'r': s.append('\r'); break;
                        case 't': s.append('\t'); break;
                        case 'u':
                            if (i + 3 < n) {
                                String hex = t.substring(i, i + 4);
                                try { s.append((char) Integer.parseInt(hex, 16)); }
                                catch (Exception ignored) {}
                                i += 4;
                            }
                            break;
                        default: s.append(c);
                    }
                    esc = false;
                } else {
                    if (c == '\\') {
                        esc = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        s.append(c);
                    }
                }
            }
            out.add(s.toString());

            while (i < n && Character.isWhitespace(t.charAt(i))) i++;
            if (i < n && t.charAt(i) == ']') break;
        }

        return out;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int k = hex.length(); k < 4; k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}