package server;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class HttpResponse {

    private int statusCode = 200;
    private String statusText = "OK";

    //Preserve insertion order so headers print correctly
    private final Map<String, String> headers = new LinkedHashMap<>();

    // Set-Cookie headers stored separately because there can be multiple
    // (a Map can't hold duplicate keys, so cookies need their own list)
    private final List<String> cookies = new ArrayList<>();

    //Body stored only as plain text
    private String body = "";

    //Status codes table
    private static final Map<Integer, String> STATUS_TEXTS = Map.ofEntries(
        Map.entry(200, "OK"),
        Map.entry(201, "Created"),
        Map.entry(204, "No Content"),
        Map.entry(400, "Bad Request"),
        Map.entry(401, "Unauthorized"),
        Map.entry(403, "Forbidden"),
        Map.entry(404, "Not Found"),
        Map.entry(405, "Method Not Allowed"),
        Map.entry(409, "Conflict"),
        Map.entry(415, "Unsupported Media Type"),
        Map.entry(500, "Internal Server Error")
    );

    //Fluent setters

    public HttpResponse status(int code) {
        this.statusCode = code;
        this.statusText = STATUS_TEXTS.getOrDefault(code, "Unknown");
        return this;
    }

    public HttpResponse header(String key, String value) {
        headers.put(key, value);
        return this;
    }

    //Set a JSON body. Automatically sets Content-Type to application/json
    public HttpResponse json(String jsonBody) {
        this.body = jsonBody;
        headers.put("Content-Type", "application/json; charset=utf-8");
        return this;
    }

    //Set a plain text body
    public HttpResponse text(String textBody) {
        this.body = textBody;
        headers.put("Content-Type", "text/plain; charset=utf-8");
        return this;
    }

    //Set an HTML body
    public HttpResponse html(String htmlBody) {
        this.body = htmlBody;
        headers.put("Content-Type", "text/html; charset=utf-8");
        return this;
    }

    // Cookies
    // Adds a Set-Cookie header to the response.
    // path is the Path scope, e.g. "/" means the cookie is sent for all paths. Use "/games" to restrict it to the games section only.
    public HttpResponse setCookie(String name, String value, int maxAge, String path) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value);
        cookie.append("; Path=").append(path);
 
        if (maxAge > 0) {
            cookie.append("; Max-Age=").append(maxAge);
        } else if (maxAge < 0) {
            // Negative maxAge = tell the browser to delete the cookie
            cookie.append("; Max-Age=0");
        }
        // maxAge == 0 is session cookie, no Max-Age attribute added
 
        cookies.add(cookie.toString());
        return this;
    }


    //Accessors
    public int getStatusCode() {
        return statusCode;
    }

    public int getBodyLength() {
        return body.getBytes(StandardCharsets.UTF_8).length;
    }

    //Serialise to bytes

    //Builds the full HTTP response as a byte array ready to write to the socket.
    public byte[] toBytes() {

        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        //Required HTTP headers
        headers.put("Content-Length", String.valueOf(bodyBytes.length));
        headers.put("Server", "mini-http-server/1.0");

        //Build header section
        StringBuilder sb = new StringBuilder();

        sb.append("HTTP/1.1 ")
          .append(statusCode)
          .append(" ")
          .append(statusText)
          .append("\r\n");

        headers.forEach((k, v) ->
            sb.append(k).append(": ").append(v).append("\r\n")
        );

        // One Set-Cookie line per cookie (can't be merged into one line)
        for (String cookie : cookies) {
            sb.append("Set-Cookie: ").append(cookie).append("\r\n");
        }

        sb.append("\r\n");

        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.US_ASCII);

        // Combine headers + body
        byte[] full = new byte[headerBytes.length + bodyBytes.length];

        System.arraycopy(headerBytes, 0, full, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, full, headerBytes.length, bodyBytes.length);

        return full;
    }
}