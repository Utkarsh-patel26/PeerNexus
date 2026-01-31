package com.example.jtorrent.events;

/**
 * Base class for all torrent-related events.
 */
public abstract class TorrentEvent {

    private final long timestamp;

    protected TorrentEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the info hash of the torrent this event relates to.
     * Return null for global events.
     */
    public abstract byte[] getInfoHash();
}
