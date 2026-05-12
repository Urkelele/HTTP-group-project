package server;

import java.util.Map;

//Maps file extensions and MIME type.
public class MimeTypes {

    private static final Map<String, String> TYPES = Map.ofEntries(
        // Text / web
        Map.entry("html", "text/html; charset=utf-8"),
        Map.entry("htm", "text/html; charset=utf-8"),
        Map.entry("css", "text/css; charset=utf-8"),
        Map.entry("js", "application/javascript"),
        Map.entry("json", "application/json; charset=utf-8"),
        Map.entry("txt", "text/plain; charset=utf-8"),
        // Images
        Map.entry("png", "image/png"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("ico", "image/x-icon"),
        // Fonts
        Map.entry("woff", "font/woff"),
        Map.entry("woff2", "font/woff2"),
        // Other
        Map.entry("pdf", "application/pdf")
    );

    //Returns the MIME type for a filename. "cover.png" -> "image/png".
    public static String forFile(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot == -1) return "application/octet-stream";
        String ext = filename.substring(dot + 1).toLowerCase();
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    // Returns true if the MIME type represents a text-based format.
    public static boolean isText(String mimeType) {
        return mimeType.startsWith("text/")
            || mimeType.contains("json")
            || mimeType.contains("javascript")
            || mimeType.contains("xml")
            || mimeType.contains("svg");
    }
}
