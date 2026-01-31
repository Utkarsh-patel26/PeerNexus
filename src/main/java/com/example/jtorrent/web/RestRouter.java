package com.example.jtorrent.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RestRouter {

    private final Map<String, Map<Pattern, BiFunction<HttpExchange, Map<String, String>, String>>> routes;
    private final TorrentController torrentController;
    private final PeerController peerController;
    private final StatsController statsController;

    public RestRouter(TorrentController torrentController, PeerController peerController,
            StatsController statsController) {
        this.torrentController = torrentController;
        this.peerController = peerController;
        this.statsController = statsController;
        this.routes = new ConcurrentHashMap<>();
        registerRoutes();
    }

    private void registerRoutes() {
        register("GET", "/api/torrents", (ex, params) -> torrentController.list());
        register("POST", "/api/torrents", (ex, params) -> torrentController.add(readBody(ex)));
        register("DELETE", "/api/torrents/([a-fA-F0-9]+)",
                (ex, params) -> torrentController.remove(params.get("hash")));
        register("POST", "/api/torrents/([a-fA-F0-9]+)/start",
                (ex, params) -> torrentController.start(params.get("hash")));
        register("POST", "/api/torrents/([a-fA-F0-9]+)/stop",
                (ex, params) -> torrentController.stop(params.get("hash")));
        register("GET", "/api/torrents/([a-fA-F0-9]+)/peers",
                (ex, params) -> peerController.getPeers(params.get("hash")));
        register("GET", "/api/stats", (ex, params) -> statsController.getStats());
    }

    private void register(String method, String pattern,
            BiFunction<HttpExchange, Map<String, String>, String> handler) {
        routes.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                .put(Pattern.compile("^" + pattern + "$"), handler);
    }

    public String route(String method, String path, HttpExchange exchange) {
        Map<Pattern, BiFunction<HttpExchange, Map<String, String>, String>> methodRoutes = routes.get(method);
        if (methodRoutes == null) {
            return JsonResponse.error("Method not allowed");
        }

        for (Map.Entry<Pattern, BiFunction<HttpExchange, Map<String, String>, String>> entry : methodRoutes
                .entrySet()) {
            Matcher matcher = entry.getKey().matcher(path);
            if (matcher.matches()) {
                Map<String, String> params = new HashMap<>();
                if (matcher.groupCount() > 0) {
                    params.put("hash", matcher.group(1));
                }
                return entry.getValue().apply(exchange, params);
            }
        }

        return JsonResponse.error("Not found");
    }

    private String readBody(HttpExchange exchange) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }
}
