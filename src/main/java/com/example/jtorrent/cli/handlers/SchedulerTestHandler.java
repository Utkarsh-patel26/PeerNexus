package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.scheduler.Choker;
import com.example.jtorrent.scheduler.PeerStats;
import java.security.SecureRandom;
import java.util.Set;

/**
 * Handler for testing the scheduling and choking algorithm.
 */
public final class SchedulerTestHandler implements CommandHandler {

  private static final int NUM_PEERS = 10;
  private static final int UPLOAD_SLOTS = 4;
  private static final int RATE_CALCULATION_PERIOD_MS = 100;
  private static final int KB = 1024;

  @Override
  public void execute(CliArgs args) {
    System.out.println("Scheduler Test");
    System.out.println("==============");
    System.out.println();

    Choker choker = new Choker(UPLOAD_SLOTS);
    SecureRandom random = new SecureRandom();

    printConfiguration();
    setupPeers(choker, random);

    try {
      Thread.sleep(RATE_CALCULATION_PERIOD_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    choker.updateAllRates(RATE_CALCULATION_PERIOD_MS);

    runChokingRounds(choker);
    printSummary(choker);

    System.out.println("Scheduler test completed successfully!");
  }

  private static void printConfiguration() {
    System.out.println("Configuration:");
    System.out.println("  Peers: " + NUM_PEERS);
    System.out.println("  Upload Slots: " + UPLOAD_SLOTS);
    System.out.println();
  }

  private static void setupPeers(Choker choker, SecureRandom random) {
    System.out.println("Peer Setup:");
    System.out.println("-----------");

    for (int i = 1; i <= NUM_PEERS; i++) {
      String peerId = "peer" + i;
      PeerStats stats = choker.addPeer(peerId);

      stats.setPeerInterested(true);

      int downloadRate = i * 10 * KB;
      for (int j = 0; j < downloadRate / 10; j++) {
        stats.recordDownload(10);
      }

      int uploadRate = random.nextInt(50) * KB;
      for (int j = 0; j < uploadRate / 10; j++) {
        stats.recordUpload(10);
      }

      System.out.printf("  %s: downloads %d KB/s from us%n", peerId, i * 10);
    }

    System.out.println();
  }

  private static void runChokingRounds(Choker choker) {
    System.out.println("Running Choking Algorithm:");
    System.out.println("--------------------------");

    for (int round = 1; round <= 3; round++) {
      System.out.println("Round " + round + ":");

      Set<String> unchoked = choker.runChokingAlgorithm();

      printUnchokedPeers(choker, unchoked);
      printChokedPeers(choker, unchoked);

      System.out.println();

      if (round < 3) {
        choker.forceOptimisticRotation();
      }
    }
  }

  private static void printUnchokedPeers(Choker choker, Set<String> unchoked) {
    System.out.println("  Unchoked peers:");
    for (String peerId : unchoked) {
      PeerStats stats = choker.getPeerStats(peerId);
      String marker = peerId.equals(choker.getOptimisticUnchokedPeer()) ? " [OPTIMISTIC]" : "";
      System.out.printf("    %s: down=%.1f KB/s%s%n",
          peerId, stats.getDownloadRateBytesPerSec() / (double) KB, marker);
    }
  }

  private static void printChokedPeers(Choker choker, Set<String> unchoked) {
    System.out.println("  Choked peers:");
    for (int i = 1; i <= NUM_PEERS; i++) {
      String peerId = "peer" + i;
      if (!unchoked.contains(peerId)) {
        PeerStats stats = choker.getPeerStats(peerId);
        System.out.printf("    %s: down=%.1f KB/s%n",
            peerId, stats.getDownloadRateBytesPerSec() / (double) KB);
      }
    }
  }

  private static void printSummary(Choker choker) {
    System.out.println("Summary:");
    System.out.println("--------");
    System.out.println("  Total Peers: " + choker.getPeerCount());
    System.out.println("  Active Uploads: " + choker.getActiveUploadCount());
    System.out.printf("  Total Upload Rate: %.1f KB/s%n", choker.getTotalUploadRate() / (double) KB);
    System.out.printf("  Total Download Rate: %.1f KB/s%n",
        choker.getTotalDownloadRate() / (double) KB);
    System.out.println();
  }
}
