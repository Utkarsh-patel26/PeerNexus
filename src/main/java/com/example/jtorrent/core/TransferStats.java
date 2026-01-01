package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates transfer statistics and provides periodic summary logging
 * to reduce log spam from individual block transfers.
 */
public class TransferStats {

    private final Logger logger;
    private final String context;

    // Download stats
    private final AtomicLong downloadedBlocks = new AtomicLong(0);
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private final AtomicLong downloadRequests = new AtomicLong(0);

    // Upload stats
    private final AtomicLong uploadedBlocks = new AtomicLong(0);
    private final AtomicLong uploadedBytes = new AtomicLong(0);
    private final AtomicLong uploadRequests = new AtomicLong(0);

    // Peer stats
    private final AtomicLong peersConnected = new AtomicLong(0);
    private final AtomicLong peersUnchoked = new AtomicLong(0);
    private final AtomicLong peersFailed = new AtomicLong(0);

    // Piece stats
    private final AtomicLong piecesCompleted = new AtomicLong(0);
    private final AtomicLong piecesInProgress = new AtomicLong(0);

    private long lastLogTime = System.currentTimeMillis();
    private long lastDownloadBytes = 0;
    private long lastUploadBytes = 0;

    public TransferStats(Logger logger, String context) {
        this.logger = logger;
        this.context = context;
    }

    /**
     * Record a downloaded block.
     *
     * @param bytes bytes downloaded
     */
    public void recordDownload(int bytes) {
        downloadedBlocks.incrementAndGet();
        downloadedBytes.addAndGet(bytes);
    }

    /**
     * Record a download request sent.
     */
    public void recordDownloadRequest() {
        downloadRequests.incrementAndGet();
    }

    /**
     * Record an uploaded block.
     *
     * @param bytes bytes uploaded
     */
    public void recordUpload(int bytes) {
        uploadedBlocks.incrementAndGet();
        uploadedBytes.addAndGet(bytes);
    }

    /**
     * Record an upload request received.
     */
    public void recordUploadRequest() {
        uploadRequests.incrementAndGet();
    }

    /**
     * Record peer connected.
     */
    public void recordPeerConnected() {
        peersConnected.incrementAndGet();
    }

    /**
     * Record peer unchoked.
     */
    public void recordPeerUnchoked() {
        peersUnchoked.incrementAndGet();
    }

    /**
     * Record peer unchoke removed.
     */
    public void recordPeerChoked() {
        long current = peersUnchoked.get();
        if (current > 0) {
            peersUnchoked.decrementAndGet();
        }
    }

    /**
     * Record peer failure.
     */
    public void recordPeerFailed() {
        peersFailed.incrementAndGet();
    }

    /**
     * Record piece completed.
     */
    public void recordPieceCompleted() {
        piecesCompleted.incrementAndGet();
    }

    /**
     * Set pieces in progress count.
     *
     * @param count number of pieces being downloaded
     */
    public void setPiecesInProgress(int count) {
        piecesInProgress.set(count);
    }

    /**
     * Log aggregated stats if enough time has passed.
     *
     * @param intervalMs          minimum interval between logs
     * @param outstandingRequests number of outstanding block requests
     */
    public void logIfDue(long intervalMs, int outstandingRequests) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= intervalMs) {
            logSummary(outstandingRequests);
            lastLogTime = now;
        }
    }

    /**
     * Log summary statistics.
     *
     * @param outstandingRequests number of outstanding block requests
     */
    public void logSummary(int outstandingRequests) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastLogTime;

        if (elapsed > 0) {
            // Calculate rates
            long currentDownload = downloadedBytes.get();
            long currentUpload = uploadedBytes.get();
            double downloadRate = (currentDownload - lastDownloadBytes) * 1000.0 / elapsed / 1024.0;
            double uploadRate = (currentUpload - lastUploadBytes) * 1000.0 / elapsed / 1024.0;

            lastDownloadBytes = currentDownload;
            lastUploadBytes = currentUpload;

            logger.info(
                    "[%s] STATS: DL=%d blocks (%.1f KB/s) | UL=%d blocks (%.1f KB/s) | "
                            + "Peers=%d (unchoked=%d, failed=%d) | Pieces: complete=%d, active=%d | "
                            + "Pending requests=%d",
                    context,
                    downloadedBlocks.get(), downloadRate,
                    uploadedBlocks.get(), uploadRate,
                    peersConnected.get(), peersUnchoked.get(), peersFailed.get(),
                    piecesCompleted.get(), piecesInProgress.get(),
                    outstandingRequests);
        }
    }

    /**
     * Force log summary immediately.
     *
     * @param outstandingRequests number of outstanding block requests
     */
    public void forceLog(int outstandingRequests) {
        logSummary(outstandingRequests);
        lastLogTime = System.currentTimeMillis();
    }

    // Getters
    public long getDownloadedBlocks() {
        return downloadedBlocks.get();
    }

    public long getDownloadedBytes() {
        return downloadedBytes.get();
    }

    public long getUploadedBlocks() {
        return uploadedBlocks.get();
    }

    public long getUploadedBytes() {
        return uploadedBytes.get();
    }

    public long getPeersConnected() {
        return peersConnected.get();
    }

    public long getPeersUnchoked() {
        return peersUnchoked.get();
    }

    public long getPeersFailed() {
        return peersFailed.get();
    }

    public long getPiecesCompleted() {
        return piecesCompleted.get();
    }
}
