package com.example.jtorrent.dht;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Peer Exchange (PEX) - BEP 11.
 * Tracks peers that can be shared with other peers via extension messages.
 */
public class PexManager {

  /** Maximum peers to send in a single PEX message. */
  private static final int MAX_PEERS_PER_MESSAGE = 50;

  /** Minimum interval between PEX messages (milliseconds). */
  private static final long PEX_INTERVAL_MS = 60000; // 1 minute

  private final Set<InetSocketAddress> knownPeers;
  private final Set<InetSocketAddress> addedPeers;
  private final Set<InetSocketAddress> droppedPeers;
  private long lastPexSent;

  /**
   * Create a PEX manager.
   */
  public PexManager() {
    this.knownPeers = ConcurrentHashMap.newKeySet();
    this.addedPeers = ConcurrentHashMap.newKeySet();
    this.droppedPeers = ConcurrentHashMap.newKeySet();
    this.lastPexSent = 0;
  }

  /**
   * Add a peer to the known peers list.
   *
   * @param peer the peer address
   */
  public void addPeer(InetSocketAddress peer) {
    if (knownPeers.add(peer)) {
      addedPeers.add(peer);
      droppedPeers.remove(peer);
    }
  }

  /**
   * Remove a peer from the known peers list.
   *
   * @param peer the peer address
   */
  public void removePeer(InetSocketAddress peer) {
    if (knownPeers.remove(peer)) {
      droppedPeers.add(peer);
      addedPeers.remove(peer);
    }
  }

  /**
   * Get peers added since last PEX message.
   *
   * @return list of added peers
   */
  public List<InetSocketAddress> getAddedPeers() {
    return new ArrayList<>(addedPeers);
  }

  /**
   * Get peers dropped since last PEX message.
   *
   * @return list of dropped peers
   */
  public List<InetSocketAddress> getDroppedPeers() {
    return new ArrayList<>(droppedPeers);
  }

  /**
   * Check if it's time to send a PEX message.
   *
   * @return true if PEX should be sent
   */
  public boolean shouldSendPex() {
    long now = System.currentTimeMillis();
    return (now - lastPexSent) >= PEX_INTERVAL_MS
        && (!addedPeers.isEmpty() || !droppedPeers.isEmpty());
  }

  /**
   * Get a PEX message with added and dropped peers.
   *
   * @return PEX message
   */
  public PexMessage getPexMessage() {
    List<InetSocketAddress> added = new ArrayList<>(addedPeers);
    List<InetSocketAddress> dropped = new ArrayList<>(droppedPeers);

    // Limit message size
    if (added.size() > MAX_PEERS_PER_MESSAGE) {
      added = added.subList(0, MAX_PEERS_PER_MESSAGE);
    }
    if (dropped.size() > MAX_PEERS_PER_MESSAGE) {
      dropped = dropped.subList(0, MAX_PEERS_PER_MESSAGE);
    }

    PexMessage message = new PexMessage(added, dropped);

    // Clear sent peers
    addedPeers.clear();
    droppedPeers.clear();
    lastPexSent = System.currentTimeMillis();

    return message;
  }

  /**
   * Handle incoming PEX message from a peer.
   *
   * @param message the PEX message
   * @return list of new peers to try connecting to
   */
  public List<InetSocketAddress> handlePexMessage(PexMessage message) {
    List<InetSocketAddress> newPeers = new ArrayList<>();

    // Add new peers (but don't share them back - they came from PEX)
    for (InetSocketAddress peer : message.getAdded()) {
      if (knownPeers.add(peer)) {
        newPeers.add(peer);
      }
    }

    // Remove dropped peers
    for (InetSocketAddress peer : message.getDropped()) {
      knownPeers.remove(peer);
    }

    return newPeers;
  }

  /**
   * Get all known peers.
   *
   * @return set of known peers
   */
  public Set<InetSocketAddress> getKnownPeers() {
    return new HashSet<>(knownPeers);
  }

  /**
   * Get number of known peers.
   *
   * @return peer count
   */
  public int getPeerCount() {
    return knownPeers.size();
  }

  /**
   * PEX message containing added and dropped peers.
   */
  public static class PexMessage {
    private final List<InetSocketAddress> added;
    private final List<InetSocketAddress> dropped;

    /**
     * Create a PEX message.
     *
     * @param added peers added
     * @param dropped peers dropped
     */
    public PexMessage(List<InetSocketAddress> added, List<InetSocketAddress> dropped) {
      this.added = new ArrayList<>(added);
      this.dropped = new ArrayList<>(dropped);
    }

    /**
     * Get added peers.
     *
     * @return list of added peers
     */
    public List<InetSocketAddress> getAdded() {
      return new ArrayList<>(added);
    }

    /**
     * Get dropped peers.
     *
     * @return list of dropped peers
     */
    public List<InetSocketAddress> getDropped() {
      return new ArrayList<>(dropped);
    }

    /**
     * Encode peers as compact format (6 bytes per peer).
     *
     * @param peers list of peers
     * @return compact encoded peers
     */
    public static byte[] encodeCompactPeers(List<InetSocketAddress> peers) {
      byte[] result = new byte[peers.size() * 6];
      int offset = 0;

      for (InetSocketAddress peer : peers) {
        byte[] ip = peer.getAddress().getAddress();
        System.arraycopy(ip, 0, result, offset, 4);
        int port = peer.getPort();
        result[offset + 4] = (byte) (port >> 8);
        result[offset + 5] = (byte) port;
        offset += 6;
      }

      return result;
    }

    /**
     * Decode compact peers (6 bytes per peer).
     *
     * @param compact compact encoded peers
     * @return list of peers
     */
    public static List<InetSocketAddress> decodeCompactPeers(byte[] compact) {
      if (compact.length % 6 != 0) {
        throw new IllegalArgumentException("Invalid compact peer data");
      }

      List<InetSocketAddress> peers = new ArrayList<>();
      for (int i = 0; i < compact.length; i += 6) {
        try {
          byte[] ip = new byte[4];
          System.arraycopy(compact, i, ip, 0, 4);
          int port = ((compact[i + 4] & 0xFF) << 8) | (compact[i + 5] & 0xFF);
          peers.add(new InetSocketAddress(java.net.InetAddress.getByAddress(ip), port));
        } catch (Exception e) {
          // Skip invalid peer
        }
      }

      return peers;
    }

    @Override
    public String toString() {
      return String.format("PexMessage[added=%d, dropped=%d]", added.size(), dropped.size());
    }
  }
}
