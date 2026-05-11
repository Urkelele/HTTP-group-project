package server;

import java.util.LinkedHashMap;
import java.util.Map;

//Represents a parsed incoming HTTP request.
public class HttpRequest {
    public String method  = "";
    public String path    = "";
    public String version = "";
    public Map<String, String> headers = new LinkedHashMap<>();
    public byte[] bodyBytes = new byte[0];
    public String body      = "";

    //Helpers

    //Returns the value of a header (case-insensitive), or null if absent
    public String getHeader(String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    //Returns the Content-Type header value (lowercase), or empty string
    public String getContentType() {
        String ct = getHeader("Content-Type");
        return ct == null ? "" : ct.toLowerCase();
    }

    @Override
    public String toString() {
        return method + " " + path + " " + version;
    }
}
