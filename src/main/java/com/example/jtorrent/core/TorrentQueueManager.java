package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages torrent queue with download/seed limits and auto-start rules.
 * 
 * Features:
 * - Maximum active downloads limit
 * - Maximum active seeds limit
 * - Priority queue ordering
 * - Auto-start when slots available
 * - Seeding goals (ratio-based, time-based)
 */
public class TorrentQueueManager {

    private static final Logger logger = Logger.getLogger(TorrentQueueManager.class);

    /** Default limits. */
    private static final int DEFAULT_MAX_ACTIVE_DOWNLOADS = 3;
    private static final int DEFAULT_MAX_ACTIVE_SEEDS = 5;

    private int maxActiveDownloads = DEFAULT_MAX_ACTIVE_DOWNLOADS;
    private int maxActiveSeeds = DEFAULT_MAX_ACTIVE_SEEDS;

    /** Seeding goals. */
    private double targetSeedRatio = 1.0; // 1:1 ratio by default
    private long targetSeedTimeMs = 0; // No time limit by default

    /** Queue of managed torrents. */
    private final List<QueuedTorrent> queue = new CopyOnWriteArrayList<>();

    /** Active download count. */
    private final AtomicInteger activeDownloads = new AtomicInteger(0);

    /** Active seed count. */
    private final AtomicInteger activeSeeds = new AtomicInteger(0);

    /** Queue processing enabled. */
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    /** Queue processor thread. */
    private ScheduledExecutorService scheduler;

    /**
     * Start the queue manager.
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TorrentQueueManager");
            t.setDaemon(true);
            return t;
        });

        // Process queue every 5 seconds
        scheduler.scheduleWithFixedDelay(this::processQueue, 1, 5, TimeUnit.SECONDS);

        logger.info("TorrentQueueManager started (maxDownloads=%d, maxSeeds=%d)",
                maxActiveDownloads, maxActiveSeeds);
    }

    /**
     * Stop the queue manager.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("TorrentQueueManager stopped");
    }

    /**
     * Add a torrent to the queue.
     *
     * @param torrent  the torrent to add
     * @param priority priority (higher = started sooner)
     */
    public void addTorrent(TorrentSession torrent, int priority) {
        QueuedTorrent qt = new QueuedTorrent(torrent, priority);
        queue.add(qt);
        sortQueue();
        logger.info("Added torrent to queue: %s (priority=%d)", torrent.getName(), priority);
        processQueue();
    }

    /**
     * Remove a torrent from the queue.
     *
     * @param torrent the torrent to remove
     */
    public void removeTorrent(TorrentSession torrent) {
        queue.removeIf(qt -> qt.session == torrent);
        logger.info("Removed torrent from queue: %s", torrent.getName());
    }

    /**
     * Set torrent priority.
     *
     * @param torrent  the torrent
     * @param priority new priority
     */
    public void setPriority(TorrentSession torrent, int priority) {
        for (QueuedTorrent qt : queue) {
            if (qt.session == torrent) {
                qt.priority = priority;
                break;
            }
        }
        sortQueue();
    }

    /**
     * Process the queue - start/stop torrents based on limits.
     */
    private void processQueue() {
        if (!enabled.get()) {
            return;
        }

        // Count current active
        int currentDownloads = 0;
        int currentSeeds = 0;

        for (QueuedTorrent qt : queue) {
            if (qt.state == QueuedTorrent.State.DOWNLOADING) {
                currentDownloads++;
            } else if (qt.state == QueuedTorrent.State.SEEDING) {
                currentSeeds++;
            }
        }

        activeDownloads.set(currentDownloads);
        activeSeeds.set(currentSeeds);

        // Check seeding goals and stop completed seeds
        for (QueuedTorrent qt : queue) {
            if (qt.state == QueuedTorrent.State.SEEDING) {
                if (shouldStopSeeding(qt)) {
                    stopTorrent(qt);
                }
            }
        }

        // Start queued torrents if slots available
        for (QueuedTorrent qt : queue) {
            if (qt.state == QueuedTorrent.State.QUEUED) {
                if (qt.isDownload && activeDownloads.get() < maxActiveDownloads) {
                    startTorrent(qt);
                } else if (!qt.isDownload && activeSeeds.get() < maxActiveSeeds) {
                    startTorrent(qt);
                }
            }
        }
    }

