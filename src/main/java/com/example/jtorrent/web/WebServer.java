package com.example.jtorrent.web;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebServer {

    private static final Logger logger = Logger.getLogger(WebServer.class);

    private final int port;
    private final AuthManager authManager;
    private final RestRouter router;
    private final WebSocketStatsPublisher statsPublisher;
    private final ExecutorService executor;
    private HttpServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WebServer(Config config, RestRouter router, AuthManager authManager) {
        this.port = config.getWebPort();
        this.router = router;
        this.authManager = authManager;
        this.statsPublisher = new WebSocketStatsPublisher(config.getWebSocketPort());
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);

        server.createContext("/api/torrents", new AuthenticatedHandler(this::handleTorrents));
        server.createContext("/api/stats", new AuthenticatedHandler(this::handleStats));
        server.createContext("/api/peers", new AuthenticatedHandler(this::handlePeers));

        server.start();
        statsPublisher.start();

        logger.info("Web server started on port %d", port);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (server != null) {
            server.stop(0);
        }
        statsPublisher.stop();
        executor.shutdown();

        logger.info("Web server stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public WebSocketStatsPublisher getStatsPublisher() {
        return statsPublisher;
    }

    private void handleTorrents(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String response;

        try {
            response = router.route(method, path, exchange);
        } catch (Exception e) {
            response = JsonResponse.error(e.getMessage());
            exchange.sendResponseHeaders(500, 0);
        }

        sendResponse(exchange, response);
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        String response = router.route("GET", "/api/stats", exchange);
        sendResponse(exchange, response);
    }

    private void handlePeers(HttpExchange exchange) throws IOException {
        String response = router.route("GET", exchange.getRequestURI().getPath(), exchange);
        sendResponse(exchange, response);
    }

    private void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (exchange.getResponseCode() == -1) {
            exchange.sendResponseHeaders(200, bytes.length);
        }
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private class AuthenticatedHandler implements HttpHandler {
        private final HttpHandler delegate;

        AuthenticatedHandler(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (!authManager.authenticate(authHeader)) {
                exchange.sendResponseHeaders(401, 0);
                exchange.getResponseBody().close();
                return;
            }
            delegate.handle(exchange);
        }
    }
}
