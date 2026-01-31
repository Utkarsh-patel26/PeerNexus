package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.peer.PeerConnection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Advanced choking algorithm manager implementing the BitTorrent spec.
 * 
 * Implements:
 * - Regular rechoke every 10 seconds
 * - Optimistic unchoke every 30 seconds
 * - Rate-based peer selection when seeding
 * - Download rate-based selection when leeching
 */
public class ChokingManager {

    private static final Logger logger = Logger.getLogger(ChokingManager.class);

    // Rechoke interval in seconds
    private static final int RECHOKE_INTERVAL_SECONDS = 10;

    // Optimistic unchoke interval (3 rechoke cycles)
    private static final int OPTIMISTIC_UNCHOKE_ROUNDS = 3;

    // Maximum number of unchoked peers
    private static final int DEFAULT_MAX_UNCHOKED = 4;

    // Number of optimistic unchoke slots
    private static final int OPTIMISTIC_UNCHOKE_SLOTS = 1;

    private final ScheduledExecutorService scheduler;
    private final Map<PeerConnection, PeerStats> peerStats = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private int maxUnchokedPeers = DEFAULT_MAX_UNCHOKED;
    private int rechokeRound = 0;
    private PeerConnection optimisticUnchokedPeer;

    // Whether we are seeding (affects peer selection algorithm)
    private volatile boolean seeding = false;

    /**
     * Statistics for a connected peer.
     */
    private static class PeerStats {
        final PeerConnection peer;
        long downloadedBytes = 0;
        long uploadedBytes = 0;
        long lastDownloadRate = 0;
        long lastUploadRate = 0;
        long lastMeasureTime = System.currentTimeMillis();
        boolean isInterested = false;
        boolean isChoked = true;
        @SuppressWarnings("unused") // Reserved for future bidirectional state tracking
        boolean amInterested = false;
        @SuppressWarnings("unused") // Reserved for future bidirectional state tracking
        boolean amChoked = true;
        int optimisticUnchokeScore = 0;

        PeerStats(PeerConnection peer) {
            this.peer = peer;
        }

