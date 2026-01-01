package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.PeerConnection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages keep-alive messages for peer connections.
 * 
 * BitTorrent protocol requires peers to send keep-alive messages every 2
 * minutes
 * to prevent connection timeouts. This manager:
 * - Sends keep-alive messages to all active peers periodically
 * - Tracks last activity time for each peer
 * - Closes connections that haven't responded in timeout period
 */
public class KeepAliveManager {

    private static final Logger logger = Logger.getLogger(KeepAliveManager.class);

    // Keep-alive interval (BEP-3 recommends every 2 minutes)
    private static final long KEEP_ALIVE_INTERVAL_MS = 90_000; // 90 seconds

    // Timeout for closing inactive connections
    private static final long INACTIVITY_TIMEOUT_MS = 180_000; // 3 minutes

    private final Map<InetSocketAddress, PeerActivityInfo> peerActivity;
    private final Map<InetSocketAddress, PeerConnection> connections;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    /**
     * Tracks activity for a single peer.
     */
    private static class PeerActivityInfo {
        volatile long lastSentTime;
        volatile long lastReceivedTime;
        volatile int missedKeepAlives;

        PeerActivityInfo() {
            long now = System.currentTimeMillis();
            this.lastSentTime = now;
            this.lastReceivedTime = now;
            this.missedKeepAlives = 0;
        }

        void markSent() {
            lastSentTime = System.currentTimeMillis();
        }

        void markReceived() {
            lastReceivedTime = System.currentTimeMillis();
            missedKeepAlives = 0;
        }

        void incrementMissed() {
            missedKeepAlives++;
        }

        boolean isTimedOut(long timeoutMs) {
            return System.currentTimeMillis() - lastReceivedTime > timeoutMs;
        }
    }

    /**
     * Create a keep-alive manager.
     */
    public KeepAliveManager() {
        this.peerActivity = new ConcurrentHashMap<>();
        this.connections = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "KeepAlive-Manager");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the keep-alive manager.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // Schedule keep-alive task
            scheduler.scheduleAtFixedRate(
                    this::sendKeepAlives,
                    KEEP_ALIVE_INTERVAL_MS / 2,
                    KEEP_ALIVE_INTERVAL_MS / 2,
                    TimeUnit.MILLISECONDS);

            // Schedule timeout check task
            scheduler.scheduleAtFixedRate(
                    this::checkTimeouts,
                    INACTIVITY_TIMEOUT_MS / 2,
                    30_000, // Check every 30 seconds
                    TimeUnit.MILLISECONDS);

            logger.info("Keep-alive manager started (interval=%dms, timeout=%dms)",
                    KEEP_ALIVE_INTERVAL_MS, INACTIVITY_TIMEOUT_MS);
        }
    }

    /**
     * Stop the keep-alive manager.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            peerActivity.clear();
            connections.clear();
            logger.info("Keep-alive manager stopped");
        }
    }

    /**
     * Register a peer connection for keep-alive management.
     */
    public void registerPeer(InetSocketAddress address, PeerConnection connection) {
        peerActivity.put(address, new PeerActivityInfo());
        connections.put(address, connection);
        logger.debug("Registered peer %s for keep-alive", address);
    }

    /**
     * Unregister a peer connection.
     */
    public void unregisterPeer(InetSocketAddress address) {
        peerActivity.remove(address);
        connections.remove(address);
        logger.debug("Unregistered peer %s from keep-alive", address);
    }

    /**
     * Mark that we received activity from a peer.
     * Should be called whenever any message is received from the peer.
     */
    public void markActivity(InetSocketAddress address) {
        PeerActivityInfo info = peerActivity.get(address);
        if (info != null) {
            info.markReceived();
        }
    }

    /**
     * Send keep-alive messages to all peers that need them.
     */
    private void sendKeepAlives() {
        if (!running.get())
            return;

        long now = System.currentTimeMillis();
        int sent = 0;

        for (Map.Entry<InetSocketAddress, PeerActivityInfo> entry : peerActivity.entrySet()) {
            InetSocketAddress address = entry.getKey();
            PeerActivityInfo info = entry.getValue();

            // Only send if we haven't sent anything recently
            if (now - info.lastSentTime > KEEP_ALIVE_INTERVAL_MS) {
                PeerConnection conn = connections.get(address);
                if (conn != null && conn.isConnected()) {
                    try {
                        conn.send(new Message(Message.KEEP_ALIVE));
                        info.markSent();
                        sent++;
                    } catch (IOException e) {
                        logger.debug("Failed to send keep-alive to %s: %s", address, e.getMessage());
                        info.incrementMissed();
                    }
                }
            }
        }

        if (sent > 0) {
            logger.debug("Sent keep-alive to %d peer(s)", sent);
        }
    }

    /**
     * Check for timed-out connections and close them.
     */
    private void checkTimeouts() {
        if (!running.get())
            return;

        int closed = 0;

        for (Map.Entry<InetSocketAddress, PeerActivityInfo> entry : peerActivity.entrySet()) {
            InetSocketAddress address = entry.getKey();
            PeerActivityInfo info = entry.getValue();

            if (info.isTimedOut(INACTIVITY_TIMEOUT_MS) || info.missedKeepAlives >= 3) {
                PeerConnection conn = connections.get(address);
                if (conn != null) {
                    logger.info("Closing timed-out connection to %s (last activity: %dms ago, missed: %d)",
                            address,
                            System.currentTimeMillis() - info.lastReceivedTime,
                            info.missedKeepAlives);
                    try {
                        conn.close();
                    } catch (Exception e) {
                        // Ignore close errors
                    }
                    closed++;
                }
                unregisterPeer(address);
            }
        }

        if (closed > 0) {
            logger.info("Closed %d timed-out connection(s)", closed);
        }
    }

    /**
     * Get the number of managed peers.
     */
    public int getPeerCount() {
        return peerActivity.size();
    }

    /**
     * Check if a peer is registered.
     */
    public boolean isPeerRegistered(InetSocketAddress address) {
        return peerActivity.containsKey(address);
    }
}
