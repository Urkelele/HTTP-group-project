package server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Videogames server
 *
 * GET /games -> all games
 * GET /games/id -> one game
 * POST /games -> create a game
 * PUT /games/id -> update a game
 * DELETE /games/id -> delete a game
 * GET /games/:id/cover -> serve cover image
 * POST /games/:id/cover -> upload cover image
 */
public class GameController {

    // Game record
    public static class Game {
        public final int id;
        public String title;
        public String genre;
        public String platform;
        public double price;
        public int year;
        public String cover; // filename of cover

        public Game(int id, String title, String genre, String platform, double price, int year, String cover) 
        {
            this.id = id;
            this.title = title;
            this.genre = genre;
            this.platform = platform;
            this.price = price;
            this.year = year;
            this.cover = cover;
        }

        public String toJson() 
        {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(id));
            m.put("title", title);
            m.put("genre", genre);
            m.put("platform", platform);
            m.put("price", String.format(Locale.US, "%.2f", price));
            m.put("year", String.valueOf(year));
            if (cover != null && !cover.isEmpty()) m.put("cover", cover);
            return JsonParser.buildObject(m);
        }
    }

    // State
    // ConcurrentHashMap so reads and writes from multiple threads are safe, for concurrency
    private final Map<Integer, Game> games = new ConcurrentHashMap<>();

    // Is AtomicInteger in order for it to generate unique game IDs in concurrent requests
    private final AtomicInteger nextId = new AtomicInteger(1);

    private static final String COVERS_DIR = "static/covers/";

    public GameController() 
    {
        // Init data
        addSample("The Legend of Zelda: Breath of the Wild", "Adventure",  "Nintendo Switch", 59.99, 2017, "zelda.png");
        addSample("Elden Ring", "RPG", "PC / PS5 / Xbox", 49.99, 2022, "eldenring.png");
        addSample("Red Dead Redemption 2", "Action", "PC / PS4 / Xbox", 39.99, 2018, "rdr2.png");
        addSample("Hollow Knight", "Metroidvania","PC / Switch", 14.99, 2017, "hollowknight.png");
        addSample("Stardew Valley", "Simulation", "PC / Switch", 9.99, 2016, "stardew.png");

        // Create covers directory if not exist
        try { Files.createDirectories(Path.of(COVERS_DIR)); }
        catch (IOException ignored) {}
    }

    private void addSample(String title, String genre, String platform, double price, int year, String cover) 
    {
        int id = nextId.getAndIncrement();
        games.put(id, new Game(id, title, genre, platform, price, year, cover));
    }

    // Router registration
    public void register(Router router) 
    {
        router.on("GET", "/games", this::getAll);
        router.on("POST", "/games", this::create);

        // More specific routes first to ensure they match before the more general /games/:id
        router.on("GET", "/games/*/cover", this::getCover);
        router.on("POST", "/games/*/cover", this::uploadCover);
        router.on("DELETE", "/games/*/cover", this::deleteCover);

        router.on("GET", "/games/*", this::getOne);
        router.on("PUT", "/games/*", this::update);
        router.on("DELETE", "/games/*", this::delete);
    }

    // Handlers
    // GET /games
    private HttpResponse getAll(HttpRequest req, HttpResponse res) 
    {
        List<String> jsons = new ArrayList<>();
        // Sort by id for consistent ordering
        games.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> jsons.add(e.getValue().toJson()));
        return res.status(200).json(JsonParser.buildArray(jsons));
    }

    // GET /games/id
    private HttpResponse getOne(HttpRequest req, HttpResponse res) 
    {
        String param = extractId(req.path, "/games/");
        
        if (param == null || param.contains("/")) 
        {
            return res.status(400).json("{\"error\":\"Invalid path\"}");
        }

        Game game = findGame(param);
        if (game == null) return notFound(res);
        return res.status(200).json(game.toJson());
    }

    // POST /games
    private HttpResponse create(HttpRequest req, HttpResponse res) 
    {
        Map<String, String> data;
        try 
        {
            data = JsonParser.parseObject(req.body);
        } 
        catch (Exception e) 
        {
            return badRequest(res, "Malformed JSON: " + e.getMessage());
        }

        if (!data.containsKey("title") || data.get("title").isBlank()) 
        {
            return badRequest(res, "Field 'title' is required");
        }

        int id = nextId.getAndIncrement();
        Game game = new Game(
            id,
            data.getOrDefault("title","Untitled"),
            data.getOrDefault("genre","Unknown"),
            data.getOrDefault("platform","Unknown"),
            parseDouble(data.getOrDefault("price","0.0")),
            parseInt(data.getOrDefault("year","0")),
            data.getOrDefault("cover","")
        );
        
        games.put(id, game);

        Logger.info("Game created: [" + id + "] " + game.title);
        return res.status(201).json(game.toJson());
    }

    // PUT /games/id
    private HttpResponse update(HttpRequest req, HttpResponse res) 
    {
        Game game = findGame(extractId(req.path, "/games/"));
        if (game == null) return notFound(res);

        Map<String, String> data;
        try 
        {
            data = JsonParser.parseObject(req.body);
        }
        catch (Exception e) 
        {
            return badRequest(res, "Malformed JSON: " + e.getMessage());
        }

        // Only overwrite fields that are present
        if (data.containsKey("title")) game.title = data.get("title");
        if (data.containsKey("genre")) game.genre = data.get("genre");
        if (data.containsKey("platform")) game.platform = data.get("platform");
        if (data.containsKey("price")) game.price = parseDouble(data.get("price"));
        if (data.containsKey("year")) game.year = parseInt(data.get("year"));
        if (data.containsKey("cover")) game.cover = data.get("cover");

        Logger.info("Game updated: [" + game.id + "] " + game.title);
        return res.status(200).json(game.toJson());
    }

    // DELETE /games/id
    private HttpResponse delete(HttpRequest req, HttpResponse res) 
    {
        Game game = findGame(extractId(req.path, "/games/"));
        if (game == null) return notFound(res);
        games.remove(game.id);

        Logger.info("Game deleted: [" + game.id + "] " + game.title);

        // 204 must have empty body
        return res.status(204).json("");
    }

    /*
     * GET /games/:id/cover -> the cover image as binary.
     */
    private HttpResponse getCover(HttpRequest req, HttpResponse res) {
        
        String idStr = extractId(req.path, "/games/");

        if (idStr != null && idStr.endsWith("/cover")) {
            idStr = idStr.substring(0, idStr.length() - "/cover".length());
        }

        Game game = findGame(idStr);

        if (game == null) {
            return notFound(res);
        }

        if (game.cover == null || game.cover.isEmpty()) {
            return res.status(404).json(
                "{\"error\":\"Cover image not found\"}"
            );
        }

        Path coverPath = Path.of(COVERS_DIR + game.cover);

        if (!Files.exists(coverPath)) {
            return res.status(404).json(
                "{\"error\":\"Cover image not found\"}"
            );
        }

        try {

            byte[] bytes = Files.readAllBytes(coverPath);

            String mime = MimeTypes.forFile(game.cover);

            return res.status(200).binary(bytes, mime);

        } catch (IOException e) {

            Logger.error("Could not read cover image", e);

            return res.status(500).json(
                "{\"error\":\"Could not read image\"}"
            );
        }
    }

    /*
     * POST /games/:id/cover  (body = raw image bytes, Content-Type = image/png)
     * Stores the image and updates the games cover field.
     */
    private HttpResponse uploadCover(HttpRequest req, HttpResponse res) {

        String idStr = extractId(req.path, "/games/");

        if (idStr != null && idStr.endsWith("/cover")) {
            idStr = idStr.substring(0, idStr.length() - "/cover".length());
        }

        Game game = findGame(idStr);

        if (game == null) {
            return notFound(res);
        }

        String contentType = req.getContentType();

        if (!contentType.startsWith("image/")) {
            return res.status(415).json(
                "{\"error\":\"Expected image Content-Type\"}"
            );
        }

        if (req.bodyBytes.length == 0) {
            return badRequest(res, "Image body is empty");
        }

        // Extract extension from MIME type
        String extension = contentType.substring("image/".length());

        // Use the existing game cover filename
        String coverNameWithoutExtension = game.cover.split("\\.")[0];
        String filename = coverNameWithoutExtension + "." + extension;

        if (filename == null || filename.isBlank()) {
            return res.status(400).json(
                "{\"error\":\"Game has no cover filename defined\"}"
            );
        }

        Path savePath = Path.of(COVERS_DIR + filename);

        try {

            // Save image to disk, make sure data from older files is not present
            Files.write(savePath, req.bodyBytes, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {

            Logger.error("Could not save cover image", e);

            return res.status(500).json(
                "{\"error\":\"Could not save image\"}"
            );
        }

        Logger.info("Cover uploaded for game [" + game.id + "] -> " + filename);

        // Update cover path
        game.cover = filename;

        return res.status(200).json(
            "{"
            + "\"message\":\"Cover uploaded successfully\","
            + "\"file\":\"" + filename + "\""
            + "}"
        );

    }

    
    // DELETE /games/:id/cover
    // Deletes cover from disk
    private HttpResponse deleteCover(HttpRequest req, HttpResponse res) {

        String idStr = extractId(req.path, "/games/");

        if (idStr != null && idStr.endsWith("/cover")) {
            idStr = idStr.substring(0, idStr.length() - "/cover".length());
        }

        Game game = findGame(idStr);

        if (game == null) {
            return notFound(res);
        }

        if (game.cover == null || game.cover.isEmpty()) {
            return res.status(404).json("{\"error\":\"No cover to delete\"}");
        }

        // Delete file from disk
        Path path = Path.of(COVERS_DIR).resolve(game.cover);

        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            Logger.error("Could not delete cover", e);
            return res.status(500).json("{\"error\":\"Could not delete cover\"}");
        }

        Logger.info("Cover deleted for game [" + game.id + "]");

        return res.status(200).json("{\"message\":\"Cover deleted\"}");
    }

    // Helpers
    private Game findGame(String idStr) 
    {
        if (idStr == null || idStr.isBlank()) return null;
        try 
        {
            int id = Integer.parseInt(idStr.trim());
            return games.get(id);
        } 
        catch (NumberFormatException e) 
        {
            return null;
        }
    }

    // Extracts the segment after basePath up to the next '/' or end. 
    private String extractId(String path, String basePath) {
        if (!path.startsWith(basePath)) return null;
        return path.substring(basePath.length());
    }

    private HttpResponse notFound(HttpResponse res) {
        return res.status(404).json("{\"error\":\"Game not found\"}");
    }

    private HttpResponse badRequest(HttpResponse res, String message) {
        return res.status(400).json("{\"error\":\"" + message + "\"}");
    }

    private double parseDouble(String s) {
        try 
        { 
            return Double.parseDouble(s); 
        }
        catch (NumberFormatException e) 
        { 
            return 0.0; 
        }
    }

    private int parseInt(String s) {
        try 
        { 
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) 
        {
            return 0;
        }
    }
}
