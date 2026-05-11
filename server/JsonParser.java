package server;

import java.util.LinkedHashMap;
import java.util.Map;

/* JSON parser and builder
 * Supports string, number and boolean values.
 * Doesn't support nested objects or arrays.
 */
public class JsonParser {

    // Parses a flat JSON into a String map
    public static Map<String, String> parseObject(String json) {
        if (json == null) throw new IllegalArgumentException("Body is null");
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) 
        {
            throw new IllegalArgumentException("Body is not a JSON object");
        }

        Map<String, String> result = new LinkedHashMap<>();
        // Remove outer brquets
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return result;

        // Split by commas that are not inside strings
        boolean inString = false;
        int depth = 0;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // Toggle inString when see a "
            if (c == '"' && (i == 0 || content.charAt(i - 1) != '\\')) inString = !inString;
            if (!inString && (c == '{' || c == '[')) depth++;
            if (!inString && (c == '}' || c == ']')) depth--;
            if (!inString && depth == 0 && c == ',') 
            {
                parseEntry(content.substring(start, i).trim(), result);
                start = i + 1;
            }
        }
        parseEntry(content.substring(start).trim(), result);

        return result;
    }

    private static void parseEntry(String entry, Map<String, String> result) {
        if (entry.isEmpty()) return;
        int twoDotsIndex = entry.indexOf(':');
        if (twoDotsIndex == -1) throw new IllegalArgumentException("Invalid JSON entry: " + entry);

        String key = unQuote(entry.substring(0, twoDotsIndex).trim());
        String value = entry.substring(twoDotsIndex + 1).trim();

        // Clean up values
        if (value.startsWith("\"")) {
            value = unQuote(value);
        }
        if (value.equals("null")) value = "";

        result.put(key, value);
    }

    private static String unQuote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) 
        {
            s = s.substring(1, s.length() - 1);
        }

        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    /* Builds a JSON from a map
     * Numbers and booleans without quotes, strings get quotes
     */
    public static String buildObject(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeString(e.getKey())).append("\":");
            String v = e.getValue();
            // If number, boolean or null, no quote
            if (isNumeric(v) || v.equals("true") || v.equals("false") || v.equals("null")) {
                sb.append(v);
            } else {
                sb.append("\"").append(escapeString(v)).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Builds an array from serialised JSONs
    public static String buildArray(Iterable<String> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String item : items) {
            if (!first) sb.append(",");
            first = false;
            sb.append(item);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
                Double.parseDouble(s);
                return true; 
            }
        catch (NumberFormatException e) { return false; }
    }
}
