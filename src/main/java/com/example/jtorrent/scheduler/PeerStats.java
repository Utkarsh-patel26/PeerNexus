package com.example.jtorrent.scheduler;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Peer connection statistics.
 */
public class PeerStats {

  private final String peerId;
  private final InetSocketAddress address;
  private final AtomicLong uploadedBytes = new AtomicLong(0);
  private final AtomicLong downloadedBytes = new AtomicLong(0);
  private final AtomicLong lastUploadTime = new AtomicLong(0);
  private final AtomicLong lastDownloadTime = new AtomicLong(0);

  // For rate calculation
  private volatile long uploadBytesAtWindow;
  private volatile long downloadBytesAtWindow;
  private volatile long windowStartTime;

  private volatile double uploadRateBytesPerSec;
  private volatile double downloadRateBytesPerSec;

  private volatile boolean choked = true;
  private volatile boolean interested = false;
  private volatile boolean peerChoked = true;
  private volatile boolean peerInterested = false;

  // Performance metrics
  private final AtomicLong connectionStartTime;
  private final AtomicInteger successfulRequests = new AtomicInteger(0);
  private final AtomicInteger failedRequests = new AtomicInteger(0);
  private final AtomicLong totalLatencyMs = new AtomicLong(0);
  private volatile long lastActivityTime;
  private volatile boolean isSeeder = false;

  public PeerStats(String peerId) {
    this(peerId, null);
  }

  public PeerStats(String peerId, InetSocketAddress address) {
    this.peerId = peerId;
    this.address = address;
    long now = System.currentTimeMillis();
    this.windowStartTime = now;
    this.uploadBytesAtWindow = 0;
    this.downloadBytesAtWindow = 0;
    this.connectionStartTime = new AtomicLong(now);
    this.lastActivityTime = now;
  }

  /**
   * Get peer identifier.
   *
   * @return peer ID
   */
  public String getPeerId() {
    return peerId;
  }

  /**
   * Record uploaded bytes.
   *
   * @param bytes number of bytes uploaded
   */
  public void recordUpload(long bytes) {
    uploadedBytes.addAndGet(bytes);
    lastUploadTime.set(System.currentTimeMillis());
  }

  /**
   * Record downloaded bytes.
   *
   * @param bytes number of bytes downloaded
   */
  public void recordDownload(long bytes) {
    downloadedBytes.addAndGet(bytes);
    long now = System.currentTimeMillis();
    lastDownloadTime.set(now);
    lastActivityTime = now;
  }

  /**
   * Record successful request.
   *
   * @param latencyMs request latency in milliseconds
   */
  public void recordSuccess(long latencyMs) {
    successfulRequests.incrementAndGet();
    totalLatencyMs.addAndGet(latencyMs);
    lastActivityTime = System.currentTimeMillis();
  }

  /**
   * Record failed request.
   */
  public void recordFailure() {
    failedRequests.incrementAndGet();
    lastActivityTime = System.currentTimeMillis();
  }

  /**
   * Update rate calculations based on time window.
   *
   * @param windowMillis time window in milliseconds
   */
  public void updateRates(long windowMillis) {
    long now = System.currentTimeMillis();
    long elapsed = now - windowStartTime;

    if (elapsed > 0) {
      long currentUploaded = uploadedBytes.get();
      long currentDownloaded = downloadedBytes.get();

      long uploadDelta = currentUploaded - uploadBytesAtWindow;
      long downloadDelta = currentDownloaded - downloadBytesAtWindow;

      uploadRateBytesPerSec = (uploadDelta * 1000.0) / elapsed;
      downloadRateBytesPerSec = (downloadDelta * 1000.0) / elapsed;

      uploadBytesAtWindow = currentUploaded;
      downloadBytesAtWindow = currentDownloaded;
      windowStartTime = now;
    }
  }

  /**
   * Get upload rate.
   *
   * @return bytes per second
   */
  public double getUploadRateBytesPerSec() {
    return uploadRateBytesPerSec;
  }

  /**
   * Get download rate.
   *
   * @return bytes per second
   */
  public double getDownloadRateBytesPerSec() {
    return downloadRateBytesPerSec;
  }

  /**
   * Get total uploaded bytes.
   *
   * @return total bytes
   */
  public long getTotalUploaded() {
    return uploadedBytes.get();
  }

