package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.PeerConnection;
import com.example.jtorrent.scheduler.PeerStats;
import com.example.jtorrent.storage.DiskManager;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceState;
import java.net.InetSocketAddress;

/**
 * Upload logic for sending PIECE messages to peers.
 */
public class UploadHandler {
    private final DiskManager diskManager;
    private final PieceManager pieceManager;
    private final TransferStats transferStats;
    private final Logger logger;

    public UploadHandler(DiskManager diskManager, PieceManager pieceManager,
            TransferStats transferStats, Logger logger) {
        this.diskManager = diskManager;
        this.pieceManager = pieceManager;
        this.transferStats = transferStats;
        this.logger = logger;
    }

    /**
     * Send a PIECE message to the specified peer.
     * 
     * @param peer        The peer connection
     * @param peerAddress The peer address
     * @param pieceIndex  The piece index
     * @param offset      The offset within the piece
     * @param length      The length of the block
     * @param stats       The peer stats (can be null)
     * @param chokerStats The choker stats (can be null)
     * @return true if piece was sent successfully
     */
    public boolean sendPiece(PeerConnection peer, InetSocketAddress peerAddress,
            int pieceIndex, int offset, int length, PeerStats stats, PeerStats chokerStats) {

        // Validate piece and offset
        if (pieceIndex < 0 || pieceIndex >= pieceManager.getPieceCount()) {
            logger.debug("Peer %s requested invalid piece %d", peerAddress, pieceIndex);
            return false;
        }

        PieceState pieceState = pieceManager.getPieceState(pieceIndex);
        if (pieceState != PieceState.COMPLETE) {
            logger.debug("Peer %s requested incomplete piece %d", peerAddress, pieceIndex);
            return false;
        }

        try {
            byte[] blockData = diskManager.readBlock(pieceIndex, offset, length);
            if (blockData == null || blockData.length == 0) {
                logger.debug("Failed to read block for REQUEST from %s", peerAddress);
                return false;
            }

            // Send PIECE: [index(4) | begin(4) | block data]
            byte[] piecePayload = new byte[8 + blockData.length];
            java.nio.ByteBuffer pieceBuf = java.nio.ByteBuffer.wrap(piecePayload);
            pieceBuf.putInt(pieceIndex);
            pieceBuf.putInt(offset);
            pieceBuf.put(blockData);

            peer.send(new Message(Message.PIECE, piecePayload));

            // Track upload stats
            if (stats != null) {
                stats.recordUpload(blockData.length);
            }
            if (chokerStats != null) {
                chokerStats.recordUpload(blockData.length);
            }
            transferStats.recordUpload(blockData.length);

            return true;

        } catch (Exception e) {
            logger.debug("Failed to send PIECE to %s: %s", peerAddress, e.getMessage());
            return false;
        }
    }
}
