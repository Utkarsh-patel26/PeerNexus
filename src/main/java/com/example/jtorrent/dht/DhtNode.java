package com.example.jtorrent.dht;

import com.example.jtorrent.parser.BencodeParser;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal Kademlia DHT node implementing BEP 5.
 * Phase 1.2.2: Enhanced with bootstrap retry and fallback support.
 */
public class DhtNode implements AutoCloseable {

  private static final int K = 8; // K-bucket size
  private static final int ALPHA = 3; // Parallel lookup count
  private static final int DHT_PORT = 6881;
  private static final int MAX_PACKET_SIZE = 8192;

  // Fallback bootstrap nodes (public DHT routers)
  private static final List<String> FALLBACK_BOOTSTRAP_NODES = List.of(
      "router.bittorrent.com:6881",
      "dht.transmissionbt.com:6881",
      "router.utorrent.com:6881",
      "dht.libtorrent.org:25401");

  private final byte[] nodeId;
  private final DatagramSocket socket;
  private final Map<String, DhtPeer> routingTable;
  private final Map<String, List<InetSocketAddress>> peerStorage;
  private final Map<String, PendingRequest> pendingRequests;
  private final AtomicBoolean running;
  private final Thread receiveThread;

  /**
   * Create a DHT node.
   *
   * @param port UDP port to listen on
   * @throws SocketException if socket cannot be created
   */
  public DhtNode(int port) throws SocketException {
    this.nodeId = generateNodeId();
    this.socket = new DatagramSocket(port);
    this.routingTable = new ConcurrentHashMap<>();
    this.peerStorage = new ConcurrentHashMap<>();
    this.pendingRequests = new ConcurrentHashMap<>();
    this.running = new AtomicBoolean(true);
    this.receiveThread = new Thread(this::receiveLoop, "DHT-Receive");
    this.receiveThread.setDaemon(true);
    this.receiveThread.start();
  }

  /**
   * Create a DHT node on default port.
   *
   * @throws SocketException if socket cannot be created
   */
  public DhtNode() throws SocketException {
    this(DHT_PORT);
  }

  /**
   * Generate a random 20-byte node ID.
   *
   * @return node ID
   */
  private static byte[] generateNodeId() {
    byte[] id = new byte[20];
    new SecureRandom().nextBytes(id);
    return id;
  }

  /**
   * Get node ID.
   *
   * @return 20-byte node ID
   */
  public byte[] getNodeId() {
    return nodeId;
  }

  /**
   * Get listening port.
   *
   * @return UDP port
   */
  public int getPort() {
    return socket.getLocalPort();
  }

