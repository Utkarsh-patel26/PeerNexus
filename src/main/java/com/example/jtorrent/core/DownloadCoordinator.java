package com.example.jtorrent.core;

import com.example.jtorrent.events.*;
import com.example.jtorrent.scheduler.BandwidthLimiter;
import com.example.jtorrent.storage.DiskManager;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceVerifier;
import com.example.jtorrent.storage.PieceState;
import com.example.jtorrent.logging.Logger;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced download coordinator with bandwidth limiting, piece verification,
 * and event publishing capabilities.
 */
public class DownloadCoordinator {

    private static final String DEFAULT_SOCKET_ID = "global";

    private final PieceManager pieceManager;
    private final DiskManager diskManager;
    private final PieceVerifier pieceVerifier;
    private final BandwidthLimiter bandwidthLimiter;
    private final TransferStats transferStats;
    private final Logger logger;
    private final byte[] infoHash;
    private final EventBus eventBus;

    // Upload tracking
    private final AtomicLong uploadedBytes = new AtomicLong(0);
    private final AtomicLong lastUploadTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastUploadBytes = new AtomicLong(0);
    private volatile long lastCalculatedUploadRate = 0;

    // Statistics
    private final AtomicInteger pieceVerificationFailures = new AtomicInteger(0);
    private final AtomicInteger completedPieces = new AtomicInteger(0);

    public DownloadCoordinator(
            PieceManager pieceManager,
            DiskManager diskManager,
            PieceVerifier pieceVerifier,
            BandwidthLimiter bandwidthLimiter,
            TransferStats transferStats,
            byte[] infoHash,
            Logger logger) {
        this.pieceManager = pieceManager;
        this.diskManager = diskManager;
        this.pieceVerifier = pieceVerifier;
        this.bandwidthLimiter = bandwidthLimiter;
        this.transferStats = transferStats;
        this.infoHash = infoHash;
        this.logger = logger;
        this.eventBus = EventBus.getInstance();
    }

    /**
     * Write a piece block to disk with bandwidth limiting.
     *
     * @param pieceIndex piece index
     * @param offset     offset within piece
     * @param data       block data
     * @return true if write successful
     */
    public boolean writePieceBlock(int pieceIndex, int offset, byte[] data) throws IOException {
        // Apply bandwidth limiting for download using requestDownload API
        if (bandwidthLimiter != null) {
            long bytesToConsume = data.length;
            long allowed = bandwidthLimiter.requestDownload(DEFAULT_SOCKET_ID, bytesToConsume);

            // If not all bytes allowed, wait and retry
            while (allowed < bytesToConsume) {
                try {
                    Thread.sleep(10); // Wait 10ms and retry
                    allowed += bandwidthLimiter.requestDownload(DEFAULT_SOCKET_ID,
                            bytesToConsume - allowed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        // Write to disk using writeBlock API
        diskManager.writeBlock(pieceIndex, offset, data);

        // Mark block as received in piece manager (pass the offset, not block index)
        boolean pieceComplete = pieceManager.markBlockReceived(pieceIndex, offset);

        // Check if piece is complete
        if (pieceComplete) {
            return completePiece(pieceIndex);
        }

        return true;
    }

    /**
     * Complete a piece with verification.
     *
     * @param pieceIndex piece index
     * @return true if piece verified successfully
     */
    private boolean completePiece(int pieceIndex) throws IOException {
        logger.debug("Piece %d downloaded, verifying...", pieceIndex);

        // Verify piece hash using verifyPiece API
        boolean hashValid = pieceVerifier.verifyPiece(pieceIndex);

        if (hashValid) {
            // Mark as complete
            pieceManager.setPieceState(pieceIndex, PieceState.COMPLETE);
            completedPieces.incrementAndGet();
            logger.info("Piece %d verified successfully (%d total)",
                    pieceIndex, completedPieces.get());

            // Publish event
            int pieceLength = pieceManager.getPieceLength(pieceIndex);
            eventBus.publishAsync(new PieceCompletedEvent(
                    infoHash, pieceIndex, pieceLength, true));

            return true;
        } else {
            // Hash verification failed
            int failures = pieceVerificationFailures.incrementAndGet();
            logger.warn("Piece %d hash verification FAILED (attempt #%d)", pieceIndex, failures);

            // Reset piece for re-download
            pieceManager.setPieceState(pieceIndex, PieceState.MISSING);

            // Publish event with hash failure
            int pieceLength = pieceManager.getPieceLength(pieceIndex);
            eventBus.publishAsync(new PieceCompletedEvent(
                    infoHash, pieceIndex, pieceLength, false));

            return false;
        }
    }

    /**
     * Read and send a piece block with bandwidth limiting and upload tracking.
     *
     * @param pieceIndex piece index
     * @param offset     offset within piece
     * @param length     block length
     * @return block data, or null if unavailable
     */
    public byte[] readPieceBlockForUpload(int pieceIndex, int offset, int length) throws IOException {
        // Check if piece is available for uploading
        if (pieceManager.getPieceState(pieceIndex) != PieceState.COMPLETE) {
            return null;
        }

        // Read from disk using readBlock API
        byte[] blockData = diskManager.readBlock(pieceIndex, offset, length);
        if (blockData == null) {
            return null;
        }

        // Apply bandwidth limiting for upload using requestUpload API
        if (bandwidthLimiter != null) {
            long allowed = bandwidthLimiter.requestUpload(DEFAULT_SOCKET_ID, blockData.length);

            // If not all bytes allowed, wait and retry
            while (allowed < blockData.length) {
                try {
                    Thread.sleep(10);
                    allowed += bandwidthLimiter.requestUpload(DEFAULT_SOCKET_ID,
                            blockData.length - (int) allowed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        // Track upload
        recordUpload(blockData.length);

        return blockData;
    }

    /**
     * Record uploaded bytes and update statistics.
     *
     * @param bytes bytes uploaded
     */
    public void recordUpload(long bytes) {
        uploadedBytes.addAndGet(bytes);
        transferStats.recordUpload((int) bytes);
    }

    /**
     * Get current upload rate in bytes/second.
     *
     * @return upload rate
     */
    public long getUploadRate() {
        long currentTime = System.currentTimeMillis();
        long currentBytes = uploadedBytes.get();

        long timeDelta = currentTime - lastUploadTime.get();
        if (timeDelta >= 1000) { // Update every second
            long bytesDelta = currentBytes - lastUploadBytes.get();
            long rate = (bytesDelta * 1000) / timeDelta;

            lastUploadTime.set(currentTime);
            lastUploadBytes.set(currentBytes);
            lastCalculatedUploadRate = rate;

            return rate;
        }

        // Return last calculated rate
        return lastCalculatedUploadRate;
    }

    /**
     * Get total uploaded bytes.
     */
    public long getTotalUploaded() {
        return uploadedBytes.get();
    }

    /**
     * Get piece verification failure count.
     */
    public int getVerificationFailures() {
        return pieceVerificationFailures.get();
    }

    /**
     * Force recheck of all pieces.
     *
     * @return number of valid pieces found
     */
    public int recheckAllPieces() throws IOException {
        logger.info("Starting full piece recheck...");
        int validCount = pieceVerifier.recheckAllPieces();
        logger.info("Recheck complete: %d/%d pieces valid",
                validCount, pieceManager.getPieceCount());
        return validCount;
    }
}
