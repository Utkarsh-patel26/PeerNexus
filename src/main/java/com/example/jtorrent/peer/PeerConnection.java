package com.example.jtorrent.peer;

import com.example.jtorrent.scheduler.BandwidthLimiter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP connection to a BitTorrent peer.
 */
public class PeerConnection implements AutoCloseable {

  private static final String PROTOCOL = "BitTorrent protocol";
  private static final int HANDSHAKE_SIZE = 68;

  private final byte[] infoHash;
  private final byte[] localPeerId;
  private Socket socket;
  private InputStream in;
  private OutputStream out;
  private byte[] remotePeerId;
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicBoolean handshakeComplete = new AtomicBoolean(false);
  private final CopyOnWriteArrayList<MessageListener> listeners = new CopyOnWriteArrayList<>();

  private final AtomicBoolean amChoking = new AtomicBoolean(true);
  private final AtomicBoolean amInterested = new AtomicBoolean(false);
  private final AtomicBoolean peerChoking = new AtomicBoolean(true);
  private final AtomicBoolean peerInterested = new AtomicBoolean(false);
  private final Object bitfieldLock = new Object();
  private java.util.BitSet peerBitfield = null;
  private volatile boolean peerIsSeeder = false;

  // Bandwidth limiter for rate limiting (optional)
  private BandwidthLimiter bandwidthLimiter = null;
  private String socketId = null; // Identifier for bandwidth tracking

  /**
   * Create a peer connection.
   *
   * @param infoHash    the 20-byte info hash
   * @param localPeerId the local 20-byte peer ID
   */
  public PeerConnection(byte[] infoHash, byte[] localPeerId) {
    if (infoHash == null || infoHash.length != 20) {
      throw new IllegalArgumentException("Info hash must be 20 bytes");
    }
    if (localPeerId == null || localPeerId.length != 20) {
      throw new IllegalArgumentException("Peer ID must be 20 bytes");
    }
    this.infoHash = infoHash.clone();
    this.localPeerId = localPeerId.clone();
  }

  /**
   * Add a message listener.
   *
   * @param listener the listener
   */
  public void addListener(MessageListener listener) {
    listeners.add(listener);
  }

  /**
   * Remove a message listener.
   *
   * @param listener the listener
   */
  public void removeListener(MessageListener listener) {
    listeners.remove(listener);
  }

  /**
   * Connect to a remote peer.
   *
   * @param address the peer address
   * @param timeout connection timeout in milliseconds
   * @throws IOException if connection fails
   */
  public void connect(InetSocketAddress address, int timeout) throws IOException {
    connect(address, timeout, 0); // No retries by default
  }

  /**
   * Connect to a remote peer with retry logic.
   *
   * @param address    the peer address
   * @param timeout    connection timeout in milliseconds
   * @param maxRetries maximum number of retries (3 recommended for metadata
   *                   exchange)
   * @throws IOException if all connection attempts fail
   */
  public void connect(InetSocketAddress address, int timeout, int maxRetries) throws IOException {
    IOException lastException = null;
    int attempts = 0;
    int maxAttempts = 1 + Math.max(0, maxRetries);

    while (attempts < maxAttempts) {
      try {
        socket = new Socket();
        socket.connect(address, timeout);
        socket.setSoTimeout(timeout);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        connected.set(true);
        return; // Success!

      } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
        lastException = e;
        attempts++;

        // Close the failed socket
        try {
          if (socket != null && !socket.isClosed()) {
            socket.close();
          }
        } catch (IOException ignored) {
          // Ignore close errors
        }
        socket = null;

        if (attempts < maxAttempts) {
          // Exponential backoff: 500ms, 1s, 2s
          int backoffMs = 500 * (1 << (attempts - 1));
          try {
            Thread.sleep(backoffMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection interrupted during retry backoff", ie);
          }
        }
      }
    }

    // All attempts failed
    throw new IOException("Failed to connect after " + attempts + " attempts", lastException);
  }

  /**
   * Update the socket read timeout.
   *
   * @param timeoutMs the new socket read timeout in milliseconds
   * @throws IOException if setting timeout fails
   */
  public void setSocketTimeout(int timeoutMs) throws IOException {
    if (socket != null && !socket.isClosed()) {
      socket.setSoTimeout(timeoutMs);
    }
  }

  /**
   * Accept a connection from a socket.
   *
   * @param acceptedSocket the accepted socket
   * @param timeout        socket timeout in milliseconds
   * @throws IOException if setup fails
   */
  public void accept(Socket acceptedSocket, int timeout) throws IOException {
    this.socket = acceptedSocket;
    socket.setSoTimeout(timeout);
    in = socket.getInputStream();
    out = socket.getOutputStream();
    connected.set(true);
  }