  /**
   * Get total downloaded bytes.
   *
   * @return total bytes
   */
  public long getTotalDownloaded() {
    return downloadedBytes.get();
  }

  /**
   * Check if we are choking this peer.
   *
   * @return true if choking
   */
  public boolean isChoked() {
    return choked;
  }

  /**
   * Set choking state.
   *
   * @param choked true to choke
   */
  public void setChoked(boolean choked) {
    this.choked = choked;
  }

  /**
   * Check if we are interested in this peer.
   *
   * @return true if interested
   */
  public boolean isInterested() {
    return interested;
  }

  /**
   * Set interested state.
   *
   * @param interested true if interested
   */
  public void setInterested(boolean interested) {
    this.interested = interested;
  }

  /**
   * Check if peer is choking us.
   *
   * @return true if peer is choking
   */
  public boolean isPeerChoked() {
    return peerChoked;
  }

  /**
   * Set peer choking state.
   *
   * @param peerChoked true if peer is choking
   */
  public void setPeerChoked(boolean peerChoked) {
    this.peerChoked = peerChoked;
  }

  /**
   * Check if peer is interested in us.
   *
   * @return true if peer is interested
   */
  public boolean isPeerInterested() {
    return peerInterested;
  }

  /**
   * Set peer interested state.
   *
   * @param peerInterested true if peer is interested
   */
  public void setPeerInterested(boolean peerInterested) {
    this.peerInterested = peerInterested;
  }

  /**
   * Get peer address.
   *
   * @return peer address
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Get connection age in milliseconds.
   *
   * @return milliseconds since connection
   */
  public long getConnectionAge() {
    return System.currentTimeMillis() - connectionStartTime.get();
  }

  /**
   * Get milliseconds since last activity.
   *
   * @return milliseconds
   */
  public long getIdleTime() {
    return System.currentTimeMillis() - lastActivityTime;
  }

  /**
   * Get average latency in milliseconds.
   *
   * @return average latency
   */
  public double getAverageLatency() {
    int total = successfulRequests.get();
    return total > 0 ? (double) totalLatencyMs.get() / total : 0.0;
  }

  /**
   * Get success rate (0.0 to 1.0).
   *
   * @return success rate
   */
  public double getSuccessRate() {
    int total = successfulRequests.get() + failedRequests.get();
    return total > 0 ? (double) successfulRequests.get() / total : 1.0;
  }

  /**
   * Check if peer is seeder.
   *
   * @return true if seeder
   */
  public boolean isSeeder() {
    return isSeeder;
  }

  /**
   * Set seeder status.
   *
   * @param isSeeder true if seeder
   */
  public void setSeeder(boolean isSeeder) {
    this.isSeeder = isSeeder;
  }

  /**
   * Calculate peer quality score (0-100).
   * Higher is better. Based on download rate, success rate, and latency.
   *
   * @return quality score
   */
  public double getQualityScore() {
    double speedScore = Math.min(downloadRateBytesPerSec / 1024.0 / 100.0, 50.0); // 0-50
    double successScore = getSuccessRate() * 30.0; // 0-30
    double latencyScore = Math.max(0, 20.0 - (getAverageLatency() / 100.0)); // 0-20
    return speedScore + successScore + latencyScore;
  }

  /**
   * Check if peer is responsive (activity within last 30 seconds).
   *
   * @return true if responsive
   */
  public boolean isResponsive() {
    return getIdleTime() < 30000;
  }

  /**
   * Check if peer is slow (download rate below threshold).
   *
   * @param thresholdBytesPerSec minimum acceptable rate
   * @return true if slow
   */
  public boolean isSlow(double thresholdBytesPerSec) {
    return getConnectionAge() > 10000 && downloadRateBytesPerSec < thresholdBytesPerSec;
  }

  @Override
  public String toString() {
    return String.format(
        "PeerStats[%s: down=%.1f KB/s, up=%.1f KB/s, quality=%.1f, latency=%.0fms, "
            + "success=%.1f%%, idle=%.1fs]",
        peerId != null ? peerId.substring(0, Math.min(8, peerId.length())) : "unknown",
        downloadRateBytesPerSec / 1024.0,
        uploadRateBytesPerSec / 1024.0,
        getQualityScore(),
        getAverageLatency(),
        getSuccessRate() * 100,
        getIdleTime() / 1000.0);
  }
}
