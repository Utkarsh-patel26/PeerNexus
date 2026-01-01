package com.example.jtorrent.gui.components;

import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.persistence.DownloadState;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model for a download item in the download manager.
 */
public class DownloadItem {

  private final StringProperty name;
  private final StringProperty size;
  private final DoubleProperty progress;
  private final StringProperty speed;
  private final StringProperty peers;
  private final StringProperty status;
  private final String input;
  private final String outputDirectory;
  private TorrentSession session;

  // Speed calculation tracking
  private long lastDownloadedBytes = 0;
  private long lastUpdateTime = 0;
  // Last computed instantaneous speed in bytes/sec
  private volatile long lastSpeedBytesPerSec = 0;

  public DownloadItem(String input, String outputDirectory) {
    this.input = input;
    this.outputDirectory = outputDirectory;
    this.name = new SimpleStringProperty(extractName(input));
    this.size = new SimpleStringProperty("0 B");
    this.progress = new SimpleDoubleProperty(0.0);
    this.speed = new SimpleStringProperty("0 B/s");
    this.peers = new SimpleStringProperty("0");
    this.status = new SimpleStringProperty("Queued");
  }

  private static String extractName(String input) {
    if (input.startsWith("magnet:")) {
      // Extract dn parameter from magnet link
      int dnIndex = input.indexOf("&dn=");
      if (dnIndex > 0) {
        int endIndex = input.indexOf("&", dnIndex + 4);
        if (endIndex > 0) {
          return input.substring(dnIndex + 4, endIndex).replace("+", " ");
        }
      }
      return "Unknown";
    } else {
      // Extract filename from path
      return new java.io.File(input).getName();
    }
  }

  // Getters and property accessors
  public String getInput() {
    return input;
  }

  public String getOutputDirectory() {
    return outputDirectory;
  }

  public StringProperty nameProperty() {
    return name;
  }

  public String getName() {
    return name.get();
  }

  public StringProperty sizeProperty() {
    return size;
  }

  public String getSize() {
    return size.get();
  }

  public void setSize(String size) {
    this.size.set(size);
  }

  public DoubleProperty progressProperty() {
    return progress;
  }

  public double getProgress() {
    return progress.get();
  }

  public void setProgress(double progress) {
    this.progress.set(Math.min(1.0, Math.max(0.0, progress)));
  }

  public StringProperty speedProperty() {
    return speed;
  }

  public String getSpeed() {
    return speed.get();
  }

  public void setSpeed(String speed) {
    this.speed.set(speed);
  }

  public StringProperty peersProperty() {
    return peers;
  }

  public String getPeers() {
    return peers.get();
  }

  public void setPeers(String peers) {
    this.peers.set(peers);
  }

  public StringProperty statusProperty() {
    return status;
  }

  public String getStatus() {
    return status.get();
  }

  public void setStatus(String status) {
    this.status.set(status);
  }

  /**
   * Set the torrent session for this download.
   */
  public void setSession(TorrentSession session) {
    this.session = session;
  }

  /**
   * Get the torrent session for this download.
   */
  public TorrentSession getSession() {
    return session;
  }

  /**
   * Update UI from session progress.
   */
  public void updateFromSession() {
    if (session == null) {
      return;
    }

    DownloadState state = session.getDownloadState();
    if (state != null) {
      long downloadedBytes = state.getDownloadedBytes();
      long totalBytes = state.getTotalBytes();
      double progressValue = state.getProgress();
      setProgress(progressValue);
      setSize(formatBytes(totalBytes));

      // Calculate download speed
      long currentTime = System.currentTimeMillis();
      if (lastUpdateTime > 0) {
        long timeDiffMs = currentTime - lastUpdateTime;
        long bytesDiff = downloadedBytes - lastDownloadedBytes;

        if (timeDiffMs > 0) {
          long speedBytesPerSec = (bytesDiff * 1000) / timeDiffMs;
          setSpeed(formatBytes(speedBytesPerSec) + "/s");
          lastSpeedBytesPerSec = speedBytesPerSec;
        }
      }

      lastDownloadedBytes = downloadedBytes;
      lastUpdateTime = currentTime;
    }

    // Update peer count
    int peerCount = session.getConnectedPeerCount();
    setPeers(String.valueOf(peerCount));

    if (session.isCompleted()) {
      setStatus("Completed");
    } else if (session.isRunning()) {
      setStatus("Downloading");
    }
  }

  /**
   * Get last computed instantaneous speed in bytes/sec.
   */
  public long getLastSpeedBytesPerSec() {
    return lastSpeedBytesPerSec;
  }

  /**
   * Format bytes to human-readable format.
   */
  private static String formatBytes(long bytes) {
    if (bytes <= 0)
      return "0 B";
    final String[] units = new String[] { "B", "KB", "MB", "GB" };
    int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
    return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
  }
}
