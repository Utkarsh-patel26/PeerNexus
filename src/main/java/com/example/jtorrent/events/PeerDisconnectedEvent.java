package com.example.jtorrent.events;

import java.net.InetSocketAddress;

/**
 * Event fired when a peer connection is closed.
 */
public class PeerDisconnectedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final InetSocketAddress peerAddress;
    private final String reason;

    public PeerDisconnectedEvent(byte[] infoHash, InetSocketAddress peerAddress, String reason) {
        super();
        this.infoHash = infoHash;
        this.peerAddress = peerAddress;
        this.reason = reason;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format("PeerDisconnected[%s, reason=%s]", peerAddress, reason);
    }
}
