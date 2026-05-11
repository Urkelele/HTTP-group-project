package server;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * OPTIONAL FEATURE: LOGGING
 *
 * Writes structured log entries to:
 *   - Console with colour
 *   - server.log file in plain text
 *
 * Log levels: INFO, WARN, ERROR
 *
 * Request log format:
 *   [2025-01-01 12:12:12] INFO GET /games | 200 OK | 342ms | 1024 bytes
 *
 * Server event format:
 *   [2025-01-01 12:12:12] INFO Server started on port 8080
 */
public class Logger {

    public enum Level { INFO, WARN, ERROR }

    private static final String LOG_FILE = "server.log";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ANSI colours
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String CYAN   = "\u001B[36m";
    private static final String DIM    = "\u001B[2m";
    private static final String BOLD   = "\u001B[1m";

    // Public API

    // Log a completed HTTP request/response cycle.
    public static void logRequest(HttpRequest req, HttpResponse res, long durationMs) {
        int code = res.getStatusCode();
        Level level;
        if(code >= 500){ level = Level.ERROR;}
        else if(code >= 400){ level = Level.WARN;}
        else{ level = Level.INFO;}

        StringBuilder plainBuilder = new StringBuilder();

        plainBuilder.append(req.method)
            .append(" ")
            .append(req.path)
            .append(" | ")
            .append(code)
            .append(" | ")
            .append(durationMs)
            .append("ms | ")
            .append(res.getBodyLength())
            .append(" bytes");

        String plain = plainBuilder.toString();

        StringBuilder colouredBuilder = new StringBuilder();

        colouredBuilder.append(BOLD)
            .append(req.method)
            .append(RESET)
            .append(" ")
            .append(req.path)
            .append(" | ")
            .append(statusColour(code))
            .append(code)
            .append(RESET)
            .append(" | ")
            .append(durationMs)
            .append("ms | ")
            .append(res.getBodyLength())
            .append(" bytes");

        String coloured  = colouredBuilder.toString();

        write(level, plain, coloured);
    }

    // Log a general server event (startup, shutdown, config...)
    public static void info(String message) {
        write(Level.INFO, message, CYAN + message + RESET);
    }

    public static void warn(String message) {
        write(Level.WARN, message, YELLOW + message + RESET);
    }

    public static void error(String message) {
        write(Level.ERROR, message, RED + message + RESET);
    }

    public static void error(String message, Throwable t) {
        error(message + ": " + t.getMessage());
    }

    // Internal
    private static synchronized void write(Level level, String plain, String coloured) {
        String timestamp = LocalDateTime.now().format(FMT);
        String prefix = "[" + timestamp + "] " + level.name() + " ";

        // Console with colour
        String levelColour = switch (level) {
            case INFO -> GREEN;
            case WARN -> YELLOW;
            case ERROR -> RED;
        };
        System.out.println(DIM + "[" + timestamp + "] " + RESET + levelColour + level.name() + RESET + "  " + coloured);

        // File plain text
        String fileLine = prefix + plain;
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println(fileLine);
        } catch (IOException e) {
            System.err.println("[Logger] Could not write to " + LOG_FILE + ": " + e.getMessage());
        }
    }

    private static String statusColour(int code) {
        if (code >= 500) return RED;
        if (code >= 400) return YELLOW;
        if (code >= 300) return CYAN;
        return GREEN;
    }
}
