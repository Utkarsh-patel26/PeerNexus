package com.example.jtorrent.events;

/**
 * Event fired when a torrent is removed from the session.
 */
public class TorrentRemovedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final boolean deleteData;

    public TorrentRemovedEvent(byte[] infoHash, boolean deleteData) {
        super();
        this.infoHash = infoHash;
        this.deleteData = deleteData;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public boolean isDeleteData() {
        return deleteData;
    }

    @Override
    public String toString() {
        return String.format("TorrentRemoved[deleteData=%s]", deleteData);
    }
}
