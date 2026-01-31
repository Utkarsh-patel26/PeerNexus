package com.example.jtorrent.scheduler;

import com.example.jtorrent.core.PeerScoring;
import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.peer.PeerConnection;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages peer connections with limits and performance-based prioritization.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Global peer limit (~150 connections)
 * <li>Per-torrent peer limit (~80 connections)
 * <li>Automatic dropping of slow/unresponsive peers
 * <li>Priority queue based on peer performance
 * <li>Integration with PeerScoring for misbehavior tracking
 * <li>Connection pool management
 * </ul>
 */
public class PeerConnectionManager {

    private static final Logger logger = Logger.getLogger(PeerConnectionManager.class);

    // Connection limits
    private static final int MAX_GLOBAL_PEERS = 150;
    private static final int MAX_PEERS_PER_TORRENT = 80;
    private static final int MIN_DOWNLOAD_RATE_BYTES_PER_SEC = 1024; // 1 KB/s minimum
    private static final long IDLE_TIMEOUT_MS = 60000; // 60 seconds
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds

    // Connection tracking
    private final Map<InetSocketAddress, ManagedPeerConnection> activePeers;
    private final Map<String, List<ManagedPeerConnection>> peersByTorrent;
    private final PriorityBlockingQueue<ManagedPeerConnection> peerQueue;
    private final AtomicInteger totalConnections;

    // Peer behavior scoring (misbehavior tracking)
    private PeerScoring peerScoring;

    // Cleanup thread
    private volatile boolean running = true;
    private Thread cleanupThread;

    /**
     * Managed peer connection wrapper.
     */
    public static class ManagedPeerConnection implements Comparable<ManagedPeerConnection> {
        public final InetSocketAddress address;
        public final PeerConnection connection;
        public final PeerStats stats;
        public final String torrentId;
        private volatile boolean closed = false;
        private double combinedScore = 0.0;

        public ManagedPeerConnection(
                InetSocketAddress address,
                PeerConnection connection,
                PeerStats stats,
                String torrentId) {
            this.address = address;
            this.connection = connection;
            this.stats = stats;
            this.torrentId = torrentId;
        }

        public boolean isClosed() {
            return closed;
        }

        public void markClosed() {
            closed = true;
        }

        /**
         * Set combined score factoring in both performance and behavior.
         *
         * @param score combined score
         */
        public void setCombinedScore(double score) {
            this.combinedScore = score;
        }

        /**
         * Get combined score.
         *
         * @return combined score
         */
        public double getCombinedScore() {
            return combinedScore > 0 ? combinedScore : stats.getQualityScore();
        }

        @Override
        public int compareTo(ManagedPeerConnection other) {
            // Higher combined score = higher priority
            return Double.compare(other.getCombinedScore(), this.getCombinedScore());
        }
    }

    /**
     * Create peer connection manager.
     */
    public PeerConnectionManager() {
        this(null);
    }

    /**
     * Create peer connection manager with peer scoring integration.
     *
     * @param peerScoring the peer scoring system for misbehavior tracking
     */
    public PeerConnectionManager(PeerScoring peerScoring) {
        this.activePeers = new ConcurrentHashMap<>();
        this.peersByTorrent = new ConcurrentHashMap<>();
        this.peerQueue = new PriorityBlockingQueue<>(
                MAX_GLOBAL_PEERS,
                Comparator.comparingDouble(p -> -p.getCombinedScore()));
        this.totalConnections = new AtomicInteger(0);
        this.peerScoring = peerScoring;

        startCleanupThread();
    }

    /**
     * Set the peer scoring system.
     *
     * @param peerScoring the peer scoring system
     */
    public void setPeerScoring(PeerScoring peerScoring) {
        this.peerScoring = peerScoring;
    }

