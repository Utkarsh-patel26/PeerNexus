package com.example.jtorrent.events;

/**
 * Event fired when a tracker responds to an announce.
 */
public class TrackerResponseEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final String trackerUrl;
    private final boolean success;
    private final int peerCount;
    private final String errorMessage;

    public TrackerResponseEvent(byte[] infoHash, String trackerUrl, boolean success,
            int peerCount, String errorMessage) {
        super();
        this.infoHash = infoHash;
        this.trackerUrl = trackerUrl;
        this.success = success;
        this.peerCount = peerCount;
        this.errorMessage = errorMessage;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getTrackerUrl() {
        return trackerUrl;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getPeerCount() {
        return peerCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return String.format("TrackerResponse[%s, success=%s, peers=%d]",
                trackerUrl, success, peerCount);
    }
}
