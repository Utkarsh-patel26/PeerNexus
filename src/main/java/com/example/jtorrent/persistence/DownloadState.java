package com.example.jtorrent.persistence;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent download state that can be saved and resumed.
 * Tracks which pieces have been completed and download progress.
 */
public class DownloadState {

  private final byte[] infoHash;
  private final String torrentName;
  private final long totalBytes;
  private final int pieceCount;
  private final BitSet completedPieces;
  private long downloadedBytes;
  private long uploadedBytes;
  private long addedTime;
  private long completedTime;
  private DownloadStatus status;

  // Fast resume additional fields
  private String savePath;
  private Map<Integer, BitSet> partialBlocks; // pieceIndex -> received blocks
  private int[] filePriorities; // Per-file priority (0=skip, 1=low, 2=normal, 3=high)
  private long lastActiveTime;
  private long seedingTime; // Total time spent seeding

  /**
   * Download status.
   */
  public enum DownloadStatus {
    DOWNLOADING,
    SEEDING,
    PAUSED,
    ERROR,
    COMPLETED
  }

  /**
   * Create a new download state.
   *
   * @param infoHash    torrent info hash
   * @param torrentName torrent name
   * @param totalBytes  total size
   * @param pieceCount  number of pieces
   */
  public DownloadState(byte[] infoHash, String torrentName, long totalBytes, int pieceCount) {
    this.infoHash = infoHash.clone();
    this.torrentName = torrentName;
    this.totalBytes = totalBytes;
    this.pieceCount = pieceCount;
    this.completedPieces = new BitSet(pieceCount);
    this.downloadedBytes = 0;
    this.uploadedBytes = 0;
    this.addedTime = System.currentTimeMillis();
    this.completedTime = 0;
    this.status = DownloadStatus.DOWNLOADING;
    this.partialBlocks = new HashMap<>();
    this.lastActiveTime = System.currentTimeMillis();
    this.seedingTime = 0;
  }

  /**
   * Mark a piece as completed.
   *
   * @param pieceIndex piece index
   */
  public void markPieceComplete(int pieceIndex) {
    if (!completedPieces.get(pieceIndex)) {
      completedPieces.set(pieceIndex);
    }
  }

  /**
   * Check if a piece is complete.
   *
   * @param pieceIndex piece index
   * @return true if complete
   */
  public boolean isPieceComplete(int pieceIndex) {
    return completedPieces.get(pieceIndex);
  }

  /**
   * Get number of completed pieces.
   *
   * @return completed piece count
   */
  public int getCompletedPieceCount() {
    return completedPieces.cardinality();
  }

  /**
   * Get download progress (0.0 to 1.0).
   * Uses completed pieces for accurate progress tracking,
   * falling back to bytes-based progress if piece tracking isn't available.
   *
   * @return progress percentage
   */
  public double getProgress() {
    // Primary: use piece-based progress
    if (pieceCount > 0) {
      double pieceProgress = (double) completedPieces.cardinality() / pieceCount;
      if (pieceProgress > 0) {
        return pieceProgress;
      }
    }
    // Fallback: use bytes-based progress if pieces aren't being tracked
    // This provides smoother progress during initial download
    if (totalBytes > 0 && downloadedBytes > 0) {
      return Math.min(1.0, (double) downloadedBytes / totalBytes);
    }
    return 0.0;
  }

  /**
   * Check if download is complete.
   *
   * @return true if all pieces downloaded
   */
  public boolean isComplete() {
    return completedPieces.cardinality() == pieceCount;
  }

  /**
   * Save state to file.
   *
   * @param stateFile path to state file
   * @throws IOException if save fails
   */
  public void save(Path stateFile) throws IOException {
    Map<String, Object> state = new HashMap<>();

    // Basic info
    state.put("infoHash", infoHash);
    state.put("torrentName", torrentName);
    state.put("totalBytes", totalBytes);
    state.put("pieceCount", (long) pieceCount);

    // Progress
    state.put("completedPieces", completedPieces.toByteArray());
    state.put("downloadedBytes", downloadedBytes);
    state.put("uploadedBytes", uploadedBytes);

    // Timestamps
    state.put("addedTime", addedTime);
    state.put("completedTime", completedTime);
    state.put("status", status.name());

    // Fast resume fields
    if (savePath != null) {
      state.put("savePath", savePath);
    }
    state.put("lastActiveTime", lastActiveTime);
    state.put("seedingTime", seedingTime);

    // File priorities
    if (filePriorities != null) {
      StringBuilder fpStr = new StringBuilder();
      for (int fp : filePriorities) {
        if (fpStr.length() > 0)
          fpStr.append(",");
        fpStr.append(fp);
      }
      state.put("filePriorities", fpStr.toString());
    }

    try {
      byte[] encoded = BencodeParser.encode(state);
      Files.write(stateFile, encoded);
    } catch (BencodeException e) {
      throw new IOException("Failed to encode state", e);
    }
  }

