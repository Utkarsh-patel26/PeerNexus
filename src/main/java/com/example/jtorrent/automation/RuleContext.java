package com.example.jtorrent.automation;

import com.example.jtorrent.persistence.DownloadState;
import java.nio.file.Path;

/**
 * Context information for rule execution.
 */
public class RuleContext {

    private final byte[] infoHash;
    private final DownloadState downloadState;
    private final Path torrentPath;
    private double ratio;
    private long seedingTimeSeconds;
    private long downloadedBytes;
    private long uploadedBytes;
    private boolean isComplete;
    private int connectedPeers;
    private long diskSpaceAvailable;

    public RuleContext(byte[] infoHash, DownloadState downloadState, Path torrentPath) {
        this.infoHash = infoHash;
        this.downloadState = downloadState;
        this.torrentPath = torrentPath;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public DownloadState getDownloadState() {
        return downloadState;
    }

    public Path getTorrentPath() {
        return torrentPath;
    }

    public double getRatio() {
        return ratio;
    }

    public void setRatio(double ratio) {
        this.ratio = ratio;
    }

    public long getSeedingTimeSeconds() {
        return seedingTimeSeconds;
    }

    public void setSeedingTimeSeconds(long seedingTimeSeconds) {
        this.seedingTimeSeconds = seedingTimeSeconds;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public void setUploadedBytes(long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    public int getConnectedPeers() {
        return connectedPeers;
    }

    public void setConnectedPeers(int connectedPeers) {
        this.connectedPeers = connectedPeers;
    }

    public long getDiskSpaceAvailable() {
        return diskSpaceAvailable;
    }

    public void setDiskSpaceAvailable(long diskSpaceAvailable) {
        this.diskSpaceAvailable = diskSpaceAvailable;
    }
}
