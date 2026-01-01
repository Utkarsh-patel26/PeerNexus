package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages connection limits and throttling for peer connections.
 * 
 * Features:
 * - Global connection limit
 * - Per-torrent connection limit
 * - Half-open connection limit (pending connections)
 * - Connection rate throttling
 * - IPv4/IPv6 balance
 */
public class ConnectionLimiter {

    private static final Logger logger = Logger.getLogger(ConnectionLimiter.class);

    /** Default limits. */
    private static final int DEFAULT_MAX_GLOBAL_CONNECTIONS = 200;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_TORRENT = 50;
    private static final int DEFAULT_MAX_HALF_OPEN = 20;
    private static final int DEFAULT_CONNECTIONS_PER_SECOND = 10;

    /** Current limits. */
    private int maxGlobalConnections = DEFAULT_MAX_GLOBAL_CONNECTIONS;
    private int maxConnectionsPerTorrent = DEFAULT_MAX_CONNECTIONS_PER_TORRENT;
    private int maxHalfOpen = DEFAULT_MAX_HALF_OPEN;
    private int maxConnectionsPerSecond = DEFAULT_CONNECTIONS_PER_SECOND;

    /** Connection tracking. */
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger halfOpenConnections = new AtomicInteger(0);
    private final Map<String, AtomicInteger> torrentConnections = new ConcurrentHashMap<>();
    private final Set<InetSocketAddress> connectedPeers = ConcurrentHashMap.newKeySet();

    /** Rate limiting. */
    private final AtomicLong lastConnectionTime = new AtomicLong(0);
    private final AtomicInteger connectionsThisSecond = new AtomicInteger(0);

    /** Statistics. */
    private final AtomicLong totalConnectionAttempts = new AtomicLong(0);
    private final AtomicLong rejectedByGlobalLimit = new AtomicLong(0);
    private final AtomicLong rejectedByTorrentLimit = new AtomicLong(0);
    private final AtomicLong rejectedByHalfOpen = new AtomicLong(0);
    private final AtomicLong rejectedByRateLimit = new AtomicLong(0);

    /**
     * Check if a new connection can be initiated.
     *
     * @param torrentId the torrent identifier
     * @param peer      the peer address
     * @return true if connection is allowed
     */
    public synchronized boolean canConnect(String torrentId, InetSocketAddress peer) {
        totalConnectionAttempts.incrementAndGet();

        // Already connected?
        if (connectedPeers.contains(peer)) {
            return false;
        }

        // Global limit
        if (totalConnections.get() >= maxGlobalConnections) {
            rejectedByGlobalLimit.incrementAndGet();
            logger.debug("Connection rejected: global limit (%d/%d)",
                    totalConnections.get(), maxGlobalConnections);
            return false;
        }

        // Per-torrent limit
        AtomicInteger torrentCount = torrentConnections.computeIfAbsent(torrentId, k -> new AtomicInteger(0));
        if (torrentCount.get() >= maxConnectionsPerTorrent) {
            rejectedByTorrentLimit.incrementAndGet();
            logger.debug("Connection rejected: torrent limit for %s (%d/%d)",
                    torrentId, torrentCount.get(), maxConnectionsPerTorrent);
            return false;
        }

        // Half-open limit
        if (halfOpenConnections.get() >= maxHalfOpen) {
            rejectedByHalfOpen.incrementAndGet();
            logger.debug("Connection rejected: half-open limit (%d/%d)",
                    halfOpenConnections.get(), maxHalfOpen);
            return false;
        }

        // Rate limit
        long now = System.currentTimeMillis();
        long lastSecond = lastConnectionTime.get();
        if (now - lastSecond >= 1000) {
            // New second, reset counter
            lastConnectionTime.set(now);
            connectionsThisSecond.set(0);
        }

        if (connectionsThisSecond.get() >= maxConnectionsPerSecond) {
            rejectedByRateLimit.incrementAndGet();
            logger.debug("Connection rejected: rate limit (%d/s)",
                    connectionsThisSecond.get());
            return false;
        }

        return true;
    }

    /**
     * Record that a connection attempt has started (half-open).
     *
     * @param torrentId the torrent identifier
     * @param peer      the peer address
     */
    public void connectionStarted(String torrentId, InetSocketAddress peer) {
        halfOpenConnections.incrementAndGet();
        connectionsThisSecond.incrementAndGet();
        logger.trace("Connection started to %s (half-open: %d)",
                peer, halfOpenConnections.get());
    }

