package com.example.jtorrent.streaming;

import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.logging.Logger;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingManager {

    private static final Logger logger = Logger.getLogger(StreamingManager.class);

    private static final long DEFAULT_BUFFER_SIZE = 50 * 1024 * 1024;
    @SuppressWarnings("unused") // Reserved for future preload implementation
    private static final int PRELOAD_PIECES = 10;

    private final ConcurrentHashMap<String, StreamingSession> sessions = new ConcurrentHashMap<>();
    private final HttpStreamServer streamServer;

    public StreamingManager(int port) {
        this.streamServer = new HttpStreamServer(port, this);
    }

    public void start() throws Exception {
        streamServer.start();
        logger.info("Streaming manager started");
    }

    public void stop() {
        streamServer.stop();
        sessions.values().forEach(StreamingSession::stop);
        sessions.clear();
        logger.info("Streaming manager stopped");
    }

    public StreamingSession startStreaming(TorrentSession torrentSession, int fileIndex) {
        String name = torrentSession.getName();
        String key = (name != null ? name : "unknown") + ":" + fileIndex;

        return sessions.computeIfAbsent(key, k -> {
            StreamingSession session = new StreamingSession(torrentSession, fileIndex, DEFAULT_BUFFER_SIZE);
            session.start();
            logger.info("Started streaming session: %s file %d", name, fileIndex);
            return session;
        });
    }

    public void stopStreaming(String hash, int fileIndex) {
        String key = hash + ":" + fileIndex;
        StreamingSession session = sessions.remove(key);
        if (session != null) {
            session.stop();
        }
    }

    public StreamingSession getSession(String hash, int fileIndex) {
        return sessions.get(hash + ":" + fileIndex);
    }

    public byte[] readData(String hash, int fileIndex, long offset, int length) throws Exception {
        StreamingSession session = getSession(hash, fileIndex);
        if (session == null) {
            throw new IllegalStateException("No streaming session for " + hash);
        }
        return session.readData(offset, length);
    }

    public long getFileSize(String hash, int fileIndex) {
        StreamingSession session = getSession(hash, fileIndex);
        return session != null ? session.getFileSize() : -1;
    }

    public static class StreamingSession {
        private final TorrentSession torrentSession;
        @SuppressWarnings("unused") // Reserved for file selection in streaming
        private final int fileIndex;
        @SuppressWarnings("unused") // Reserved for buffer management
        private final long bufferSize;
        private final AtomicBoolean running = new AtomicBoolean(false);
        @SuppressWarnings("unused") // Updated by readData for tracking
        private long currentPosition = 0;

        StreamingSession(TorrentSession torrentSession, int fileIndex, long bufferSize) {
            this.torrentSession = torrentSession;
            this.fileIndex = fileIndex;
            this.bufferSize = bufferSize;
        }

        void start() {
            running.set(true);
        }

        void stop() {
            running.set(false);
        }

        public byte[] readData(long offset, int length) throws Exception {
            currentPosition = offset;
            waitForData(offset, length);
            // Return empty data for now - actual implementation would read from disk
            return new byte[length];
        }

        private void waitForData(long offset, int length) throws InterruptedException {
            var torrentFile = torrentSession.getTorrentFile();
            if (torrentFile == null) {
                throw new IllegalStateException("Torrent metadata not loaded");
            }
            int pieceLength = torrentFile.pieceLength();
            BitSet completed = torrentSession.getCompletedPiecesSnapshot();

            int startPiece = (int) (offset / pieceLength);
            int endPiece = (int) ((offset + length - 1) / pieceLength);

            long deadline = System.currentTimeMillis() + 30000;

            while (System.currentTimeMillis() < deadline) {
                boolean allReady = true;
                for (int i = startPiece; i <= endPiece; i++) {
                    if (!completed.get(i)) {
                        allReady = false;
                        break;
                    }
                }
                if (allReady)
                    return;
                Thread.sleep(100);
                completed = torrentSession.getCompletedPiecesSnapshot();
            }

            throw new InterruptedException("Timeout waiting for streaming data");
        }

        public long getFileSize() {
            var torrentFile = torrentSession.getTorrentFile();
            if (torrentFile == null || torrentFile.files() == null ||
                    fileIndex >= torrentFile.files().size()) {
                return -1;
            }
            return torrentFile.files().get(fileIndex).length();
        }

        public double getBufferProgress() {
            var torrentFile = torrentSession.getTorrentFile();
            if (torrentFile == null) {
                return 0.0;
            }
            BitSet completed = torrentSession.getCompletedPiecesSnapshot();
            int totalPieces = torrentFile.pieces().size();
            if (totalPieces == 0) {
                return 0.0;
            }
            return (double) completed.cardinality() / totalPieces;
        }
    }
}
