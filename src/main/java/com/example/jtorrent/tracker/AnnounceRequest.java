package com.example.jtorrent.tracker;

/**
 * Tracker announce request parameters.
 */
public class AnnounceRequest {

  private final byte[] infoHash;
  private final byte[] peerId;
  private final int port;
  private final long uploaded;
  private final long downloaded;
  private final long left;
  private final Event event;

  /**
   * Event types for announce requests.
   */
  public enum Event {
    NONE(0, ""),
    COMPLETED(1, "completed"),
    STARTED(2, "started"),
    STOPPED(3, "stopped");

    private final int udpValue;
    private final String httpValue;

    Event(int udpValue, String httpValue) {
      this.udpValue = udpValue;
      this.httpValue = httpValue;
    }

    public int udpValue() {
      return udpValue;
    }

    public String httpValue() {
      return httpValue;
    }
  }

  public AnnounceRequest(
      byte[] infoHash,
      byte[] peerId,
      int port,
      long uploaded,
      long downloaded,
      long left,
      Event event) {
    if (infoHash == null || infoHash.length != 20) {
      throw new IllegalArgumentException("Info hash must be 20 bytes");
    }
    if (peerId == null || peerId.length != 20) {
      throw new IllegalArgumentException("Peer ID must be 20 bytes");
    }
    this.infoHash = infoHash.clone();
    this.peerId = peerId.clone();
    this.port = port;
    this.uploaded = uploaded;
    this.downloaded = downloaded;
    this.left = left;
    this.event = event != null ? event : Event.NONE;
  }

  public byte[] infoHash() {
    return infoHash.clone();
  }

  public byte[] peerId() {
    return peerId.clone();
  }

  public int port() {
    return port;
  }

  public long uploaded() {
    return uploaded;
  }

  public long downloaded() {
    return downloaded;
  }

  public long left() {
    return left;
  }

  public Event event() {
    return event;
  }
}
