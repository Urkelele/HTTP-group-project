package server;

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
 * 
 * Cookies
 * - GET /games sets a "visited" cookie the first time a client hits the API
 * - All responses include a "visits" counter cookie that increments each request
 * - Handlers use req.getCookie() to read cookies sent by the client
 * - There is a cookie that remembers the last viewed game of the user
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

        public Game(int id, String title, String genre, String platform, double price, int year) 
        {
            this.id = id;
            this.title = title;
            this.genre = genre;
            this.platform = platform;
            this.price = price;
            this.year = year;
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
            return JsonParser.buildObject(m);
        }
    }

    // State
    // ConcurrentHashMap so reads and writes from multiple threads are safe, for concurrency
    private final Map<Integer, Game> games = new ConcurrentHashMap<>();

    // Is AtomicInteger in order for it to generate unique game IDs in concurrent requests
    private final AtomicInteger nextId = new AtomicInteger(1);

    public GameController() 
    {
        // Init data
        addSample("The Legend of Zelda: Breath of the Wild", "Adventure",  "Nintendo Switch", 59.99, 2017);
        addSample("Elden Ring", "RPG", "PC / PS5 / Xbox", 49.99, 2022);
        addSample("Red Dead Redemption 2", "Action", "PC / PS4 / Xbox", 39.99, 2018);
        addSample("Hollow Knight", "Metroidvania","PC / Switch", 14.99, 2017);
        addSample("Stardew Valley", "Simulation", "PC / Switch", 9.99, 2016);
    }

    private void addSample(String title, String genre, String platform, double price, int year) 
    {
        int id = nextId.getAndIncrement();
        games.put(id, new Game(id, title, genre, platform, price, year));
    }

    // Router registration
    public void register(Router router) 
    {
        router.on("GET", "/games", this::getAll);
        router.on("POST", "/games", this::create);

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
        
        // Cookies
        // Read how many times this client has called GET /games
        int visits = readVisitCount(req);
        visits++;

        // Always update the visits counter cookie (expires in 1 hour, scoped to /games)
        res.setCookie("visits", String.valueOf(visits), 3600, "/games");
 
        Logger.info("Cookie: visits=" + visits + " for GET /games");
     
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

        // Cookies remember the last viewed game
        // Set a cookie so the client "remembers" which game they last viewed.
        // Scoped to /games so it's only sent back to game endpoints, not to /
        res.setCookie("lastViewed", String.valueOf(game.id), 86400, "/games");
 
        // If we can read it back from the request, log it
        String previouslyViewed = req.getCookie("lastViewed");
        if (previouslyViewed != null && !previouslyViewed.equals(String.valueOf(game.id))) {
            Logger.info("Cookie: client previously viewed game id=" + previouslyViewed + ", now viewing id=" + game.id);
        }

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
        Game game = new Game(id, data.getOrDefault("title", "Untitled"), data.getOrDefault("genre", "Unknown"), data.getOrDefault("platform", "Unknown"),
            parseDouble(data.getOrDefault("price", "0.0")), parseInt(data.getOrDefault("year", "0")));
        
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

        // Cookies clear the lastViewed cookie if it was this game
        String lastViewed = req.getCookie("lastViewed");
        if (String.valueOf(game.id).equals(lastViewed)) {
            res.setCookie("lastViewed", "", -1, "/games");
        }

        // 204 must have empty body
        return res.status(204).json("");
    }

    // Cookie helpers
    private int readVisitCount(HttpRequest req) {
        String raw = req.getCookie("visits");
        if (raw == null) return 0;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
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
