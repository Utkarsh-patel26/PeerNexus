package com.example.jtorrent.core;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.dht.DhtNode;
import com.example.jtorrent.dht.MagnetLink;
import com.example.jtorrent.logging.Logger;
import com.example.jtorrent.metadata.MetadataFetcher;
import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.peer.Message;
import com.example.jtorrent.peer.PeerConnection;
import com.example.jtorrent.persistence.DownloadState;
import com.example.jtorrent.scheduler.BandwidthLimiter;
import com.example.jtorrent.scheduler.Choker;
import com.example.jtorrent.scheduler.PeerConnectionManager;
import com.example.jtorrent.scheduler.PeerStats;
import com.example.jtorrent.storage.BlockInfo;
import com.example.jtorrent.storage.BlockRequest;
import com.example.jtorrent.storage.BlockRequestTracker;
import com.example.jtorrent.storage.DiskManager;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceSelectionStrategy;
import com.example.jtorrent.storage.PieceState;
import com.example.jtorrent.storage.PieceVerifier;
import com.example.jtorrent.tracker.AnnounceRequest;
import com.example.jtorrent.tracker.AnnounceResponse;
import com.example.jtorrent.tracker.HttpTrackerClient;
import com.example.jtorrent.tracker.PeerEndpoint;
import com.example.jtorrent.tracker.UdpTrackerClient;
import com.example.jtorrent.ui.DownloadTui;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.BitSet;

/**
 * Main orchestrator for BitTorrent download sessions.
 * Coordinates all components: trackers, DHT, peers, storage, scheduling.
 */
public class TorrentSession {

  private static final int STATE_SAVE_INTERVAL_MS = 15000; // 15 seconds

  private final URI input;
  private final Path outputDir;
  private final Config config;
  private final Logger logger;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean completed = new AtomicBoolean(false);

  private TorrentFile torrentFile;
  private PieceManager pieceManager;
  private DiskManager diskManager;
  private PieceVerifier pieceVerifier; // TODO: Integrate piece verification
  private Choker choker;
  private BandwidthLimiter bandwidthLimiter; // TODO: Integrate bandwidth limiting
  private PeerConnectionManager connectionManager;
  private BlockRequestTracker requestTracker;
  private TransferStats transferStats;
  private RequestScheduler requestScheduler;
  private UploadHandler uploadHandler;
  private DhtNode dhtNode;
  private DownloadState downloadState;
  private DownloadTui tui;
  private Thread stateSaverThread;
  private Thread schedulerThread;
  private ConcurrentHashMap<InetSocketAddress, ActivePeer> activePeers = new ConcurrentHashMap<>();
  private static final int MAX_PEERS_GLOBAL = 50;

  // Peer statistics tracking
  private final AtomicLong totalSeeders = new AtomicLong(0);
  private final AtomicLong totalLeechers = new AtomicLong(0);
  private final AtomicLong connectedSeeders = new AtomicLong(0);
  private final AtomicLong connectedPeersEstimate = new AtomicLong(0);

  /**
   * Create a torrent session.
   *
   * @param input     .torrent file path or magnet URI
   * @param outputDir output directory
   * @param config    configuration
   * @param logger    logger instance
   */
  public TorrentSession(URI input, Path outputDir, Config config, Logger logger) {
    this.input = input;
    this.outputDir = outputDir;
    this.config = config;
    this.logger = logger;
  }

