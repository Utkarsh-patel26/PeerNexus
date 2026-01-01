package com.example.jtorrent.metadata;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import com.example.jtorrent.parser.TorrentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates .torrent files from files or directories.
 * Supports single-file and multi-file torrents with configurable piece sizes.
 */
public class TorrentCreator {
    private static final int MIN_PIECE_SIZE = 16 * 1024; // 16 KB
    private static final int MAX_PIECE_SIZE = 16 * 1024 * 1024; // 16 MB
    private static final int DEFAULT_PIECE_SIZE = 256 * 1024; // 256 KB

    private final List<String> trackers;
    private final String comment;
    private final String createdBy;
    private final boolean privateTorrent;
    private int pieceSize;

    /**
     * Progress callback interface for torrent creation.
     */
    public interface ProgressCallback {
        void onProgress(long current, long total, String status);
    }

    /**
     * Builder for TorrentCreator configuration.
     */
    public static class Builder {
        private final List<String> trackers = new ArrayList<>();
        private String comment = "";
        private String createdBy = "JTorrent 1.0";
        private boolean privateTorrent = false;
        private int pieceSize = 0; // 0 = auto-calculate

        public Builder addTracker(String trackerUrl) {
            trackers.add(trackerUrl);
            return this;
        }

        public Builder addTrackers(List<String> trackerUrls) {
            trackers.addAll(trackerUrls);
            return this;
        }

        public Builder setComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder setPrivate(boolean privateTorrent) {
            this.privateTorrent = privateTorrent;
            return this;
        }

        public Builder setPieceSize(int pieceSize) {
            if (pieceSize != 0 && (pieceSize < MIN_PIECE_SIZE || pieceSize > MAX_PIECE_SIZE)) {
                throw new IllegalArgumentException(
                        "Piece size must be between " + MIN_PIECE_SIZE + " and " + MAX_PIECE_SIZE);
            }
            if (pieceSize != 0 && (pieceSize & (pieceSize - 1)) != 0) {
                throw new IllegalArgumentException("Piece size must be a power of 2");
            }
            this.pieceSize = pieceSize;
            return this;
        }

        public TorrentCreator build() {
            return new TorrentCreator(trackers, comment, createdBy, privateTorrent, pieceSize);
        }
    }

    private TorrentCreator(List<String> trackers, String comment, String createdBy,
            boolean privateTorrent, int pieceSize) {
        this.trackers = new ArrayList<>(trackers);
        this.comment = comment;
        this.createdBy = createdBy;
        this.privateTorrent = privateTorrent;
        this.pieceSize = pieceSize;
    }

    /**
     * Create a torrent file from a file or directory.
     *
     * @param inputPath  Path to file or directory
     * @param outputPath Path for the .torrent file
     * @param callback   Optional progress callback
     * @throws IOException if file operations fail
     */
    public void createTorrent(Path inputPath, Path outputPath, ProgressCallback callback)
            throws IOException {
        File input = inputPath.toFile();
        if (!input.exists()) {
            throw new IOException("Input path does not exist: " + inputPath);
        }

        // Calculate total size and determine piece size
        long totalSize = calculateTotalSize(input);
        if (pieceSize == 0) {
            pieceSize = calculateOptimalPieceSize(totalSize);
        }

        // Build info dictionary
        Map<String, Object> info = new HashMap<>();
        info.put("piece length", (long) pieceSize);

        if (input.isFile()) {
            createSingleFileTorrent(input, info, totalSize, callback);
        } else {
            createMultiFileTorrent(input, info, totalSize, callback);
        }

        if (privateTorrent) {
            info.put("private", 1L);
        }

        // Build main torrent dictionary
        Map<String, Object> torrent = new HashMap<>();
        if (!trackers.isEmpty()) {
            if (trackers.size() == 1) {
                torrent.put("announce", trackers.get(0));
            } else {
                // Multi-tracker support (BEP-12)
                torrent.put("announce", trackers.get(0));
                List<List<String>> announceList = new ArrayList<>();
                for (String tracker : trackers) {
                    announceList.add(Arrays.asList(tracker));
                }
                torrent.put("announce-list", announceList);
            }
        }

        if (!comment.isEmpty()) {
            torrent.put("comment", comment);
        }
        if (!createdBy.isEmpty()) {
            torrent.put("created by", createdBy);
        }
        torrent.put("creation date", System.currentTimeMillis() / 1000);
        torrent.put("info", info);

        // Write to file
        try {
            byte[] encodedTorrent = BencodeParser.encode(torrent);
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                fos.write(encodedTorrent);
            }
        } catch (BencodeException e) {
            throw new IOException("Failed to encode torrent", e);
        }

