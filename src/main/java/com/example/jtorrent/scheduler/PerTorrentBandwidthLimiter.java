package com.example.jtorrent.scheduler;

import com.example.jtorrent.logging.Logger;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extended bandwidth limiter with per-torrent bandwidth control.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Global upload/download limits (inherited from BandwidthLimiter)
 * <li>Per-torrent upload/download limits
 * <li>Dynamic limit adjustment
 * <li>Bandwidth allocation priorities
 * <li>Statistics tracking per torrent
 * </ul>
 */
public class PerTorrentBandwidthLimiter extends BandwidthLimiter {

    private static final Logger logger = Logger.getLogger(PerTorrentBandwidthLimiter.class);

    /** Per-torrent upload buckets. Keyed by info hash (hex string). */
    private final Map<String, TokenBucket> perTorrentUploadBuckets;

    /** Per-torrent download buckets. Keyed by info hash (hex string). */
    private final Map<String, TokenBucket> perTorrentDownloadBuckets;

    /** Per-torrent limits configuration. */
    private final Map<String, TorrentBandwidthConfig> torrentConfigs;

    /** Per-torrent statistics. */
    private final Map<String, TorrentBandwidthStats> torrentStats;

    /**
     * Create per-torrent bandwidth limiter.
     *
     * @param globalUploadBytesPerSec   global upload limit (0 = unlimited)
     * @param globalDownloadBytesPerSec global download limit (0 = unlimited)
     * @param perSocketBytesPerSec      per-socket limit (0 = unlimited)
     */
    public PerTorrentBandwidthLimiter(
            long globalUploadBytesPerSec,
            long globalDownloadBytesPerSec,
            long perSocketBytesPerSec) {
        super(globalUploadBytesPerSec, globalDownloadBytesPerSec, perSocketBytesPerSec);
        this.perTorrentUploadBuckets = new ConcurrentHashMap<>();
        this.perTorrentDownloadBuckets = new ConcurrentHashMap<>();
        this.torrentConfigs = new ConcurrentHashMap<>();
        this.torrentStats = new ConcurrentHashMap<>();
    }

    /**
     * Set bandwidth limits for a specific torrent.
     *
     * @param infoHash            torrent info hash (hex string or byte array)
     * @param uploadBytesPerSec   upload limit (0 = use global limit only)
     * @param downloadBytesPerSec download limit (0 = use global limit only)
     */
    public void setTorrentLimits(byte[] infoHash, long uploadBytesPerSec, long downloadBytesPerSec) {
        setTorrentLimits(bytesToHex(infoHash), uploadBytesPerSec, downloadBytesPerSec);
    }

    /**
     * Set bandwidth limits for a specific torrent.
     *
     * @param infoHashHex         torrent info hash (hex string)
     * @param uploadBytesPerSec   upload limit (0 = use global limit only)
     * @param downloadBytesPerSec download limit (0 = use global limit only)
     */
    public void setTorrentLimits(String infoHashHex, long uploadBytesPerSec, long downloadBytesPerSec) {
        TorrentBandwidthConfig config = new TorrentBandwidthConfig(uploadBytesPerSec, downloadBytesPerSec);
        torrentConfigs.put(infoHashHex, config);

        // Create or update buckets
        if (uploadBytesPerSec > 0) {
            perTorrentUploadBuckets.put(infoHashHex,
                    new TokenBucket(uploadBytesPerSec, uploadBytesPerSec));
        } else {
            perTorrentUploadBuckets.remove(infoHashHex);
        }

        if (downloadBytesPerSec > 0) {
            perTorrentDownloadBuckets.put(infoHashHex,
                    new TokenBucket(downloadBytesPerSec, downloadBytesPerSec));
        } else {
            perTorrentDownloadBuckets.remove(infoHashHex);
        }

        logger.info("Set bandwidth limits for torrent %s: upload=%d KB/s, download=%d KB/s",
                infoHashHex.substring(0, 8) + "...",
                uploadBytesPerSec / 1024,
                downloadBytesPerSec / 1024);
    }

    /**
     * Remove bandwidth limits for a specific torrent.
     *
     * @param infoHash torrent info hash
     */
    public void removeTorrentLimits(byte[] infoHash) {
        removeTorrentLimits(bytesToHex(infoHash));
    }

    /**
     * Remove bandwidth limits for a specific torrent.
     *
     * @param infoHashHex torrent info hash (hex string)
     */
    public void removeTorrentLimits(String infoHashHex) {
        torrentConfigs.remove(infoHashHex);
        perTorrentUploadBuckets.remove(infoHashHex);
        perTorrentDownloadBuckets.remove(infoHashHex);
        logger.debug("Removed bandwidth limits for torrent %s", infoHashHex.substring(0, 8) + "...");
    }

    /**
     * Request upload bandwidth for a specific torrent.
     *
     * @param infoHash torrent info hash
     * @param socketId socket identifier for per-socket limiting
     * @param bytes    bytes to upload
     * @return bytes allowed (may be less than requested)
     */
    public long requestTorrentUpload(byte[] infoHash, String socketId, long bytes) {
        return requestTorrentUpload(bytesToHex(infoHash), socketId, bytes);
    }