  /**
   * Start the download session (blocking until complete or interrupted).
   *
   * @throws Exception if session fails to start
   */
  public void start() throws Exception {
    if (!running.compareAndSet(false, true)) {
      throw new IllegalStateException("Session already running");
    }

    try {
      logger.info("Starting torrent session");
      logger.info("Input: %s", input);
      logger.info("Output: %s", outputDir);

      // Create output directory
      Files.createDirectories(outputDir);
      Files.createDirectories(Paths.get(config.getStateDirectory()));

      // Load or create torrent metadata
      if (input.toString().startsWith("magnet:")) {
        logger.info("Loading magnet link...");
        loadMagnetLink();
      } else {
        logger.info("Loading torrent file...");
        loadTorrentFile();
      }

      // Initialize state
      Path stateFile = Paths.get(config.getStateDirectory(),
          bytesToHex(torrentFile.infoHash()) + ".state");
      if (Files.exists(stateFile)) {
        logger.info("Loading existing download state");
        downloadState = DownloadState.load(stateFile);
      } else {
        logger.info("Creating new download state");
        downloadState = new DownloadState(
            torrentFile.infoHash(),
            torrentFile.name(),
            torrentFile.totalLength(),
            torrentFile.pieces().size());
      }

      // Initialize components
      initializeComponents();

      // Start DHT if enabled
      if (config.isDhtEnabled() && dhtNode != null) {
        logger.info("Starting DHT node on port %d", config.getDhtPort());
        List<InetSocketAddress> bootstrapNodes = new ArrayList<>();
        for (String node : config.getDhtBootstrapNodes()) {
          String[] parts = node.split(":");
          bootstrapNodes.add(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        }
        dhtNode.bootstrap(bootstrapNodes);
      }

      // Discover peers
      Set<InetSocketAddress> peers = discoverPeers();
      logger.info("Discovered %d peer(s)", peers.size());

      // Start TUI
      if (tui != null) {
        tui.setTorrentInfo(torrentFile.name(), torrentFile.totalLength());
        tui.start();
      }

      // Start state saver
      startStateSaver();

      // Main download loop (simplified - in production would manage peer connections)
      logger.info("Download session started");
      logger.info("Torrent: %s", torrentFile.name());
      logger.info("Size: %d bytes", torrentFile.totalLength());
      logger.info("Pieces: %d", torrentFile.pieces().size());

      // Wait for completion or interruption
      waitForCompletion();

      logger.info("Download session completed");
      completed.set(true);

    } finally {
      stop();
    }
  }

  /**
   * Stop the download session.
   */
  public void stop() {
    if (running.compareAndSet(true, false)) {
      logger.info("Stopping torrent session");

      // Stop TUI
      if (tui != null) {
        tui.stop();
      }

      // Shutdown connection manager (closes all peer connections)
      if (connectionManager != null) {
        connectionManager.shutdown();
      }

      // Save state
      if (downloadState != null) {
        try {
          Path stateFile = Paths.get(config.getStateDirectory(),
              downloadState.getStateFileName());
          downloadState.save(stateFile);
          logger.info("Saved download state");
        } catch (IOException e) {
          logger.error("Failed to save download state", e);
        }
      }

      // Close DHT
      if (dhtNode != null) {
        try {
          dhtNode.close();
        } catch (Exception e) {
          logger.error("Failed to close DHT node", e);
        }
      }

      // Close disk manager
      if (diskManager != null) {
        try {
          diskManager.close();
        } catch (IOException e) {
          logger.error("Failed to close disk manager", e);
        }
      }

      // Interrupt state saver
      if (stateSaverThread != null) {
        stateSaverThread.interrupt();
      }

      logger.info("Torrent session stopped");
    }
  }

  /**
   * Load magnet link and resolve metadata.
   */
  private void loadMagnetLink() throws Exception {
    MagnetLink magnet = MagnetLink.parse(input.toString());
    logger.info("Magnet link parsed");
    if (magnet.getDisplayName() != null) {
      logger.info("Name: %s", magnet.getDisplayName());
    }
    logger.info("Info hash: %s", magnet.getInfoHashHex());

    // Discover peers from trackers and DHT
    logger.info("Discovering peers for metadata exchange...");
    Set<InetSocketAddress> peers = new HashSet<>();

    // Start DHT if enabled
    if (config.isDhtEnabled()) {
      logger.info("Starting DHT for peer discovery...");
      dhtNode = new DhtNode(config.getDhtPort());
      dhtNode.bootstrap(Arrays.asList(
          new InetSocketAddress("router.bittorrent.com", 6881),
          new InetSocketAddress("dht.transmissionbt.com", 6881),
          new InetSocketAddress("router.utorrent.com", 6881)));

      // Wait for DHT to bootstrap
      logger.info("Waiting for DHT to bootstrap...");
      Thread.sleep(3000);

      // Query DHT for peers
      try {
        List<InetSocketAddress> dhtPeers = dhtNode.getPeers(magnet.getInfoHash());
        peers.addAll(dhtPeers);
        logger.info("Found %d peer(s) from DHT", dhtPeers.size());
      } catch (Exception e) {
        logger.warn("DHT peer discovery failed: %s", e.getMessage());
      }
    }

    // Fallback trackers for robust peer discovery
    List<String> fallbackTrackers = getFallbackTrackers();

    // Merge magnet trackers with fallback trackers (preserving order, no
    // duplicates)
    Set<String> allTrackers = new LinkedHashSet<>();
    allTrackers.addAll(magnet.getTrackers());
    allTrackers.addAll(fallbackTrackers);
    List<String> trackers = new ArrayList<>(allTrackers);

    if (!trackers.isEmpty()) {
      logger.info(
          "Starting parallel tracker announces for %d tracker(s) "
              + "(%d from magnet + %d fallback)...",
          trackers.size(), magnet.getTrackers().size(), fallbackTrackers.size());

      // Thread-safe peer collection
      Set<InetSocketAddress> trackerPeers = ConcurrentHashMap.newKeySet();

      // Create thread pool for parallel tracker queries
      ExecutorService executor = Executors.newFixedThreadPool(Math.min(trackers.size(), 4));
      List<Future<Void>> futures = new ArrayList<>();

      // Submit all tracker queries in parallel
      for (String trackerUrl : trackers) {
        Future<Void> future = executor.submit(new Callable<Void>() {
          @Override
          public Void call() {
            try {
              // For magnet links, we don't have metadata yet, so use left=0 (metadata only
              // mode)
              List<PeerEndpoint> result = queryTracker(
                  trackerUrl,
                  magnet.getInfoHash(),
                  0, // downloaded
                  0 // left (0 means we're just looking for peers for metadata exchange)
              );

              if (!result.isEmpty()) {
                for (PeerEndpoint peer : result) {
                  trackerPeers.add(peer.address());
                }
                logger.info("Tracker %s responded with %d peer(s)", trackerUrl, result.size());
              }
            } catch (Exception e) {
              logger.warn("Tracker %s timed out or failed (ignored): %s", trackerUrl, e.getMessage());
            }
            return null;
          }
        });
        futures.add(future);
      }

      // Poll futures with early proceed logic - proceed as soon as we have ANY peers
      long startTime = System.currentTimeMillis();
      long maxWaitTime = 10000; // Max 10 seconds total wait for all trackers

      while (trackerPeers.isEmpty() && System.currentTimeMillis() - startTime < maxWaitTime) {
        // Check if any tracker has responded
        boolean allDone = true;
        for (Future<Void> future : futures) {
          if (!future.isDone()) {
            allDone = false;
          }
        }

        if (allDone) {
          break; // All trackers finished (success or failure)
        }

        // Small sleep to avoid busy-waiting
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      // Add discovered peers to main peer set
      peers.addAll(trackerPeers);

      // Cancel remaining tracker tasks if we have peers
      if (!trackerPeers.isEmpty()) {
        logger.info("Proceeding with metadata exchange using %d discovered peer(s)...", trackerPeers.size());
        for (Future<Void> future : futures) {
          future.cancel(true);
        }
      }

      // Shutdown executor
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    if (peers.isEmpty()) {
      throw new IOException("No peers found for metadata exchange. "
          + "Cannot download magnet link without peers.");
    }

    logger.info("Total peers discovered: %d", peers.size());

    // Fetch metadata using BEP-9
    logger.info("Starting metadata exchange via BEP-9 (ut_metadata)...");
    byte[] peerId = generatePeerId();
    MetadataFetcher fetcher = new MetadataFetcher(magnet.getInfoHash(), peerId, config, logger);

    byte[] metadata;
    try {
      metadata = fetcher.fetchMetadata(new ArrayList<>(peers));
    } catch (IOException e) {
      throw new IOException("Failed to fetch metadata from peers: " + e.getMessage(), e);
    }

    logger.info("Metadata successfully fetched (%d bytes)", metadata.length);

    // Parse metadata as torrent file
    logger.info("Parsing metadata...");
    try {
      // Metadata from BEP-9 is just the raw "info" dictionary
      // We need to wrap it in a proper torrent structure: {"info": <metadata>}
      // Convert tracker strings to URIs
      List<URI> trackerUris = new ArrayList<>();
      for (String tracker : magnet.getTrackers()) {
        try {
          trackerUris.add(new URI(tracker));
        } catch (Exception e) {
          logger.warn("Invalid tracker URI: %s", tracker);
        }
      }
      torrentFile = TorrentFile.parseInfoDict(metadata, trackerUris);
      logger.info("Torrent metadata loaded: %s", torrentFile.name());
      logger.info("Total size: %d bytes", torrentFile.totalLength());
      logger.info("Pieces: %d", torrentFile.pieces().size());
    } catch (BencodeException e) {
      throw new IOException("Failed to parse metadata: " + e.getMessage(), e);
    }
  }

  /**
   * Load torrent file.
   */
  private void loadTorrentFile() throws IOException, BencodeException {
    // Convert URI to Path properly for Windows compatibility
    Path torrentPath;
    if (input.getScheme() != null && input.getScheme().equals("file")) {
      torrentPath = Path.of(input);
    } else {
      torrentPath = Paths.get(input.getPath());
    }

    if (!Files.exists(torrentPath)) {
      throw new IOException("Torrent file not found: " + torrentPath);
    }

    torrentFile = TorrentFile.parse(torrentPath.toFile());
    logger.info("Loaded torrent: %s", torrentFile.name());
    logger.info("Announce URL: %s", torrentFile.primaryAnnounce());
  }

  /**
   * Initialize download components.
   */
  private void initializeComponents() throws IOException {
    logger.debug("Initializing components");

    // Piece manager
    pieceManager = new PieceManager(
        torrentFile.pieces().size(),
        torrentFile.pieceLength(),
        torrentFile.totalLength());

    // Restore completed pieces from state
    if (downloadState != null) {
      logger.warn("***** INIT: Restoring download state *****");
      logger.warn("INIT: Downloaded %d bytes, %d/%d pieces complete",
          downloadState.getDownloadedBytes(), downloadState.getCompletedPieceCount(), downloadState.getPieceCount());

      for (int i = 0; i < downloadState.getPieceCount(); i++) {
        if (downloadState.isPieceComplete(i)) {
          pieceManager.setPieceState(i, PieceState.COMPLETE);
        }
      }
      logger.warn("INIT: Restored %d completed pieces", downloadState.getCompletedPieceCount());

      // CRITICAL FIX: Reset DOWNLOADING pieces to MISSING
      // Since DownloadState doesn't track blocks, DOWNLOADING pieces have no block
      // data
      int resetCount = 0;
      logger.warn("***** INIT: Scanning for DOWNLOADING pieces *****");
      for (int i = 0; i < pieceManager.getPieceCount(); i++) {
        PieceState state = pieceManager.getPieceState(i);
        if (state == PieceState.DOWNLOADING) {
          pieceManager.setPieceState(i, PieceState.MISSING);
          resetCount++;
        }
      }
      if (resetCount > 0) {
        logger.warn("***** INIT: Reset %d DOWNLOADING pieces to MISSING *****", resetCount);
      } else {
        logger.warn("***** INIT: No DOWNLOADING pieces found *****");
      }
      logger.warn("INIT: Final state - completed=%d downloading=%d missing=%d",
          pieceManager.getCompletedCount(), pieceManager.getDownloadingCount(),
          pieceManager.getPieceCount() - pieceManager.getCompletedCount() - pieceManager.getDownloadingCount());
    }

    // Disk manager
    diskManager = new DiskManager(
        outputDir,
        torrentFile.files(),
        torrentFile.pieceLength(),
        torrentFile.pieces().size(),
        torrentFile.totalLength());

    // Piece verifier (concatenate piece hashes into single byte array)
    List<byte[]> piecesList = torrentFile.pieces();
    byte[] piecesConcat = new byte[piecesList.size() * 20];
    for (int i = 0; i < piecesList.size(); i++) {
      System.arraycopy(piecesList.get(i), 0, piecesConcat, i * 20, 20);
    }
    pieceVerifier = new PieceVerifier(
        diskManager,
        pieceManager,
        piecesConcat);

    // Choker
    choker = new Choker(config.getUploadSlots(), logger);

    // Bandwidth limiter
    bandwidthLimiter = new BandwidthLimiter(
        config.getMaxUploadBytesPerSec(),
        config.getMaxDownloadBytesPerSec(),
        0 // No per-socket limit
    );

    // Peer connection manager
    connectionManager = new PeerConnectionManager();

    // Block request tracker for pipelining
    requestTracker = new BlockRequestTracker();

    // Transfer statistics aggregator
    transferStats = new TransferStats(logger, "Download");

    // Request scheduler for pipeline management
    requestScheduler = new RequestScheduler(requestTracker, pieceManager, transferStats, logger);

    // Upload handler for sending pieces
    uploadHandler = new UploadHandler(diskManager, pieceManager, transferStats, logger);

    // Start choking algorithm thread
    startChokingThread();

    // Start request scheduler thread
    startSchedulerThread();

    // DHT node
    if (config.isDhtEnabled()) {
      try {
        dhtNode = new DhtNode(config.getDhtPort());
      } catch (Exception e) {
        logger.warn("Failed to start DHT node: %s", e.getMessage());
      }
    }

    // TUI
    tui = new DownloadTui();

    logger.debug("Components initialized");
  }

  /**
   * Start request scheduler thread for pipelined block requests.
   */
  private void startSchedulerThread() {
    schedulerThread = new Thread(() -> {
      while (running.get() && !pieceManager.isComplete()) {
        try {
          // Get list of active peers
          List<ActivePeer> activePeerList = getActivePeerList();

          // Run scheduler tick to fill pipeline
          requestScheduler.tick(activePeerList);

          // Update pieces in progress for stats
          transferStats.setPiecesInProgress(pieceManager.getDownloadingCount());

          // Log aggregated stats every 10 seconds
          transferStats.logIfDue(10000, requestTracker.getTotalRequestCount());

          // Perform peer eviction if we have too many peers
          evictSlowPeersIfNeeded();

          // Sleep for scheduler interval
          Thread.sleep(250); // 250ms scheduler tick

        } catch (InterruptedException e) {
          break;
        } catch (Exception e) {
          logger.error("Error in scheduler thread", e);
        }
      }
    }, "Request-Scheduler");
    schedulerThread.setDaemon(true);
    schedulerThread.start();
    logger.info("Started request scheduler thread (pipeline: up to 50 requests, 250ms tick)");
  }

  /**
   * Get list of active peers eligible for block requests.
   * Active peers are:
   * - Handshake complete
   * - Not choking us
   * - Have pieces we need
   * - Below request capacity
   * 
   * @return List of active peers
   */
  private List<ActivePeer> getActivePeerList() {
    List<ActivePeer> result = new ArrayList<>();

    if (activePeers == null) {
      return result;
    }

    for (ActivePeer peer : activePeers.values()) {
      if (peer == null) {
        continue;
      }

      // Check if peer is unchoked
      if (peer.isPeerChoking()) {
        continue;
      }

      // Check if peer has bitfield
      if (peer.getBitfield() == null) {
        continue;
      }

      // Check if peer has pieces we need
      boolean hasPiecesWeNeed = false;
      for (int i = 0; i < pieceManager.getPieceCount(); i++) {
        if (pieceManager.getPieceState(i) != PieceState.COMPLETE && peer.hasPiece(i)) {
          hasPiecesWeNeed = true;
          break;
        }
      }
      if (!hasPiecesWeNeed) {
        continue;
      }

      // Check request capacity
      int requestCount = requestTracker.getRequestCount(peer.getAddress());
      if (requestCount < 5) {
        result.add(peer);
      }
    }

    return result;
  }

  /**
   * Evict slow peers if we exceed MAX_PEERS_GLOBAL.
   */
  private void evictSlowPeersIfNeeded() {
    if (activePeers.size() <= MAX_PEERS_GLOBAL) {
      return;
    }

    // Sort peers by download rate (ascending)
    List<ActivePeer> sortedPeers = activePeers.values().stream()
        .sorted(Comparator.comparingDouble(ActivePeer::getDownloadRate))
        .collect(Collectors.toList());

    // Remove slowest peers
    int toRemove = activePeers.size() - MAX_PEERS_GLOBAL;
    for (int i = 0; i < toRemove && i < sortedPeers.size(); i++) {
      ActivePeer peer = sortedPeers.get(i);
      logger.info("Evicting slow peer %s (rate: %.1f KB/s)",
          peer.getAddress(), peer.getDownloadRate() / 1024.0);

      // Cancel pending requests
      requestTracker.cancelPeerRequests(peer.getAddress());

      // Remove from active peers
      activePeers.remove(peer.getAddress());

      // Close connection
      try {
        peer.getConnection().close();
      } catch (Exception e) {
        logger.debug("Error closing evicted peer: %s", e.getMessage());
      }
    }
  }

  /**
   * Discover peers from trackers and DHT.
   */
  private Set<InetSocketAddress> discoverPeers() {
    Set<InetSocketAddress> peers = new HashSet<>();

    logger.info("Starting peer discovery (trackers + DHT)...");

    // Check if this is a private torrent
    boolean isPrivateTorrent = torrentFile != null && torrentFile.isPrivate();
    if (isPrivateTorrent) {
      logger.info("Private torrent detected - DHT and PEX disabled");
    }

    // Determine which trackers to use
    List<String> trackersToQuery = new ArrayList<>();

    if (torrentFile.primaryAnnounce() != null) {
      trackersToQuery.add(torrentFile.primaryAnnounce().toString());
    }

    // Add all trackers from announce-list
    for (URI tracker : torrentFile.announceList()) {
      String trackerUrl = tracker.toString();
      if (!trackersToQuery.contains(trackerUrl)) {
        trackersToQuery.add(trackerUrl);
      }
    }

    // If no trackers found in torrent, use fallback public trackers (only for
    // non-private)
    if (trackersToQuery.isEmpty() && !isPrivateTorrent) {
      logger.warn("No trackers in torrent file, using fallback public trackers");
      trackersToQuery.addAll(getFallbackTrackers());
    }

    // Query all available trackers
    for (String trackerUrl : trackersToQuery) {
      try {
        logger.info("Querying tracker: %s", trackerUrl);
        List<PeerEndpoint> trackerPeers = queryTracker(trackerUrl);
        for (PeerEndpoint peer : trackerPeers) {
          peers.add(peer.address());
        }
        logger.info("Tracker %s returned %d peer(s)", trackerUrl, trackerPeers.size());
      } catch (Exception e) {
        logger.warn("Tracker query failed (%s): %s", trackerUrl, e.getMessage());
      }
    }

    // Query DHT (only for non-private torrents)
    if (!isPrivateTorrent && config.isDhtEnabled() && dhtNode != null) {
      try {
        logger.info("Querying DHT...");
        List<InetSocketAddress> dhtPeers = dhtNode.getPeers(torrentFile.infoHash());
        peers.addAll(dhtPeers);
        logger.info("DHT returned %d peer(s)", dhtPeers.size());
      } catch (Exception e) {
        logger.warn("DHT query failed: %s", e.getMessage());
      }
    } else if (isPrivateTorrent) {
      logger.debug("Skipping DHT for private torrent");
    } else {
      logger.info("DHT disabled or not initialized");
    }

    logger.info("Peer discovery complete: found %d total peer(s)", peers.size());
    return peers;
  }

  /**
   * Get fallback public trackers when torrent has no trackers.
   *
   * @return list of fallback tracker URLs
   */
  private List<String> getFallbackTrackers() {
    return Arrays.asList(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://open.stealth.si:80/announce");
  }

  /**
   * Query a tracker for peers (for regular torrents with TorrentFile).
   */
  private List<PeerEndpoint> queryTracker(String announceUrl) throws Exception {
    if (torrentFile == null) {
      throw new IllegalStateException("TorrentFile is null - use queryTracker with raw parameters");
    }

    return queryTracker(
        announceUrl,
        torrentFile.infoHash(),
        downloadState != null ? downloadState.getDownloadedBytes() : 0,
        torrentFile.totalLength() - (downloadState != null ? downloadState.getDownloadedBytes() : 0));
  }

  /**
   * Query a tracker for peers (for magnet links or when TorrentFile not yet
   * available).
   *
   * @param announceUrl tracker announce URL
   * @param infoHash    torrent info hash
   * @param downloaded  bytes downloaded so far
   * @param left        bytes left to download
   * @return list of peer endpoints
   * @throws Exception if tracker query fails
   */
  private List<PeerEndpoint> queryTracker(String announceUrl, byte[] infoHash, long downloaded,
      long left) throws Exception {
    AnnounceRequest request = new AnnounceRequest(
        infoHash,
        generatePeerId(),
        config.getPort(),
        0, // uploaded
        downloaded,
        left,
        AnnounceRequest.Event.STARTED);

    AnnounceResponse response = null;
    if (announceUrl.startsWith("http://") || announceUrl.startsWith("https://")) {
      try (HttpTrackerClient client = new HttpTrackerClient(URI.create(announceUrl))) {
        response = client.announce(request);
      } catch (Exception e) {
        throw e;
      }
    } else if (announceUrl.startsWith("udp://")) {
      try (UdpTrackerClient client = new UdpTrackerClient(URI.create(announceUrl))) {
        response = client.announce(request);
      } catch (Exception e) {
        throw e;
      }
    } else {
      throw new IllegalArgumentException("Unsupported tracker protocol: " + announceUrl);
    }

    // Update tracker statistics
    if (response != null && !response.isFailure()) {
      totalSeeders.set(response.seeders());
      totalLeechers.set(response.leechers());
      logger.info("Tracker reports: %d seeders, %d leechers", response.seeders(), response.leechers());
      return response.peers();
    }

    return new ArrayList<>();
  }

  /**
   * Generate a random peer ID.
   */
  private byte[] generatePeerId() {
    byte[] peerId = new byte[20];
    System.arraycopy("-JT0100-".getBytes(), 0, peerId, 0, 8);
    new java.security.SecureRandom().nextBytes(Arrays.copyOfRange(peerId, 8, 20));
    return peerId;
  }

  /**
   * Start periodic state saver.
   */
  private void startStateSaver() {
    stateSaverThread = new Thread(() -> {
      while (running.get()) {
        try {
          Thread.sleep(STATE_SAVE_INTERVAL_MS);
          if (downloadState != null) {
            Path stateFile = Paths.get(config.getStateDirectory(),
                downloadState.getStateFileName());
            downloadState.save(stateFile);
            logger.debug("Saved download state");
          }
        } catch (InterruptedException e) {
          break;
        } catch (IOException e) {
          logger.error("Failed to save state", e);
        }
      }
    }, "State-Saver");
    stateSaverThread.setDaemon(true);
    stateSaverThread.start();
  }

  /**
   * Start choking algorithm thread for upload management.
   */
  private void startChokingThread() {
    Thread chokingThread = new Thread(() -> {
      while (running.get()) {
        try {
          Thread.sleep(10000); // Run every 10 seconds

          if (choker != null) {
            // Update all peer rates
            choker.updateAllRates(10000);

            // Get all peers sorted by download rate
            Map<String, PeerStats> allPeerStats = choker.getAllPeerStats();
            List<PeerStats> allPeers = new ArrayList<>(allPeerStats.values());

            // Sort by download rate (descending)
            allPeers.sort((a, b) -> Double.compare(
                b.getDownloadRateBytesPerSec(), a.getDownloadRateBytesPerSec()));

            // Unchoke top 6 peers by download rate
            Set<String> unchokedPeers = new HashSet<>();
            int regularSlots = Math.min(6, allPeers.size());
            for (int i = 0; i < regularSlots; i++) {
              PeerStats stats = allPeers.get(i);
              stats.setChoked(false);
              unchokedPeers.add(stats.getPeerId());
            }

            // 1 optimistic unchoke (random peer not in top 6)
            List<PeerStats> remainingPeers = allPeers.stream()
                .filter(p -> !unchokedPeers.contains(p.getPeerId()))
                .collect(Collectors.toList());

            if (!remainingPeers.isEmpty()) {
              int randomIndex = new java.util.Random().nextInt(remainingPeers.size());
              PeerStats optimistic = remainingPeers.get(randomIndex);
              optimistic.setChoked(false);
              unchokedPeers.add(optimistic.getPeerId());
              logger.debug("Optimistic unchoke: %s", optimistic.getPeerId());
            }

            // Choke all others
            for (PeerStats stats : allPeers) {
              if (!unchokedPeers.contains(stats.getPeerId())) {
                stats.setChoked(true);
              }
            }

            // Log choking decisions
            if (!allPeers.isEmpty()) {
              logger.debug("Choking algorithm: %d peers, %d unchoked (6 regular + 1 optimistic)",
                  allPeers.size(), unchokedPeers.size());
              logger.debug("Upload: %.1f KB/s, Download: %.1f KB/s",
                  choker.getTotalUploadRate() / 1024.0,
                  choker.getTotalDownloadRate() / 1024.0);
            }

            // Note: Actual CHOKE/UNCHOKE messages sent by peer workers
          }
        } catch (InterruptedException e) {
          break;
        } catch (Exception e) {
          logger.error("Error in choking thread: %s", e.getMessage());
        }
      }
    }, "Choking-Algorithm");
    chokingThread.setDaemon(true);
    chokingThread.start();
    logger.info("Started choking algorithm thread (6 regular + 1 optimistic unchoke)");
  }

  /**
   * Wait for download completion.
   */
  private void waitForCompletion() {
    logger.info("Starting download with peer workers...");

    // Discover initial peers
    Set<InetSocketAddress> peers = new HashSet<>();
    try {
      Set<InetSocketAddress> discovered = discoverPeers();
      peers.addAll(discovered);
      logger.info("Discovered %d peer(s) from trackers/DHT", discovered.size());
    } catch (Exception e) {
      logger.warn("Peer discovery failed: %s", e.getMessage());
    }

    // If no peers found, log and wait a bit for user-added peers
    if (peers.isEmpty()) {
      logger.warn("No peers discovered! Waiting for external peer sources...");
      logger.info("Try adding a well-known torrent with active seeders");
      // Give up after 30 seconds without peers
      long startTime = System.currentTimeMillis();
      while (running.get() && peers.isEmpty() && System.currentTimeMillis() - startTime < 30000) {
        try {
          Thread.sleep(5000);
          Set<InetSocketAddress> retry = discoverPeers();
          if (!retry.isEmpty()) {
            peers.addAll(retry);
            logger.info("Rediscovered %d peer(s)", retry.size());
          }
        } catch (Exception e) {
          logger.debug("Retry peer discovery failed: %s", e.getMessage());
        }
      }
    }

    if (peers.isEmpty()) {
      logger.error("No peers available - cannot download without peers!");
      if (downloadState != null) {
        downloadState.setStatus(DownloadState.DownloadStatus.PAUSED);
      }
      return;
    }

    logger.info("Starting %d peer download worker(s)", Math.min(peers.size(), 32));

    // Create executor for peer workers (increased from 4 to 32 for better
    // throughput)
    int poolSize = Math.max(1, Math.min(peers.size(), 32));
    ExecutorService executor = Executors.newFixedThreadPool(poolSize);
    List<Future<?>> workers = new ArrayList<>();

    // Start download worker for each peer
    for (InetSocketAddress peer : peers) {
      Future<?> worker = executor.submit(() -> downloadFromPeer(peer));
      workers.add(worker);
      logger.debug("Started worker for peer %s", peer);
    }

    // Monitor download progress
    long lastStateUpdate = System.currentTimeMillis();
    long lastBytesValue = downloadState != null ? downloadState.getDownloadedBytes() : 0;
    long lastSpeedCalcTime = System.currentTimeMillis();
    long lastPeerDiscovery = System.currentTimeMillis();

    while (running.get() && !pieceManager.isComplete()) {
      try {
        Thread.sleep(1000);

        // Update state periodically
        long now = System.currentTimeMillis();
        if (now - lastStateUpdate > STATE_SAVE_INTERVAL_MS) {
          if (downloadState != null) {
            downloadState.setStatus(DownloadState.DownloadStatus.DOWNLOADING);
          }
          lastStateUpdate = now;
        }

        // Periodic peer discovery every 30 seconds
        if (now - lastPeerDiscovery > 30000) {
          logger.info("Periodic peer discovery...");
          try {
            Set<InetSocketAddress> newPeers = discoverPeers();
            int newPeerCount = 0;
            for (InetSocketAddress peer : newPeers) {
              if (!peers.contains(peer)) {
                peers.add(peer);
                Future<?> worker = executor.submit(() -> downloadFromPeer(peer));
                workers.add(worker);
                newPeerCount++;
              }
            }
            if (newPeerCount > 0) {
              logger.info("Added %d new peer(s)", newPeerCount);
            }
          } catch (Exception e) {
            logger.debug("Periodic peer discovery failed: %s", e.getMessage());
          }
          lastPeerDiscovery = now;
        }

        // Update TUI with real values
        if (tui != null && downloadState != null) {
          long currentBytes = downloadState.getDownloadedBytes();
          long timeDiffMs = now - lastSpeedCalcTime;
          long bytesDiff = currentBytes - lastBytesValue;

          // Calculate download speed in bytes/sec
          long downloadSpeed = 0;
          if (timeDiffMs > 0) {
            downloadSpeed = (bytesDiff * 1000) / timeDiffMs;
            lastBytesValue = currentBytes;
            lastSpeedCalcTime = now;
          }

          tui.setDownloadedBytes(currentBytes);

          // Count active (non-completed) workers to estimate connected peers
          long activeWorkers = workers.stream().filter(w -> !w.isDone()).count();
          long connectedPeers = Math.max(1, activeWorkers); // At least 1 if any downloading

          connectedPeersEstimate.set(connectedPeers);

          // Display seeder count from tracker and connected seeders
          tui.setPeerCounts(connectedPeers, connectedSeeders.get());
          tui.setTrackerStats(totalSeeders.get(), totalLeechers.get());
          tui.setRates(downloadSpeed, 0); // 0 upload rate (uploading not implemented)

          // Display tracker statistics
          if (totalSeeders.get() > 0 || totalLeechers.get() > 0) {
            logger.debug("Tracker stats: %d seeders, %d leechers (connected: %d peers, %d seeders)",
                totalSeeders.get(), totalLeechers.get(), connectedPeers, connectedSeeders.get());
          }
        }

        // Check if all workers are done
        boolean allDone = true;
        for (Future<?> worker : workers) {
          if (!worker.isDone()) {
            allDone = false;
            break;
          }
        }

        if (allDone && !pieceManager.isComplete()) {
          logger.info("All workers finished, rediscovering peers...");
          try {
            peers.clear();
            peers.addAll(discoverPeers());
            if (!peers.isEmpty()) {
              for (InetSocketAddress peer : peers) {
                Future<?> worker = executor.submit(() -> downloadFromPeer(peer));
                workers.add(worker);
              }
              logger.info("Started %d new download workers", peers.size());
            }
          } catch (Exception e) {
            logger.warn("Peer rediscovery failed: %s", e.getMessage());
          }
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Download complete
    if (pieceManager.isComplete()) {
      logger.info("Download complete!");
      if (downloadState != null) {
        downloadState.setStatus(DownloadState.DownloadStatus.COMPLETED);
      }
    }

    // Cleanup
    executor.shutdownNow();
    try {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Download pieces from a single peer.
   */
  private void downloadFromPeer(InetSocketAddress peerAddress) {
    PeerConnection peer = null;
    PeerStats stats = null;
    boolean isPeerSeeder = false;
    String torrentId = bytesToHex(torrentFile.infoHash());
    long connectionStartTime = System.currentTimeMillis();

    try {
      logger.info("Attempting to connect to peer: %s", peerAddress);

      // Check if we can accept more peers
      if (!connectionManager.canAcceptMorePeers(torrentId)) {
        logger.debug("Peer limit reached for torrent, skipping %s", peerAddress);
        return;
      }

      peer = new PeerConnection(torrentFile.infoHash(), generatePeerId());
      stats = new PeerStats(peerAddress.toString(), peerAddress);

      peer.connect(peerAddress, 5000);
      logger.info("Connected to peer: %s", peerAddress);

      // Perform handshake
      try {
        peer.handshake();
      } catch (Exception e) {
        logger.warn("Handshake failed with peer %s: %s", peerAddress, e.getMessage());
        if (stats != null) {
          stats.recordFailure();
        }
        if (peer != null) {
          try {
            peer.close();
          } catch (Exception ignored) {
          }
        }
        return;
      }

      if (!peer.isHandshakeComplete()) {
        logger.debug("Handshake not complete with peer: %s", peerAddress);
        if (stats != null) {
          stats.recordFailure();
        }
        if (peer != null) {
          try {
            peer.close();
          } catch (Exception ignored) {
          }
        }
        return;
      }

      logger.info("Handshake successful with peer: %s, amChoking=%s, peerChoking=%s", peerAddress,
          peer.amChoking(), peer.peerChoking());

      // Register peer with connection manager
      if (!connectionManager.registerPeer(peerAddress, peer, stats, torrentId)) {
        logger.debug("Failed to register peer %s (limits reached)", peerAddress);
        try {
          peer.close();
        } catch (Exception ignored) {
        }
        return;
      }

      // Register peer with choker for upload management
      String peerIdStr = peerAddress.toString();
      PeerStats chokerStats = choker.addPeer(peerIdStr);

      stats.recordSuccess(System.currentTimeMillis() - connectionStartTime);

      // Wait for initial messages (BITFIELD, EXTENDED, etc.)
      // Modern clients may send EXTENDED first, then BITFIELD
      try {
        logger.debug("Waiting for initial messages from peer %s", peerAddress);
        int messagesReceived = 0;
        long deadline = System.currentTimeMillis() + 2000; // 2 second timeout

        while (System.currentTimeMillis() < deadline && messagesReceived < 5) {
          try {
            Message msg = peer.receive();
            if (msg != null) {
              messagesReceived++;

              if (msg.type() == Message.BITFIELD) {
                // Parse bitfield
                byte[] bitfieldBytes = msg.payload();
                java.util.BitSet bitfield = bytesToBitSet(bitfieldBytes, pieceManager.getPieceCount());
                peer.setPeerBitfield(bitfield, pieceManager.getPieceCount());

                isPeerSeeder = peer.isPeerSeeder();
                if (isPeerSeeder) {
                  connectedSeeders.incrementAndGet();
                  if (stats != null) {
                    stats.setSeeder(true);
                  }
                  logger.info("Peer %s is a SEEDER (has all %d pieces)",
                      peerAddress, pieceManager.getPieceCount());
                } else {
                  logger.info("Peer %s has %d/%d pieces",
                      peerAddress, bitfield.cardinality(), pieceManager.getPieceCount());
                }
                break; // Got bitfield, can proceed
              } else if (msg.type() == Message.EXTENDED) {
                logger.debug("Peer %s sent EXTENDED handshake", peerAddress);
                // Continue receiving to get BITFIELD
              } else if (msg.type() == Message.HAVE) {
                // Some peers send HAVE instead of BITFIELD
                byte[] payload = msg.payload();
                if (payload.length >= 4) {
                  int pieceIdx = java.nio.ByteBuffer.wrap(payload).getInt();
                  peer.updatePeerHasPiece(pieceIdx, pieceManager.getPieceCount());
                  logger.debug("Peer %s has piece %d", peerAddress, pieceIdx);
                }
              } else if (msg.type() == Message.UNCHOKE) {
                logger.info("Peer %s unchoked us", peerAddress);
              } else if (msg.type() == Message.CHOKE) {
                logger.debug("Peer %s is choking us", peerAddress);
              }
            }
          } catch (java.net.SocketTimeoutException e) {
            // Timeout is ok, break and continue
            break;
          }
        }

        // If no bitfield received, assume peer has nothing initially
        // They'll send HAVE messages for pieces they have
        if (peer.getPeerBitfield() == null) {
          logger.debug("No BITFIELD from peer %s, will build from HAVE messages", peerAddress);
        }
      } catch (Exception e) {
        logger.warn("Error receiving initial messages from peer %s: %s", peerAddress, e.getMessage());
      }

      // Send our bitfield if we have any pieces
      java.util.BitSet ourBitfield = pieceManager.getCompleteBitfield();
      if (ourBitfield.cardinality() > 0) {
        try {
          byte[] bitfieldBytes = bitSetToBytes(ourBitfield, pieceManager.getPieceCount());
          peer.send(new Message(Message.BITFIELD, bitfieldBytes));
          logger.debug("Sent BITFIELD to peer %s (%d pieces)", peerAddress, ourBitfield.cardinality());
        } catch (Exception e) {
          logger.debug("Failed to send BITFIELD: %s", e.getMessage());
        }
      }

      // Send interested message
      try {
        peer.send(new Message(Message.INTERESTED));
        logger.debug("Sent INTERESTED to peer %s", peerAddress);
      } catch (IOException e) {
        logger.warn("Failed to send INTERESTED to peer %s: %s", peerAddress, e.getMessage());
        if (peer != null) {
          try {
            peer.close();
          } catch (Exception ignored) {
          }
        }
        return;
      }

      // Wait for UNCHOKE before sending any REQUEST.
      // Many peers will close the connection if we request while choked.
      long unchokeDeadline = System.currentTimeMillis() + 10000; // 10s
      while (running.get() && peer.peerChoking() && System.currentTimeMillis() < unchokeDeadline) {
        try {
          Message msg = peer.receive();
          if (msg == null) {
            continue;
          }

          if (msg.type() == Message.UNCHOKE) {
            logger.info("Peer %s unchoked us", peerAddress);
            break;
          }

          if (msg.type() == Message.CHOKE) {
            logger.debug("Peer %s is still choking us", peerAddress);
          } else if (msg.type() == Message.KEEP_ALIVE) {
            // Ignore
          } else if (msg.type() == Message.HAVE) {
            byte[] payload = msg.payload();
            if (payload.length >= 4) {
              int pieceIdx = java.nio.ByteBuffer.wrap(payload).getInt();
              peer.updatePeerHasPiece(pieceIdx, pieceManager.getPieceCount());
            }
          } else if (msg.type() == Message.BITFIELD) {
            byte[] bitfieldBytes = msg.payload();
            java.util.BitSet bitfield = bytesToBitSet(bitfieldBytes, pieceManager.getPieceCount());
            peer.setPeerBitfield(bitfield, pieceManager.getPieceCount());

            isPeerSeeder = peer.isPeerSeeder();
            if (isPeerSeeder) {
              connectedSeeders.incrementAndGet();
              logger.info("Peer %s is a SEEDER (has all %d pieces)",
                  peerAddress, pieceManager.getPieceCount());
            }
          } else if (msg.type() == Message.EXTENDED) {
            // Ignore; extension handshake often arrives before/after unchoke
          }
        } catch (java.net.SocketTimeoutException e) {
          // Timeout is normal; keep waiting a bit
        } catch (java.io.EOFException | java.net.SocketException e) {
          logger.debug("Peer %s disconnected while waiting for UNCHOKE: %s", peerAddress, e.getMessage());
          try {
            peer.close();
          } catch (Exception ignored) {
          }
          return;
        } catch (Exception e) {
          logger.debug("Error while waiting for UNCHOKE from %s: %s", peerAddress, e.getMessage());
          try {
            peer.close();
          } catch (Exception ignored) {
          }
          return;
        }
      }

      if (peer.peerChoking()) {
        logger.debug("Peer %s never unchoked us; moving on", peerAddress);
        try {
          peer.close();
        } catch (Exception ignored) {
        }
        return;
      }

      // SUCCESS: peer unchoked us, now set reasonable socket timeout
      try {
        peer.setSocketTimeout(5000); // 5 second timeout to handle network latency
      } catch (IOException e) {
        logger.debug("Failed to set socket timeout: %s", e.getMessage());
      }

      logger.info("Starting download from peer %s", peerAddress);

      // Send UNCHOKE to peer for reciprocity (so they'll upload to us)
      try {
        peer.send(new Message(Message.UNCHOKE));
        logger.debug("Sent UNCHOKE to peer %s (reciprocity)", peerAddress);
      } catch (IOException e) {
        logger.debug("Failed to send UNCHOKE: %s", e.getMessage());
      }

      // Register as active peer for scheduler
      ActivePeer activePeer = new ActivePeer(peerAddress, peer, stats);
      activePeers.put(peerAddress, activePeer);

      // Download pieces - timeout after 5 minutes per peer
      long startTime = System.currentTimeMillis();
      int blocksDownloaded = 0;
      final long maxTime = 300000; // 5 minutes per peer
      long lastSuccessTime = System.currentTimeMillis();
      final long maxIdleTime = 30000; // 30 seconds without progress = move on
      long lastStatsLog = System.currentTimeMillis();

      // Track peer stats
      transferStats.recordPeerConnected();

      while (running.get() && !pieceManager.isComplete()) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > maxTime) {
          logger.debug("Peer %s max time exceeded (%dms), downloaded %d blocks",
              peerAddress, elapsed, blocksDownloaded);
          break;
        }

        // Check for idle timeout (disconnect slow/dead peers)
        if (blocksDownloaded > 0 && System.currentTimeMillis() - lastSuccessTime > maxIdleTime) {
          logger.debug("Peer %s idle for %dms, disconnecting (downloaded %d blocks)",
              peerAddress, System.currentTimeMillis() - lastSuccessTime, blocksDownloaded);
          transferStats.recordPeerFailed();
          break;
        }

        // Disconnect if peer delivered 0 bytes after 60 seconds
        if (blocksDownloaded == 0 && elapsed > 60000) {
          logger.debug("Peer %s delivered 0 blocks in 60s, disconnecting", peerAddress);
          transferStats.recordPeerFailed();
          break;
        }

        try {
          // NOTE: Request issuing is now handled by RequestScheduler.tick()
          // This loop only receives and processes messages

          // Check for incoming messages
          try {
            Message incomingMsg = peer.receive();
            if (incomingMsg != null) {

              if (incomingMsg.type() == Message.PIECE) {
                // SUCCESS! We got a piece block
                byte[] piecePayload = incomingMsg.payload();
                if (piecePayload.length >= 8) {
                  java.nio.ByteBuffer payloadBuf = java.nio.ByteBuffer.wrap(piecePayload);
                  int pieceIndex = payloadBuf.getInt();
                  int offset = payloadBuf.getInt();
                  int blockSize = piecePayload.length - 8;

                  try {
                    // Handle piece data
                    handlePieceData(incomingMsg, pieceIndex);
                    blocksDownloaded++;
                    lastSuccessTime = System.currentTimeMillis();

                    // Mark request fulfilled in tracker
                    requestTracker.fulfillRequest(pieceIndex, offset);

                    // Record success for peer scoring
                    activePeer.recordSuccess();

                    // Update stats
                    if (stats != null) {
                      stats.recordDownload(blockSize);
                      stats.recordSuccess(50); // Assume 50ms latency
                    }

                    // Update choker stats for tit-for-tat
                    if (chokerStats != null) {
                      chokerStats.recordDownload(blockSize);
                    }

                    // Update download state
                    if (downloadState != null) {
                      long currentDownloaded = downloadState.getDownloadedBytes();
                      downloadState.setDownloadedBytes(currentDownloaded + blockSize);
                    }

                    // Update transfer stats (aggregated logging)
                    transferStats.recordDownload(blockSize);

                    // Trigger scheduler to issue more requests immediately
                    requestScheduler.issueMoreRequests(getActivePeerList());

                  } catch (Exception e) {
                    logger.warn("Failed to handle PIECE data: %s", e.getMessage());
                    if (stats != null) {
                      stats.recordFailure();
                    }
                    activePeer.recordFailure();
                  }
                }

              } else if (incomingMsg.type() == Message.UNCHOKE) {
                logger.debug("Peer %s unchoked us (during download)", peerAddress);

              } else if (incomingMsg.type() == Message.CHOKE) {
                logger.debug("Peer %s choked us during download", peerAddress);
                // Cancel pending requests for this peer
                requestTracker.cancelPeerRequests(peerAddress);
                Thread.sleep(500); // Wait before retrying

              } else if (incomingMsg.type() == Message.HAVE) {
                // Update peer's bitfield
                byte[] havePayload = incomingMsg.payload();
                if (havePayload.length >= 4) {
                  int pieceIdx = java.nio.ByteBuffer.wrap(havePayload).getInt();
                  peer.updatePeerHasPiece(pieceIdx, pieceManager.getPieceCount());

                  // Check if peer just became a seeder
                  if (!isPeerSeeder && peer.isPeerSeeder()) {
                    isPeerSeeder = true;
                    connectedSeeders.incrementAndGet();
                    if (stats != null) {
                      stats.setSeeder(true);
                    }
                    logger.info("Peer %s is now a SEEDER (completed all pieces)", peerAddress);
                  }
                }

              } else if (incomingMsg.type() == Message.REQUEST) {
                // INCOMING REQUEST FROM PEER - they want a block from us!
                byte[] reqPayload = incomingMsg.payload();
                if (reqPayload != null && reqPayload.length >= 12) {
                  java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(reqPayload);
                  int pieceIdx = buf.getInt();
                  int offset = buf.getInt();
                  int length = buf.getInt();

                  logger.info("[UPLOAD-REQUEST] Peer %s requested piece=%d offset=%d len=%d",
                      peerAddress, pieceIdx, offset, length);

                  // Track upload request
                  transferStats.recordUploadRequest();

                  // Check if we should send (not choking this peer)
                  boolean shouldSend = true;
                  if (choker != null && chokerStats != null && chokerStats.isChoked()) {
                    logger.debug("Not sending to %s - peer is CHOKED", peerAddress);
                    shouldSend = false;
                  }

                  if (shouldSend) {
                    // Use UploadHandler to send piece
                    uploadHandler.sendPiece(peer, peerAddress, pieceIdx, offset, length,
                        stats, chokerStats);
                  }
                }

              } else if (incomingMsg.type() == Message.KEEP_ALIVE) {
                // Ignore keep-alive

              } else if (incomingMsg.type() == Message.INTERESTED) {
                logger.info("[PEER-INTERESTED] Peer %s is INTERESTED in us", peerAddress);
                if (chokerStats != null) {
                  chokerStats.setPeerInterested(true);
                  logger.info("[PEER-INTERESTED] Peer %s marked as interested (choked=%s)",
                      peerAddress, chokerStats.isChoked());
                }

              } else if (incomingMsg.type() == Message.NOT_INTERESTED) {
                logger.info("[PEER-NOT-INTERESTED] Peer %s is NOT_INTERESTED in us", peerAddress);
                if (chokerStats != null) {
                  chokerStats.setPeerInterested(false);
                }
              }
            }
          } catch (java.net.SocketTimeoutException e) {
            // Timeout - no message available, continue
            logger.debug("Socket timeout for peer %s, continuing", peerAddress);
          } catch (java.io.EOFException | java.net.SocketException e) {
            logger.info("Peer %s disconnected: %s", peerAddress, e.getMessage());
            break;
          } catch (java.io.IOException e) {
            // Stream might be out of sync after a block, but we can recover
            if (blocksDownloaded > 0) {
              logger.debug("Peer %s IO error (stream may be out of sync, continuing): %s", peerAddress, e.getMessage());
              Thread.sleep(100);
              continue;
            } else {
              logger.warn("Peer %s IO error (no blocks yet): %s", peerAddress, e.getMessage());
              break;
            }
          }

          // Very small delay to prevent busy-waiting but keep loop fast
          Thread.sleep(5);

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          logger.warn("Error in download loop with peer %s: %s", peerAddress, e.getMessage());
          Thread.sleep(100);
        }
      }

      logger.info("Finished with peer %s (downloaded %d blocks in %dms)", peerAddress,
          blocksDownloaded, System.currentTimeMillis() - startTime);

      try {
        peer.close();
      } catch (Exception ignored) {
      }
    } catch (

    Exception e) {
      logger.warn("Failed to connect to peer %s: %s", peerAddress, e.getMessage());
      if (peer != null) {
        try {
          peer.close();
        } catch (Exception ignored) {
        }
      }
    } finally {
      // Remove from active peers
      activePeers.remove(peerAddress);

      // Clean shutdown - remove from connection manager
      String peerIdStr = peerAddress.toString();
      if (connectionManager != null) {
        connectionManager.removePeer(peerAddress, "worker finished");
      }

      // Remove from choker
      if (choker != null) {
        choker.removePeer(peerIdStr);
      }

      // Decrement seeder count if peer was a seeder
      if (isPeerSeeder) {
        connectedSeeders.decrementAndGet();
      }

      // Ensure peer connection is closed
      if (peer != null) {
        try {
          peer.close();
        } catch (Exception e) {
          logger.debug("Error closing peer %s: %s", peerAddress, e.getMessage());
        }
      }
    }
  }

  /**
   * Select the next piece to download from this specific peer.
   *
   * @param peerBitfield the peer's bitfield (may be null)
   * @return piece index, or -1 if no suitable piece
   */
  private int selectNextPieceForPeer(java.util.BitSet peerBitfield) {
    // If peer has no bitfield, we don't know what they have
    if (peerBitfield == null) {
      return -1;
    }

    // Find pieces we need that the peer has
    // Only select pieces that are MISSING or DOWNLOADING (not COMPLETE)
    for (int i = 0; i < pieceManager.getPieceCount(); i++) {
      PieceState state = pieceManager.getPieceState(i);
      if ((state == PieceState.MISSING || state == PieceState.DOWNLOADING)
          && peerBitfield.get(i)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Handle received piece data.
   */
  private void handlePieceData(Message msg, int pieceIndex) throws Exception {
    byte[] payload = msg.payload();
    if (payload.length < 8)
      return;

    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(payload);
    int index = buf.getInt();
    int begin = buf.getInt();

    if (index != pieceIndex)
      return;

    // Extract block data
    byte[] blockData = new byte[payload.length - 8];
    System.arraycopy(payload, 8, blockData, 0, blockData.length);

    // Write to disk
    if (diskManager != null) {
      diskManager.writeBlock(pieceIndex, begin, blockData);
    }

    // Mark block as received and check if piece is complete
    boolean pieceComplete = pieceManager.markBlockReceived(pieceIndex, begin);

    if (pieceComplete) {
      logger.info("All blocks received for piece %d, verifying...", pieceIndex);

      // Verify the piece
      if (pieceVerifier != null) {
        try {
          boolean valid = pieceVerifier.verifyPiece(pieceIndex);
          if (valid) {
            pieceManager.setPieceState(pieceIndex, PieceState.COMPLETE);
            logger.info("✓ Piece %d verified and marked COMPLETE", pieceIndex);

            // Update download state
            if (downloadState != null) {
              downloadState.markPieceComplete(pieceIndex);
            }
          } else {
            logger.warn("✗ Piece %d verification FAILED - will re-download", pieceIndex);
            pieceManager.setPieceState(pieceIndex, PieceState.MISSING);
          }
        } catch (Exception e) {
          logger.error("Error verifying piece %d: %s", pieceIndex, e.getMessage());
          pieceManager.setPieceState(pieceIndex, PieceState.MISSING);
        }
      } else {
        // No verifier available, mark as complete without verification
        pieceManager.setPieceState(pieceIndex, PieceState.COMPLETE);
        logger.warn("Piece %d marked COMPLETE without verification (no verifier)", pieceIndex);

        if (downloadState != null) {
          downloadState.markPieceComplete(pieceIndex);
        }
      }
    }
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

  /**
   * Convert byte array to BitSet for bitfield handling.
   *
   * @param bytes     the byte array
   * @param numPieces total number of pieces
   * @return BitSet representation
   */
  private java.util.BitSet bytesToBitSet(byte[] bytes, int numPieces) {
    java.util.BitSet bitset = new java.util.BitSet(numPieces);
    for (int i = 0; i < numPieces && i < bytes.length * 8; i++) {
      int byteIndex = i / 8;
      int bitIndex = 7 - (i % 8);
      if ((bytes[byteIndex] & (1 << bitIndex)) != 0) {
        bitset.set(i);
      }
    }
    return bitset;
  }

  /**
   * Convert BitSet to byte array for bitfield messages.
   *
   * @param bitset    the BitSet
   * @param numPieces total number of pieces
   * @return byte array representation
   */
  private byte[] bitSetToBytes(java.util.BitSet bitset, int numPieces) {
    int numBytes = (numPieces + 7) / 8;
    byte[] bytes = new byte[numBytes];
    for (int i = 0; i < numPieces; i++) {
      if (bitset.get(i)) {
        int byteIndex = i / 8;
        int bitIndex = 7 - (i % 8);
        bytes[byteIndex] |= (1 << bitIndex);
      }
    }
    return bytes;
  }

  /**
   * Check if session is running.
   *
   * @return true if running
   */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Check if download completed.
   *
   * @return true if completed
   */
  public boolean isCompleted() {
    return completed.get();
  }

  /**
   * Get download state for progress tracking.
   *
   * @return download state or null if not initialized
   */
  public DownloadState getDownloadState() {
    return downloadState;
  }

  /**
   * Get torrent file metadata.
   *
   * @return torrent file or null if not loaded
   */
  public TorrentFile getTorrentFile() {
    return torrentFile;
  }

  /**
   * Tracker-reported seeders (from last successful announce).
   */
  public int getTrackerSeeders() {
    return Math.toIntExact(totalSeeders.get());
  }

  /**
   * Tracker-reported leechers (from last successful announce).
   */
  public int getTrackerLeechers() {
    return Math.toIntExact(totalLeechers.get());
  }

  /**
   * Connected peers currently registered as active.
   */
  public int getConnectedPeerCount() {
    int active = activePeers != null ? activePeers.size() : 0;
    int estimated = Math.toIntExact(connectedPeersEstimate.get());
    return Math.max(active, estimated);
  }

  /**
   * Connected peers that appear to be seeders (have all pieces).
   */
  public int getConnectedSeederCount() {
    return Math.toIntExact(connectedSeeders.get());
  }

  /**
   * Snapshot of currently active peers.
   */
  public List<ActivePeer> getActivePeersSnapshot() {
    if (activePeers == null || activePeers.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(activePeers.values());
  }

  /**
   * Snapshot of completed pieces for this session (best-effort).
   */
  public BitSet getCompletedPiecesSnapshot() {
    if (downloadState == null) {
      return new BitSet();
    }
    return downloadState.getCompletedPieces();
  }

  /**
   * Force recheck all pieces by verifying against hashes.
   * This should be called when the session is stopped.
   * 
   * @return number of valid pieces found, or -1 if recheck failed
   */
  public int forceRecheck() {
    if (running.get()) {
      logger.warn("Cannot recheck while session is running. Stop the session first.");
      return -1;
    }

    if (torrentFile == null) {
      logger.warn("Cannot recheck: torrent metadata not loaded");
      return -1;
    }

    if (pieceManager == null || diskManager == null) {
      logger.warn("Cannot recheck: storage not initialized");
      return -1;
    }

    logger.info("Starting force recheck of all pieces...");

    try {
      // Convert List<byte[]> pieces to concatenated byte array
      List<byte[]> piecesList = torrentFile.pieces();
      byte[] pieceHashes = new byte[piecesList.size() * 20];
      for (int i = 0; i < piecesList.size(); i++) {
        System.arraycopy(piecesList.get(i), 0, pieceHashes, i * 20, 20);
      }

      // Create a piece verifier
      PieceVerifier verifier = new PieceVerifier(diskManager, pieceManager, pieceHashes);

      int validPieces = verifier.recheckAllPieces();

      // Update download state
      if (downloadState != null) {
        // Clear and re-mark all completed pieces
        for (int i = 0; i < pieceManager.getPieceCount(); i++) {
          if (pieceManager.getPieceState(i) == PieceState.COMPLETE) {
            downloadState.markPieceComplete(i);
          }
        }

        // Recalculate downloaded bytes
        long downloadedBytes = 0;
        for (int i = 0; i < pieceManager.getPieceCount(); i++) {
          if (pieceManager.getPieceState(i) == PieceState.COMPLETE) {
            downloadedBytes += pieceManager.getPieceLength(i);
          }
        }
        downloadState.setDownloadedBytes(downloadedBytes);

        // Save state
        Path stateFile = Paths.get(config.getStateDirectory(), downloadState.getStateFileName());
        downloadState.save(stateFile);
      }

      logger.info("Force recheck complete: %d/%d pieces valid", validPieces, pieceManager.getPieceCount());
      return validPieces;

    } catch (Exception e) {
      logger.error("Force recheck failed: %s", e.getMessage());
      return -1;
    }
  }

  /**
   * Force reannounce to all trackers immediately.
   * Useful for refreshing peer list.
   * 
   * @return number of new peers discovered, or -1 if reannounce failed
   */
  public int forceReannounce() {
    if (torrentFile == null) {
      logger.warn("Cannot reannounce: torrent metadata not loaded");
      return -1;
    }

    // Collect all tracker URLs
    Set<String> allTrackers = new LinkedHashSet<>();

    // Add primary announce URL
    if (torrentFile.primaryAnnounce() != null) {
      allTrackers.add(torrentFile.primaryAnnounce().toString());
    }

    // Add announce list URLs
    if (torrentFile.announceList() != null) {
      for (URI uri : torrentFile.announceList()) {
        allTrackers.add(uri.toString());
      }
    }

    // Add fallback trackers
    allTrackers.addAll(getFallbackTrackers());

    if (allTrackers.isEmpty()) {
      logger.warn("No trackers configured for reannounce");
      return 0;
    }

    logger.info("Reannouncing to %d tracker(s)...", allTrackers.size());

    // Thread-safe peer collection
    Set<InetSocketAddress> newPeers = ConcurrentHashMap.newKeySet();

    // Query all trackers in parallel
    ExecutorService executor = Executors.newFixedThreadPool(Math.min(allTrackers.size(), 4));
    List<Future<Void>> futures = new ArrayList<>();

    for (String trackerUrl : allTrackers) {
      Future<Void> future = executor.submit(() -> {
        try {
          List<PeerEndpoint> peers = queryTracker(trackerUrl);
          for (PeerEndpoint peer : peers) {
            newPeers.add(peer.address());
          }
          logger.info("Tracker %s responded with %d peer(s)", trackerUrl, peers.size());
        } catch (Exception e) {
          logger.warn("Tracker %s failed: %s", trackerUrl, e.getMessage());
        }
        return null;
      });
      futures.add(future);
    }

    // Wait for all tracker queries to complete (with timeout)
    executor.shutdown();
    try {
      executor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Note: New peers discovered will be picked up by the regular peer discovery
    // loop
    // when the session is running. We just report how many we found.

    logger.info("Force reannounce complete: found %d unique peer(s)", newPeers.size());
    return newPeers.size();
  }

  /**
   * Get the torrent name (from metadata or filename).
   */
  public String getName() {
    if (torrentFile != null && torrentFile.name() != null) {
      return torrentFile.name();
    }
    if (input != null) {
      String path = input.getPath();
      if (path != null) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
      }
    }
    return "Unknown";
  }

  /**
   * Get current seed ratio (uploaded / downloaded).
   */
  public double getSeedRatio() {
    if (downloadState == null) {
      return 0.0;
    }
    long downloaded = downloadState.getDownloadedBytes();
    if (downloaded == 0) {
      return 0.0;
    }
    long uploaded = transferStats != null ? transferStats.getUploadedBytes() : 0;
    return (double) uploaded / downloaded;
  }

  /**
   * Get seeding time in milliseconds.
   */
  public long getSeedingTime() {
    if (downloadState == null) {
      return 0;
    }
    return downloadState.getSeedingTime();
  }

  /**
   * Check if download is complete (all pieces verified).
   */
  public boolean isComplete() {
    if (pieceManager == null) {
      return false;
    }
    return pieceManager.isComplete();
  }
}
