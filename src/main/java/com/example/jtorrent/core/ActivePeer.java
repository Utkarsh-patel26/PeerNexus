package com.example.jtorrent.core;

import com.example.jtorrent.peer.PeerConnection;
import com.example.jtorrent.scheduler.PeerStats;
import java.net.InetSocketAddress;
import java.util.BitSet;

/**
 * Active peer eligible for requesting blocks.
 */
public class ActivePeer {
    private final InetSocketAddress address;
    private final PeerConnection connection;
    private final PeerStats stats;
    private int consecutiveFailures = 0;
    private long lastRequestTime = 0;
    private boolean deprioritized = false;

    public ActivePeer(InetSocketAddress address, PeerConnection connection, PeerStats stats) {
        this.address = address;
        this.connection = connection;
        this.stats = stats;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public PeerConnection getConnection() {
        return connection;
    }

    public PeerStats getStats() {
        return stats;
    }

    public BitSet getBitfield() {
        return connection.getPeerBitfield();
    }

    public boolean isPeerChoking() {
        return connection.peerChoking();
    }

    /**
     * Check if the peer connection is still alive and usable.
     * 
     * @return true if connected and ready for requests
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    public boolean isDeprioritized() {
        return deprioritized;
    }

    public void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= 3) {
            deprioritized = true;
        }
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
        deprioritized = false;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setLastRequestTime(long time) {
        this.lastRequestTime = time;
    }

    public long getLastRequestTime() {
        return lastRequestTime;
    }

    public double getDownloadRate() {
        return stats != null ? stats.getDownloadRateBytesPerSec() : 0.0;
    }

    public boolean hasPiece(int pieceIndex) {
        BitSet bitfield = getBitfield();
        return bitfield != null && bitfield.get(pieceIndex);
    }
}
