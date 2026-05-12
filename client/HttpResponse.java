package client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse {
    public int statusCode;
    public String statusText;
    public String httpVersion;
    public Map<String, String> headers = new LinkedHashMap<>();
    public byte[] bodyBytes = new byte[0];
    public String body = "";
    
    // Cookies
    // Set-Cookie headers are stored here separately from normal headers because a response can contain multiple Set-Cookie lines and a Map
    // cant haev multiple keys
    // Populated by HttpClient.parseResponse() and consumed by storeCookies().
    public List<String> setCookieHeaders = new ArrayList<>();

    public boolean isSuccess() 
    {
        return statusCode >= 200 && statusCode < 300;
    }

    // Returns the value of the content-type header, if not found returns empty string
    public String getContentType() 
    {
        if (headers.getOrDefault("Content-Type", null) != null)
        {
            return headers.get("Content-Type");
        }
        return headers.getOrDefault("content-type", "");
    }
}
