package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.storage.BlockInfo;
import com.example.jtorrent.storage.PieceManager;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Endgame mode manager for BitTorrent downloads.
 * 
 * When download is nearly complete (last few pieces), endgame mode requests the
 * same blocks from multiple peers simultaneously to speed up completion.
 * When a block is received, CANCEL messages are sent to other peers.
 */
public class EndgameManager {

    private static final Logger logger = Logger.getLogger(EndgameManager.class);

    /** Threshold: enter endgame when this many pieces remain. */
    private static final int ENDGAME_THRESHOLD = 10;

    /** Maximum peers to request same block from. */
    private static final int MAX_DUPLICATE_REQUESTS = 3;

    private final PieceManager pieceManager;
    private final AtomicBoolean endgameActive = new AtomicBoolean(false);

    /** Track which peers have been sent requests for each block. */
    private final Map<BlockKey, Set<InetSocketAddress>> blockRequestPeers = new ConcurrentHashMap<>();

    /** Track pending blocks in endgame mode. */
    private final Set<BlockKey> pendingBlocks = ConcurrentHashMap.newKeySet();

    /**
     * Create an endgame manager.
     *
     * @param pieceManager the piece manager
     */
    public EndgameManager(PieceManager pieceManager) {
        this.pieceManager = pieceManager;
    }

    /**
     * Check if endgame mode should be activated based on progress.
     *
     * @return true if endgame mode should be active
     */
    public boolean shouldActivateEndgame() {
        int completed = pieceManager.getCompletedCount();
        int total = pieceManager.getPieceCount();
        int remaining = total - completed;

        boolean shouldActivate = remaining <= ENDGAME_THRESHOLD && remaining > 0;

        if (shouldActivate && !endgameActive.get()) {
            logger.info("Activating endgame mode: %d pieces remaining", remaining);
            endgameActive.set(true);
            initializeEndgamePendingBlocks();
        }

        return endgameActive.get();
    }

    /**
     * Check if endgame mode is currently active.
     *
     * @return true if active
     */
    public boolean isEndgameActive() {
        return endgameActive.get();
    }

    /**
     * Initialize the set of pending blocks when entering endgame.
     */
    private void initializeEndgamePendingBlocks() {
        pendingBlocks.clear();
        blockRequestPeers.clear();

        int pieceCount = pieceManager.getPieceCount();
        for (int i = 0; i < pieceCount; i++) {
            if (pieceManager.getPieceState(i) != com.example.jtorrent.storage.PieceState.COMPLETE) {
                List<BlockInfo> missing = pieceManager.getMissingBlocks(i);
                for (BlockInfo block : missing) {
                    pendingBlocks.add(new BlockKey(block.pieceIndex(), block.offset()));
                }
            }
        }

        logger.info("Endgame initialized with %d pending blocks", pendingBlocks.size());
    }

    /**
     * Get blocks to request from a peer in endgame mode.
     * Returns blocks that this peer hasn't been asked for yet, or blocks with
     * fewer than MAX_DUPLICATE_REQUESTS outstanding.
     *
     * @param peerAddress  the peer's address
     * @param peerBitfield the peer's available pieces
     * @param maxBlocks    maximum blocks to return
     * @return list of blocks to request
     */
    public List<BlockInfo> getEndgameBlocks(InetSocketAddress peerAddress, BitSet peerBitfield, int maxBlocks) {
        List<BlockInfo> result = new ArrayList<>();

        if (!endgameActive.get() || peerBitfield == null) {
            return result;
        }

        for (BlockKey key : pendingBlocks) {
            if (result.size() >= maxBlocks) {
                break;
            }

            // Check if peer has this piece
            if (!peerBitfield.get(key.pieceIndex)) {
                continue;
            }

            // Check if we can request from this peer
            Set<InetSocketAddress> requestedPeers = blockRequestPeers.computeIfAbsent(key,
                    k -> ConcurrentHashMap.newKeySet());

            if (requestedPeers.contains(peerAddress)) {
                continue; // Already requested from this peer
            }

            if (requestedPeers.size() >= MAX_DUPLICATE_REQUESTS) {
                continue; // Already requested from too many peers
            }

            // Calculate block length
            int blockCount = pieceManager.getBlockCount(key.pieceIndex);
            int blockIndex = key.offset / PieceManager.BLOCK_SIZE;
            int length = PieceManager.BLOCK_SIZE;

            if (blockIndex == blockCount - 1) {
                int pieceLen = pieceManager.getPieceLength(key.pieceIndex);
                int remaining = pieceLen - key.offset;
                if (remaining < PieceManager.BLOCK_SIZE) {
                    length = remaining;
                }
            }

            result.add(new BlockInfo(key.pieceIndex, key.offset, length));
            requestedPeers.add(peerAddress);
        }

        return result;
    }

    /**
     * Mark a block as received. Returns peers that should receive CANCEL messages.
     *
     * @param pieceIndex   the piece index
     * @param offset       the block offset
     * @param receivedFrom the peer that sent the block
     * @return set of peers to send CANCEL to (excluding receivedFrom)
     */
    public Set<InetSocketAddress> blockReceived(int pieceIndex, int offset, InetSocketAddress receivedFrom) {
        BlockKey key = new BlockKey(pieceIndex, offset);
        pendingBlocks.remove(key);

        Set<InetSocketAddress> peers = blockRequestPeers.remove(key);
        if (peers == null) {
            return Collections.emptySet();
        }

        // Remove the peer that sent us the block
        peers.remove(receivedFrom);

        if (!peers.isEmpty()) {
            logger.debug("Endgame: block %d:%d received, canceling from %d peers",
                    pieceIndex, offset, peers.size());
        }

        return peers;
    }

    /**
     * Handle peer disconnection - remove from tracking.
     *
     * @param peerAddress the disconnected peer
     */
    public void peerDisconnected(InetSocketAddress peerAddress) {
        for (Set<InetSocketAddress> peers : blockRequestPeers.values()) {
            peers.remove(peerAddress);
        }
    }

    /**
     * Reset endgame state (e.g., after piece verification failure).
     */
    public void reset() {
        endgameActive.set(false);
        pendingBlocks.clear();
        blockRequestPeers.clear();
    }

    /**
     * Get statistics about endgame progress.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active", endgameActive.get());
        stats.put("pendingBlocks", pendingBlocks.size());
        stats.put("trackedRequests", blockRequestPeers.size());
        return stats;
    }

    /**
     * Key for tracking blocks.
     */
    private static class BlockKey {
        final int pieceIndex;
        final int offset;

        BlockKey(int pieceIndex, int offset) {
            this.pieceIndex = pieceIndex;
            this.offset = offset;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof BlockKey))
                return false;
            BlockKey blockKey = (BlockKey) o;
            return pieceIndex == blockKey.pieceIndex && offset == blockKey.offset;
        }

        @Override
        public int hashCode() {
            return Objects.hash(pieceIndex, offset);
        }
    }
}
