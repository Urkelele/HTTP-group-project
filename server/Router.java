package server;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class Router {

    public record Route(String method, String pattern, BiFunction<HttpRequest, HttpResponse, HttpResponse> handler) {}

    private final List<Route> routes = new ArrayList<>();

    //Register a handler for a method + path pattern.
    public void on(String method, String pattern, BiFunction<HttpRequest, HttpResponse, HttpResponse> handler)
    {
        routes.add(new Route(method.toUpperCase(), pattern, handler));
    }

    // Finds the right handler and calls it.
    // Returns 405 if the path matched but method didn't.
    // Returns 404 if no path matched.
    public HttpResponse dispatch(HttpRequest req) {
        boolean pathMatched = false;

        for (Route route : routes) {
            if (matches(req.path, route.pattern()))
            {
                pathMatched = true;
                if (req.method.equalsIgnoreCase(route.method()))
                {
                    try {
                        return route.handler().apply(req, new HttpResponse());
                    } catch (Exception e) {
                        return new HttpResponse().status(500).json("{\"error\":\"Internal server error: " + escape(e.getMessage()) + "\"}");
                    }
                }
            }
        }

        if (pathMatched) {
            return new HttpResponse().status(405).header("Allow", allowedMethods(req.path)).json("{\"error\":\"Method Not Allowed\"}");
        }

        return new HttpResponse().status(404).json("{\"error\":\"Not Found\"}");
    }

    private boolean matches(String path, String pattern) {
        // Strip query string before matching
        int queryIndex = path.indexOf('?');
        String cleanPath = path;
        if(queryIndex != -1)
        {
            cleanPath = path.substring(0, queryIndex);
        } 

        if (pattern.endsWith("/*")) {
            String base = pattern.substring(0, pattern.length() - 2);
            // Matches /base/anything but NOT /base alone (use separate exact route for that)
            return cleanPath.startsWith(base + "/");
        }

        return cleanPath.equals(pattern);
    }

    // Collects all methods registered for a given path
    private String allowedMethods(String path) {
        StringBuilder sb = new StringBuilder();
        for (Route r : routes)
        {
            if (matches(path, r.pattern()))
            {
                if (sb.length() > 0) sb.append(", ");
                sb.append(r.method());
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}

