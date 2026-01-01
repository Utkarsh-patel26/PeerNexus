package com.example.jtorrent.ui;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Text-based UI for download progress.
 */
public class DownloadTui {

  private static final int PROGRESS_BAR_WIDTH = 50;
  private static final int UPDATE_INTERVAL_MS = 1000;

  private final PrintStream out;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Thread updateThread;

  // Download statistics
  private final AtomicLong totalBytes = new AtomicLong(0);
  private final AtomicLong downloadedBytes = new AtomicLong(0);
  private final AtomicLong uploadedBytes = new AtomicLong(0);
  private final AtomicLong downloadRate = new AtomicLong(0);
  private final AtomicLong uploadRate = new AtomicLong(0);
  private final AtomicLong peersConnected = new AtomicLong(0);
  private final AtomicLong seedsConnected = new AtomicLong(0);
  private final AtomicLong trackerSeeders = new AtomicLong(0);
  private final AtomicLong trackerLeechers = new AtomicLong(0);

  private String torrentName = "Unknown";
  private volatile long startTime = 0;
  private volatile List<PeerInfo> peers = new ArrayList<>();

  public DownloadTui(PrintStream out) {
    this.out = out;
    this.updateThread = new Thread(this::updateLoop, "TUI-Update");
    this.updateThread.setDaemon(true);
  }

  public DownloadTui() {
    this(System.out);
  }

  public void start() {
    if (running.compareAndSet(false, true)) {
      startTime = System.currentTimeMillis();
      clearScreen();
      updateThread.start();
    }
  }