    /**
     * Record that a connection has been established.
     *
     * @param torrentId the torrent identifier
     * @param peer      the peer address
     */
    public void connectionEstablished(String torrentId, InetSocketAddress peer) {
        halfOpenConnections.decrementAndGet();
        totalConnections.incrementAndGet();
        connectedPeers.add(peer);

        AtomicInteger torrentCount = torrentConnections.computeIfAbsent(torrentId, k -> new AtomicInteger(0));
        torrentCount.incrementAndGet();

        logger.debug("Connection established to %s (total: %d, torrent: %d)",
                peer, totalConnections.get(), torrentCount.get());
    }

    /**
     * Record that a connection attempt failed.
     *
     * @param torrentId the torrent identifier
     * @param peer      the peer address
     */
    public void connectionFailed(String torrentId, InetSocketAddress peer) {
        halfOpenConnections.decrementAndGet();
        logger.trace("Connection failed to %s (half-open: %d)",
                peer, halfOpenConnections.get());
    }

    /**
     * Record that a connection has been closed.
     *
     * @param torrentId the torrent identifier
     * @param peer      the peer address
     */
    public void connectionClosed(String torrentId, InetSocketAddress peer) {
        if (connectedPeers.remove(peer)) {
            totalConnections.decrementAndGet();

            AtomicInteger torrentCount = torrentConnections.get(torrentId);
            if (torrentCount != null) {
                torrentCount.decrementAndGet();
            }

            logger.debug("Connection closed to %s (total: %d)",
                    peer, totalConnections.get());
        }
    }

    /**
     * Check if we're at or near the global connection limit.
     *
     * @return true if at 90% of limit
     */
    public boolean isNearLimit() {
        return totalConnections.get() >= (maxGlobalConnections * 0.9);
    }

    /**
     * Get the number of available connection slots.
     *
     * @return available slots
     */
    public int getAvailableSlots() {
        return Math.max(0, maxGlobalConnections - totalConnections.get());
    }

    /**
     * Get the number of available slots for a specific torrent.
     *
     * @param torrentId the torrent identifier
     * @return available slots for this torrent
     */
    public int getAvailableSlotsForTorrent(String torrentId) {
        int globalAvailable = getAvailableSlots();
        AtomicInteger torrentCount = torrentConnections.get(torrentId);
        int torrentUsed = torrentCount != null ? torrentCount.get() : 0;
        int torrentAvailable = maxConnectionsPerTorrent - torrentUsed;
        return Math.min(globalAvailable, torrentAvailable);
    }

    // Configuration setters

    public void setMaxGlobalConnections(int max) {
        this.maxGlobalConnections = max;
        logger.info("Max global connections set to %d", max);
    }

    public void setMaxConnectionsPerTorrent(int max) {
        this.maxConnectionsPerTorrent = max;
        logger.info("Max connections per torrent set to %d", max);
    }

    public void setMaxHalfOpen(int max) {
        this.maxHalfOpen = max;
        logger.info("Max half-open connections set to %d", max);
    }

    public void setMaxConnectionsPerSecond(int max) {
        this.maxConnectionsPerSecond = max;
        logger.info("Max connections per second set to %d", max);
    }

    // Getters

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public int getHalfOpenConnections() {
        return halfOpenConnections.get();
    }

    public int getConnectionsForTorrent(String torrentId) {
        AtomicInteger count = torrentConnections.get(torrentId);
        return count != null ? count.get() : 0;
    }

    public int getMaxGlobalConnections() {
        return maxGlobalConnections;
    }

    public int getMaxConnectionsPerTorrent() {
        return maxConnectionsPerTorrent;
    }

    /**
     * Get statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalConnections", totalConnections.get());
        stats.put("halfOpenConnections", halfOpenConnections.get());
        stats.put("maxGlobalConnections", maxGlobalConnections);
        stats.put("maxConnectionsPerTorrent", maxConnectionsPerTorrent);
        stats.put("maxHalfOpen", maxHalfOpen);
        stats.put("totalAttempts", totalConnectionAttempts.get());
        stats.put("rejectedByGlobalLimit", rejectedByGlobalLimit.get());
        stats.put("rejectedByTorrentLimit", rejectedByTorrentLimit.get());
        stats.put("rejectedByHalfOpen", rejectedByHalfOpen.get());
        stats.put("rejectedByRateLimit", rejectedByRateLimit.get());
        stats.put("torrentsTracked", torrentConnections.size());
        return stats;
    }

    /**
     * Reset statistics (but not connection counts).
     */
    public void resetStatistics() {
        totalConnectionAttempts.set(0);
        rejectedByGlobalLimit.set(0);
        rejectedByTorrentLimit.set(0);
        rejectedByHalfOpen.set(0);
        rejectedByRateLimit.set(0);
    }

    /**
     * Clean up tracking for a torrent.
     *
     * @param torrentId the torrent identifier
     */
    public void removeTorrent(String torrentId) {
        torrentConnections.remove(torrentId);
    }
}
