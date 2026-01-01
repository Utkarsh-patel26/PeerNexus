package com.example.jtorrent.storage;

import java.net.InetSocketAddress;

/**
 * Outstanding block request for pipelining.
 */
public class BlockRequest {

    private final int pieceIndex;
    private final int offset;
    private final int length;
    private final InetSocketAddress peer;
    private final long requestTime;
    private volatile boolean fulfilled;

    /**
     * Create a block request.
     *
     * @param pieceIndex piece index
     * @param offset     byte offset in piece
     * @param length     block length
     * @param peer       peer address
     */
    public BlockRequest(int pieceIndex, int offset, int length, InetSocketAddress peer) {
        this.pieceIndex = pieceIndex;
        this.offset = offset;
        this.length = length;
        this.peer = peer;
        this.requestTime = System.currentTimeMillis();
        this.fulfilled = false;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public InetSocketAddress getPeer() {
        return peer;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public long getAge() {
        return System.currentTimeMillis() - requestTime;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    public void markFulfilled() {
        this.fulfilled = true;
    }

    public boolean isStale(long timeoutMs) {
        return getAge() > timeoutMs && !fulfilled;
    }

    @Override
    public String toString() {
        return String.format("BlockRequest[piece=%d, offset=%d, length=%d, peer=%s, age=%dms]",
                pieceIndex, offset, length, peer, getAge());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof BlockRequest))
            return false;
        BlockRequest other = (BlockRequest) obj;
        return pieceIndex == other.pieceIndex && offset == other.offset;
    }

    @Override
    public int hashCode() {
        return pieceIndex * 31 + offset;
    }
}
