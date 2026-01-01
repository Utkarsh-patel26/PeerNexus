package com.example.jtorrent.tracker;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * UDP tracker client implementation per BEP 15.
 */
public class UdpTrackerClient implements TrackerClient {

  private static final long PROTOCOL_ID = 0x41727101980L;
  private static final int ACTION_CONNECT = 0;
  private static final int ACTION_ANNOUNCE = 1;
  private static final int ACTION_ERROR = 3;

  private static final int CONNECT_REQUEST_SIZE = 16;
  private static final int CONNECT_RESPONSE_SIZE = 16;
  private static final int ANNOUNCE_REQUEST_SIZE = 98;
  private static final int ANNOUNCE_RESPONSE_MIN_SIZE = 20;

  // For magnet link mode: 2 retries max, 2 seconds each = 4 seconds total per
  // tracker
  private static final int MAX_RETRIES = 2;
  private static final int INITIAL_TIMEOUT_MS = 2000;

  private final URI trackerUri;
  private final SecureRandom random = new SecureRandom();

  private long connectionId;
  private long connectionExpiry;
  @SuppressWarnings("unused") // Used by setConnectTimeout() required by interface
  private int connectTimeout = INITIAL_TIMEOUT_MS;
  private int readTimeout = INITIAL_TIMEOUT_MS;

  /**
   * Create a UDP tracker client.
   *
   * @param trackerUri the tracker URI (udp://host:port/announce)
   */
  public UdpTrackerClient(URI trackerUri) {
    this.trackerUri = trackerUri;
    this.connectionId = 0;
    this.connectionExpiry = 0;
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
    try (DatagramSocket socket = new DatagramSocket()) {
      InetAddress trackerAddress = InetAddress.getByName(trackerUri.getHost());
      int trackerPort = trackerUri.getPort();
      if (trackerPort == -1) {
        trackerPort = 6969; // Default UDP tracker port
      }

      // Ensure we have a valid connection ID
      if (System.currentTimeMillis() >= connectionExpiry) {
        connect(socket, trackerAddress, trackerPort);
      }

      // Send announce request
      return sendAnnounce(socket, trackerAddress, trackerPort, request);

    } catch (IOException e) {
      throw new TrackerException("UDP tracker communication failed: " + e.getMessage(), e);
    }
  }

  /**
   * Establish a connection with the tracker.
   *
   * @param socket  the datagram socket
   * @param address tracker address
   * @param port    tracker port
   * @throws TrackerException if connection fails
   */
  private void connect(DatagramSocket socket, InetAddress address, int port)
      throws TrackerException, IOException {

    int transactionId = random.nextInt();

    // Build connect request
    ByteBuffer request = ByteBuffer.allocate(CONNECT_REQUEST_SIZE);
    request.putLong(PROTOCOL_ID);
    request.putInt(ACTION_CONNECT);
    request.putInt(transactionId);

    byte[] responseData = sendWithRetry(socket, address, port, request.array(),
        CONNECT_RESPONSE_SIZE);

    // Parse connect response
    ByteBuffer response = ByteBuffer.wrap(responseData);
    int action = response.getInt();
    int responseTransactionId = response.getInt();
    final long receivedConnectionId = response.getLong();

    if (action == ACTION_ERROR) {
      throw new TrackerException("Tracker returned error during connect");
    }

    if (action != ACTION_CONNECT) {
      throw new TrackerException("Invalid connect response action: " + action);
    }

    if (responseTransactionId != transactionId) {
      throw new TrackerException("Transaction ID mismatch");
    }

    this.connectionId = receivedConnectionId;
    // Connection ID valid for 1 minute
    this.connectionExpiry = System.currentTimeMillis() + 60000;
  }

