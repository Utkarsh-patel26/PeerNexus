package com.example.jtorrent.events;

import java.net.InetSocketAddress;

/**
 * Event fired when a peer connection is established.
 */
public class PeerConnectedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final InetSocketAddress peerAddress;
    private final String peerId;

    public PeerConnectedEvent(byte[] infoHash, InetSocketAddress peerAddress, String peerId) {
        super();
        this.infoHash = infoHash;
        this.peerAddress = peerAddress;
        this.peerId = peerId;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    public String getPeerId() {
        return peerId;
    }

    @Override
    public String toString() {
        return String.format("PeerConnected[%s, id=%s]", peerAddress, peerId);
    }
}
