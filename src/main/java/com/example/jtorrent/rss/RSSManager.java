package com.example.jtorrent.rss;

import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Manages RSS feeds, periodic polling, and automatic torrent downloads.
 */
public class RSSManager {

    private final Logger logger;
    private final RSSParser parser;
    private final RSSDownloader downloader;
    @SuppressWarnings("unused") // Reserved for future event publishing
    private final EventBus eventBus;
    private final List<RSSFeed> feeds;
    private final Set<String> downloadedItems; // Track to prevent duplicates
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public RSSManager(RSSDownloader downloader, Logger logger) {
        this.logger = logger;
        this.parser = new RSSParser(logger);
        this.downloader = downloader;
        this.eventBus = EventBus.getInstance();
        this.feeds = new CopyOnWriteArrayList<>();
        this.downloadedItems = ConcurrentHashMap.newKeySet();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "RSS-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Add RSS feed.
     *
     * @param feed feed to add
     */
    public void addFeed(RSSFeed feed) {
        feeds.add(feed);
        logger.info("Added RSS feed: %s", feed.getName());

        // Schedule immediate fetch if running
        if (running && feed.isEnabled()) {
            scheduleFeedUpdate(feed, 0);
        }
    }

    /**
     * Remove RSS feed.
     *
     * @param feed feed to remove
     */
    public void removeFeed(RSSFeed feed) {
        feeds.remove(feed);
        logger.info("Removed RSS feed: %s", feed.getName());
    }

    /**
     * Get all feeds.
     */
    public List<RSSFeed> getFeeds() {
        return new ArrayList<>(feeds);
    }

    /**
     * Start RSS polling.
     */
    public void start() {
        if (running) {
            return;
        }

        running = true;
        logger.info("Starting RSS manager");

        // Schedule all enabled feeds
        for (RSSFeed feed : feeds) {
            if (feed.isEnabled()) {
                scheduleFeedUpdate(feed, 0); // Immediate first fetch
            }
        }
    }

    /**
     * Stop RSS polling.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        logger.info("Stopping RSS manager");
        scheduler.shutdown();
    }

    /**
     * Force refresh of a specific feed.
     *
     * @param feed feed to refresh
     */
    public void refreshFeed(RSSFeed feed) {
        if (!running) {
            start();
        }
        scheduleFeedUpdate(feed, 0);
    }

    /**
     * Force refresh all feeds.
     */
    public void refreshAll() {
        for (RSSFeed feed : feeds) {
            if (feed.isEnabled()) {
                refreshFeed(feed);
            }
        }
    }

    /**
     * Schedule feed update.
     */
    private void scheduleFeedUpdate(RSSFeed feed, long delayMinutes) {
        scheduler.schedule(() -> {
            try {
                updateFeed(feed);
            } catch (Exception e) {
                logger.error("RSS feed update failed: %s - %s", feed.getName(), e.getMessage());
                feed.setLastError(e.getMessage());
            }

            // Reschedule if still running and enabled
            if (running && feed.isEnabled()) {
                scheduleFeedUpdate(feed, feed.getRefreshIntervalMinutes());
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    /**
     * Update single feed.
     */
    private void updateFeed(RSSFeed feed) throws Exception {
        logger.debug("Updating RSS feed: %s", feed.getName());

        // Fetch and parse feed
        List<RSSItem> items = parser.fetchFeed(feed.getUrl());
        feed.setLastFetchTime(System.currentTimeMillis());
        feed.setLastError(null);

        logger.info("RSS feed %s: fetched %d items", feed.getName(), items.size());

        // Process items against rules
        int matchCount = 0;
        for (RSSItem item : items) {
            // Check if already downloaded
            String itemKey = item.getTorrentUrl().toString();
            if (downloadedItems.contains(itemKey)) {
                continue;
            }

            // Check against feed rules
            for (RSSRule rule : feed.getRules()) {
                if (rule.matches(item)) {
                    logger.info("RSS rule '%s' matched: %s", rule.getName(), item.getTitle());

                    // Download torrent
                    try {
                        downloader.downloadTorrent(item, rule);
                        downloadedItems.add(itemKey);
                        matchCount++;

                        // Publish event
                        // Note: We don't have the actual info hash yet, will be added when torrent
                        // loads
                        // This is just for notification purposes

                    } catch (Exception e) {
                        logger.error("Failed to download RSS item: %s - %s",
                                item.getTitle(), e.getMessage());
                    }

                    break; // Only match first rule
                }
            }
        }

        if (matchCount > 0) {
            logger.info("RSS feed %s: downloaded %d new torrents", feed.getName(), matchCount);
        }
    }

    /**
     * Clear download history (for testing or reset).
     */
    public void clearDownloadHistory() {
        downloadedItems.clear();
        logger.info("RSS download history cleared");
    }

    /**
     * Get count of unique items downloaded via RSS.
     */
    public int getDownloadedCount() {
        return downloadedItems.size();
    }
}