  /**
   * Send announce request to tracker.
   *
   * @param socket  the datagram socket
   * @param address tracker address
   * @param port    tracker port
   * @param request the announce request
   * @return the announce response
   * @throws TrackerException if announce fails
   */
  private AnnounceResponse sendAnnounce(
      DatagramSocket socket, InetAddress address, int port, AnnounceRequest request)
      throws TrackerException, IOException {

    int transactionId = random.nextInt();

    // Build announce request
    ByteBuffer buffer = ByteBuffer.allocate(ANNOUNCE_REQUEST_SIZE);
    buffer.putLong(connectionId);
    buffer.putInt(ACTION_ANNOUNCE);
    buffer.putInt(transactionId);
    buffer.put(request.infoHash());
    buffer.put(request.peerId());
    buffer.putLong(request.downloaded());
    buffer.putLong(request.left());
    buffer.putLong(request.uploaded());
    buffer.putInt(request.event().udpValue());
    buffer.putInt(0); // IP address (0 = use source IP)
    buffer.putInt(random.nextInt()); // Key (random)
    buffer.putInt(-1); // num_want (-1 = default)
    buffer.putShort((short) request.port());

    byte[] responseData = sendWithRetry(socket, address, port, buffer.array(),
        ANNOUNCE_RESPONSE_MIN_SIZE);

    // Parse announce response
    return parseAnnounceResponse(responseData, transactionId);
  }

  /**
   * Send a packet with retry logic per BEP 15.
   *
   * @param socket          the datagram socket
   * @param address         tracker address
   * @param port            tracker port
   * @param requestData     the request data
   * @param expectedMinSize minimum expected response size
   * @return the response data
   * @throws TrackerException if all retries fail
   */
  private byte[] sendWithRetry(
      DatagramSocket socket, InetAddress address, int port, byte[] requestData, int expectedMinSize)
      throws TrackerException, IOException {

    DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, address, port);

    byte[] responseBuffer = new byte[2048];
    DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);

    for (int n = 0; n < MAX_RETRIES; n++) {
      // Use configured timeout
      socket.setSoTimeout(readTimeout);

      try {
        socket.send(requestPacket);
        socket.receive(responsePacket);

        if (responsePacket.getLength() >= expectedMinSize) {
          byte[] data = new byte[responsePacket.getLength()];
          System.arraycopy(responseBuffer, 0, data, 0, data.length);
          return data;
        }

        throw new TrackerException(
            "Response too short: " + responsePacket.getLength() + " < " + expectedMinSize);

      } catch (SocketTimeoutException e) {
        // Retry with longer timeout
        if (n == MAX_RETRIES - 1) {
          throw new TrackerException("Tracker request timed out after " + MAX_RETRIES + " retries");
        }
      }
    }

    throw new TrackerException("Failed to communicate with tracker");
  }

  /**
   * Parse the UDP announce response.
   *
   * @param data                  the response data
   * @param expectedTransactionId the expected transaction ID
   * @return the announce response
   * @throws TrackerException if parsing fails
   */
  private AnnounceResponse parseAnnounceResponse(byte[] data, int expectedTransactionId)
      throws TrackerException {

    if (data.length < ANNOUNCE_RESPONSE_MIN_SIZE) {
      throw new TrackerException("Announce response too short");
    }

    ByteBuffer buffer = ByteBuffer.wrap(data);
    int action = buffer.getInt();
    int transactionId = buffer.getInt();

    if (transactionId != expectedTransactionId) {
      throw new TrackerException("Transaction ID mismatch in announce response");
    }

    if (action == ACTION_ERROR) {
      byte[] errorBytes = new byte[data.length - 8];
      buffer.get(errorBytes);
      String errorMessage = new String(errorBytes).trim();
      return new AnnounceResponse("Tracker error: " + errorMessage);
    }

    if (action != ACTION_ANNOUNCE) {
      throw new TrackerException("Invalid announce response action: " + action);
    }

    int interval = buffer.getInt();
    int leechers = buffer.getInt();
    int seeders = buffer.getInt();

    // Parse peers (6 bytes each: 4 IP + 2 port)
    List<PeerEndpoint> peers = new ArrayList<>();
    while (buffer.remaining() >= 6) {
      byte[] ipBytes = new byte[4];
      buffer.get(ipBytes);
      int port = buffer.getShort() & 0xFFFF;

      try {
        InetAddress ip = InetAddress.getByAddress(ipBytes);
        peers.add(new PeerEndpoint(new InetSocketAddress(ip, port)));
      } catch (Exception e) {
        // Skip invalid peer
      }
    }

    return new AnnounceResponse(interval, leechers, seeders, peers);
  }

  /**
   * Get the current connection ID (for testing).
   *
   * @return the connection ID
   */
  long getConnectionId() {
    return connectionId;
  }
}
