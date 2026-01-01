package com.example.jtorrent.metadata;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.peer.ExtendedMessage;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.MetadataMessage;
import com.example.jtorrent.peer.PeerConnection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches torrent metadata from peers using BEP-9 (ut_metadata extension).
 * Used when starting from magnet links.
 */
public class MetadataFetcher {

  private static final int UT_METADATA_LOCAL_ID = 1; // Our local ID for ut_metadata

  private final byte[] infoHash;
  private final byte[] peerId;
  private final Config config;
  private final Logger logger;

  /**
   * Create a metadata fetcher.
   *
   * @param infoHash the torrent info hash
   * @param peerId our peer ID
   * @param config configuration settings
   * @param logger logger instance
   */
  public MetadataFetcher(byte[] infoHash, byte[] peerId, Config config, Logger logger) {
    this.infoHash = infoHash.clone();
    this.peerId = peerId.clone();
    this.config = config;
    this.logger = logger;
  }

  /**
   * Fetch metadata from a list of peers.
   * Tries multiple peers in parallel until metadata is successfully retrieved.
   *
   * @param peers list of peer addresses
   * @return complete metadata bytes (bencoded info dictionary)
   * @throws IOException if metadata cannot be fetched
   */
  public byte[] fetchMetadata(List<InetSocketAddress> peers) throws IOException {
    if (peers == null || peers.isEmpty()) {
      throw new IOException("No peers available for metadata fetch");
    }

    int parallelPeers = config.getMetadataParallelPeers();
    logger.info("Starting metadata exchange with %d initial peer(s) (max %d concurrent attempts)...",
        peers.size(), parallelPeers);

    // Create thread pool for parallel fetching
    ExecutorService executor = Executors.newFixedThreadPool(parallelPeers);
    List<Future<byte[]>> futures = new ArrayList<>();
    List<InetSocketAddress> attemptedPeers = new ArrayList<>();

    try {
      // Submit fetch tasks for each peer
      for (InetSocketAddress peer : peers) {
        attemptedPeers.add(peer);
        Future<byte[]> future = executor.submit(new Callable<byte[]>() {
          @Override
          public byte[] call() throws Exception {
            logger.info("Attempting metadata fetch from peer: %s", peer);
            return fetchFromPeer(peer);
          }
        });
        futures.add(future);
      }

      // Poll futures and return first successful result
      IOException lastException = null;
      long startTime = System.currentTimeMillis();
      long totalTimeout = config.getMetadataConnectTimeoutMs() * 4; // Allow time for retries

      while (!futures.isEmpty()) {
        // Check for overall timeout
        if (System.currentTimeMillis() - startTime > totalTimeout) {
          logger.warn("Overall metadata fetch timeout exceeded (%d ms)", totalTimeout);
          break;
        }

        // Check each future
        for (int i = futures.size() - 1; i >= 0; i--) {
          Future<byte[]> future = futures.get(i);
          InetSocketAddress peer = attemptedPeers.get(i);

          try {
            // Try to get result with short timeout (non-blocking check)
            if (future.isDone()) {
              byte[] metadata = future.get(100, TimeUnit.MILLISECONDS);
              if (metadata != null) {
                logger.info("Successfully fetched metadata (%d bytes) from %s",
                    metadata.length, peer);

                // Cancel remaining tasks
                for (Future<byte[]> f : futures) {
                  f.cancel(true);
                }

                return metadata;
              } else {
                logger.debug("Peer %s returned null metadata", peer);
                futures.remove(i);
                attemptedPeers.remove(i);
              }
            }

          } catch (ExecutionException e) {
            logger.warn("Failed to fetch metadata from %s: %s",
                peer, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            lastException = new IOException("Metadata fetch failed from " + peer, e.getCause());
            futures.remove(i);
            attemptedPeers.remove(i);

          } catch (TimeoutException e) {
            // Future not ready yet, continue polling
            continue;

          } catch (Exception e) {
            logger.warn("Unexpected error fetching from %s: %s", peer, e.getMessage());
            lastException = new IOException("Metadata fetch failed from " + peer, e);
            futures.remove(i);
            attemptedPeers.remove(i);
          }
        }

        // Small sleep to avoid busy-waiting
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Metadata fetch interrupted", e);
        }
      }

      // All peers failed
      if (lastException != null) {
        throw lastException;
      } else {
        throw new IOException("Failed to fetch metadata from all " + peers.size() + " peers");
      }

    } finally {
      // Shutdown executor
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Fetch metadata from a single peer.
   *
   * @param peer peer address
   * @return metadata bytes, or null if peer doesn't support ut_metadata
   * @throws IOException if connection or protocol error occurs
   */
  private byte[] fetchFromPeer(InetSocketAddress peer) throws IOException {
    try (PeerConnection conn = new PeerConnection(infoHash, peerId)) {
      // Connect to peer with retry logic
      int timeout = config.getMetadataConnectTimeoutMs();
      int retries = config.getMetadataConnectRetries();
      logger.info("Connecting to peer %s for metadata exchange (timeout=%dms, retries=%d)...",
          peer, timeout, retries);
      conn.connect(peer, timeout, retries);
      logger.info("Connected successfully to %s", peer);

      // Perform BitTorrent handshake
      logger.debug("Performing BitTorrent handshake with %s", peer);
      conn.handshake();

      // Check if peer supports extensions
      if (!conn.peerSupportsExtensions()) {
        logger.warn("Peer %s does not support metadata extension, skipping...", peer);
        return null;
      }
      logger.info("Peer %s supports extension protocol ✓", peer);

      // Send extended handshake
      logger.info("Sending extended handshake to %s", peer);
      Map<String, Integer> extensions = new HashMap<>();
      extensions.put("ut_metadata", UT_METADATA_LOCAL_ID);
      ExtendedMessage handshake = ExtendedMessage.createHandshake(extensions, 0);
      conn.send(handshake.toMessage());

      // Receive extended handshake
      logger.info("Waiting for extended handshake response from %s", peer);
      Message msg = conn.receive();

      if (msg == null || msg.type() != Message.EXTENDED) {
        logger.warn("Peer %s did not send extended handshake", peer);
        return null;
      }

      ExtendedMessage peerHandshake = ExtendedMessage.fromMessage(msg);
      if (peerHandshake.getExtensionId() != ExtendedMessage.HANDSHAKE_ID) {
        logger.warn("Peer %s sent non-handshake extended message", peer);
        return null;
      }

      Map<String, Object> handshakeData;
      try {
        handshakeData = peerHandshake.parseHandshake();
      } catch (BencodeException e) {
        logger.warn("Failed to parse handshake from %s: %s", peer, e.getMessage());
        return null;
      }

      // Check if peer supports ut_metadata
      int peerUtMetadataId = ExtendedMessage.getUtMetadataId(handshakeData);
      if (peerUtMetadataId < 0) {
        logger.warn("Peer %s does not support ut_metadata extension", peer);
        return null;
      }

      int metadataSize = ExtendedMessage.getMetadataSize(handshakeData);
      if (metadataSize <= 0) {
        logger.warn("Peer %s does not have metadata (size=%d)", peer, metadataSize);
        return null;
      }

      logger.info("Using extended peer %s to fetch metadata (Extension ID: %d, Size: %d bytes)",
          peer, peerUtMetadataId, metadataSize);

      // Download metadata pieces
      byte[] metadata = downloadMetadata(conn, peerUtMetadataId, metadataSize);

      // Verify metadata hash
      if (!verifyMetadata(metadata)) {
        throw new IOException("Metadata hash verification failed");
      }

      logger.info("METADATA FETCH SUCCESS from %s (size = %d bytes)", peer, metadata.length);
      return metadata;

    } catch (TimeoutException e) {
      throw new IOException("Timeout fetching metadata from " + peer, e);
    } catch (BencodeException e) {
      throw new IOException("Bencode error fetching metadata from " + peer, e);
    }
  }

  /**
   * Download all metadata pieces from a peer.
   *
   * @param conn peer connection
   * @param peerUtMetadataId peer's ut_metadata extension ID
   * @param metadataSize total metadata size
   * @return complete metadata bytes
   * @throws IOException if download fails
   * @throws TimeoutException if timeout occurs
   * @throws BencodeException if parsing fails
   */
  private byte[] downloadMetadata(PeerConnection conn, int peerUtMetadataId, int metadataSize)
      throws IOException, TimeoutException, BencodeException {

    int pieceCount = MetadataMessage.calculatePieceCount(metadataSize);
    logger.info("Downloading %d metadata piece(s) (%d bytes total)", pieceCount, metadataSize);

    // Track received pieces
    Map<Integer, byte[]> pieces = new ConcurrentHashMap<>();
    Set<Integer> requested = ConcurrentHashMap.newKeySet();

    // Request all pieces
    for (int i = 0; i < pieceCount; i++) {
      MetadataMessage request = MetadataMessage.createRequest(i);
      ExtendedMessage extMsg = request.toExtendedMessage(peerUtMetadataId);
      conn.send(extMsg.toMessage());
      requested.add(i);
      logger.info("Requesting metadata piece %d/%d...", i + 1, pieceCount);
    }

    // Receive pieces
    long startTime = System.currentTimeMillis();
    int pieceTimeout = config.getMetadataPieceTimeoutMs();
    long totalTimeout = pieceTimeout * pieceCount;

    while (pieces.size() < pieceCount) {
      // Check timeout
      if (System.currentTimeMillis() - startTime > totalTimeout) {
        throw new TimeoutException(String.format(
            "Metadata download timeout after %d ms (received %d/%d pieces)",
            totalTimeout, pieces.size(), pieceCount));
      }

      // Receive message
      Message msg = conn.receive();
      if (msg == null) {
        throw new TimeoutException("No response from peer");
      }

      // Handle extended messages only
      if (msg.type() != Message.EXTENDED) {
        logger.debug("Ignoring non-extended message: %s", msg.typeName());
        continue;
      }

      ExtendedMessage extMsg = ExtendedMessage.fromMessage(msg);
      if (extMsg.getExtensionId() != UT_METADATA_LOCAL_ID) {
        logger.debug("Ignoring extended message with wrong ID: %d", extMsg.getExtensionId());
        continue;
      }

      // Parse metadata message
      MetadataMessage metaMsg = MetadataMessage.fromExtendedMessage(extMsg);

      if (metaMsg.getMsgType() == MetadataMessage.REJECT) {
        logger.warn("Peer rejected metadata piece %d", metaMsg.getPiece());
        throw new IOException("Peer rejected metadata piece " + metaMsg.getPiece());
      }

      if (metaMsg.getMsgType() == MetadataMessage.DATA) {
        int pieceIndex = metaMsg.getPiece();
        byte[] pieceData = metaMsg.getData();

        if (pieceData != null && pieceIndex >= 0 && pieceIndex < pieceCount) {
          pieces.put(pieceIndex, pieceData);
          logger.info("Received metadata piece %d/%d (%d bytes)",
              pieceIndex + 1, pieceCount, pieceData.length);
        } else {
          logger.warn("Invalid metadata piece received: index=%d, data=%s",
              pieceIndex, pieceData != null ? pieceData.length + " bytes" : "null");
        }
      }
    }

    // Reassemble metadata
    logger.info("Metadata complete. Reassembling %d pieces into %d bytes...", pieceCount, metadataSize);
    byte[] metadata = new byte[metadataSize];
    int offset = 0;

    for (int i = 0; i < pieceCount; i++) {
      byte[] piece = pieces.get(i);
      if (piece == null) {
        throw new IOException("Missing metadata piece " + i);
      }

      int length = Math.min(piece.length, metadataSize - offset);
      System.arraycopy(piece, 0, metadata, offset, length);
      offset += length;
    }

    logger.info("Metadata reassembly complete");
    return metadata;
  }

  /**
   * Verify that metadata hash matches the expected info hash.
   *
   * @param metadata metadata bytes to verify
   * @return true if hash matches
   */
  private boolean verifyMetadata(byte[] metadata) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] hash = sha1.digest(metadata);

      boolean matches = Arrays.equals(hash, infoHash);
      if (matches) {
        logger.info("Metadata hash verification: PASSED");
      } else {
        logger.error("Metadata hash verification: FAILED");
        logger.error("Expected: %s", bytesToHex(infoHash));
        logger.error("Got:      %s", bytesToHex(hash));
      }

      return matches;

    } catch (NoSuchAlgorithmException e) {
      logger.error("SHA-1 algorithm not available: %s", e.getMessage());
      return false;
    }
  }

  /**
   * Convert bytes to hex string.
   *
   * @param bytes bytes to convert
   * @return hex string
   */
  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }
}