    /**
     * Check if a seeding torrent should be stopped based on goals.
     */
    private boolean shouldStopSeeding(QueuedTorrent qt) {
        // Check ratio goal
        if (targetSeedRatio > 0) {
            double ratio = qt.session.getSeedRatio();
            if (ratio >= targetSeedRatio) {
                logger.info("Torrent %s reached seed ratio %.2f (target: %.2f)",
                        qt.session.getName(), ratio, targetSeedRatio);
                return true;
            }
        }

        // Check time goal
        if (targetSeedTimeMs > 0) {
            long seedTime = qt.session.getSeedingTime();
            if (seedTime >= targetSeedTimeMs) {
                logger.info("Torrent %s reached seed time %d ms (target: %d)",
                        qt.session.getName(), seedTime, targetSeedTimeMs);
                return true;
            }
        }

        return false;
    }

    /**
     * Start a queued torrent.
     */
    private void startTorrent(QueuedTorrent qt) {
        try {
            qt.state = qt.isDownload ? QueuedTorrent.State.DOWNLOADING : QueuedTorrent.State.SEEDING;

            if (qt.isDownload) {
                activeDownloads.incrementAndGet();
            } else {
                activeSeeds.incrementAndGet();
            }

            // Start the session in a separate thread
            new Thread(() -> {
                try {
                    qt.session.start();
                } catch (Exception e) {
                    logger.error("Failed to start torrent: %s", e, qt.session.getName());
                    qt.state = QueuedTorrent.State.ERROR;
                }
            }, "Torrent-" + qt.session.getName()).start();

            logger.info("Started torrent: %s", qt.session.getName());
        } catch (Exception e) {
            logger.error("Error starting torrent", e);
            qt.state = QueuedTorrent.State.ERROR;
        }
    }

    /**
     * Stop a torrent.
     */
    private void stopTorrent(QueuedTorrent qt) {
        try {
            qt.session.stop();
            qt.state = QueuedTorrent.State.STOPPED;

            if (qt.isDownload) {
                activeDownloads.decrementAndGet();
            } else {
                activeSeeds.decrementAndGet();
            }

            logger.info("Stopped torrent: %s", qt.session.getName());
        } catch (Exception e) {
            logger.error("Error stopping torrent", e);
        }
    }

    /**
     * Sort queue by priority (higher first).
     */
    private void sortQueue() {
        queue.sort((a, b) -> Integer.compare(b.priority, a.priority));
    }

    // Configuration methods

    public void setMaxActiveDownloads(int max) {
        this.maxActiveDownloads = max;
        processQueue();
    }

    public int getMaxActiveDownloads() {
        return maxActiveDownloads;
    }

    public void setMaxActiveSeeds(int max) {
        this.maxActiveSeeds = max;
        processQueue();
    }

    public int getMaxActiveSeeds() {
        return maxActiveSeeds;
    }

    public void setTargetSeedRatio(double ratio) {
        this.targetSeedRatio = ratio;
    }

    public double getTargetSeedRatio() {
        return targetSeedRatio;
    }

    public void setTargetSeedTimeMinutes(int minutes) {
        this.targetSeedTimeMs = minutes * 60 * 1000L;
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    /**
     * Get queue statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queueSize", queue.size());
        stats.put("activeDownloads", activeDownloads.get());
        stats.put("activeSeeds", activeSeeds.get());
        stats.put("maxActiveDownloads", maxActiveDownloads);
        stats.put("maxActiveSeeds", maxActiveSeeds);
        stats.put("targetSeedRatio", targetSeedRatio);
        stats.put("enabled", enabled.get());
        return stats;
    }

    /**
     * Get list of queued torrents with their states.
     */
    public List<Map<String, Object>> getQueueList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (QueuedTorrent qt : queue) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", qt.session.getName());
            info.put("priority", qt.priority);
            info.put("state", qt.state.name());
            info.put("isDownload", qt.isDownload);
            list.add(info);
        }
        return list;
    }

    /**
     * Queued torrent wrapper.
     */
    private static class QueuedTorrent {
        final TorrentSession session;
        int priority;
        State state;
        boolean isDownload;

        enum State {
            QUEUED,
            DOWNLOADING,
            SEEDING,
            STOPPED,
            ERROR
        }

        QueuedTorrent(TorrentSession session, int priority) {
            this.session = session;
            this.priority = priority;
            this.state = State.QUEUED;
            this.isDownload = !session.isComplete();
        }
    }
}
