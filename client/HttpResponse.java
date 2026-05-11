package client;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {
    public int statusCode;
    public String statusText;
    public String httpVersion;
    public Map<String, String> headers = new LinkedHashMap<>();
    public byte[] bodyBytes = new byte[0];
    public String body = "";
    
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