    /**
     * Try to register a new peer connection.
     *
     * @param address    peer address
     * @param connection peer connection
     * @param stats      peer statistics
     * @param torrentId  torrent identifier
     * @return true if registered, false if rejected due to limits or ban
     */
    public boolean registerPeer(
            InetSocketAddress address,
            PeerConnection connection,
            PeerStats stats,
            String torrentId) {

        // Check if peer is banned
        if (peerScoring != null && peerScoring.isBanned(address)) {
            logger.debug("Rejecting banned peer %s", address);
            return false;
        }

        // Check global limit
        if (totalConnections.get() >= MAX_GLOBAL_PEERS) {
            logger.debug("Global peer limit reached (%d), rejecting peer %s",
                    MAX_GLOBAL_PEERS, address);
            return false;
        }

        // Check per-torrent limit
        List<ManagedPeerConnection> torrentPeers = peersByTorrent.computeIfAbsent(
                torrentId, k -> new ArrayList<>());

        synchronized (torrentPeers) {
            // Calculate combined score for new peer
            double newPeerScore = calculateCombinedScore(address, stats);

            if (torrentPeers.size() >= MAX_PEERS_PER_TORRENT) {
                // Try to evict worst peer
                ManagedPeerConnection worstPeer = findWorstPeer(torrentPeers);
                if (worstPeer != null && worstPeer.getCombinedScore() < newPeerScore) {
                    logger.info("Evicting low-quality peer %s (score=%.1f) for %s (score=%.1f)",
                            worstPeer.address, worstPeer.getCombinedScore(),
                            address, newPeerScore);
                    removePeer(worstPeer.address, "replaced by better peer");
                } else {
                    logger.debug("Per-torrent peer limit reached (%d) for %s, rejecting peer %s",
                            MAX_PEERS_PER_TORRENT, torrentId, address);
                    return false;
                }
            }

            ManagedPeerConnection managed = new ManagedPeerConnection(
                    address, connection, stats, torrentId);
            managed.setCombinedScore(newPeerScore);

            activePeers.put(address, managed);
            torrentPeers.add(managed);
            peerQueue.add(managed);
            totalConnections.incrementAndGet();

            logger.debug("Registered peer %s for torrent %s (total: %d global, %d for torrent)",
                    address, torrentId, totalConnections.get(), torrentPeers.size());

            return true;
        }
    }

    /**
     * Remove a peer connection.
     *
     * @param address peer address
     * @param reason  reason for removal
     */
    public void removePeer(InetSocketAddress address, String reason) {
        ManagedPeerConnection managed = activePeers.remove(address);
        if (managed != null) {
            managed.markClosed();
            peerQueue.remove(managed);

            List<ManagedPeerConnection> torrentPeers = peersByTorrent.get(managed.torrentId);
            if (torrentPeers != null) {
                synchronized (torrentPeers) {
                    torrentPeers.remove(managed);
                }
            }

            totalConnections.decrementAndGet();

            // Clean close
            try {
                if (managed.connection != null) {
                    managed.connection.close();
                }
            } catch (Exception e) {
                logger.debug("Error closing peer %s: %s", address, e.getMessage());
            }

            logger.debug("Removed peer %s: %s (total: %d)", address, reason, totalConnections.get());
        }
    }

    /**
     * Get number of active connections.
     *
     * @return connection count
     */
    public int getConnectionCount() {
        return totalConnections.get();
    }

    /**
     * Get number of connections for a torrent.
     *
     * @param torrentId torrent identifier
     * @return connection count
     */
    public int getConnectionCount(String torrentId) {
        List<ManagedPeerConnection> peers = peersByTorrent.get(torrentId);
        return peers != null ? peers.size() : 0;
    }