  /**
   * Perform the BitTorrent handshake.
   *
   * @throws IOException if handshake fails
   */
  public void handshake() throws IOException {
    if (!connected.get()) {
      throw new IOException("Not connected");
    }

    // Send handshake
    byte[] handshakeOut = buildHandshake();
    out.write(handshakeOut);
    out.flush();

    // Receive handshake
    byte[] handshakeIn = new byte[HANDSHAKE_SIZE];
    readFully(in, handshakeIn);

    // Validate handshake
    validateHandshake(handshakeIn);

    handshakeComplete.set(true);

    // Notify listeners
    for (MessageListener listener : listeners) {
      listener.onHandshakeComplete(remotePeerId);
    }
  }

  /**
   * Build the handshake message.
   *
   * @return handshake bytes
   */
  private byte[] buildHandshake() {
    ByteBuffer buf = ByteBuffer.allocate(HANDSHAKE_SIZE);
    buf.put((byte) PROTOCOL.length());
    buf.put(PROTOCOL.getBytes(StandardCharsets.US_ASCII));

    // Reserved bytes - set bit 20 (extension protocol support, BEP-10)
    byte[] reserved = new byte[8];
    reserved[5] = (byte) 0x10; // Bit 43 from the left = bit 20 from the right
    buf.put(reserved);

    buf.put(infoHash);
    buf.put(localPeerId);
    return buf.array();
  }

  /**
   * Validate the received handshake.
   *
   * @param handshake the received handshake
   * @throws IOException if validation fails
   */
  private byte[] peerReservedBytes;
  private boolean peerSupportsExtensions = false;

  private void validateHandshake(byte[] handshake) throws IOException {
    ByteBuffer buf = ByteBuffer.wrap(handshake);

    // Check protocol string length
    int protocolLen = buf.get() & 0xFF;
    if (protocolLen != PROTOCOL.length()) {
      throw new IOException("Invalid protocol string length: " + protocolLen);
    }

    // Check protocol string
    byte[] protocolBytes = new byte[protocolLen];
    buf.get(protocolBytes);
    String protocol = new String(protocolBytes, StandardCharsets.US_ASCII);
    if (!PROTOCOL.equals(protocol)) {
      throw new IOException("Invalid protocol: " + protocol);
    }

    // Read reserved bytes and check for extension support
    peerReservedBytes = new byte[8];
    buf.get(peerReservedBytes);
    peerSupportsExtensions = (peerReservedBytes[5] & 0x10) != 0;

    // Check info hash
    byte[] remoteInfoHash = new byte[20];
    buf.get(remoteInfoHash);
    if (!Arrays.equals(infoHash, remoteInfoHash)) {
      throw new IOException("Info hash mismatch");
    }

    // Get remote peer ID
    remotePeerId = new byte[20];
    buf.get(remotePeerId);
  }

  /**
   * Send a message to the peer.
   *
   * @param message the message to send
   * @throws IOException if sending fails
   */
  public void send(Message message) throws IOException {
    if (!connected.get()) {
      throw new IOException("Not connected");
    }
    if (!handshakeComplete.get()) {
      throw new IOException("Handshake not complete");
    }

    byte[] data = message.serialize();

    // Apply bandwidth limiting if configured
    if (bandwidthLimiter != null && socketId != null) {
      long bytesToSend = data.length;
      long allowed = bandwidthLimiter.requestUpload(socketId, bytesToSend);

      // If bandwidth is limited, we need to send in chunks
      int offset = 0;
      while (offset < data.length) {
        if (allowed <= 0) {
          // No tokens available - wait a bit and retry
          try {
            Thread.sleep(10); // 10ms wait
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for bandwidth", e);
          }
          allowed = bandwidthLimiter.requestUpload(socketId, bytesToSend - offset);
        }

        int chunkSize = (int) Math.min(allowed, data.length - offset);
        out.write(data, offset, chunkSize);
        offset += chunkSize;
        allowed = 0; // Request more tokens on next iteration
      }
    } else {
      // No bandwidth limiting - send directly
      out.write(data);
    }

    out.flush();

    // Update state based on message type
    switch (message.type()) {
      case Message.CHOKE:
        amChoking.set(true);
        break;
      case Message.UNCHOKE:
        amChoking.set(false);
        break;
      case Message.INTERESTED:
        amInterested.set(true);
        break;
      case Message.NOT_INTERESTED:
        amInterested.set(false);
        break;
      default:
        break;
    }
  }