  /**
   * Get fallback bootstrap nodes.
   * Used when user-provided bootstrap nodes fail.
   *
   * @return list of fallback bootstrap node addresses
   */
  public static List<InetSocketAddress> getFallbackBootstrapNodes() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    for (String nodeStr : FALLBACK_BOOTSTRAP_NODES) {
      try {
        String[] parts = nodeStr.split(":");
        if (parts.length == 2) {
          String host = parts[0];
          int port = Integer.parseInt(parts[1]);
          nodes.add(new InetSocketAddress(host, port));
        }
      } catch (Exception e) {
        System.err.println("[DHT] Failed to parse fallback node: " + nodeStr);
      }
    }
    return nodes;
  }

  /**
   * Get list of working DHT nodes from routing table.
   * Can be persisted for faster bootstrap on next launch.
   *
   * @return list of known working node addresses
   */
  public List<InetSocketAddress> getWorkingNodes() {
    List<InetSocketAddress> workingNodes = new ArrayList<>();
    for (DhtPeer peer : routingTable.values()) {
      if (peer != null && peer.address != null) {
        workingNodes.add(peer.address);
      }
    }
    return workingNodes;
  }

  /**
   * Bootstrap from known DHT nodes with retry and fallback.
   * Implements Phase 1.2.2: DHT Bootstrap Failure Handling
   *
   * @param bootstrapNodes list of bootstrap node addresses
   * @return true if at least one node responded
   */
  public boolean bootstrap(List<InetSocketAddress> bootstrapNodes) {
    List<InetSocketAddress> nodesToTry = new ArrayList<>();

    // Add user-provided nodes first
    if (bootstrapNodes != null && !bootstrapNodes.isEmpty()) {
      nodesToTry.addAll(bootstrapNodes);
    }

    // Add fallback nodes
    nodesToTry.addAll(getFallbackBootstrapNodes());

    if (nodesToTry.isEmpty()) {
      System.err.println("[DHT] No bootstrap nodes available");
      return false;
    }

    int maxAttempts = 5;
    int[] backoffDelays = { 1000, 2000, 4000, 8000, 16000 }; // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    int successCount = 0;

    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      if (attempt > 0) {
        System.out.println("[DHT] Bootstrap attempt " + (attempt + 1) + "/" + maxAttempts);
        try {
          Thread.sleep(backoffDelays[attempt - 1]);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          System.err.println("[DHT] Bootstrap interrupted");
          return successCount > 0;
        }
      }

      // Try each bootstrap node
      for (InetSocketAddress node : nodesToTry) {
        try {
          if (ping(node)) {
            successCount++;
            System.out.println("[DHT] Successfully contacted bootstrap node: " + node);
          }
        } catch (Exception e) {
          // Silent failure, continue to next node
        }
      }

      if (successCount > 0) {
        System.out.println("[DHT] Bootstrap successful, " + successCount + " node(s) responded");
        return true;
      }
    }

    System.err.println("[DHT] WARNING: All bootstrap attempts failed. DHT will not function.");
    System.err.println("[DHT] Possible causes: Network connectivity, firewall blocking UDP port " + getPort());
    System.err.println("[DHT] You can manually add working DHT nodes via the GUI");
    return false;
  }

  /**
   * Find peers for an info hash.
   *
   * @param infoHash 20-byte info hash
   * @return list of peer addresses
   */
  public List<InetSocketAddress> getPeers(byte[] infoHash) {
    String infoHashKey = bytesToHex(infoHash);

    // Check local storage first
    List<InetSocketAddress> localPeers = peerStorage.get(infoHashKey);
    if (localPeers != null && !localPeers.isEmpty()) {
      return new ArrayList<>(localPeers);
    }

    // Query DHT nodes
    List<DhtPeer> closestNodes = getClosestNodes(infoHash, ALPHA);
    List<InetSocketAddress> peers = new ArrayList<>();

    for (DhtPeer node : closestNodes) {
      try {
        List<InetSocketAddress> nodePeers = sendGetPeers(node.address, infoHash);
        peers.addAll(nodePeers);
      } catch (Exception e) {
        // Node didn't respond, continue
      }
    }

    return peers;
  }

  /**
   * Announce that we have a torrent.
   *
   * @param infoHash 20-byte info hash
   * @param port     our listening port
   */
  public void announcePeer(byte[] infoHash, int port) {
    List<DhtPeer> closestNodes = getClosestNodes(infoHash, K);

    for (DhtPeer node : closestNodes) {
      try {
        sendAnnouncePeer(node.address, infoHash, port);
      } catch (Exception e) {
        // Node didn't respond, continue
      }
    }
  }

  /**
   * Ping a node to check if it's alive.
   *
   * @param address node address
   * @return true if node responded
   */
  public boolean ping(InetSocketAddress address) {
    try {
      Map<String, Object> query = new HashMap<>();
      query.put("y", "q");
      query.put("q", "ping");
      Map<String, Object> args = new HashMap<>();
      args.put("id", nodeId);
      query.put("a", args);

      String txnId = generateTransactionId();
      query.put("t", txnId.getBytes());

      byte[] encoded = BencodeParser.encode(query);
      DatagramPacket packet = new DatagramPacket(encoded, encoded.length, address);
      socket.send(packet);

      // Wait for response (simplified - in production use proper timeout)
      Thread.sleep(1000);
      return pendingRequests.containsKey(txnId);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Send get_peers query.
   *
   * @param address  node address
   * @param infoHash 20-byte info hash
   * @return list of peers
   */
  private List<InetSocketAddress> sendGetPeers(InetSocketAddress address, byte[] infoHash)
      throws Exception {
    Map<String, Object> query = new HashMap<>();
    query.put("y", "q");
    query.put("q", "get_peers");
    Map<String, Object> args = new HashMap<>();
    args.put("id", nodeId);
    args.put("info_hash", infoHash);
    query.put("a", args);

    String txnId = generateTransactionId();
    query.put("t", txnId.getBytes());

    PendingRequest request = new PendingRequest();
    pendingRequests.put(txnId, request);

    byte[] encoded = BencodeParser.encode(query);
    DatagramPacket packet = new DatagramPacket(encoded, encoded.length, address);
    socket.send(packet);

    // Wait for response (simplified)
    synchronized (request) {
      try {
        request.wait(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    pendingRequests.remove(txnId);
    return request.peers != null ? request.peers : new ArrayList<>();
  }

  /**
   * Send announce_peer message.
   *
   * @param address  node address
   * @param infoHash 20-byte info hash
   * @param port     our listening port
   */
  private void sendAnnouncePeer(InetSocketAddress address, byte[] infoHash, int port)
      throws Exception {
    Map<String, Object> query = new HashMap<>();
    query.put("y", "q");
    query.put("q", "announce_peer");
    Map<String, Object> args = new HashMap<>();
    args.put("id", nodeId);
    args.put("info_hash", infoHash);
    args.put("port", (long) port);
    args.put("token", "dummy".getBytes()); // Should use token from get_peers response
    query.put("a", args);

    String txnId = generateTransactionId();
    query.put("t", txnId.getBytes());

    byte[] encoded = BencodeParser.encode(query);
    DatagramPacket packet = new DatagramPacket(encoded, encoded.length, address);
    socket.send(packet);
  }

  /**
   * Receive loop for incoming DHT messages.
   */
  private void receiveLoop() {
    byte[] buffer = new byte[MAX_PACKET_SIZE];

    while (running.get()) {
      try {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        byte[] data = new byte[packet.getLength()];
        System.arraycopy(buffer, 0, data, 0, packet.getLength());

        InetSocketAddress sender = new InetSocketAddress(
            packet.getAddress(), packet.getPort());

        handleMessage(data, sender);
      } catch (IOException e) {
        if (running.get()) {
          // Only log if we're still running
          System.err.println("DHT receive error: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Handle incoming DHT message.
   *
   * @param data   message data
   * @param sender sender address
   */
  @SuppressWarnings("unchecked")
  private void handleMessage(byte[] data, InetSocketAddress sender) {
    try {
      BencodeParser parser = new BencodeParser(data);
      Object parsed = parser.parse();

      if (!(parsed instanceof Map)) {
        return;
      }

      Map<String, Object> dict = (Map<String, Object>) parsed;
      Object yObj = dict.get("y");
      if (yObj == null) {
        return;
      }

      String messageType = new String((byte[]) yObj);

      switch (messageType) {
        case "q":
          handleQuery(dict, sender);
          break;
        case "r":
          handleResponse(dict, sender);
          break;
        case "e":
          handleError(dict, sender);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      // Ignore malformed messages
    }
  }

  /**
   * Handle query message.
   */
  @SuppressWarnings("unchecked") // Needed for bencode Map<String, Object> casts
  private void handleQuery(Map<String, Object> dict, InetSocketAddress sender) {
    try {
      String queryType = new String((byte[]) dict.get("q"));
      byte[] txnId = (byte[]) dict.get("t");

      switch (queryType) {
        case "ping":
          sendPingResponse(sender, txnId);
          break;
        case "get_peers":
          handleGetPeersQuery(dict, sender, txnId);
          break;
        case "announce_peer":
          handleAnnouncePeerQuery(dict, sender, txnId);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * Handle response message.
   */
  @SuppressWarnings("unchecked")
  private void handleResponse(Map<String, Object> dict, InetSocketAddress sender) {
    try {
      byte[] txnId = (byte[]) dict.get("t");
      String txnIdStr = new String(txnId);

      PendingRequest request = pendingRequests.get(txnIdStr);
      if (request != null) {
        Map<String, Object> response = (Map<String, Object>) dict.get("r");

        // Check for peers in response
        Object valuesObj = response.get("values");
        if (valuesObj instanceof List) {
          request.peers = decodePeerList((List<Object>) valuesObj);
        }

        synchronized (request) {
          request.notify();
        }
      }

      // Add sender to routing table
      Map<String, Object> response = (Map<String, Object>) dict.get("r");
      byte[] senderId = (byte[]) response.get("id");
      addToRoutingTable(senderId, sender);
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * Handle error message.
   */
  private void handleError(Map<String, Object> dict, InetSocketAddress sender) {
    // Just ignore errors for now
  }

  /**
   * Send ping response.
   */
  private void sendPingResponse(InetSocketAddress address, byte[] txnId) throws Exception {
    Map<String, Object> response = new HashMap<>();
    response.put("y", "r");
    response.put("t", txnId);
    Map<String, Object> r = new HashMap<>();
    r.put("id", nodeId);
    response.put("r", r);

    byte[] encoded = BencodeParser.encode(response);
    DatagramPacket packet = new DatagramPacket(encoded, encoded.length, address);
    socket.send(packet);
  }

  /**
   * Handle get_peers query.
   */
  @SuppressWarnings("unchecked")
  private void handleGetPeersQuery(
      Map<String, Object> dict, InetSocketAddress sender, byte[] txnId) {
    try {
      Map<String, Object> args = (Map<String, Object>) dict.get("a");
      byte[] infoHash = (byte[]) args.get("info_hash");
      String infoHashKey = bytesToHex(infoHash);

      Map<String, Object> response = new HashMap<>();
      response.put("y", "r");
      response.put("t", txnId);
      Map<String, Object> r = new HashMap<>();
      r.put("id", nodeId);
      r.put("token", "dummy".getBytes());

      // Return peers if we have them
      List<InetSocketAddress> peers = peerStorage.get(infoHashKey);
      if (peers != null && !peers.isEmpty()) {
        List<byte[]> values = new ArrayList<>();
        for (InetSocketAddress peer : peers) {
          values.add(encodePeerAddress(peer));
        }
        r.put("values", values);
      } else {
        // Return closest nodes (simplified - just return empty for now)
        r.put("nodes", new byte[0]);
      }

      response.put("r", r);

      byte[] encoded = BencodeParser.encode(response);
      DatagramPacket packet = new DatagramPacket(encoded, encoded.length, sender);
      socket.send(packet);
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * Handle announce_peer query.
   */
  @SuppressWarnings("unchecked")
  private void handleAnnouncePeerQuery(
      Map<String, Object> dict, InetSocketAddress sender, byte[] txnId) {
    try {
      Map<String, Object> args = (Map<String, Object>) dict.get("a");
      byte[] infoHash = (byte[]) args.get("info_hash");
      long port = (Long) args.get("port");

      String infoHashKey = bytesToHex(infoHash);
      InetSocketAddress peerAddress = new InetSocketAddress(sender.getAddress(), (int) port);

      peerStorage.computeIfAbsent(infoHashKey, k -> new ArrayList<>()).add(peerAddress);

      // Send acknowledgment
      Map<String, Object> response = new HashMap<>();
      response.put("y", "r");
      response.put("t", txnId);
      Map<String, Object> r = new HashMap<>();
      r.put("id", nodeId);
      response.put("r", r);

      byte[] encoded = BencodeParser.encode(response);
      DatagramPacket packet = new DatagramPacket(encoded, encoded.length, sender);
      socket.send(packet);
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * Get closest nodes to a target.
   */
  private List<DhtPeer> getClosestNodes(byte[] target, int count) {
    List<DhtPeer> nodes = new ArrayList<>(routingTable.values());
    nodes.sort((a, b) -> {
      byte[] distA = xorDistance(a.nodeId, target);
      byte[] distB = xorDistance(b.nodeId, target);
      return compareBytes(distA, distB);
    });
    return nodes.subList(0, Math.min(count, nodes.size()));
  }

  /**
   * Add node to routing table.
   */
  private void addToRoutingTable(byte[] nodeId, InetSocketAddress address) {
    String key = bytesToHex(nodeId);
    if (routingTable.size() < 100) { // Simplified limit
      routingTable.put(key, new DhtPeer(nodeId, address));
    }
  }

  /**
   * Calculate XOR distance between two node IDs.
   */
  private static byte[] xorDistance(byte[] a, byte[] b) {
    byte[] result = new byte[20];
    for (int i = 0; i < 20; i++) {
      result[i] = (byte) (a[i] ^ b[i]);
    }
    return result;
  }

  /**
   * Compare two byte arrays.
   */
  private static int compareBytes(byte[] a, byte[] b) {
    for (int i = 0; i < Math.min(a.length, b.length); i++) {
      int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
      if (diff != 0) {
        return diff;
      }
    }
    return a.length - b.length;
  }

  /**
   * Encode peer address as 6-byte compact format.
   */
  private static byte[] encodePeerAddress(InetSocketAddress address) {
    byte[] result = new byte[6];
    byte[] ip = address.getAddress().getAddress();
    System.arraycopy(ip, 0, result, 0, 4);
    int port = address.getPort();
    result[4] = (byte) (port >> 8);
    result[5] = (byte) port;
    return result;
  }

  /**
   * Decode list of compact peer addresses.
   */
  private static List<InetSocketAddress> decodePeerList(List<Object> list) {
    List<InetSocketAddress> peers = new ArrayList<>();
    for (Object element : list) {
      if (element instanceof byte[]) {
        byte[] compact = (byte[]) element;
        if (compact.length == 6) {
          try {
            byte[] ip = new byte[4];
            System.arraycopy(compact, 0, ip, 0, 4);
            int port = ((compact[4] & 0xFF) << 8) | (compact[5] & 0xFF);
            peers.add(new InetSocketAddress(
                java.net.InetAddress.getByAddress(ip), port));
          } catch (Exception e) {
            // Skip invalid peer
          }
        }
      }
    }
    return peers;
  }

  /**
   * Generate transaction ID.
   */
  private String generateTransactionId() {
    return "t" + System.currentTimeMillis();
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

  @Override
  public void close() {
    running.set(false);
    socket.close();
    try {
      receiveThread.join(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * DHT peer information.
   */
  private static class DhtPeer {
    final byte[] nodeId;
    final InetSocketAddress address;

    DhtPeer(byte[] nodeId, InetSocketAddress address) {
      this.nodeId = nodeId;
      this.address = address;
    }
  }

  /**
   * Pending request tracking.
   */
  private static class PendingRequest {
    List<InetSocketAddress> peers;
  }
}
