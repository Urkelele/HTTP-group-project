package server;

public class ServerMain {
    public static void main(String[] args) throws Exception {

        Router router = new Router();

        GameController controller = new GameController();
        controller.register(router);

        HttpServer server = new HttpServer(8080, router);
        server.start();
    }
}