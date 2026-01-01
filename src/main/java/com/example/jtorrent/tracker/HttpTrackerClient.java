package com.example.jtorrent.tracker;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP tracker client implementation.
 */
public class HttpTrackerClient implements TrackerClient {

  private final URI trackerUri;
  // For magnet link mode: short timeouts to avoid blocking (5 seconds total)
  private int connectTimeout = 3000; // 3 seconds to connect
  private int readTimeout = 2000; // 2 seconds to read response

  /**
   * Create an HTTP tracker client.
   *
   * @param trackerUri the tracker URI
   */
  public HttpTrackerClient(URI trackerUri) {
    this.trackerUri = trackerUri;
  }

  @Override
  public URI getTrackerUri() {
    return trackerUri;
  }

  @Override
  public void setConnectTimeout(int timeoutMs) {
    this.connectTimeout = timeoutMs;
  }

  @Override
  public void setReadTimeout(int timeoutMs) {
    this.readTimeout = timeoutMs;
  }

  @Override
  public AnnounceResponse announce(AnnounceRequest request) throws TrackerException {
    String url = buildAnnounceUrl(request);

    try {
      URL announceUrl = URI.create(url).toURL();
      HttpURLConnection conn = (HttpURLConnection) announceUrl.openConnection();
      conn.setConnectTimeout(connectTimeout);
      conn.setReadTimeout(readTimeout);
      conn.setRequestMethod("GET");
      conn.setRequestProperty("User-Agent", "JTorrent/1.0");

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        throw new TrackerException("HTTP error: " + responseCode);
      }

      byte[] responseData = readInputStream(conn.getInputStream());
      return parseResponse(responseData);

    } catch (IOException e) {
      throw new TrackerException("Failed to connect to tracker: " + e.getMessage(), e);
    } catch (BencodeException e) {
      throw new TrackerException("Failed to parse tracker response: " + e.getMessage(), e);
    }
  }

  /**
   * Build the announce URL with query parameters.
   *
   * @param request the announce request
   * @return the full URL string
   */
  String buildAnnounceUrl(AnnounceRequest request) {
    StringBuilder sb = new StringBuilder();
    sb.append(trackerUri.toString());

    // Add query separator
    if (trackerUri.getQuery() != null) {
      sb.append("&");
    } else {
      sb.append("?");
    }

    // Add parameters
    sb.append("info_hash=").append(urlEncode(request.infoHash()));
    sb.append("&peer_id=").append(urlEncode(request.peerId()));
    sb.append("&port=").append(request.port());
    sb.append("&uploaded=").append(request.uploaded());
    sb.append("&downloaded=").append(request.downloaded());
    sb.append("&left=").append(request.left());
    sb.append("&compact=1");

    if (request.event() != AnnounceRequest.Event.NONE) {
      sb.append("&event=").append(request.event().httpValue());
    }

    return sb.toString();
  }

  /**
   * URL encode a byte array (for info_hash and peer_id).
   *
   * @param data the bytes to encode
   * @return URL encoded string
   */
  static String urlEncode(byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (byte b : data) {
      int unsigned = b & 0xFF;
      if ((unsigned >= 'A' && unsigned <= 'Z')
          || (unsigned >= 'a' && unsigned <= 'z')
          || (unsigned >= '0' && unsigned <= '9')
          || unsigned == '-'
          || unsigned == '_'
          || unsigned == '.'
          || unsigned == '~') {
        sb.append((char) unsigned);
      } else {
        sb.append(String.format("%%%02X", unsigned));
      }
    }
    return sb.toString();
  }

  /**
   * Read all bytes from an input stream.
   *
   * @param in the input stream
   * @return the bytes read
   * @throws IOException if reading fails
   */
  private byte[] readInputStream(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
      out.write(buffer, 0, bytesRead);
    }
    return out.toByteArray();
  }

  /**
   * Parse the bencoded tracker response.
   *
   * @param data the response data
   * @return parsed announce response
   * @throws BencodeException if parsing fails
   */
  @SuppressWarnings("unchecked")
  AnnounceResponse parseResponse(byte[] data) throws BencodeException {
    BencodeParser parser = new BencodeParser(data);
    Object parsed = parser.parse();

    if (!(parsed instanceof Map)) {
      throw new BencodeException("Tracker response must be a dictionary");
    }

    Map<String, Object> dict = (Map<String, Object>) parsed;

    // Check for failure
    if (dict.containsKey("failure reason")) {
      byte[] failureBytes = (byte[]) dict.get("failure reason");
      String failureReason = new String(failureBytes, StandardCharsets.UTF_8);
      return new AnnounceResponse(failureReason);
    }

    // Extract required fields
    int interval = 1800; // Default interval
    if (dict.containsKey("interval")) {
      interval = ((Long) dict.get("interval")).intValue();
    }

    int leechers = 0;
    if (dict.containsKey("incomplete")) {
      leechers = ((Long) dict.get("incomplete")).intValue();
    }

    int seeders = 0;
    if (dict.containsKey("complete")) {
      seeders = ((Long) dict.get("complete")).intValue();
    }

    // Parse peers
    List<PeerEndpoint> peers = new ArrayList<>();
    if (dict.containsKey("peers")) {
      Object peersObj = dict.get("peers");
      if (peersObj instanceof byte[]) {
        // Compact format: 6 bytes per peer (4 IP + 2 port)
        peers = parseCompactPeers((byte[]) peersObj);
      } else if (peersObj instanceof List) {
        // Dictionary format
        peers = parseDictionaryPeers((List<Object>) peersObj);
      }
    }

    // Check for warning
    String warning = null;
    if (dict.containsKey("warning message")) {
      byte[] warningBytes = (byte[]) dict.get("warning message");
      warning = new String(warningBytes, StandardCharsets.UTF_8);
    }

    if (warning != null) {
      return new AnnounceResponse(interval, leechers, seeders, peers, warning);
    }
    return new AnnounceResponse(interval, leechers, seeders, peers);
  }

  /**
   * Parse compact peer format (6 bytes per peer).
   *
   * @param data the compact peer data
   * @return list of peer endpoints
   */
  List<PeerEndpoint> parseCompactPeers(byte[] data) {
    List<PeerEndpoint> peers = new ArrayList<>();

    if (data.length % 6 != 0) {
      return peers; // Invalid data
    }

    for (int i = 0; i < data.length; i += 6) {
      try {
        byte[] ipBytes = new byte[4];
        System.arraycopy(data, i, ipBytes, 0, 4);
        InetAddress ip = InetAddress.getByAddress(ipBytes);

        int port = ((data[i + 4] & 0xFF) << 8) | (data[i + 5] & 0xFF);

        peers.add(new PeerEndpoint(new InetSocketAddress(ip, port)));
      } catch (Exception e) {
        // Skip invalid peer
      }
    }

    return peers;
  }

  /**
   * Parse dictionary peer format.
   *
   * @param peersList the list of peer dictionaries
   * @return list of peer endpoints
   */
  @SuppressWarnings("unchecked")
  List<PeerEndpoint> parseDictionaryPeers(List<Object> peersList) {
    List<PeerEndpoint> peers = new ArrayList<>();

    for (Object peerObj : peersList) {
      if (!(peerObj instanceof Map)) {
        continue;
      }

      Map<String, Object> peerDict = (Map<String, Object>) peerObj;

      try {
        byte[] ipBytes = (byte[]) peerDict.get("ip");
        String ipStr = new String(ipBytes, StandardCharsets.UTF_8);
        int port = ((Long) peerDict.get("port")).intValue();

        InetAddress ip = InetAddress.getByName(ipStr);
        InetSocketAddress address = new InetSocketAddress(ip, port);

        byte[] peerId = null;
        if (peerDict.containsKey("peer id")) {
          peerId = (byte[]) peerDict.get("peer id");
        }

        peers.add(new PeerEndpoint(address, peerId));
      } catch (Exception e) {
        // Skip invalid peer
      }
    }

    return peers;
  }
}
