package com.example.jtorrent.scheduler;

import com.example.jtorrent.logging.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * BitTorrent choking algorithm with tit-for-tat and optimistic unchoke.
 */
public class Choker {

  public static final int DEFAULT_UPLOAD_SLOTS = 8;
  public static final int MIN_UPLOAD_SLOTS = 6;
  public static final int MAX_UPLOAD_SLOTS = 10;
  public static final long OPTIMISTIC_UNCHOKE_INTERVAL_MS = 30000;
  public static final long CHOKE_INTERVAL_MS = 10000;

  private final int uploadSlots;
  private final Map<String, PeerStats> peerStats;
  private final Set<String> unchokedPeers;
  private String optimisticUnchokedPeer;
  private long lastOptimisticRotation;
  private long lastChokeEvaluation;
  private final Random random;
  private final Logger logger;

  public Choker() {
    this(DEFAULT_UPLOAD_SLOTS, Logger.getLogger("Choker"));
  }

  public Choker(int uploadSlots) {
    this(uploadSlots, Logger.getLogger("Choker"));
  }

  public Choker(int uploadSlots, Logger logger) {
    this.uploadSlots = Math.max(MIN_UPLOAD_SLOTS, Math.min(MAX_UPLOAD_SLOTS, uploadSlots));
    this.peerStats = new ConcurrentHashMap<>();
    this.unchokedPeers = new HashSet<>();
    this.optimisticUnchokedPeer = null;
    long now = System.currentTimeMillis();
    this.lastOptimisticRotation = now;
    this.lastChokeEvaluation = now;
    this.random = new Random();
    this.logger = logger;
  }

  public PeerStats addPeer(String peerId) {
    PeerStats stats = new PeerStats(peerId);
    peerStats.put(peerId, stats);
    return stats;
  }

  /**
   * Remove a peer from tracking.
   *
   * @param peerId the peer identifier
   */
  public void removePeer(String peerId) {
    peerStats.remove(peerId);
    unchokedPeers.remove(peerId);
    if (peerId.equals(optimisticUnchokedPeer)) {
      optimisticUnchokedPeer = null;
    }
  }

  /**
   * Get peer statistics.
   *
   * @param peerId the peer identifier
   * @return peer stats or null
   */
  public PeerStats getPeerStats(String peerId) {
    return peerStats.get(peerId);
  }

  /**
   * Get all peer statistics.
   *
   * @return map of peer ID to stats
   */
  public Map<String, PeerStats> getAllPeerStats() {
    return new ConcurrentHashMap<>(peerStats);
  }

  /**
   * Update rates for all peers.
   *
   * @param windowMillis time window for rate calculation
   */
  public void updateAllRates(long windowMillis) {
    for (PeerStats stats : peerStats.values()) {
      stats.updateRates(windowMillis);
    }
  }