  public void stop() {
    if (running.compareAndSet(true, false)) {
      try {
        updateThread.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      render(); // Final render
    }
  }

  public void setTorrentInfo(String name, long totalBytes) {
    this.torrentName = name;
    this.totalBytes.set(totalBytes);
  }

  public void setDownloadedBytes(long downloaded) {
    this.downloadedBytes.set(downloaded);
  }

  public void setUploadedBytes(long uploaded) {
    this.uploadedBytes.set(uploaded);
  }

  public void setRates(long downloadRate, long uploadRate) {
    this.downloadRate.set(downloadRate);
    this.uploadRate.set(uploadRate);
  }

  public void setPeerCounts(long peers, long seeds) {
    this.peersConnected.set(peers);
    this.seedsConnected.set(seeds);
  }

  public void setPeers(List<PeerInfo> peers) {
    this.peers = new ArrayList<>(peers);
  }

  public void setTrackerStats(long seeders, long leechers) {
    this.trackerSeeders.set(seeders);
    this.trackerLeechers.set(leechers);
  }

  /**
   * Update loop that redraws the UI.
   */
  private void updateLoop() {
    while (running.get()) {
      try {
        render();
        Thread.sleep(UPDATE_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * Render the complete UI.
   */
  private void render() {
    StringBuilder sb = new StringBuilder();

    // Clear screen and move to top
    sb.append("\033[H\033[2J");
    sb.append("\033[H");

    // Title
    sb.append("╔════════════════════════════════════════════════════════════════════════╗\n");
    sb.append("║                         JTorrent Download Status                       ║\n");
    sb.append("╚════════════════════════════════════════════════════════════════════════╝\n");
    sb.append("\n");

    // Torrent info
    sb.append(String.format("Torrent: %s\n", truncate(torrentName, 60)));
    sb.append(String.format("Size:    %s\n", formatBytes(totalBytes.get())));
    sb.append("\n");

    // Progress bar
    double progress = totalBytes.get() > 0
        ? (double) downloadedBytes.get() / totalBytes.get()
        : 0.0;
    sb.append("Progress: " + renderProgressBar(progress) + String.format(" %.1f%%\n", progress * 100));
    sb.append(String.format("Downloaded: %s / %s\n",
        formatBytes(downloadedBytes.get()),
        formatBytes(totalBytes.get())));
    sb.append("\n");

    // Transfer stats
    sb.append("Transfer Statistics:\n");
    sb.append(String.format("  Download: %s/s  (Total: %s)\n",
        formatBytes(downloadRate.get()), formatBytes(downloadedBytes.get())));
    sb.append(String.format("  Upload:   %s/s  (Total: %s)\n",
        formatBytes(uploadRate.get()), formatBytes(uploadedBytes.get())));
    sb.append("\n");

    // Peer stats
    sb.append("Peers:\n");
    sb.append(String.format("  Connected: %d (%d seeds)\n",
        peersConnected.get(), seedsConnected.get()));

    // Show tracker statistics if available
    if (trackerSeeders.get() > 0 || trackerLeechers.get() > 0) {
      sb.append(String.format("  Tracker reports: %d seeds, %d leechers\n",
          trackerSeeders.get(), trackerLeechers.get()));
    }
    sb.append("\n");

    // Time stats
    long elapsed = System.currentTimeMillis() - startTime;
    sb.append(String.format("Elapsed: %s", formatDuration(elapsed)));

    if (downloadRate.get() > 0) {
      long remaining = totalBytes.get() - downloadedBytes.get();
      long eta = remaining / downloadRate.get() * 1000;
      sb.append(String.format("  ETA: %s", formatDuration(eta)));
    }
    sb.append("\n");

    // Top peers
    if (!peers.isEmpty()) {
      sb.append("\nTop Peers:\n");
      sb.append("  Address              Down         Up           %\n");
      sb.append("  ─────────────────────────────────────────────────\n");

      int count = Math.min(5, peers.size());
      for (int i = 0; i < count; i++) {
        PeerInfo peer = peers.get(i);
        sb.append(String.format("  %-20s %-12s %-12s %.1f%%\n",
            truncate(peer.address, 20),
            formatBytes(peer.downloadRate) + "/s",
            formatBytes(peer.uploadRate) + "/s",
            peer.progress * 100));
      }
    }

    sb.append("\n");
    sb.append("Press Ctrl+C to cancel\n");

    out.print(sb.toString());
    out.flush();
  }

  /**
   * Render a progress bar.
   */
  private String renderProgressBar(double progress) {
    progress = Math.max(0, Math.min(1, progress));
    int filled = (int) (PROGRESS_BAR_WIDTH * progress);
    int empty = PROGRESS_BAR_WIDTH - filled;

    StringBuilder bar = new StringBuilder("[");
    for (int i = 0; i < filled; i++) {
      bar.append("█");
    }
    for (int i = 0; i < empty; i++) {
      bar.append("░");
    }
    bar.append("]");

    return bar.toString();
  }

  /**
   * Format bytes as human-readable string.
   */
  private String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    } else if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024));
    } else {
      return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
  }

  /**
   * Format duration as human-readable string.
   */
  private String formatDuration(long millis) {
    long seconds = millis / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long days = hours / 24;

    if (days > 0) {
      return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
    } else if (hours > 0) {
      return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
    } else if (minutes > 0) {
      return String.format("%dm %ds", minutes, seconds % 60);
    } else {
      return String.format("%ds", seconds);
    }
  }

  /**
   * Truncate string to max length.
   */
  private String truncate(String str, int maxLength) {
    if (str.length() <= maxLength) {
      return str;
    }
    return str.substring(0, maxLength - 3) + "...";
  }

  /**
   * Clear the screen.
   */
  private void clearScreen() {
    out.print("\033[H\033[2J");
    out.flush();
  }

  /**
   * Information about a peer.
   */
  public static class PeerInfo {
    public final String address;
    public final long downloadRate;
    public final long uploadRate;
    public final double progress;

    public PeerInfo(String address, long downloadRate, long uploadRate, double progress) {
      this.address = address;
      this.downloadRate = downloadRate;
      this.uploadRate = uploadRate;
      this.progress = progress;
    }
  }
}
