package client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
 * HTTP/1.1 client library
 *
 * Supports:
 *   - GET, HEAD, POST, PUT, DELETE
 *   - Custom headers
 *   - Request body (text / JSON)
 *   - Redirects (3xx follow)
 */
public class HttpClient {

    public static final int MAX_REDIRECTS = 10; // Usually HTTP clients cap at 5-20 redirects

    /**
     * Entry point
     * Sends an HTTP request and returns the parsed response.
     *
     * @param method  HTTP verb (GET, POST, PUT, DELETE, HEAD, ...)
     * @param url     Full URL, e.g. "http://example.com/cats" or "http://localhost:8080/games"
     * @param headers Additional headers (can be null)
     * @param body    Request body as string (can be null or empty)
     */
    public HttpResponse request(String method, String url, Map<String, String> headers, String body) throws Exception 
    {
        return requestInternal(method, url, headers, body, 0);
    }

    private HttpResponse requestInternal(String method, String url, Map<String, String> headers, String body, int redirectCount) throws Exception 
    {
        if (redirectCount > MAX_REDIRECTS) throw new IOException("Too many redirects");

        // Only HTTP supported
        if (url.startsWith("https://"))
        {
            throw new Exception("HTTPS is not supported. Use http:// URLs only.");
        }

        if (!url.startsWith("http://"))
        {
            url = "http://" + url;
        }

        String withoutScheme = url.substring(7);

        int slashIdx = withoutScheme.indexOf('/');
        String hostPort = withoutScheme;
        String path = "/";
        if (slashIdx != -1)
        {
            hostPort = withoutScheme.substring(0, slashIdx);
            path = withoutScheme.substring(slashIdx);
        }

        // Check if URL has a defined port
        String host;
        int port;
        if (hostPort.contains(":")) 
        {
            host = hostPort.split(":")[0];
            port = Integer.parseInt(hostPort.split(":")[1]);
        } 
        else 
        {
            host = hostPort;
            port = 80; // 80 = HTTP default port
        }

        // Build request
        byte[] bodyBytes;
        if (body != null && !body.isEmpty()) bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        else bodyBytes = new byte[0];

        StringBuilder reqBuilder = new StringBuilder();
        reqBuilder.append(method.toUpperCase()).append(" ").append(path).append(" HTTP/1.1\r\n");
        reqBuilder.append("Host: ").append(host).append("\r\n");
        reqBuilder.append("User-Agent: mini-http-client/1.0\r\n");
        reqBuilder.append("Accept: */*\r\n");
        reqBuilder.append("Connection: close\r\n");

        // Body-related headers
        if (bodyBytes.length > 0) 
        {
            reqBuilder.append("Content-Length: ").append(bodyBytes.length).append("\r\n");

            // Check if there is a content-type header, if not add default
            boolean hasContentType = false;
            if (headers != null) 
            {
                for (String header : headers.keySet()) 
                {
                    if ("Content-Type".equalsIgnoreCase(header)) 
                    {
                        hasContentType = true;
                        break;
                    }
                }
            }

            if (!hasContentType) 
            {
                reqBuilder.append("Content-Type: application/json\r\n");
            }
        }

        // Custom headers from caller
        if (headers != null) 
        {
            for (Map.Entry<String, String> entry : headers.entrySet()) 
            {
                reqBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }

        reqBuilder.append("\r\n"); // blank line = end of headers

        // Open TCP socket and send 
        try (Socket socket = new Socket(host, port);
            OutputStream socketOut = socket.getOutputStream();
            InputStream socketIn = socket.getInputStream()) 
            {

            socketOut.write(reqBuilder.toString().getBytes(StandardCharsets.US_ASCII));
            if (bodyBytes.length > 0) 
            {
                socketOut.write(bodyBytes);
            }
            socketOut.flush();

            // Read response 
            HttpResponse response = parseResponse(socketIn);

            // Follow redirects
            if (response.statusCode >= 300 && response.statusCode < 400) 
            {
                String location = response.headers.get("Location");
                if (location == null) location = response.headers.get("location");
                if (location != null && !location.isEmpty()) 
                {
                    // Relative redirect to absolute
                    if (location.startsWith("/")) 
                    {
                        location = "http://" + host + (port == 80 ? "" : ":" + port) + location;
                    }

                    System.out.println(" -> Redirecting to " + location);

                    // GET after redirect (except 307/308 which preserve the method)
                    String redirectMethod = (response.statusCode == 307 || response.statusCode == 308) ? method : "GET";
                    return requestInternal(redirectMethod, location, headers, null, redirectCount + 1);
                }
            }

            return response;
        }
    }

    // Response parser 
    private HttpResponse parseResponse(InputStream in) throws IOException {
        HttpResponse res = new HttpResponse();

        DataInputStream data = new DataInputStream(in);

        // Read status line
        String statusLine = readLine(data);
        if (statusLine == null || statusLine.isBlank()) 
        {
            throw new IOException("Empty response from server");
        }
        String[] statusParts = statusLine.split(" ", 3);
        res.httpVersion = statusParts[0];
        res.statusCode = Integer.parseInt(statusParts[1]);
        res.statusText = statusParts.length > 2 ? statusParts[2] : "";

        // Read headers until blank line
        String line;
        while ((line = readLine(data)) != null && !line.isEmpty()) 
        {
            int colon = line.indexOf(':');
            if (colon > 0) 
            {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                res.headers.put(key, value);
            }
        }

        // Read body
        res.bodyBytes = readBody(data, res.headers);
        res.body = new String(res.bodyBytes, StandardCharsets.UTF_8);

        return res;
    }

    
    //Reads a single CRLF-terminated line from the stream. Returns null on EOF.
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1, curr;
        while ((curr = in.read()) != -1) 
        {
            if (prev == '\r' && curr == '\n') 
            {
                byte[] bytes = buf.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            buf.write(curr);
            prev = curr;
        }
        return buf.size() > 0 ? buf.toString(StandardCharsets.US_ASCII) : null;
    }

    // Reads the response body using Content-Length or chunked transfer encoding. Falls back until EOF if not present.
    private byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String contentLength = getHeader(headers, "Content-Length");
        if (contentLength != null) {
            int length = Integer.parseInt(contentLength.trim());
            if (length == 0) return new byte[0];
            byte[] buf = new byte[length];
            int read = 0;
            while (read < length) {
                int n = in.read(buf, read, length - read);
                if (n == -1) break;
                read += n;
            }
            return buf;
        }

        // Check Transfer-Encoding: chunked
        String transferEncoding = getHeader(headers, "Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.equalsIgnoreCase("chunked")) 
        {
            return readChunked(in);
        }

        // Fallback: read until EOF
        return in.readAllBytes();
    }

    /*
     * Reads chunked transfer-encoded body.
     * Format: hex-size\r\n  data\r\n  ... 0\r\n\r\n
     */
    private byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) 
        {
            String sizeLine = readLine(in);
            if (sizeLine == null) break;
            int chunkSize = Integer.parseInt(sizeLine.trim(), 16);
            if (chunkSize == 0) break;
            byte[] chunk = new byte[chunkSize];
            int read = 0;
            while (read < chunkSize) 
            {
                int n = in.read(chunk, read, chunkSize - read);
                if (n == -1) break;
                read += n;
            }
            result.write(chunk);
            readLine(in); // trailing \r\n after each chunk
        }
        return result.toByteArray();
    }


    // Case-insensitive header lookup
    private String getHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) 
        {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }
}
