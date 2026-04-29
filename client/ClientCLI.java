package client;

import java.util.*;

/*
 * HTTP client CLI.
 *
 * Suports:
 * - Prompts user for method, URL, headers, and body.
 * - Sends request using HttpClient and displays response status, headers, and body.
 * - Supports GET, POST, PUT, DELETE, HEAD methods.
 */

public class ClientCLI {

    // ANSI colour codes for terminal output
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String DIM    = "\u001B[2m";

    private static final HttpClient client = new HttpClient();
    private static final Scanner scanner   = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        printBanner();

        System.out.println(CYAN + "Type 'help' for commands, 'exit' to quit." + RESET);
        System.out.println();

        while (true) 
        {
            try 
            {
                String[] input = promptRequest();
                if (input == null) break;              // user typed 'exit'
                if (input.length == 0) continue;       // empty input, re-prompt

                String method  = input[0];
                String url     = input[1];
                Map<String, String> headers = parseHeaders(input[2]);
                String body    = input[3].isEmpty() ? null : input[3];

                sendAndPrint(method, url, headers, body);
            } 
            catch (Exception e)
            {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
            }
        }

        System.out.println(DIM + "\nGoodbye." + RESET);
    }

    /*
     * Prompt
     * 
     * Returns a String[4]: {method, url, headersStr, body}.
     * Returns null if the user typed 'exit'.
     * Returns empty array if input was invalid/should be re-prompted.
     */
    private static String[] promptRequest() 
    {
        // METHOD
        System.out.print(BOLD + "Method" + RESET + " [GET/POST/PUT/DELETE/HEAD] (or 'exit'): ");
        String method = scanner.nextLine().trim().toUpperCase();

        if (method.equals("EXIT") || method.equals("QUIT")) return null;
        
        if (method.equals("HELP")) 
        { 
            printHelp();
            return new String[0];
        }
        
        if (!isValidMethod(method)) 
        {
            System.out.println(YELLOW + "  Unknown method '" + method + "'. Using GET." + RESET);
            method = "GET";
        }

        // URL
        System.out.print(BOLD + "URL" + RESET + ": ");
        String url = scanner.nextLine().trim();
        if (url.isEmpty()) 
        {
            System.out.println(YELLOW + "  URL cannot be empty." + RESET);
            return new String[0];
        }

        // HEADERS
        System.out.print(BOLD + "Headers" + RESET + " [Enter to skip]: ");
        String headersStr = scanner.nextLine().trim();

        // BODY
        String body = "";
        if (methodHasBody(method)) 
        {
            System.out.print(BOLD + "Body" + RESET + " (JSON or text) [Enter to skip]: ");
            body = scanner.nextLine().trim();
        }

        System.out.println(); 
        return new String[]{method, url, headersStr, body};
    }

    private static void sendAndPrint(String method, String url, Map<String, String> headers, String body) 
    {
        System.out.println(DIM + "Sending " + method + " " + url + " ..." + RESET);
        long start = System.currentTimeMillis();

        HttpResponse res;
        try 
        {
            res = client.request(method, url, headers, body);
        } 
        catch (Exception e) 
        {
            System.out.println(RED + "Request failed: " + e.getMessage() + RESET);
            System.out.println();
            return;
        }

        long elapsed = System.currentTimeMillis() - start;

        // Status line
        String statusColour = statusColour(res.statusCode);
        System.out.println(BOLD + "--- Response " + DIM + "(" + elapsed + "ms)" + RESET);
        System.out.println(BOLD + "Status  " + RESET + statusColour + res.statusCode + " " + res.statusText + RESET);

        // Headers
        System.out.println(BOLD + "Headers " + RESET);
        for (var entry : res.headers.entrySet()) 
        {
            System.out.println("  " + CYAN + entry.getKey() + RESET + ": " + entry.getValue());
        }

        // Body
        System.out.println(BOLD + "Body    " + RESET);
        if (res.body.isEmpty()) 
        {
            System.out.println(DIM + "  (empty)" + RESET);
        } 
        else 
        {
            String displayed = res.body.trim();
            if (res.getContentType().contains("json")) 
            {
                displayed = prettyJson(displayed);
            }
            System.out.println(displayed);
        }

        System.out.println(BOLD + "---------------------------" + RESET);
        System.out.println();
    }

    // Helpers
    // Returns a map that links the header name to its content
    private static Map<String, String> parseHeaders(String headersStr) 
    {
        Map<String, String> map = new LinkedHashMap<>();

        if (headersStr == null || headersStr.isBlank()) return map;
        
        for (String pair : headersStr.split(",")) 
        {
            int colon = pair.indexOf(':');
            if (colon > 0) 
            {
                map.put(pair.substring(0, colon).trim(), pair.substring(colon + 1).trim());
            }
        }

        return map;
    }

    private static boolean isValidMethod(String m) 
    {
        return Set.of("GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS").contains(m);
    }

    private static boolean methodHasBody(String m) 
    {
        return Set.of("POST", "PUT", "PATCH").contains(m);
    }

    private static String statusColour(int code) 
    {
        if (code >= 200 && code < 300) return GREEN;
        if (code >= 300 && code < 400) return YELLOW;
        return RED;
    }

    // AI-assisted code for UI
    private static String prettyJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) { sb.append(c); continue; }
            switch (c) {
                case '{', '[' -> {
                    sb.append(c).append('\n').append("  ".repeat(++indent));
                }
                case '}', ']' -> {
                    sb.append('\n').append("  ".repeat(--indent)).append(c);
                }
                case ',' -> {
                    sb.append(c).append('\n').append("  ".repeat(indent));
                }
                case ':' -> sb.append(": ");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void printBanner() {
        System.out.println(CYAN + BOLD);
        System.out.println("  ╔══════════════════════════════╗");
        System.out.println("  ║   mini-HTTP client  v1.0     ║");
        System.out.println("  ║   Networks & Comms II · USJ  ║");
        System.out.println("  ╚══════════════════════════════╝");
        System.out.println(RESET);
    }

    private static void printHelp() {
        System.out.println(CYAN);
        System.out.println("  Commands:");
        System.out.println("    exit / quit  → close the client");
        System.out.println("    help         → show this message");
        System.out.println();
        System.out.println("  Sending a request:");
        System.out.println("    1. Enter method:  GET, POST, PUT, DELETE, HEAD");
        System.out.println("    2. Enter URL:      http://example.com/resource");
        System.out.println("    3. Enter headers:  Content-Type:application/json, X-Api-Key:secret");
        System.out.println("       (comma-separated key:value pairs, or press Enter to skip)");
        System.out.println("    4. Enter body:     {\"title\":\"Zelda\",\"price\":59.99}");
        System.out.println("       (only shown for POST/PUT/PATCH)");
        System.out.println();
        System.out.println("  Examples:");
        System.out.println("    GET  http://example.com");
        System.out.println("    GET  http://localhost:8080/games");
        System.out.println("    POST http://localhost:8080/games   body: {\"title\":\"Zelda\",\"price\":59.99}");
        System.out.println(RESET);
    }
}
