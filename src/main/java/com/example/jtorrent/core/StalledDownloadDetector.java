package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Detects stalled downloads and provides status information.
 * 
 * A download is considered stalled when:
 * - No data has been received for a configurable timeout period
 * - We have peers but none are uploading to us
 * - We are interested but all peers are choking us
 */
public class StalledDownloadDetector {

    private static final Logger logger = Logger.getLogger(StalledDownloadDetector.class);

    // Default stall timeout in seconds
    private static final int DEFAULT_STALL_TIMEOUT_SECONDS = 60;

    // Check interval in seconds
    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final AtomicLong lastDataReceivedTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicBoolean stalled = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private int stallTimeoutSeconds = DEFAULT_STALL_TIMEOUT_SECONDS;
    private StallReason stallReason = StallReason.NONE;

    private ScheduledExecutorService scheduler;
    private Consumer<StallInfo> stallCallback;

    // Peer statistics
    private int connectedPeers = 0;
    private int unchokedPeers = 0;
    private int interestedPeers = 0;

    /**
     * Reasons for stalling.
     */
    public enum StallReason {
        NONE,
        NO_PEERS,
        ALL_CHOKED,
        NO_PIECES_AVAILABLE,
        TIMEOUT,
        DISK_ERROR
    }

    /**
     * Information about a stalled download.
     */
    public static class StallInfo {
        private final StallReason reason;
        private final long stallDurationMs;
        private final int connectedPeers;
        private final int unchokedPeers;
        private final String message;

        public StallInfo(StallReason reason, long stallDurationMs, int connectedPeers,
                int unchokedPeers, String message) {
            this.reason = reason;
            this.stallDurationMs = stallDurationMs;
            this.connectedPeers = connectedPeers;
            this.unchokedPeers = unchokedPeers;
            this.message = message;
        }

        public StallReason getReason() {
            return reason;
        }

        public long getStallDurationMs() {
            return stallDurationMs;
        }

        public int getConnectedPeers() {
            return connectedPeers;
        }

        public int getUnchokedPeers() {
            return unchokedPeers;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("Stalled: %s - %s (peers: %d, unchoked: %d)",
                    reason, message, connectedPeers, unchokedPeers);
        }
    }

    /**
     * Create a stalled download detector.
     */
    public StalledDownloadDetector() {
        this(DEFAULT_STALL_TIMEOUT_SECONDS);
    }

    /**
     * Create a stalled download detector with custom timeout.
     * 
     * @param stallTimeoutSeconds timeout in seconds before considering stalled
     */
    public StalledDownloadDetector(int stallTimeoutSeconds) {
        this.stallTimeoutSeconds = stallTimeoutSeconds;
    }

    /**
     * Start monitoring.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "StalledDetector");
                t.setDaemon(true);
                return t;
            });

            scheduler.scheduleAtFixedRate(
                    this::checkStalled,
                    CHECK_INTERVAL_SECONDS,
                    CHECK_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);

            logger.info("Stalled download detector started (timeout: %ds)", stallTimeoutSeconds);
        }
    }

    /**
     * Stop monitoring.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    scheduler.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("Stalled download detector stopped");
        }
    }

    /**
     * Set callback for stall events.
     * 
     * @param callback callback to invoke when stall status changes
     */
    public void setStallCallback(Consumer<StallInfo> callback) {
        this.stallCallback = callback;
    }

    /**
     * Record that data was received.
     * 
     * @param bytes number of bytes received
     */
    public void recordDataReceived(long bytes) {
        lastDataReceivedTime.set(System.currentTimeMillis());
        totalBytesReceived.addAndGet(bytes);

        // Clear stall if we were stalled
        if (stalled.compareAndSet(true, false)) {
            stallReason = StallReason.NONE;
            logger.info("Download resumed - no longer stalled");
        }
    }

    /**
     * Update peer statistics.
     * 
     * @param connected  number of connected peers
     * @param unchoked   number of peers that have unchoked us
     * @param interested number of peers we are interested in
     */
    public void updatePeerStats(int connected, int unchoked, int interested) {
        this.connectedPeers = connected;
        this.unchokedPeers = unchoked;
        this.interestedPeers = interested;
    }

    /**
     * Report a disk error.
     */
    public void reportDiskError() {
        if (stalled.compareAndSet(false, true)) {
            stallReason = StallReason.DISK_ERROR;
            notifyStall();
        }
    }

