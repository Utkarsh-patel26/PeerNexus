package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.storage.DiskManager;
import com.example.jtorrent.storage.PieceManager;
import com.example.jtorrent.storage.PieceState;
import com.example.jtorrent.util.FormatUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Handler for testing storage with simulated download and verification.
 */
public final class StorageTestHandler implements CommandHandler {

  private static final int PROGRESS_REPORT_INTERVAL = 10;

  @Override
  public void execute(CliArgs args) {
    File file = new File(args.storageTest);

    if (!file.exists()) {
      System.err.println("Error: File not found: " + args.storageTest);
      System.exit(1);
    }

    try {
      TorrentFile torrent = TorrentFile.parse(file);

      printHeader(torrent);

      Path tempDir = Files.createTempDirectory("jtorrent-storage-test-");
      System.out.println("Test directory: " + tempDir);
      System.out.println();

      try {
        runStorageTest(torrent, tempDir);
      } finally {
        cleanupTestDirectory(tempDir);
      }

    } catch (IOException e) {
      System.err.println("Error reading torrent file: " + e.getMessage());
      System.exit(1);
    } catch (BencodeException e) {
      System.err.println("Error parsing torrent: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printHeader(TorrentFile torrent) {
    System.out.println("Storage Test");
    System.out.println("============");
    System.out.println();
    System.out.println("Torrent: " + torrent.name());
    System.out.println("Info Hash: " + torrent.infoHashHex());
    System.out.println("Total Size: " + FormatUtils.formatSize(torrent.totalLength()));
    System.out.println("Piece Count: " + torrent.pieceCount());
    System.out.println("Piece Length: " + FormatUtils.formatSize(torrent.pieceLength()));
    System.out.println();
  }

  private static void runStorageTest(TorrentFile torrent, Path tempDir) throws IOException {
    PieceManager pm = new PieceManager(
        torrent.pieceCount(), torrent.pieceLength(), torrent.totalLength());
    DiskManager dm = new DiskManager(
        tempDir, torrent.files(), torrent.pieceLength(), torrent.pieceCount(),
        torrent.totalLength());

    System.out.println("Simulating download...");
    System.out.println();

    int completed = simulateDownload(pm, dm, torrent);

    printDownloadResults(pm, completed, torrent.pieceCount());
    testRecovery(pm, dm, torrent);

    dm.close();

    System.out.println("Storage test completed successfully!");
  }

  private static int simulateDownload(PieceManager pm, DiskManager dm, TorrentFile torrent)
      throws IOException {
    int completed = 0;

    for (int pieceIdx = 0; pieceIdx < torrent.pieceCount(); pieceIdx++) {
      int pieceLength = pm.getPieceLength(pieceIdx);
      byte[] pieceData = generatePieceData(pieceIdx, pieceLength);

      int[] blockOrder = generateShuffledBlockOrder(pm, pieceIdx);
      writeBlocksOutOfOrder(pm, dm, pieceIdx, pieceData, blockOrder, pieceLength);

      dm.flushPiece(pieceIdx);
      pm.setPieceState(pieceIdx, PieceState.COMPLETE);
      completed++;

      if (completed % PROGRESS_REPORT_INTERVAL == 0 || completed == torrent.pieceCount()) {
        printProgress(completed, torrent.pieceCount());
      }
    }

    return completed;
  }

  private static byte[] generatePieceData(int pieceIdx, int length) {
    byte[] data = new byte[length];
    for (int i = 0; i < length; i++) {
      data[i] = (byte) ((pieceIdx * 1000 + i) % 256);
    }
    return data;
  }

  private static int[] generateShuffledBlockOrder(PieceManager pm, int pieceIdx) {
    int blockCount = pm.getBlockCount(pieceIdx);
    int[] blockOrder = new int[blockCount];

    for (int b = 0; b < blockCount; b++) {
      blockOrder[b] = b;
    }

    // Deterministic shuffle
    for (int b = blockCount - 1; b > 0; b--) {
      int j = (pieceIdx * 7 + b * 13) % (b + 1);
      int tmp = blockOrder[b];
      blockOrder[b] = blockOrder[j];
      blockOrder[j] = tmp;
    }

    return blockOrder;
  }

  private static void writeBlocksOutOfOrder(PieceManager pm, DiskManager dm, int pieceIdx,
      byte[] pieceData, int[] blockOrder, int pieceLength) throws IOException {
    for (int blockNum : blockOrder) {
      int offset = blockNum * PieceManager.BLOCK_SIZE;
      int length = Math.min(PieceManager.BLOCK_SIZE, pieceLength - offset);
      byte[] blockData = new byte[length];
      System.arraycopy(pieceData, offset, blockData, 0, length);

      pm.markBlockReceived(pieceIdx, offset);
      dm.writeBlock(pieceIdx, offset, blockData);
    }
  }

  private static void printProgress(int completed, int total) {
    System.out.printf("Progress: %d/%d pieces (%.1f%%)%n",
        completed, total, 100.0 * completed / total);
  }

  private static void printDownloadResults(PieceManager pm, int completed, int totalPieces) {
    System.out.println();
    System.out.println("Download simulation complete!");
    System.out.println("Completed pieces: " + pm.getCompletedCount());
    System.out.println("Is complete: " + pm.isComplete());
    System.out.println();
  }

  private static void testRecovery(PieceManager pm, DiskManager dm, TorrentFile torrent)
      throws IOException {
    System.out.println("Testing recovery (re-checking pieces)...");

    // Reset all pieces to missing
    for (int i = 0; i < torrent.pieceCount(); i++) {
      pm.setPieceState(i, PieceState.MISSING);
    }

    // Verify pieces match what we wrote
    int validCount = verifyAllPieces(pm, dm, torrent);

    System.out.println("Recovery complete: " + validCount + "/" + torrent.pieceCount()
        + " pieces verified");
    System.out.println();
  }

  private static int verifyAllPieces(PieceManager pm, DiskManager dm, TorrentFile torrent)
      throws IOException {
    int validCount = 0;

    for (int pieceIdx = 0; pieceIdx < torrent.pieceCount(); pieceIdx++) {
      int pieceLength = pm.getPieceLength(pieceIdx);
      byte[] expectedData = generatePieceData(pieceIdx, pieceLength);
      byte[] actualData = dm.readPiece(pieceIdx, pieceLength);

      if (Arrays.equals(expectedData, actualData)) {
        pm.setPieceState(pieceIdx, PieceState.COMPLETE);
        validCount++;
      }
    }

    return validCount;
  }

  private static void cleanupTestDirectory(Path tempDir) {
    System.out.println();
    System.out.println("Cleaning up test directory...");
    try {
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .forEach(p -> {
            try {
              Files.delete(p);
            } catch (IOException e) {
              // Ignore cleanup errors
            }
          });
      System.out.println("Cleanup complete.");
    } catch (IOException e) {
      System.err.println("Warning: Could not clean up test directory: " + e.getMessage());
    }
  }
}
