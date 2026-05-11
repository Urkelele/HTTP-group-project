package server;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple HTTP response builder.
 *
 * Usage:
 *   return new HttpResponse().status(200).json("{\"ok\":true}");
 *   return new HttpResponse().status(404).text("Not found");
 */
public class HttpResponse {

    private int statusCode = 200;
    private String statusText = "OK";

    //Preserve insertion order so headers print correctly
    private final Map<String, String> headers = new LinkedHashMap<>();

    // Body stored as raw bytes (works for text AND binary/multimedia)
    private byte[] bodyBytes = new byte[0];

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

    /**
     * Set a binary body (images, etc.) 
     * @param data        raw bytes
     * @param mimeType    e.g. "image/png", "image/jpeg"
     */
    public HttpResponse binary(byte[] data, String mimeType) {
        this.bodyBytes = data;
        headers.put("Content-Type", mimeType);
        return this;
    }

    //Accessors

    public int getStatusCode() {
        return statusCode;
    }

    public int getBodyLength() {
        return bodyBytes.length;
    }

    //Serialise to bytes

    //Builds the full HTTP response as a byte array ready to write to the socket.
    public byte[] toBytes() {

        byte[] finalBody;

        // Use binary body if present
        if (bodyBytes != null && bodyBytes.length > 0)
        {
            finalBody = bodyBytes;
        }
        else
        {
            finalBody = body.getBytes(StandardCharsets.UTF_8);
        }

        headers.put("Content-Length", String.valueOf(finalBody.length));
        headers.put("Server", "mini-http-server/1.0");

        StringBuilder sb = new StringBuilder();

        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");

        headers.forEach((k, v) ->
            sb.append(k).append(": ").append(v).append("\r\n")
        );

        sb.append("\r\n");

        byte[] headerBytes = sb.toString().getBytes(StandardCharsets.US_ASCII);

        byte[] full = new byte[headerBytes.length + finalBody.length];

        System.arraycopy(headerBytes, 0, full, 0, headerBytes.length);
        System.arraycopy(finalBody, 0, full, headerBytes.length, finalBody.length);

        return full;

    }
}