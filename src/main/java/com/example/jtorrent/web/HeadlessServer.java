package com.example.jtorrent.web;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.core.TorrentSessionManager;
import com.example.jtorrent.logging.Logger;

public class HeadlessServer {

    private static final Logger logger = Logger.getLogger(HeadlessServer.class);

    private final Config config;
    private final TorrentSessionManager sessionManager;
    private final WebServer webServer;
    private volatile boolean running = false;

    public HeadlessServer(Config config) {
        this.config = config;
        this.sessionManager = new TorrentSessionManager(config);

        TorrentController torrentController = new TorrentController(sessionManager);
        PeerController peerController = new PeerController(sessionManager);
        StatsController statsController = new StatsController(sessionManager);

        RestRouter router = new RestRouter(torrentController, peerController, statsController);
        AuthManager authManager = new AuthManager(config.getWebPassword());

        this.webServer = new WebServer(config, router, authManager);
    }

    public void start() throws Exception {
        if (running) {
            return;
        }

        running = true;
        webServer.start();

        logger.info("Headless server started");
        logger.info("REST API: http://localhost:%d/api", config.getWebPort());
        logger.info("WebSocket: ws://localhost:%d", config.getWebSocketPort());

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        webServer.stop();
        sessionManager.shutdown();
        logger.info("Headless server stopped");
    }

    public TorrentSessionManager getSessionManager() {
        return sessionManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public static void main(String[] args) throws Exception {
        Config config = new Config();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> config.setWebPort(Integer.parseInt(args[++i]));
                case "--ws-port" -> config.setWebSocketPort(Integer.parseInt(args[++i]));
                case "--password" -> config.setWebPassword(args[++i]);
                case "--download-dir" -> config.setDownloadDirectory(args[++i]);
            }
        }

        new HeadlessServer(config).start();
    }
}