        void updateRates() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastMeasureTime;
            if (elapsed > 0) {
                // Smooth rate calculation
                long newDownRate = (downloadedBytes * 1000) / elapsed;
                long newUpRate = (uploadedBytes * 1000) / elapsed;

                // Exponential moving average
                lastDownloadRate = (lastDownloadRate + newDownRate) / 2;
                lastUploadRate = (lastUploadRate + newUpRate) / 2;

                downloadedBytes = 0;
                uploadedBytes = 0;
                lastMeasureTime = now;
            }
        }
    }

    /**
     * Create a choking manager.
     */
    public ChokingManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChokingManager");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the choking manager.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                    this::rechoke,
                    RECHOKE_INTERVAL_SECONDS,
                    RECHOKE_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            logger.info("Choking manager started (rechoke every %ds)", RECHOKE_INTERVAL_SECONDS);
        }
    }

    /**
     * Stop the choking manager.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info("Choking manager stopped");
        }
    }

    /**
     * Register a peer with the choking manager.
     * 
     * @param peer the peer to register
     */
    public void registerPeer(PeerConnection peer) {
        peerStats.put(peer, new PeerStats(peer));
        logger.debug("Registered peer for choking: %s", peer.getRemoteAddress());
    }

    /**
     * Unregister a peer from the choking manager.
     * 
     * @param peer the peer to unregister
     */
    public void unregisterPeer(PeerConnection peer) {
        peerStats.remove(peer);
        if (optimisticUnchokedPeer == peer) {
            optimisticUnchokedPeer = null;
        }
        logger.debug("Unregistered peer from choking: %s", peer.getRemoteAddress());
    }

    /**
     * Record bytes downloaded from a peer.
     * 
     * @param peer  the peer
     * @param bytes number of bytes
     */
    public void recordDownload(PeerConnection peer, long bytes) {
        PeerStats stats = peerStats.get(peer);
        if (stats != null) {
            stats.downloadedBytes += bytes;
        }
    }

    /**
     * Record bytes uploaded to a peer.
     * 
     * @param peer  the peer
     * @param bytes number of bytes
     */
    public void recordUpload(PeerConnection peer, long bytes) {
        PeerStats stats = peerStats.get(peer);
        if (stats != null) {
            stats.uploadedBytes += bytes;
        }
    }

    /**
     * Update peer interest state.
     * 
     * @param peer         the peer
     * @param isInterested whether the peer is interested in us
     */
    public void setPeerInterested(PeerConnection peer, boolean isInterested) {
        PeerStats stats = peerStats.get(peer);
        if (stats != null) {
            stats.isInterested = isInterested;
        }
    }

    /**
     * Update our interest state for a peer.
     * 
     * @param peer         the peer
     * @param amInterested whether we are interested in the peer
     */
    public void setAmInterested(PeerConnection peer, boolean amInterested) {
        PeerStats stats = peerStats.get(peer);
        if (stats != null) {
            stats.amInterested = amInterested;
        }
    }

    /**
     * Set whether we are seeding (affects choking algorithm).
     * 
     * @param seeding true if seeding
     */
    public void setSeeding(boolean seeding) {
        this.seeding = seeding;
    }

    /**
     * Set maximum number of unchoked peers.
     * 
     * @param max maximum unchoked peers
     */
    public void setMaxUnchokedPeers(int max) {
        this.maxUnchokedPeers = max;
    }

    /**
     * Perform a rechoke operation.
     * Selects which peers to unchoke based on their performance.
     */
    private void rechoke() {
        if (!running.get())
            return;

        try {
            rechokeRound++;

            // Update all peer rates
            for (PeerStats stats : peerStats.values()) {
                stats.updateRates();
            }

            // Get interested peers
            List<PeerStats> interestedPeers = peerStats.values().stream()
                    .filter(s -> s.isInterested && s.peer.isConnected())
                    .collect(Collectors.toList());

            if (interestedPeers.isEmpty()) {
                return;
            }

            // Sort peers by upload rate (when seeding) or download rate (when leeching)
            if (seeding) {
                // When seeding, prefer peers that upload fastest to us (reciprocity)
                // or if we're a pure seed, just rotate
                interestedPeers.sort((a, b) -> Long.compare(b.lastUploadRate, a.lastUploadRate));
            } else {
                // When leeching, prefer peers that download fastest from us (tit-for-tat)
                interestedPeers.sort((a, b) -> Long.compare(b.lastDownloadRate, a.lastDownloadRate));
            }

            // Calculate slots
            int regularSlots = maxUnchokedPeers - OPTIMISTIC_UNCHOKE_SLOTS;

            // Determine optimistic unchoke
            boolean doOptimistic = (rechokeRound % OPTIMISTIC_UNCHOKE_ROUNDS == 0);

            Set<PeerConnection> toUnchoke = new HashSet<>();

            // Regular unchoke: top performers
            for (int i = 0; i < Math.min(regularSlots, interestedPeers.size()); i++) {
                toUnchoke.add(interestedPeers.get(i).peer);
            }

            // Optimistic unchoke
            if (doOptimistic || optimisticUnchokedPeer == null) {
                selectOptimisticPeer(interestedPeers, toUnchoke);
            }

            if (optimisticUnchokedPeer != null) {
                toUnchoke.add(optimisticUnchokedPeer);
            }

            // Apply choking decisions
            for (PeerStats stats : peerStats.values()) {
                boolean shouldUnchoke = toUnchoke.contains(stats.peer);

                if (shouldUnchoke && stats.isChoked) {
                    // Unchoke this peer
                    stats.isChoked = false;
                    sendUnchoke(stats.peer);
                } else if (!shouldUnchoke && !stats.isChoked) {
                    // Choke this peer
                    stats.isChoked = true;
                    sendChoke(stats.peer);
                }
            }

            logger.debug("Rechoke completed: %d peers unchoked", toUnchoke.size());

        } catch (Exception e) {
            logger.error("Error during rechoke: %s", e.getMessage());
        }
    }

    /**
     * Select a peer for optimistic unchoke.
     */
    private void selectOptimisticPeer(List<PeerStats> interestedPeers, Set<PeerConnection> alreadyUnchoked) {
        // Get candidates (interested, choked, not already selected)
        List<PeerStats> candidates = interestedPeers.stream()
                .filter(s -> s.isChoked && !alreadyUnchoked.contains(s.peer))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            optimisticUnchokedPeer = null;
            return;
        }

        // Prefer newer peers (higher chance of being optimistically unchoked)
        // Increment score for peers that haven't been chosen recently
        for (PeerStats stats : candidates) {
            stats.optimisticUnchokeScore++;
        }

        // Sort by score (higher = more likely to be chosen)
        candidates.sort((a, b) -> Integer.compare(b.optimisticUnchokeScore, a.optimisticUnchokeScore));

        // Select with some randomness from top candidates
        int selectFrom = Math.min(3, candidates.size());
        int idx = ThreadLocalRandom.current().nextInt(selectFrom);

        PeerStats selected = candidates.get(idx);
        selected.optimisticUnchokeScore = 0;
        optimisticUnchokedPeer = selected.peer;

        logger.debug("Optimistic unchoke selected: %s", optimisticUnchokedPeer.getRemoteAddress());
    }

    /**
     * Send unchoke message to peer.
     */
    private void sendUnchoke(PeerConnection peer) {
        try {
            peer.sendUnchoke();
            logger.debug("Sent UNCHOKE to %s", peer.getRemoteAddress());
        } catch (Exception e) {
            logger.warn("Failed to send unchoke: %s", e.getMessage());
        }
    }

    /**
     * Send choke message to peer.
     */
    private void sendChoke(PeerConnection peer) {
        try {
            peer.sendChoke();
            logger.debug("Sent CHOKE to %s", peer.getRemoteAddress());
        } catch (Exception e) {
            logger.warn("Failed to send choke: %s", e.getMessage());
        }
    }

    /**
     * Get the number of currently unchoked peers.
     * 
     * @return number of unchoked peers
     */
    public int getUnchokedCount() {
        return (int) peerStats.values().stream()
                .filter(s -> !s.isChoked)
                .count();
    }

    /**
     * Get the current optimistic unchoke peer.
     * 
     * @return the optimistic unchoke peer, or null
     */
    public PeerConnection getOptimisticPeer() {
        return optimisticUnchokedPeer;
    }

    /**
     * Check if the manager is running.
     * 
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }
}
