package com.example.jtorrent.events;

import com.example.jtorrent.persistence.DownloadState.DownloadStatus;

/**
 * Event fired when a torrent's state changes.
 */
public class TorrentStateChangedEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final DownloadStatus oldStatus;
    private final DownloadStatus newStatus;

    public TorrentStateChangedEvent(byte[] infoHash, DownloadStatus oldStatus, DownloadStatus newStatus) {
        super();
        this.infoHash = infoHash;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public DownloadStatus getOldStatus() {
        return oldStatus;
    }

    public DownloadStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String toString() {
        return String.format("TorrentStateChanged[%s -> %s]", oldStatus, newStatus);
    }
}
