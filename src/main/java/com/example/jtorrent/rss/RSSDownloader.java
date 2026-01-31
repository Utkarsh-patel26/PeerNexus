package com.example.jtorrent.rss;

import com.example.jtorrent.logging.Logger;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Interface for downloading torrents from RSS feeds.
 * Implementation should integrate with TorrentSession.
 */
public interface RSSDownloader {

    /**
     * Download and add torrent from RSS item.
     *
     * @param item RSS item containing torrent
     * @param rule matching rule (for priority and save path)
     * @throws Exception if download fails
     */
    void downloadTorrent(RSSItem item, RSSRule rule) throws Exception;

    /**
     * Default implementation that downloads .torrent file.
     */
    class DefaultRSSDownloader implements RSSDownloader {

        private final Path torrentDirectory;
        private final TorrentAddCallback callback;
        private final Logger logger;

        public DefaultRSSDownloader(Path torrentDirectory, TorrentAddCallback callback, Logger logger) {
            this.torrentDirectory = torrentDirectory;
            this.callback = callback;
            this.logger = logger;
        }

        @Override
        public void downloadTorrent(RSSItem item, RSSRule rule) throws Exception {
            URI torrentUrl = item.getTorrentUrl();
            if (torrentUrl == null) {
                throw new Exception("No torrent URL in RSS item");
            }

            logger.info("Downloading torrent from RSS: %s", item.getTitle());

            // Create directory if needed
            Files.createDirectories(torrentDirectory);

            // Generate filename from title (sanitized)
            String filename = sanitizeFilename(item.getTitle()) + ".torrent";
            Path torrentFile = torrentDirectory.resolve(filename);

            // Download .torrent file
            HttpURLConnection connection = (HttpURLConnection) torrentUrl.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "PeerNexus/1.0");

            try (InputStream in = connection.getInputStream();
                    FileOutputStream out = new FileOutputStream(torrentFile.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            logger.info("Downloaded torrent file: %s", torrentFile);

            // Add torrent via callback
            String savePath = rule.getCustomSavePath();
            int priority = rule.getPriority();
            callback.addTorrent(torrentFile, savePath, priority);
        }

        private String sanitizeFilename(String name) {
            if (name == null) {
                return "unknown";
            }
            // Remove invalid filename characters
            return name.replaceAll("[^a-zA-Z0-9._-]", "_")
                    .substring(0, Math.min(name.length(), 100));
        }
    }

    /**
     * Callback interface for adding torrents to session.
     */
    interface TorrentAddCallback {
        void addTorrent(Path torrentFile, String savePath, int priority) throws Exception;
    }
}
