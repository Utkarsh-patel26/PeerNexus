package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.FileEntry;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.util.FormatUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Handler for validating and displaying torrent file information.
 */
public final class ValidateTorrentHandler implements CommandHandler {

  @Override
  public void execute(CliArgs args) {
    File file = new File(args.validateTorrent);

    if (!file.exists()) {
      System.err.println("Error: File not found: " + args.validateTorrent);
      System.exit(1);
    }

    if (!file.isFile()) {
      System.err.println("Error: Not a file: " + args.validateTorrent);
      System.exit(1);
    }

    try {
      TorrentFile torrent = TorrentFile.parse(file);

      printHeader();
      printInfoHash(torrent);
      printBasicInfo(torrent);
      printTrackers(torrent);
      printFiles(torrent);
      System.out.println("Validation: OK");

    } catch (IOException e) {
      System.err.println("Error reading file: " + e.getMessage());
      System.exit(1);
    } catch (BencodeException e) {
      System.err.println("Error parsing torrent: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printHeader() {
    System.out.println("Torrent File Validation");
    System.out.println("========================");
    System.out.println();
  }

  private static void printInfoHash(TorrentFile torrent) {
    System.out.println("Info Hash: " + torrent.infoHashHex());
    System.out.println();
  }

  private static void printBasicInfo(TorrentFile torrent) {
    System.out.println("Name: " + torrent.name());
    System.out.println("Total Size: " + FormatUtils.formatSize(torrent.totalLength()));
    System.out.println("Piece Length: " + FormatUtils.formatSize(torrent.pieceLength()));
    System.out.println("Piece Count: " + torrent.pieceCount());
    System.out.println("Type: " + (torrent.isSingleFile() ? "Single File" : "Multi-File"));
    System.out.println();
  }

  private static void printTrackers(TorrentFile torrent) {
    System.out.println("Trackers:");
    if (torrent.primaryAnnounce() != null) {
      System.out.println("  Primary: " + torrent.primaryAnnounce());
    }
    if (!torrent.announceList().isEmpty()) {
      System.out.println("  Announce List:");
      int idx = 1;
      for (URI uri : torrent.announceList()) {
        System.out.println("    " + idx + ". " + uri);
        idx++;
      }
    }
    System.out.println();
  }

  private static void printFiles(TorrentFile torrent) {
    System.out.println("Files:");
    for (FileEntry entry : torrent.files()) {
      System.out.println("  " + entry.path());
      System.out.println("    Size: " + FormatUtils.formatSize(entry.length()));
    }
    System.out.println();
  }
}