  /**
   * Load state from file.
   *
   * @param stateFile path to state file
   * @return loaded download state
   * @throws IOException if load fails
   */
  @SuppressWarnings("unchecked")
  public static DownloadState load(Path stateFile) throws IOException {
    try {
      byte[] data = Files.readAllBytes(stateFile);
      BencodeParser parser = new BencodeParser(data);
      Map<String, Object> state = (Map<String, Object>) parser.parse();

      // Basic info
      byte[] infoHash = (byte[]) state.get("infoHash");
      String torrentName = new String((byte[]) state.get("torrentName"));
      long totalBytes = (Long) state.get("totalBytes");
      int pieceCount = ((Long) state.get("pieceCount")).intValue();

      DownloadState downloadState = new DownloadState(infoHash, torrentName, totalBytes, pieceCount);

      // Progress
      byte[] completedPiecesBytes = (byte[]) state.get("completedPieces");
      downloadState.completedPieces.or(BitSet.valueOf(completedPiecesBytes));
      downloadState.downloadedBytes = (Long) state.get("downloadedBytes");
      downloadState.uploadedBytes = (Long) state.get("uploadedBytes");

      // Timestamps
      downloadState.addedTime = (Long) state.get("addedTime");
      downloadState.completedTime = (Long) state.get("completedTime");

      // Status
      String statusStr = new String((byte[]) state.get("status"));
      downloadState.status = DownloadStatus.valueOf(statusStr);

      // Fast resume fields (optional)
      Object savePathObj = state.get("savePath");
      if (savePathObj instanceof byte[]) {
        downloadState.savePath = new String((byte[]) savePathObj);
      }

      Object lastActiveObj = state.get("lastActiveTime");
      if (lastActiveObj instanceof Long) {
        downloadState.lastActiveTime = (Long) lastActiveObj;
      }

      Object seedingTimeObj = state.get("seedingTime");
      if (seedingTimeObj instanceof Long) {
        downloadState.seedingTime = (Long) seedingTimeObj;
      }

      // File priorities
      Object fpObj = state.get("filePriorities");
      if (fpObj instanceof byte[]) {
        String fpStr = new String((byte[]) fpObj);
        String[] parts = fpStr.split(",");
        downloadState.filePriorities = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
          downloadState.filePriorities[i] = Integer.parseInt(parts[i].trim());
        }
      }

      return downloadState;
    } catch (BencodeException e) {
      throw new IOException("Failed to decode state", e);
    }
  }

  /**
   * Get state file name for this torrent.
   *
   * @return state file name
   */
  public String getStateFileName() {
    return bytesToHex(infoHash) + ".state";
  }

  /**
   * Convert bytes to hex string.
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }

  // Getters and setters

  public byte[] getInfoHash() {
    return infoHash.clone();
  }

  public String getTorrentName() {
    return torrentName;
  }

  public long getTotalBytes() {
    return totalBytes;
  }

  public int getPieceCount() {
    return pieceCount;
  }

  public BitSet getCompletedPieces() {
    return (BitSet) completedPieces.clone();
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

  public long getAddedTime() {
    return addedTime;
  }

  public long getCompletedTime() {
    return completedTime;
  }

  public void setCompletedTime(long completedTime) {
    this.completedTime = completedTime;
  }

  /**
   * Get the current download status.
   *
   * @return download status
   */
  public DownloadStatus getStatus() {
    return status;
  }

  public void setStatus(DownloadStatus status) {
    this.status = status;
    if (status == DownloadStatus.COMPLETED && completedTime == 0) {
      completedTime = System.currentTimeMillis();
    }
  }

  // Fast resume getters and setters

  public String getSavePath() {
    return savePath;
  }

  public void setSavePath(String savePath) {
    this.savePath = savePath;
  }

  public long getLastActiveTime() {
    return lastActiveTime;
  }

  public void setLastActiveTime(long lastActiveTime) {
    this.lastActiveTime = lastActiveTime;
  }

  public long getSeedingTime() {
    return seedingTime;
  }

  public void setSeedingTime(long seedingTime) {
    this.seedingTime = seedingTime;
  }

  public void addSeedingTime(long delta) {
    this.seedingTime += delta;
  }

  public int[] getFilePriorities() {
    return filePriorities;
  }

  public void setFilePriorities(int[] filePriorities) {
    this.filePriorities = filePriorities;
  }

  public int getFilePriority(int fileIndex) {
    if (filePriorities == null || fileIndex >= filePriorities.length) {
      return 2; // NORMAL by default
    }
    return filePriorities[fileIndex];
  }

  public void setFilePriority(int fileIndex, int priority) {
    if (filePriorities == null) {
      filePriorities = new int[fileIndex + 1];
      java.util.Arrays.fill(filePriorities, 2); // NORMAL
    } else if (fileIndex >= filePriorities.length) {
      int[] newPriorities = new int[fileIndex + 1];
      System.arraycopy(filePriorities, 0, newPriorities, 0, filePriorities.length);
      java.util.Arrays.fill(newPriorities, filePriorities.length, newPriorities.length, 2);
      filePriorities = newPriorities;
    }
    filePriorities[fileIndex] = priority;
  }

  /**
   * Mark partial block progress for a piece.
   *
   * @param pieceIndex the piece index
   * @param blockIndex the block index within the piece
   */
  public void markPartialBlock(int pieceIndex, int blockIndex) {
    if (partialBlocks == null) {
      partialBlocks = new HashMap<>();
    }
    partialBlocks.computeIfAbsent(pieceIndex, k -> new BitSet()).set(blockIndex);
  }

  /**
   * Get partial blocks for a piece.
   *
   * @param pieceIndex the piece index
   * @return bitset of received blocks, or null if none
   */
  public BitSet getPartialBlocks(int pieceIndex) {
    return partialBlocks != null ? partialBlocks.get(pieceIndex) : null;
  }

  /**
   * Clear partial blocks for a piece (after verification).
   *
   * @param pieceIndex the piece index
   */
  public void clearPartialBlocks(int pieceIndex) {
    if (partialBlocks != null) {
      partialBlocks.remove(pieceIndex);
    }
  }

  /**
   * Update last active time to now.
   */
  public void touch() {
    this.lastActiveTime = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return String.format("DownloadState[%s: %.1f%% (%d/%d pieces), status=%s]",
        torrentName, getProgress() * 100, getCompletedPieceCount(), pieceCount, status);
  }
}