    /**
     * Request upload bandwidth for a specific torrent.
     *
     * @param infoHashHex torrent info hash (hex string)
     * @param socketId    socket identifier for per-socket limiting
     * @param bytes       bytes to upload
     * @return bytes allowed (may be less than requested)
     */
    public long requestTorrentUpload(String infoHashHex, String socketId, long bytes) {
        // Check per-torrent limit first
        TokenBucket torrentBucket = perTorrentUploadBuckets.get(infoHashHex);
        long allowed = bytes;

        if (torrentBucket != null) {
            allowed = torrentBucket.consume(allowed);
        }

        // Then apply global limits via parent class
        if (allowed > 0) {
            allowed = super.requestUpload(socketId, allowed);
        }

        // Track statistics
        if (allowed > 0) {
            getOrCreateStats(infoHashHex).recordUpload(allowed);
        }

        return allowed;
    }

    /**
     * Request download bandwidth for a specific torrent.
     *
     * @param infoHash torrent info hash
     * @param socketId socket identifier for per-socket limiting
     * @param bytes    bytes to download
     * @return bytes allowed (may be less than requested)
     */
    public long requestTorrentDownload(byte[] infoHash, String socketId, long bytes) {
        return requestTorrentDownload(bytesToHex(infoHash), socketId, bytes);
    }

    /**
     * Request download bandwidth for a specific torrent.
     *
     * @param infoHashHex torrent info hash (hex string)
     * @param socketId    socket identifier for per-socket limiting
     * @param bytes       bytes to download
     * @return bytes allowed (may be less than requested)
     */
    public long requestTorrentDownload(String infoHashHex, String socketId, long bytes) {
        // Check per-torrent limit first
        TokenBucket torrentBucket = perTorrentDownloadBuckets.get(infoHashHex);
        long allowed = bytes;

        if (torrentBucket != null) {
            allowed = torrentBucket.consume(allowed);
        }

        // Then apply global limits via parent class
        if (allowed > 0) {
            allowed = super.requestDownload(socketId, allowed);
        }

        // Track statistics
        if (allowed > 0) {
            getOrCreateStats(infoHashHex).recordDownload(allowed);
        }

        return allowed;
    }

    /**
     * Get current bandwidth limits for a torrent.
     *
     * @param infoHash torrent info hash
     * @return config or null if no per-torrent limits set
     */
    public TorrentBandwidthConfig getTorrentLimits(byte[] infoHash) {
        return torrentConfigs.get(bytesToHex(infoHash));
    }

    /**
     * Get bandwidth statistics for a torrent.
     *
     * @param infoHash torrent info hash
     * @return statistics
     */
    public TorrentBandwidthStats getTorrentStats(byte[] infoHash) {
        return getOrCreateStats(bytesToHex(infoHash));
    }

    /**
     * Get or create statistics for a torrent.
     */
    private TorrentBandwidthStats getOrCreateStats(String infoHashHex) {
        return torrentStats.computeIfAbsent(infoHashHex, k -> new TorrentBandwidthStats());
    }

    /**
     * Convert byte array to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Torrent-specific bandwidth configuration.
     */
    public static class TorrentBandwidthConfig {
        private final long uploadBytesPerSec;
        private final long downloadBytesPerSec;

        public TorrentBandwidthConfig(long uploadBytesPerSec, long downloadBytesPerSec) {
            this.uploadBytesPerSec = uploadBytesPerSec;
            this.downloadBytesPerSec = downloadBytesPerSec;
        }

        public long getUploadBytesPerSec() {
            return uploadBytesPerSec;
        }

        public long getDownloadBytesPerSec() {
            return downloadBytesPerSec;
        }

        @Override
        public String toString() {
            return String.format("TorrentBandwidthConfig{upload=%d KB/s, download=%d KB/s}",
                    uploadBytesPerSec / 1024, downloadBytesPerSec / 1024);
        }
    }

    /**
     * Torrent-specific bandwidth statistics.
     */
    public static class TorrentBandwidthStats {
        private long totalUploaded = 0;
        private long totalDownloaded = 0;
        private long lastUploadTime = System.currentTimeMillis();
        private long lastDownloadTime = System.currentTimeMillis();
        private long uploadedLastSecond = 0;
        private long downloadedLastSecond = 0;
        private long lastRateUpdateTime = System.currentTimeMillis();
        private long currentUploadRate = 0;
        private long currentDownloadRate = 0;

        public synchronized void recordUpload(long bytes) {
            totalUploaded += bytes;
            uploadedLastSecond += bytes;
            lastUploadTime = System.currentTimeMillis();
            updateRates();
        }

        public synchronized void recordDownload(long bytes) {
            totalDownloaded += bytes;
            downloadedLastSecond += bytes;
            lastDownloadTime = System.currentTimeMillis();
            updateRates();
        }

        private void updateRates() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRateUpdateTime;
            if (elapsed >= 1000) {
                currentUploadRate = (uploadedLastSecond * 1000) / elapsed;
                currentDownloadRate = (downloadedLastSecond * 1000) / elapsed;
                uploadedLastSecond = 0;
                downloadedLastSecond = 0;
                lastRateUpdateTime = now;
            }
        }

        public long getTotalUploaded() {
            return totalUploaded;
        }

        public long getTotalDownloaded() {
            return totalDownloaded;
        }

        public long getCurrentUploadRate() {
            updateRates();
            return currentUploadRate;
        }

        public long getCurrentDownloadRate() {
            updateRates();
            return currentDownloadRate;
        }

        @Override
        public String toString() {
            return String.format(
                    "TorrentBandwidthStats{uploaded=%d MB, downloaded=%d MB, upRate=%d KB/s, downRate=%d KB/s}",
                    totalUploaded / (1024 * 1024),
                    totalDownloaded / (1024 * 1024),
                    currentUploadRate / 1024,
                    currentDownloadRate / 1024);
        }
    }
}
