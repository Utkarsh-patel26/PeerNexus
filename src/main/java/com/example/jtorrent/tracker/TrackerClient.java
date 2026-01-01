package com.example.jtorrent.tracker;

import java.net.URI;

/**
 * Common interface for tracker client implementations.
 * Supports both HTTP/HTTPS and UDP tracker protocols.
 */
public interface TrackerClient extends AutoCloseable {

  /**
   * Send an announce request to the tracker.
   *
   * @param request the announce request
   * @return the announce response
   * @throws TrackerException if communication fails
   */
  AnnounceResponse announce(AnnounceRequest request) throws TrackerException;

  /**
   * Get the tracker URI.
   *
   * @return the tracker URI
   */
  URI getTrackerUri();

  /**
   * Set connection timeout in milliseconds.
   *
   * @param timeoutMs timeout in milliseconds
   */
  void setConnectTimeout(int timeoutMs);

  /**
   * Set read timeout in milliseconds.
   *
   * @param timeoutMs timeout in milliseconds
   */
  void setReadTimeout(int timeoutMs);

  /**
   * Close the tracker client and release resources.
   * Default implementation does nothing; override if cleanup is needed.
   */
  @Override
  default void close() {
    // Default: no cleanup needed
  }

  /**
   * Factory method to create appropriate tracker client based on URI scheme.
   *
   * @param trackerUri the tracker URI
   * @return appropriate tracker client implementation
   * @throws IllegalArgumentException if URI scheme is not supported
   */
  static TrackerClient create(URI trackerUri) {
    String scheme = trackerUri.getScheme();
    if (scheme == null) {
      throw new IllegalArgumentException("Tracker URI must have a scheme");
    }

    return switch (scheme.toLowerCase()) {
      case "http", "https" -> new HttpTrackerClient(trackerUri);
      case "udp" -> new UdpTrackerClient(trackerUri);
      default -> throw new IllegalArgumentException("Unsupported tracker scheme: " + scheme);
    };
  }
}
