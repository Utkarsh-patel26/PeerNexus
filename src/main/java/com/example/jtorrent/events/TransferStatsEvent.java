package com.example.jtorrent.events;

/**
 * Event fired periodically with transfer statistics.
 */
public class TransferStatsEvent extends TorrentEvent {

    private final byte[] infoHash;
    private final long downloadRate;
    private final long uploadRate;
    private final long downloadedBytes;
    private final long uploadedBytes;
    private final int activePeers;
    private final double progress;

    public TransferStatsEvent(byte[] infoHash, long downloadRate, long uploadRate,
            long downloadedBytes, long uploadedBytes,
            int activePeers, double progress) {
        super();
        this.infoHash = infoHash;
        this.downloadRate = downloadRate;
        this.uploadRate = uploadRate;
        this.downloadedBytes = downloadedBytes;
        this.uploadedBytes = uploadedBytes;
        this.activePeers = activePeers;
        this.progress = progress;
    }

    @Override
    public byte[] getInfoHash() {
        return infoHash;
    }

    public long getDownloadRate() {
        return downloadRate;
    }

    public long getUploadRate() {
        return uploadRate;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public int getActivePeers() {
        return activePeers;
    }

    public double getProgress() {
        return progress;
    }

    @Override
    public String toString() {
        return String.format("TransferStats[↓%d KB/s, ↑%d KB/s, peers=%d, progress=%.1f%%]",
                downloadRate / 1024, uploadRate / 1024, activePeers, progress * 100);
    }
}
