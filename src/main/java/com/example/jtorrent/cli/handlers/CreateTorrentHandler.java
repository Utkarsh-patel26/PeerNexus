package com.example.jtorrent.cli.handlers;

import com.example.jtorrent.cli.CliArgs;
import com.example.jtorrent.cli.CommandHandler;
import com.example.jtorrent.metadata.TorrentCreator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * CLI handler for creating .torrent files.
 * Usage: --create --input <path> --output <file> [--tracker <url>]...
 * [--comment <text>]
 * [--private] [--piece-size <bytes>]
 */
public class CreateTorrentHandler implements CommandHandler {

    @Override
    public void execute(CliArgs args) {
        String input = args.input;
        String output = args.output;

        if (input == null || input.isEmpty()) {
            System.err.println("Error: --input is required");
            System.err.println("Usage: --create --input <path> --output <file> [--tracker <url>]...");
            System.exit(1);
        }

        File inputFile = new File(input);
        if (!inputFile.exists()) {
            System.err.println("Error: Input path does not exist: " + input);
            System.exit(1);
        }

        // Default output path if not specified
        if (output == null || output.isEmpty()) {
            output = input + ".torrent";
        }

        File outputFile = new File(output);
        if (outputFile.exists() && !args.force) {
            System.err.println("Error: Output file already exists: " + output);
            System.err.println("Use --force to overwrite");
            System.exit(1);
        }

        System.out.println("Creating torrent file...");
        System.out.println("  Input: " + inputFile.getAbsolutePath());
        System.out.println("  Output: " + outputFile.getAbsolutePath());

        // Build TorrentCreator
        TorrentCreator.Builder builder = new TorrentCreator.Builder();

        // Add trackers
        if (args.trackers != null && !args.trackers.isEmpty()) {
            builder.addTrackers(args.trackers);
            System.out.println("  Trackers: " + args.trackers.size());
        } else {
            System.out.println("  Trackers: none (DHT-only torrent)");
        }

        // Add comment
        if (args.comment != null && !args.comment.isEmpty()) {
            builder.setComment(args.comment);
        }

        // Set private flag
        if (args.privateTorrent) {
            builder.setPrivate(true);
            System.out.println("  Private: yes");
        }

        // Set piece size
        if (args.pieceSize > 0) {
            builder.setPieceSize(args.pieceSize);
            System.out.println("  Piece size: " + formatBytes(args.pieceSize));
        } else {
            System.out.println("  Piece size: auto");
        }

        TorrentCreator creator = builder.build();

        // Create torrent with progress callback
        try {
            long startTime = System.currentTimeMillis();
            Path inputPath = inputFile.toPath();
            Path outputPath = outputFile.toPath();

            creator.createTorrent(inputPath, outputPath, (current, total, status) -> {
                double percent = (double) current / total * 100;
                if (current == total) {
                    System.out.println("\n" + status);
                } else {
                    System.out.printf("\r  Progress: %.1f%% - %s", percent, status);
                }
            });

            long duration = System.currentTimeMillis() - startTime;

            // Calculate and display info hash
            byte[] infoHash = TorrentCreator.calculateInfoHash(outputPath);
            String infoHashHex = bytesToHex(infoHash);

            System.out.println("\n✓ Torrent created successfully!");
            System.out.println("  Info Hash: " + infoHashHex);
            System.out.println("  Time: " + duration + " ms");
            System.out.println("\nYou can now share: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("\n✗ Failed to create torrent: " + e.getMessage());
            if (args.verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return (bytes / 1024 / 1024) + " MB";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().toUpperCase();
    }
}