    /**
     * Get top N peers by quality.
     *
     * @param count number of peers to return
     * @return list of best peers
     */
    public List<ManagedPeerConnection> getTopPeers(int count) {
        return peerQueue.stream()
                .filter(p -> !p.isClosed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Check if can accept more peers for a torrent.
     *
     * @param torrentId torrent identifier
     * @return true if can accept more
     */
    public boolean canAcceptMorePeers(String torrentId) {
        if (totalConnections.get() >= MAX_GLOBAL_PEERS) {
            return false;
        }
        List<ManagedPeerConnection> peers = peersByTorrent.get(torrentId);
        return peers == null || peers.size() < MAX_PEERS_PER_TORRENT;
    }

    /**
     * Cleanup slow, unresponsive, and misbehaving peers.
     */
    private void cleanup() {
        List<InetSocketAddress> toRemove = new ArrayList<>();

        for (ManagedPeerConnection managed : activePeers.values()) {
            if (managed.isClosed()) {
                toRemove.add(managed.address);
                continue;
            }

            // Check if peer became banned
            if (peerScoring != null && peerScoring.isBanned(managed.address)) {
                logger.info("Removing banned peer %s", managed.address);
                toRemove.add(managed.address);
                continue;
            }

            PeerStats stats = managed.stats;

            // Check if peer is idle
            if (stats.getIdleTime() > IDLE_TIMEOUT_MS) {
                logger.info("Removing idle peer %s (idle for %.1fs)",
                        managed.address, stats.getIdleTime() / 1000.0);
                toRemove.add(managed.address);
                continue;
            }

            // Check if peer is slow
            if (stats.isSlow(MIN_DOWNLOAD_RATE_BYTES_PER_SEC)) {
                logger.info("Removing slow peer %s (rate: %.1f KB/s)",
                        managed.address, stats.getDownloadRateBytesPerSec() / 1024.0);
                toRemove.add(managed.address);
                continue;
            }

            // Check if peer has low success rate
            if (stats.getConnectionAge() > 30000 && stats.getSuccessRate() < 0.5) {
                logger.info("Removing unreliable peer %s (success rate: %.1f%%)",
                        managed.address, stats.getSuccessRate() * 100);
                toRemove.add(managed.address);
                continue;
            }

            // Update stats and combined score
            stats.updateRates(5000);
            double newScore = calculateCombinedScore(managed.address, stats);
            managed.setCombinedScore(newScore);
        }

        // Remove bad peers
        for (InetSocketAddress address : toRemove) {
            removePeer(address, "performance/timeout/banned");
        }

        if (!toRemove.isEmpty()) {
            logger.info("Cleaned up %d peers (total remaining: %d)",
                    toRemove.size(), totalConnections.get());
        }
    }

    /**
     * Find worst peer in a list.
     */
    private ManagedPeerConnection findWorstPeer(List<ManagedPeerConnection> peers) {
        return peers.stream()
                .filter(p -> !p.isClosed())
                .min(Comparator.comparingDouble(ManagedPeerConnection::getCombinedScore))
                .orElse(null);
    }

    /**
     * Calculate combined score factoring in both performance and behavior.
     * Performance score from PeerStats is weighted with behavior score from
     * PeerScoring.
     *
     * @param address peer address
     * @param stats   performance stats
     * @return combined score (0-200, higher is better)
     */
    private double calculateCombinedScore(InetSocketAddress address, PeerStats stats) {
        // Performance score (0-100)
        double performanceScore = stats.getQualityScore();

        // Behavior score from PeerScoring (0-150, higher is better)
        double behaviorScore = 100.0; // Default for new peers
        if (peerScoring != null) {
            int peerScore = peerScoring.getPeerScore(address);
            if (peerScore >= 0) {
                // Normalize to 0-100 range (original range is 0-150)
                behaviorScore = (peerScore / 150.0) * 100.0;
            } else {
                // Banned peer
                return -1.0;
            }

            // Apply deprioritization penalty
            if (peerScoring.shouldDeprioritize(address)) {
                behaviorScore *= 0.5; // 50% penalty for misbehaving peers
            }
        }

        // Combined score: 60% performance, 40% behavior
        return (performanceScore * 0.6) + (behaviorScore * 0.4);
    }

    /**
     * Update combined scores for all active peers.
     * Should be called periodically to reflect behavior changes.
     */
    public void updateAllScores() {
        for (ManagedPeerConnection managed : activePeers.values()) {
            if (!managed.isClosed()) {
                double newScore = calculateCombinedScore(managed.address, managed.stats);
                managed.setCombinedScore(newScore);
            }
        }
    }

    /**
     * Get peers sorted by combined score for a specific torrent.
     *
     * @param torrentId torrent identifier
     * @param count     max number of peers to return
     * @return sorted list of peers (best first)
     */
    public List<ManagedPeerConnection> getBestPeersForTorrent(String torrentId, int count) {
        List<ManagedPeerConnection> torrentPeers = peersByTorrent.get(torrentId);
        if (torrentPeers == null) {
            return new ArrayList<>();
        }

        return torrentPeers.stream()
                .filter(p -> !p.isClosed())
                .sorted(Comparator.comparingDouble(
                        (ManagedPeerConnection p) -> p.getCombinedScore()).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Start cleanup thread.
     */
    private void startCleanupThread() {
        cleanupThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                    cleanup();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Error in cleanup thread: %s", e.getMessage());
                }
            }
        }, "PeerConnectionManager-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
        logger.info("Started peer connection manager (max: %d global, %d per torrent)",
                MAX_GLOBAL_PEERS, MAX_PEERS_PER_TORRENT);
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        running = false;
        if (cleanupThread != null) {
            cleanupThread.interrupt();
        }

        // Close all connections
        List<InetSocketAddress> allPeers = new ArrayList<>(activePeers.keySet());
        for (InetSocketAddress address : allPeers) {
            removePeer(address, "shutdown");
        }

        logger.info("Peer connection manager shutdown complete");
    }

    /**
     * Get statistics summary.
     *
     * @return stats summary string
     */
    public String getStatsSummary() {
        int bannedCount = peerScoring != null ? peerScoring.getBannedPeers().size() : 0;
        return String.format(
                "Peers: %d/%d global | Banned: %d | Avg Score: %.1f | Top 5: %s",
                totalConnections.get(),
                MAX_GLOBAL_PEERS,
                bannedCount,
                activePeers.values().stream()
                        .mapToDouble(ManagedPeerConnection::getCombinedScore)
                        .average()
                        .orElse(0.0),
                getTopPeers(5).stream()
                        .map(p -> String.format("%.0f", p.getCombinedScore()))
                        .collect(Collectors.joining(", ")));
    }
}
