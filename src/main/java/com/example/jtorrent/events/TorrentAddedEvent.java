package com.example.jtorrent.events;

/**
 * Event fired when a new torrent is added to the session.
 */
public class TorrentAddedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final String torrentName;
    private final long totalSize;

    public TorrentAddedEvent(byte[] infoHash, String torrentName, long totalSize) {
        super();
        this.infoHash = infoHash;
        this.torrentName = torrentName;
        this.totalSize = totalSize;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getTorrentName() {
        return torrentName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    @Override
    public String toString() {
        return String.format("TorrentAdded[%s, size=%d bytes]", torrentName, totalSize);
    }
}