  /**
   * Run the choking algorithm to determine which peers to unchoke.
   * This implements the standard BitTorrent tit-for-tat with optimistic unchoke.
   *
   * @return set of peer IDs that should be unchoked
   */
  public Set<String> runChokingAlgorithm() {
    long now = System.currentTimeMillis();

    // Only run choke evaluation every 10 seconds to avoid thrashing
    if (now - lastChokeEvaluation < CHOKE_INTERVAL_MS && !unchokedPeers.isEmpty()) {
      return new HashSet<>(unchokedPeers);
    }
    lastChokeEvaluation = now;

    // DIAGNOSTIC: Log choking algorithm evaluation
    long interestedCount = peerStats.values().stream().filter(PeerStats::isPeerInterested).count();
    long chokedCount = peerStats.values().stream().filter(PeerStats::isChoked).count();
    logger.info("[CHOKER] Evaluating peers: total=%d, interested=%d, currently_unchoked=%d, choked=%d",
        peerStats.size(), interestedCount, unchokedPeers.size(), chokedCount);

    Set<String> newUnchoked = new HashSet<>();

    // Get interested peers (peers interested in us)
    List<PeerStats> interestedPeers = peerStats.values().stream()
        .filter(PeerStats::isPeerInterested)
        .collect(Collectors.toList());

    if (interestedPeers.isEmpty()) {
      logger.info("[CHOKER] No interested peers - clearing unchoked set");
      unchokedPeers.clear();
      return newUnchoked;
    }

    // Sort by download rate (tit-for-tat: reward peers who upload to us)
    // Higher download rate = they're giving us more data = we prioritize them
    List<PeerStats> sortedByDownload = new ArrayList<>(interestedPeers);
    sortedByDownload.sort(
        Comparator.comparingDouble(PeerStats::getDownloadRateBytesPerSec).reversed());

    // Unchoke top N-1 peers
    int regularSlots = Math.max(1, uploadSlots - 1);
    for (int i = 0; i < Math.min(regularSlots, sortedByDownload.size()); i++) {
      PeerStats peer = sortedByDownload.get(i);
      newUnchoked.add(peer.getPeerId());
      logger.info("[UNCHOKE] Peer %s (download rate: %.1f KB/s)",
          peer.getPeerId(), peer.getDownloadRateBytesPerSec() / 1024.0);
    }

    // Handle optimistic unchoke rotation
    boolean shouldRotate = (now - lastOptimisticRotation) >= OPTIMISTIC_UNCHOKE_INTERVAL_MS;

    if (shouldRotate || optimisticUnchokedPeer == null
        || !interestedPeers.stream().anyMatch(p -> p.getPeerId().equals(optimisticUnchokedPeer))) {
      // Select new optimistic unchoke peer
      List<String> candidates = interestedPeers.stream()
          .map(PeerStats::getPeerId)
          .filter(id -> !newUnchoked.contains(id))
          .collect(Collectors.toList());

      if (!candidates.isEmpty()) {
        String oldOptimistic = optimisticUnchokedPeer;
        optimisticUnchokedPeer = candidates.get(random.nextInt(candidates.size()));
        lastOptimisticRotation = now;
        logger.info("[OPTIMISTIC-UNCHOKE] Selected peer %s (was: %s, %d candidates)",
            optimisticUnchokedPeer, oldOptimistic, candidates.size());
      } else {
        optimisticUnchokedPeer = null;
      }
    }

    // Add optimistic unchoke
    if (optimisticUnchokedPeer != null && newUnchoked.size() < uploadSlots) {
      newUnchoked.add(optimisticUnchokedPeer);
    }

    // Update peer states and log choke decisions
    for (PeerStats stats : peerStats.values()) {
      boolean shouldUnchoke = newUnchoked.contains(stats.getPeerId());
      boolean wasChoked = stats.isChoked();
      stats.setChoked(!shouldUnchoke);

      // Log state changes
      if (wasChoked && !stats.isChoked()) {
        logger.info("[UNCHOKE] Peer %s", stats.getPeerId());
      } else if (!wasChoked && stats.isChoked()) {
        logger.info("[CHOKE] Peer %s", stats.getPeerId());
      }
    }

    unchokedPeers.clear();
    unchokedPeers.addAll(newUnchoked);

    logger.info("[CHOKER] Result: %d peers unchoked (including optimistic: %s)",
        newUnchoked.size(), optimisticUnchokedPeer);

    return newUnchoked;
  }

  /**
   * Get currently unchoked peers.
   *
   * @return set of unchoked peer IDs
   */
  public Set<String> getUnchokedPeers() {
    return new HashSet<>(unchokedPeers);
  }

  /**
   * Get the optimistically unchoked peer.
   *
   * @return peer ID or null
   */
  public String getOptimisticUnchokedPeer() {
    return optimisticUnchokedPeer;
  }

  /**
   * Set optimistic unchoked peer (for testing).
   *
   * @param peerId the peer ID
   */
  public void setOptimisticUnchokedPeer(String peerId) {
    this.optimisticUnchokedPeer = peerId;
  }

  /**
   * Get number of upload slots.
   *
   * @return upload slots
   */
  public int getUploadSlots() {
    return uploadSlots;
  }

  /**
   * Get number of active uploads (unchoked interested peers).
   *
   * @return count
   */
  public int getActiveUploadCount() {
    return (int) unchokedPeers.stream()
        .filter(id -> {
          PeerStats stats = peerStats.get(id);
          return stats != null && stats.isPeerInterested();
        })
        .count();
  }

  /**
   * Get total upload rate across all peers.
   *
   * @return bytes per second
   */
  public double getTotalUploadRate() {
    return peerStats.values().stream()
        .mapToDouble(PeerStats::getUploadRateBytesPerSec)
        .sum();
  }

  /**
   * Get total download rate across all peers.
   *
   * @return bytes per second
   */
  public double getTotalDownloadRate() {
    return peerStats.values().stream()
        .mapToDouble(PeerStats::getDownloadRateBytesPerSec)
        .sum();
  }

  /**
   * Get number of connected peers.
   *
   * @return peer count
   */
  public int getPeerCount() {
    return peerStats.size();
  }

  /**
   * Force optimistic unchoke rotation (for testing).
   */
  public void forceOptimisticRotation() {
    lastOptimisticRotation = 0;
  }

  @Override
  public String toString() {
    return String.format(
        "Choker[slots=%d, peers=%d, unchoked=%d, up=%.1f KB/s, down=%.1f KB/s]",
        uploadSlots,
        peerStats.size(),
        unchokedPeers.size(),
        getTotalUploadRate() / 1024.0,
        getTotalDownloadRate() / 1024.0);
  }
}