    /**
     * Report that no pieces are available from any peer.
     */
    public void reportNoPiecesAvailable() {
        if (!stalled.get()) {
            stallReason = StallReason.NO_PIECES_AVAILABLE;
        }
    }

    /**
     * Check if download is stalled.
     */
    private void checkStalled() {
        if (!running.get())
            return;

        try {
            long now = System.currentTimeMillis();
            long lastData = lastDataReceivedTime.get();
            long elapsed = now - lastData;

            StallReason newReason = StallReason.NONE;
            String message = "";

            // Check various stall conditions
            if (connectedPeers == 0) {
                newReason = StallReason.NO_PEERS;
                message = "No peers connected";
            } else if (interestedPeers > 0 && unchokedPeers == 0) {
                newReason = StallReason.ALL_CHOKED;
                message = "All peers are choking us";
            } else if (elapsed > stallTimeoutSeconds * 1000L) {
                newReason = StallReason.TIMEOUT;
                message = String.format("No data received for %d seconds", elapsed / 1000);
            }

            // If we have a stall reason and we weren't already stalled
            if (newReason != StallReason.NONE) {
                if (stalled.compareAndSet(false, true)) {
                    stallReason = newReason;
                    notifyStall();
                } else if (stallReason != newReason) {
                    // Update reason if it changed
                    stallReason = newReason;
                    notifyStall();
                }
            } else if (stalled.get()) {
                // Clear stall if conditions improved
                stalled.set(false);
                stallReason = StallReason.NONE;
                logger.info("Download no longer stalled");
            }

        } catch (Exception e) {
            logger.error("Error checking stall status: %s", e.getMessage());
        }
    }

    /**
     * Notify callback of stall.
     */
    private void notifyStall() {
        String message = getStallMessage();
        long duration = System.currentTimeMillis() - lastDataReceivedTime.get();

        StallInfo info = new StallInfo(
                stallReason,
                duration,
                connectedPeers,
                unchokedPeers,
                message);

        logger.warn("Download stalled: %s", info);

        if (stallCallback != null) {
            try {
                stallCallback.accept(info);
            } catch (Exception e) {
                logger.error("Error in stall callback: %s", e.getMessage());
            }
        }
    }

    /**
     * Get human-readable stall message.
     */
    private String getStallMessage() {
        switch (stallReason) {
            case NO_PEERS:
                return "No peers available. Try adding more trackers or enabling DHT.";
            case ALL_CHOKED:
                return "All peers are choking us. Download may resume when peers unchoke.";
            case NO_PIECES_AVAILABLE:
                return "No pieces available from connected peers.";
            case TIMEOUT:
                long elapsed = (System.currentTimeMillis() - lastDataReceivedTime.get()) / 1000;
                return String.format("No data received for %d seconds.", elapsed);
            case DISK_ERROR:
                return "Disk error occurred. Check disk space and permissions.";
            default:
                return "Unknown stall reason.";
        }
    }

    /**
     * Check if download is currently stalled.
     * 
     * @return true if stalled
     */
    public boolean isStalled() {
        return stalled.get();
    }

    /**
     * Get the current stall reason.
     * 
     * @return stall reason
     */
    public StallReason getStallReason() {
        return stallReason;
    }

    /**
     * Get formatted stall status.
     * 
     * @return stall status string, or empty if not stalled
     */
    public String getStallStatus() {
        if (!stalled.get()) {
            return "";
        }
        return getStallMessage();
    }

    /**
     * Get time since last data received.
     * 
     * @return milliseconds since last data
     */
    public long getTimeSinceLastData() {
        return System.currentTimeMillis() - lastDataReceivedTime.get();
    }

    /**
     * Get total bytes received.
     * 
     * @return total bytes
     */
    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }

    /**
     * Set stall timeout.
     * 
     * @param seconds timeout in seconds
     */
    public void setStallTimeout(int seconds) {
        this.stallTimeoutSeconds = seconds;
    }

    /**
     * Reset statistics.
     */
    public void reset() {
        lastDataReceivedTime.set(System.currentTimeMillis());
        totalBytesReceived.set(0);
        stalled.set(false);
        stallReason = StallReason.NONE;
        connectedPeers = 0;
        unchokedPeers = 0;
        interestedPeers = 0;
    }
}