  /**
   * Receive a single message from the peer.
   *
   * @return the received message, or null if no complete message available
   *         (timeout)
   * @throws IOException if receiving fails
   */
  public Message receive() throws IOException {
    if (!connected.get()) {
      throw new IOException("Not connected");
    }
    if (!handshakeComplete.get()) {
      throw new IOException("Handshake not complete");
    }

    try {
      // Read length prefix (4 bytes)
      byte[] lengthBytes = new byte[4];
      readFullyWithBandwidth(lengthBytes, 0, 4);

      int length = ByteBuffer.wrap(lengthBytes).getInt();

      if (length == 0) {
        // Keep-alive
        return new Message(Message.KEEP_ALIVE);
      }

      // Sanity check: reasonable message length (max 1MB for safety)
      if (length < 0 || length > 1_048_576) {
        throw new IOException("Invalid message length: " + length + " (stream out of sync)");
      }

      // Read message data
      byte[] messageData = new byte[length];
      readFullyWithBandwidth(messageData, 0, length);

      Message message = Message.deserialize(messageData);

      // Update state based on message type
      switch (message.type()) {
        case Message.CHOKE:
          peerChoking.set(true);
          break;
        case Message.UNCHOKE:
          peerChoking.set(false);
          break;
        case Message.INTERESTED:
          peerInterested.set(true);
          break;
        case Message.NOT_INTERESTED:
          peerInterested.set(false);
          break;
        default:
          break;
      }

      // Notify listeners
      for (MessageListener listener : listeners) {
        listener.onMessage(message);
      }

      return message;

    } catch (java.net.SocketTimeoutException e) {
      // Timeout is normal with short timeout settings
      return null;
    }
  }

  /**
   * Read data from input stream with bandwidth limiting support.
   * 
   * @param buffer the buffer to read into
   * @param offset the offset in buffer
   * @param length the number of bytes to read
   * @throws IOException if reading fails
   */
  private void readFullyWithBandwidth(byte[] buffer, int offset, int length) throws IOException {
    int totalRead = 0;

    while (totalRead < length) {
      int remaining = length - totalRead;

      // Apply bandwidth limiting if configured
      if (bandwidthLimiter != null && socketId != null) {
        long allowed = bandwidthLimiter.requestDownload(socketId, remaining);

        if (allowed <= 0) {
          // No tokens available - wait and retry
          try {
            Thread.sleep(10); // 10ms wait
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for bandwidth", e);
          }
          continue;
        }

        // Read up to allowed bytes
        int toRead = (int) Math.min(allowed, remaining);
        int bytesRead = in.read(buffer, offset + totalRead, toRead);

        if (bytesRead == -1) {
          throw new IOException("End of stream");
        }
        totalRead += bytesRead;
      } else {
        // No bandwidth limiting - read directly
        int bytesRead = in.read(buffer, offset + totalRead, remaining);
        if (bytesRead == -1) {
          throw new IOException("End of stream");
        }
        totalRead += bytesRead;
      }
    }
  }

  /**
   * Start a receive loop that calls listeners for each message.
   * This method blocks until the connection is closed.
   */
  public void receiveLoop() {
    try {
      while (connected.get() && socket != null && !socket.isClosed()) {
        try {
          receive();
        } catch (SocketTimeoutException e) {
          // Continue loop on timeout
        }
      }
    } catch (IOException e) {
      notifyDisconnect(e.getMessage());
    }
  }

  /**
   * Read exactly the specified number of bytes.
   *
   * @param input  the input stream
   * @param buffer the buffer to fill
   * @throws IOException if reading fails
   */
  private void readFully(InputStream input, byte[] buffer) throws IOException {
    int offset = 0;
    while (offset < buffer.length) {
      int bytesRead = input.read(buffer, offset, buffer.length - offset);
      if (bytesRead == -1) {
        throw new IOException("End of stream");
      }
      offset += bytesRead;
    }
  }

  private void notifyDisconnect(String reason) {
    for (MessageListener listener : listeners) {
      listener.onDisconnect(reason);
    }
  }

  /**
   * Check if handshake is complete.
   *
   * @return true if complete
   */
  public boolean isHandshakeComplete() {
    return handshakeComplete.get();
  }

  /**
   * Check if connected.
   *
   * @return true if connected
   */
  public boolean isConnected() {
    return connected.get() && socket != null && !socket.isClosed();
  }

  /**
   * Get the remote peer ID.
   *
   * @return remote peer ID or null
   */
  public byte[] remotePeerId() {
    return remotePeerId != null ? remotePeerId.clone() : null;
  }

  /**
   * Get local peer ID.
   *
   * @return local peer ID
   */
  public byte[] localPeerId() {
    return localPeerId.clone();
  }

  /**
   * Get info hash.
   *
   * @return info hash
   */
  public byte[] infoHash() {
    return infoHash.clone();
  }

