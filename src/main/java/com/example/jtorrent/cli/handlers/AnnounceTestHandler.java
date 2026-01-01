package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.tracker.AnnounceRequest;
import com.example.jtorrent.tracker.AnnounceResponse;
import com.example.jtorrent.tracker.HttpTrackerClient;
import com.example.jtorrent.tracker.PeerEndpoint;
import com.example.jtorrent.tracker.TrackerException;
import com.example.jtorrent.tracker.UdpTrackerClient;
import com.example.jtorrent.util.FormatUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handler for testing tracker announce functionality.
 */
public final class AnnounceTestHandler implements CommandHandler {

  @Override
  public void execute(CliArgs args) {
    if (args.tracker == null) {
      System.err.println("Error: --announce-test requires --tracker <uri>");
      System.exit(1);
    }

    File file = new File(args.announceTest);
    if (!file.exists()) {
      System.err.println("Error: File not found: " + args.announceTest);
      System.exit(1);
    }

    try {
      TorrentFile torrent = TorrentFile.parse(file);
      URI tracker = new URI(args.tracker);

      printHeader(torrent, tracker);

      AnnounceRequest request = createRequest(torrent);
      AnnounceResponse response = performAnnounce(tracker, request);

      printResponse(response);

    } catch (IOException e) {
      System.err.println("Error reading torrent file: " + e.getMessage());
      System.exit(1);
    } catch (BencodeException e) {
      System.err.println("Error parsing torrent: " + e.getMessage());
      System.exit(1);
    } catch (URISyntaxException e) {
      System.err.println("Error: Invalid tracker URI: " + e.getMessage());
      System.exit(1);
    } catch (TrackerException e) {
      System.err.println("Tracker error: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printHeader(TorrentFile torrent, URI tracker) {
    System.out.println("Announce Test");
    System.out.println("==============");
    System.out.println();
    System.out.println("Torrent: " + torrent.name());
    System.out.println("Info Hash: " + torrent.infoHashHex());
    System.out.println("Tracker: " + tracker);
    System.out.println();
  }

  private static AnnounceRequest createRequest(TorrentFile torrent) {
    return new AnnounceRequest(
        torrent.infoHash(),
        FormatUtils.generatePeerId(),
        6881,
        0,
        0,
        torrent.totalLength(),
        AnnounceRequest.Event.STARTED);
  }

  private static AnnounceResponse performAnnounce(URI tracker, AnnounceRequest request)
      throws TrackerException {
    String scheme = tracker.getScheme();

    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
      System.out.println("Using HTTP tracker client...");
      try (HttpTrackerClient client = new HttpTrackerClient(tracker)) {
        return client.announce(request);
      }
    } else if ("udp".equalsIgnoreCase(scheme)) {
      System.out.println("Using UDP tracker client...");
      try (UdpTrackerClient client = new UdpTrackerClient(tracker)) {
        return client.announce(request);
      }
    } else {
      System.err.println("Error: Unsupported tracker scheme: " + scheme);
      System.exit(1);
      return null; // Unreachable
    }
  }

  private static void printResponse(AnnounceResponse response) {
    System.out.println();

    if (response.isFailure()) {
      System.err.println("Tracker returned failure: " + response.failureReason());
      System.exit(1);
    }

    System.out.println("Response:");
    System.out.println("  Interval: " + response.interval() + " seconds");
    System.out.println("  Seeders: " + response.seeders());
    System.out.println("  Leechers: " + response.leechers());
    System.out.println("  Peers: " + response.peers().size());
    System.out.println();

    printPeers(response);

    if (response.warningMessage() != null) {
      System.out.println();
      System.out.println("Warning: " + response.warningMessage());
    }
  }

  private static void printPeers(AnnounceResponse response) {
    if (!response.peers().isEmpty()) {
      System.out.println("Peer Endpoints:");
      int idx = 1;
      for (PeerEndpoint peer : response.peers()) {
        System.out.println("  " + idx + ". " + peer);
        idx++;
      }
    } else {
      System.out.println("No peers returned.");
    }
  }
}
