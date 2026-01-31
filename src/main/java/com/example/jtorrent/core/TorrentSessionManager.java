package com.example.jtorrent.core;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.events.TorrentAddedEvent;
import com.example.jtorrent.events.TorrentRemovedEvent;
import com.example.jtorrent.logging.Logger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TorrentSessionManager {

    private static final Logger logger = Logger.getLogger(TorrentSessionManager.class);

    private final Config config;
    private final Map<String, TorrentSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final EventBus eventBus;

    public TorrentSessionManager(Config config) {
        this.config = config;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TorrentSession-Worker");
            t.setDaemon(true);
            return t;
        });
        this.eventBus = EventBus.getInstance();
    }

    public String addTorrent(String input, String outputDir) throws Exception {
        URI uri = input.startsWith("magnet:") ? URI.create(input) : Paths.get(input).toUri();
        Path output = Paths.get(outputDir);

        TorrentSession session = new TorrentSession(uri, output, config, Logger.getLogger("TorrentSession"));

        String hash = null;
        if (session.getTorrentFile() != null) {
            hash = session.getTorrentFile().infoHashHex();
        }
        if (hash == null || hash.isEmpty()) {
            if (input.startsWith("magnet:")) {
                hash = extractHashFromMagnet(input);
            } else {
                throw new IllegalStateException("Cannot determine info hash");
            }
        }

        if (sessions.containsKey(hash)) {
            throw new IllegalStateException("Torrent already exists: " + hash);
        }

        sessions.put(hash, session);
        byte[] infoHashBytes = hexToBytes(hash);
        String name = session.getName() != null ? session.getName() : hash;
        long size = session.getTorrentFile() != null ? session.getTorrentFile().totalLength() : 0;
        eventBus.publish(new TorrentAddedEvent(infoHashBytes, name, size));
        logger.info("Added torrent: %s", hash);

        return hash;
    }

    public void removeTorrent(String hash) throws Exception {
        TorrentSession session = sessions.remove(hash);
        if (session == null) {
            throw new IllegalArgumentException("Torrent not found: " + hash);
        }

        session.stop();
        byte[] infoHashBytes = hexToBytes(hash);
        eventBus.publish(new TorrentRemovedEvent(infoHashBytes, false));
        logger.info("Removed torrent: %s", hash);
    }

    public void startTorrent(String hash) throws Exception {
        TorrentSession session = sessions.get(hash);
        if (session == null) {
            throw new IllegalArgumentException("Torrent not found: " + hash);
        }

        executor.submit(() -> {
            try {
                session.start();
            } catch (Exception e) {
                logger.error("Failed to start torrent %s: %s", hash, e.getMessage());
            }
        });
    }

    public void stopTorrent(String hash) throws Exception {
        TorrentSession session = sessions.get(hash);
        if (session == null) {
            throw new IllegalArgumentException("Torrent not found: " + hash);
        }
        session.stop();
    }

    public TorrentSession getSession(String hash) {
        return sessions.get(hash);
    }

    public Collection<TorrentSession> getAllSessions() {
        return sessions.values();
    }

    public String getDownloadDirectory() {
        return config.getDownloadDirectory();
    }

    public void shutdown() {
        sessions.values().forEach(TorrentSession::stop);
        sessions.clear();
        executor.shutdown();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String extractHashFromMagnet(String magnet) {
        int start = magnet.indexOf("urn:btih:") + 9;
        if (start < 9) {
            throw new IllegalArgumentException("Invalid magnet link");
        }
        int end = magnet.indexOf('&', start);
        if (end < 0)
            end = magnet.length();
        return magnet.substring(start, end).toLowerCase();
    }
}
