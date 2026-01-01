package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.dht.DhtNode;
import com.example.jtorrent.dht.MagnetLink;
import com.example.jtorrent.util.FormatUtils;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for resolving magnet links via DHT.
 */
public final class MagnetResolverHandler implements CommandHandler {

  private static final int DHT_PORT = 0; // Random port
  private static final int BOOTSTRAP_WAIT_MS = 5000;

  private static final String[] BOOTSTRAP_NODES = {
      "router.bittorrent.com:6881",
      "dht.transmissionbt.com:6881",
      "router.utorrent.com:6881"
  };

  @Override
  public void execute(CliArgs args) {
    System.out.println("Magnet Link Resolver");
    System.out.println("====================");
    System.out.println();

    try {
      MagnetLink magnet = parseMagnetLink(args.magnetUri);
      printMagnetInfo(magnet);

      try (DhtNode dhtNode = new DhtNode(DHT_PORT)) {
        printDhtNodeInfo(dhtNode);
        bootstrapDht(dhtNode);
        List<InetSocketAddress> peers = queryForPeers(dhtNode, magnet);
        printPeers(peers);

        System.out.println("Magnet link resolution completed!");
      }

    } catch (IllegalArgumentException e) {
      System.err.println("Error parsing magnet link: " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static MagnetLink parseMagnetLink(String magnetUri) {
    System.out.println("Parsing magnet link...");
    return MagnetLink.parse(magnetUri);
  }

  private static void printMagnetInfo(MagnetLink magnet) {
    System.out.println("Info Hash: " + magnet.getInfoHashHex());
    if (magnet.getDisplayName() != null) {
      System.out.println("Name: " + magnet.getDisplayName());
    }
    if (!magnet.getTrackers().isEmpty()) {
      System.out.println("Trackers:");
      for (String tracker : magnet.getTrackers()) {
        System.out.println("  - " + tracker);
      }
    }
    System.out.println();
  }

  private static void printDhtNodeInfo(DhtNode dhtNode) {
    System.out.println("Starting DHT node...");
    System.out.println("DHT node listening on port: " + dhtNode.getPort());
    System.out.println("Node ID: " + FormatUtils.bytesToHex(dhtNode.getNodeId()));
    System.out.println();
  }

  private static void bootstrapDht(DhtNode dhtNode) throws InterruptedException {
    System.out.println("Bootstrapping DHT...");

    List<InetSocketAddress> bootstrapNodes = createBootstrapNodeList();
    dhtNode.bootstrap(bootstrapNodes);

    System.out.println("Waiting for DHT to bootstrap...");
    Thread.sleep(BOOTSTRAP_WAIT_MS);
    System.out.println();
  }

  private static List<InetSocketAddress> createBootstrapNodeList() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    for (String node : BOOTSTRAP_NODES) {
      String[] parts = node.split(":");
      nodes.add(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
    }
    return nodes;
  }

  private static List<InetSocketAddress> queryForPeers(DhtNode dhtNode, MagnetLink magnet) {
    System.out.println("Querying DHT for peers...");
    return dhtNode.getPeers(magnet.getInfoHash());
  }

  private static void printPeers(List<InetSocketAddress> peers) {
    System.out.println("Found " + peers.size() + " peer(s):");
    if (peers.isEmpty()) {
      System.out.println("  (No peers found via DHT)");
      System.out.println();
      System.out.println("Note: DHT peer discovery may take time.");
      System.out.println("Try using trackers from the magnet link for faster results.");
    } else {
      for (InetSocketAddress peer : peers) {
        System.out.println("  - " + peer.getAddress().getHostAddress() + ":" + peer.getPort());
      }
    }
    System.out.println();
  }
}
