package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.PeerConnection;
import com.example.jtorrent.storage.BlockInfo;
import com.example.jtorrent.storage.BlockRequest;
import com.example.jtorrent.storage.BlockRequestTracker;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceSelectionStrategy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Request scheduling and pipeline management for block requests.
 */
public class RequestScheduler {
    private static final int PIPELINE_GOAL = 200; // Increased from 50 for better throughput
    private static final int MAX_REQUESTS_PER_PEER = 15; // Increased from 5 for better pipelining

    private final BlockRequestTracker requestTracker;
    private final PieceManager pieceManager;
    private final TransferStats transferStats;
    private final Logger logger;

    public RequestScheduler(BlockRequestTracker requestTracker, PieceManager pieceManager,
            TransferStats transferStats, Logger logger) {
        this.requestTracker = requestTracker;
        this.pieceManager = pieceManager;
        this.transferStats = transferStats;
        this.logger = logger;
    }

    /**
     * Tick the scheduler to fill the request pipeline.
     * 
     * @param activePeers List of active peers eligible for requests
     */
    public void tick(List<ActivePeer> activePeers) {
        if (activePeers == null) {
            logger.debug("[SCHEDULER] tick called with null activePeers list");
            return;
        }

        if (activePeers.isEmpty()) {
            logger.debug("[SCHEDULER] No active peers available for requests");
            return;
        }

        // Clean up stale requests - remove INDIVIDUAL stale requests, not all from peer
        // This is more efficient and doesn't punish peers for single slow responses
        List<BlockRequest> staleRequests = requestTracker.getStaleRequests();
        int staleCount = 0;
        for (BlockRequest stale : staleRequests) {
            if (stale == null) {
                continue;
            }
            // Remove just this one stale request, not all from the peer
            boolean removed = requestTracker.fulfillRequest(stale.getPieceIndex(), stale.getOffset());
            if (removed) {
                staleCount++;
                logger.debug("Stale request removed: piece=%d offset=%d peer=%s (timeout after 15s)",
                        stale.getPieceIndex(), stale.getOffset(), stale.getPeer());
            }
        }
        if (staleCount > 0) {
            logger.info("[SCHEDULER] Cleaned up %d stale requests", staleCount);
        }

        // Fill pipeline to goal
        int currentRequests = requestTracker.getTotalRequestCount();
        if (currentRequests >= PIPELINE_GOAL) {
            return; // Pipeline is full
        }

        logger.debug("[SCHEDULER] Processing %d active peers, current pipeline: %d/%d",
                activePeers.size(), currentRequests, PIPELINE_GOAL);

        // Issue requests to active peers
        int requestsIssued = 0;
        for (ActivePeer activePeer : activePeers) {
            // CRITICAL: Skip disconnected peers first to avoid "Not connected" errors
            if (!activePeer.isConnected()) {
                logger.debug("[SCHEDULER] Skipping disconnected peer %s", activePeer.getAddress());
                continue;
            }

            if (activePeer.isDeprioritized()) {
                logger.debug("[SCHEDULER] Skipping deprioritized peer %s", activePeer.getAddress());
                continue; // Skip deprioritized peers
            }

            if (activePeer.isPeerChoking()) {
                logger.debug("[SCHEDULER] Skipping choking peer %s", activePeer.getAddress());
                continue; // Peer is choking us
            }

            int peerRequestCount = requestTracker.getRequestCount(activePeer.getAddress());
            if (peerRequestCount >= MAX_REQUESTS_PER_PEER) {
                logger.debug("[SCHEDULER] Peer %s has max requests (%d)",
                        activePeer.getAddress(), peerRequestCount);
                continue; // Peer has enough pending requests
            }

            // Issue up to MAX_REQUESTS_PER_PEER per peer
            int requestsToIssue = MAX_REQUESTS_PER_PEER - peerRequestCount;
            logger.debug("[SCHEDULER] Issuing up to %d requests to peer %s (current: %d)",
                    requestsToIssue, activePeer.getAddress(), peerRequestCount);

            for (int i = 0; i < requestsToIssue; i++) {
                if (requestTracker.getTotalRequestCount() >= PIPELINE_GOAL) {
                    break; // Pipeline full
                }

                boolean success = issueRequestToPeer(activePeer);
                if (success) {
                    requestsIssued++;
                } else {
                    logger.debug("[SCHEDULER] Failed to issue request to peer %s", activePeer.getAddress());
                    break; // No more blocks available for this peer
                }
            }
        }

        if (requestsIssued > 0) {
            logger.info("[SCHEDULER] Issued %d new requests, pipeline now: %d/%d",
                    requestsIssued, requestTracker.getTotalRequestCount(), PIPELINE_GOAL);
        }
    }

