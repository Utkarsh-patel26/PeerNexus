package com.example.jtorrent.tracker;

import java.util.Collections;
import java.util.List;

/**
 * Tracker announce response.
 */
public class AnnounceResponse {

  private final int interval;
  private final int leechers;
  private final int seeders;
  private final List<PeerEndpoint> peers;
  private final String failureReason;
  private final String warningMessage;

  public AnnounceResponse(int interval, int leechers, int seeders, List<PeerEndpoint> peers) {
    this.interval = interval;
    this.leechers = leechers;
    this.seeders = seeders;
    this.peers = peers != null ? List.copyOf(peers) : Collections.emptyList();
    this.failureReason = null;
    this.warningMessage = null;
  }

  public AnnounceResponse(
      int interval,
      int leechers,
      int seeders,
      List<PeerEndpoint> peers,
      String warningMessage) {
    this.interval = interval;
    this.leechers = leechers;
    this.seeders = seeders;
    this.peers = peers != null ? List.copyOf(peers) : Collections.emptyList();
    this.failureReason = null;
    this.warningMessage = warningMessage;
  }

  public AnnounceResponse(String failureReason) {
    this.interval = 0;
    this.leechers = 0;
    this.seeders = 0;
    this.peers = Collections.emptyList();
    this.failureReason = failureReason;
    this.warningMessage = null;
  }

  public boolean isFailure() {
    return failureReason != null;
  }

  public int interval() {
    return interval;
  }

  public int leechers() {
    return leechers;
  }

  public int seeders() {
    return seeders;
  }

  public List<PeerEndpoint> peers() {
    return peers;
  }

  public String failureReason() {
    return failureReason;
  }

  public String warningMessage() {
    return warningMessage;
  }

  @Override
  public String toString() {
    if (isFailure()) {
      return "AnnounceResponse{failure=" + failureReason + "}";
    }
    return "AnnounceResponse{"
        + "interval="
        + interval
        + ", leechers="
        + leechers
        + ", seeders="
        + seeders
        + ", peers="
        + peers.size()
        + "}";
  }
}