        if (callback != null) {
            callback.onProgress(totalSize, totalSize, "Torrent created successfully");
        }
    }

    private void createSingleFileTorrent(File file, Map<String, Object> info, long totalSize,
            ProgressCallback callback) throws IOException {
        info.put("name", file.getName());
        info.put("length", totalSize);

        // Hash pieces
        byte[] pieces = hashPieces(Arrays.asList(file), totalSize, callback);
        info.put("pieces", pieces);
    }

    private void createMultiFileTorrent(File directory, Map<String, Object> info, long totalSize,
            ProgressCallback callback) throws IOException {
        info.put("name", directory.getName());

        // Collect all files recursively
        List<File> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            files = paths.filter(Files::isRegularFile).map(Path::toFile).sorted()
                    .collect(Collectors.toList());
        }

        // Build files list
        List<Map<String, Object>> filesList = new ArrayList<>();
        for (File file : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("length", file.length());

            // Build path relative to directory
            Path relativePath = directory.toPath().relativize(file.toPath());
            List<String> pathComponents = new ArrayList<>();
            for (int i = 0; i < relativePath.getNameCount(); i++) {
                pathComponents.add(relativePath.getName(i).toString());
            }
            fileInfo.put("path", pathComponents);

            filesList.add(fileInfo);
        }
        info.put("files", filesList);

        // Hash pieces
        byte[] pieces = hashPieces(files, totalSize, callback);
        info.put("pieces", pieces);
    }

    private byte[] hashPieces(List<File> files, long totalSize, ProgressCallback callback)
            throws IOException {
        List<byte[]> pieceHashes = new ArrayList<>();
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm not available", e);
        }

        byte[] buffer = new byte[pieceSize];
        int bufferPos = 0;
        long processedBytes = 0;
        int pieceIndex = 0;

        for (File file : files) {
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                byte[] readBuffer = new byte[8192];

                while ((bytesRead = fis.read(readBuffer)) != -1) {
                    int offset = 0;
                    while (offset < bytesRead) {
                        int toCopy = Math.min(bytesRead - offset, pieceSize - bufferPos);
                        System.arraycopy(readBuffer, offset, buffer, bufferPos, toCopy);
                        bufferPos += toCopy;
                        offset += toCopy;
                        processedBytes += toCopy;

                        // Piece complete
                        if (bufferPos == pieceSize) {
                            sha1.update(buffer, 0, pieceSize);
                            pieceHashes.add(sha1.digest());
                            bufferPos = 0;
                            pieceIndex++;

                            if (callback != null) {
                                callback.onProgress(processedBytes, totalSize,
                                        "Hashing piece " + pieceIndex + "...");
                            }
                        }
                    }
                }
            }
        }

        // Hash final partial piece
        if (bufferPos > 0) {
            sha1.update(buffer, 0, bufferPos);
            pieceHashes.add(sha1.digest());
        }

        // Concatenate all hashes
        byte[] result = new byte[pieceHashes.size() * 20];
        for (int i = 0; i < pieceHashes.size(); i++) {
            System.arraycopy(pieceHashes.get(i), 0, result, i * 20, 20);
        }

        return result;
    }

    private long calculateTotalSize(File file) throws IOException {
        if (file.isFile()) {
            return file.length();
        } else {
            try (Stream<Path> paths = Files.walk(file.toPath())) {
                return paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
            }
        }
    }

    /**
     * Calculate optimal piece size based on torrent size.
     * Aims for 1000-2000 pieces for efficient distribution.
     */
    private int calculateOptimalPieceSize(long totalSize) {
        // Target: ~1500 pieces
        long targetPieceSize = totalSize / 1500;

        // Round up to nearest power of 2
        int size = MIN_PIECE_SIZE;
        while (size < targetPieceSize && size < MAX_PIECE_SIZE) {
            size *= 2;
        }

        // Clamp to valid range
        return Math.min(Math.max(size, MIN_PIECE_SIZE), MAX_PIECE_SIZE);
    }

    /**
     * Calculate the info hash for verification (before file creation).
     */
    public static byte[] calculateInfoHash(Path torrentFile) throws IOException {
        try {
            TorrentFile parsed = TorrentFile.parse(torrentFile.toFile());
            return parsed.infoHash();
        } catch (BencodeException e) {
            throw new IOException("Failed to parse torrent file", e);
        }
    }
}
