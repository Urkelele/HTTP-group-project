package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpServer {

    private final int port;
    private final Router router;

    // Thread pool handles multiple clients concurrently
    private final ExecutorService pool = Executors.newFixedThreadPool(20);

    public HttpServer(int port, Router router) 
    {
        this.port = port;
        this.router = router;
    }

    // Start
    public void start() throws IOException 
    {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            Logger.info("Server started on port " + port);
            Logger.info("Static files served from ./static/");
            Logger.info("Press Ctrl+C to stop");

            // Accept loop each connection handled in its own thread
            while (true) 
            {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleConnection(client));
            }
        }
    }

    // Connection handler
    private void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(10_000);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // keep-alive to serve multiple requests on the same connection
            while (!socket.isClosed()) {
                HttpRequest req;
                try {
                    req = parseRequest(in);
                } catch(Exception e) {
                    // Client closed connection or timed out
                    break;
                }

                if (req == null) break;

                long start = System.currentTimeMillis();
                HttpResponse res = router.dispatch(req);
                long elapsed = System.currentTimeMillis() - start;

                Logger.logRequest(req, res, elapsed);

                // Write response to socket
                out.write(res.toBytes());
                out.flush();

                // Close connection if client asked for it
                String connection = req.getHeader("Connection");
                if ("close".equalsIgnoreCase(connection) || req.version.equals("HTTP/1.0")) break;
            }
        } catch (Exception e) {
            Logger.error("Unhandled connection error", e);
        }
    }

    // Request parser

    // Parses request from the input stream.
    // Returns null if the stream is empty
    private HttpRequest parseRequest(InputStream in) throws IOException 
    {
        HttpRequest req = new HttpRequest();

        String requestLine = readLine(in);

        // Empty line before request = keep-alive idle, null = EOF
        while (requestLine != null && requestLine.isBlank()) 
        {
            requestLine = readLine(in);
        }
        if (requestLine == null) throw new EOFException("Client closed connection");

        String[] parts = requestLine.split(" ", 3);
        
        if (parts.length < 3)
        {
            Logger.warn("Malformed request line: " + requestLine);
            throw new IOException("Malformed request line");
        }
        
        req.method = parts[0].toUpperCase();
        req.path = parts[1];
        req.version = parts[2].trim();

        // Headers
        String line;
        while ((line = readLine(in)) != null && !line.isBlank()) 
        {
            int colon = line.indexOf(':');
            if (colon > 0) 
            {
                req.headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }

        // Body
        String contentLengthStr = req.getHeader("Content-Length");
        if (contentLengthStr != null) 
        {
            int length = Integer.parseInt(contentLengthStr.trim());

            if (length > 0) 
            {
                byte[] buffer = in.readNBytes(length);
                req.body = new String(buffer, StandardCharsets.UTF_8);
            }
        }

        // Parse the Cookie header into req.cookies so handlers can read them easily.
        // The Cookie header looks like: "session=abc123; visits=5; theme=dark"
        parseCookieHeader(req);

        return req;
    }


    // Parses the Cookie request header and populates req.cookies.
    private void parseCookieHeader(HttpRequest req) {
        String cookieHeader = req.getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) return;
 
        // Split by "; " to get individual name=value pairs
        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String name  = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                req.cookies.put(name, value);
            }
        }
    }


    // Reads one CRLF-terminated line from the stream.
    // Returns the line without the trailing \r\n and null in end of file
    private String readLine(InputStream in) throws IOException 
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        int curr;
        
        while ((curr = in.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                byte[] bytes = buf.toByteArray();
                // Strip the \r we already put in the buffer
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            buf.write(curr);
            prev = curr;
        }
        if (buf.size() > 0) return buf.toString(StandardCharsets.US_ASCII);
        else return null;
        
    }
}