  /**
   * Check if the peer supports the extension protocol (BEP-10).
   *
   * @return true if peer supports extensions
   */
  public boolean peerSupportsExtensions() {
    return peerSupportsExtensions;
  }

  /**
   * Check if we are choking the peer.
   *
   * @return true if choking
   */
  public boolean amChoking() {
    return amChoking.get();
  }

  /**
   * Check if we are interested in the peer.
   *
   * @return true if interested
   */
  public boolean amInterested() {
    return amInterested.get();
  }

  /**
   * Check if peer is choking us.
   *
   * @return true if choking
   */
  public boolean peerChoking() {
    return peerChoking.get();
  }

  /**
   * Check if peer is interested in us.
   *
   * @return true if interested
   */
  public boolean peerInterested() {
    return peerInterested.get();
  }

  /**
   * Get the peer's bitfield.
   *
   * @return peer bitfield or null if not received
   */
  public java.util.BitSet getPeerBitfield() {
    synchronized (bitfieldLock) {
      return peerBitfield != null ? (java.util.BitSet) peerBitfield.clone() : null;
    }
  }

  /**
   * Set the peer's bitfield and check if they are a seeder.
   *
   * @param bitfield    the peer's bitfield
   * @param totalPieces total number of pieces in torrent
   */
  public void setPeerBitfield(java.util.BitSet bitfield, int totalPieces) {
    synchronized (bitfieldLock) {
      this.peerBitfield = bitfield != null ? (java.util.BitSet) bitfield.clone() : null;
      // Check if peer has all pieces (is a seeder)
      if (bitfield != null) {
        this.peerIsSeeder = bitfield.cardinality() == totalPieces;
      }
    }
  }

  /**
   * Check if the peer is a seeder (has all pieces).
   *
   * @return true if peer is a seeder
   */
  public boolean isPeerSeeder() {
    return peerIsSeeder;
  }

  /**
   * Update peer bitfield after receiving a HAVE message.
   *
   * @param pieceIndex  the piece index the peer now has
   * @param totalPieces total number of pieces in torrent
   */
  public void updatePeerHasPiece(int pieceIndex, int totalPieces) {
    synchronized (bitfieldLock) {
      if (peerBitfield == null) {
        peerBitfield = new java.util.BitSet(totalPieces);
      }
      peerBitfield.set(pieceIndex);
      // Update seeder status
      this.peerIsSeeder = peerBitfield.cardinality() == totalPieces;
    }
  }

  /**
   * Set bandwidth limiter for rate limiting (Phase 1.1.3).
   * 
   * @param limiter  the bandwidth limiter
   * @param socketId unique socket identifier for tracking
   */
  public void setBandwidthLimiter(BandwidthLimiter limiter, String socketId) {
    this.bandwidthLimiter = limiter;
    this.socketId = socketId;
  }

  /**
   * Get the socket ID used for bandwidth tracking.
   * 
   * @return socket ID or null
   */
  public String getSocketId() {
    return socketId;
  }

  /**
   * Get the remote address of the connected peer.
   * 
   * @return the remote socket address, or null if not connected
   */
  public InetSocketAddress getRemoteAddress() {
    if (socket != null && socket.isConnected()) {
      return (InetSocketAddress) socket.getRemoteSocketAddress();
    }
    return null;
  }

  /**
   * Send a choke message to the peer.
   * 
   * @throws IOException if sending fails
   */
  public void sendChoke() throws IOException {
    send(new Message(Message.CHOKE));
  }

  /**
   * Send an unchoke message to the peer.
   * 
   * @throws IOException if sending fails
   */
  public void sendUnchoke() throws IOException {
    send(new Message(Message.UNCHOKE));
  }

  /**
   * Send an interested message to the peer.
   * 
   * @throws IOException if sending fails
   */
  public void sendInterested() throws IOException {
    send(new Message(Message.INTERESTED));
  }

  /**
   * Send a not interested message to the peer.
   * 
   * @throws IOException if sending fails
   */
  public void sendNotInterested() throws IOException {
    send(new Message(Message.NOT_INTERESTED));
  }

  /**
   * Send a keep-alive message to the peer.
   * 
   * @throws IOException if sending fails
   */
  public void sendKeepAlive() throws IOException {
    send(new Message(Message.KEEP_ALIVE));
  }

  @Override
  public void close() {
    connected.set(false);

    // Clean up bandwidth limiter tracking
    if (bandwidthLimiter != null && socketId != null) {
      bandwidthLimiter.removeSocket(socketId);
    }

    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        // Ignore
      }
    }
    notifyDisconnect("Connection closed");
  }
}