    /**
     * Issue a single request to the specified peer.
     * 
     * @param activePeer The peer to send request to
     * @return true if request was issued, false otherwise
     */
    private boolean issueRequestToPeer(ActivePeer activePeer) {
        if (activePeer == null) {
            logger.debug("[ISSUE_REQUEST] activePeer is null");
            return false;
        }

        // CRITICAL: Check connection health before attempting to send
        if (!activePeer.isConnected()) {
            logger.debug("[ISSUE_REQUEST] Peer %s is not connected", activePeer.getAddress());
            return false;
        }

        PeerConnection peer = activePeer.getConnection();
        InetSocketAddress peerAddress = activePeer.getAddress();

        if (peer == null || peerAddress == null) {
            logger.debug("[ISSUE_REQUEST] peer or peerAddress is null");
            return false;
        }

        // Get peer's bitfield
        java.util.BitSet peerBitfield = activePeer.getBitfield();
        if (peerBitfield == null) {
            logger.debug("[ISSUE_REQUEST] Peer %s has no bitfield", peerAddress);
            return false; // No bitfield yet
        }

        // Select piece using smart strategy
        PieceSelectionStrategy strategy = pieceManager.getRecommendedStrategy();
        int pieceIndex = pieceManager.selectPiece(peerBitfield, strategy);

        if (pieceIndex < 0) {
            logger.debug("[ISSUE_REQUEST] No suitable piece for peer %s (strategy: %s)",
                    peerAddress, strategy);
            return false; // No suitable piece
        }

        // Get next missing block from piece
        List<BlockInfo> missingBlocks = pieceManager.getMissingBlocks(pieceIndex);
        if (missingBlocks.isEmpty()) {
            logger.debug("[ISSUE_REQUEST] Piece %d has no missing blocks", pieceIndex);
            return false;
        }

        BlockInfo block = null;

        // Find a block not already requested
        for (BlockInfo candidate : missingBlocks) {
            if (!requestTracker.isRequested(candidate.pieceIndex(), candidate.offset())) {
                block = candidate;
                break;
            }
        }

        if (block == null) {
            logger.debug("[ISSUE_REQUEST] All blocks in piece %d already requested", pieceIndex);
            return false; // All blocks already requested
        }

        // Create and send REQUEST message
        byte[] payload = new byte[12];
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(payload);
        buf.putInt(block.pieceIndex());
        buf.putInt(block.offset());
        buf.putInt(block.length());

        try {
            peer.send(new Message(Message.REQUEST, payload));

            // Track request
            BlockRequest request = new BlockRequest(
                    block.pieceIndex(), block.offset(), block.length(), peerAddress);
            requestTracker.addRequest(request);
            transferStats.recordDownloadRequest();

            activePeer.setLastRequestTime(System.currentTimeMillis());

            logger.info("[ISSUE_REQUEST] SUCCESS - Sent REQUEST to %s for piece %d offset %d length %d",
                    peerAddress, block.pieceIndex(), block.offset(), block.length());
            return true;

        } catch (IOException e) {
            logger.warn("[ISSUE_REQUEST] FAILED to send REQUEST to peer %s: %s", peerAddress, e.getMessage());
            activePeer.recordFailure();
            return false;
        }
    }

    /**
     * Called when a PIECE is received to issue more requests immediately.
     * 
     * @param activePeers List of active peers
     */
    public void issueMoreRequests(List<ActivePeer> activePeers) {
        if (activePeers == null) {
            return;
        }

        // Same logic as tick, but only called when pipeline has capacity
        int currentRequests = requestTracker.getTotalRequestCount();
        if (currentRequests >= PIPELINE_GOAL) {
            return;
        }

        // Quick refill for responsive downloading
        for (ActivePeer activePeer : activePeers) {
            if (activePeer == null) {
                continue;
            }
            // CRITICAL: Skip disconnected peers
            if (!activePeer.isConnected()) {
                continue;
            }
            if (activePeer.isDeprioritized() || activePeer.isPeerChoking()) {
                continue;
            }

            int peerRequestCount = requestTracker.getRequestCount(activePeer.getAddress());
            if (peerRequestCount < MAX_REQUESTS_PER_PEER) {
                issueRequestToPeer(activePeer);
            }
        }
    }
}
