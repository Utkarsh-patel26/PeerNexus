package com.example.jtorrent.events;

/**
 * Event fired when a piece is successfully downloaded and verified.
 */
public class PieceCompletedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final int pieceIndex;
    private final long pieceLength;
    private final boolean hashValid;

    public PieceCompletedEvent(byte[] infoHash, int pieceIndex, long pieceLength, boolean hashValid) {
        super();
        this.infoHash = infoHash;
        this.pieceIndex = pieceIndex;
        this.pieceLength = pieceLength;
        this.hashValid = hashValid;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public boolean isHashValid() {
        return hashValid;
    }

    @Override
    public String toString() {
        return String.format("PieceCompleted[index=%d, length=%d, valid=%s]",
                pieceIndex, pieceLength, hashValid);
    }
}
