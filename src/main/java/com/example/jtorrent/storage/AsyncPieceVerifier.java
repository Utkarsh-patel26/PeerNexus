package com.example.jtorrent.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncPieceVerifier implements AutoCloseable {
    private static final int BUFFER_SIZE = 256 * 1024;

    private final byte[][] pieceHashes;
    private final int pieceLength;
    private final long totalLength;
    private final ExecutorService executor;
    private final Set<Integer> verifiedPieces = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> lastVerified = new ConcurrentHashMap<>();
    private volatile boolean verifyOnRead = false;

    public AsyncPieceVerifier(byte[][] pieceHashes, int pieceLength, long totalLength) {
        this.pieceHashes = pieceHashes;
        this.pieceLength = pieceLength;
        this.totalLength = totalLength;
        this.executor = Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "async-piece-verifier");
                    t.setDaemon(true);
                    return t;
                });
    }

    public void setVerifyOnRead(boolean enabled) {
        this.verifyOnRead = enabled;
    }

    public boolean isVerifyOnRead() {
        return verifyOnRead;
    }

    public boolean verifyPiece(int pieceIndex, byte[] data) {
        if (pieceIndex < 0 || pieceIndex >= pieceHashes.length) {
            return false;
        }

        byte[] expectedHash = pieceHashes[pieceIndex];
        byte[] actualHash = computeHash(data);

        boolean valid = Arrays.equals(expectedHash, actualHash);
        if (valid) {
            verifiedPieces.add(pieceIndex);
            lastVerified.put(pieceIndex, System.currentTimeMillis());
        }
        return valid;
    }

    public CompletableFuture<VerificationResult> verifyAllFromFile(Path filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
                return verifyAllInternal(channel);
            } catch (IOException e) {
                return new VerificationResult(0, pieceHashes.length, 0,
                        Collections.emptyList(), pieceHashes.length);
            }
        }, executor);
    }

    private VerificationResult verifyAllInternal(FileChannel channel) throws IOException {
        int pieceCount = pieceHashes.length;
        AtomicInteger verified = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicLong bytesVerified = new AtomicLong(0);
        List<Integer> failedPieces = new CopyOnWriteArrayList<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < pieceCount; i++) {
            final int pieceIndex = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    int length = getPieceLength(pieceIndex);
                    ByteBuffer buffer = ByteBuffer.allocate(length);
                    long offset = (long) pieceIndex * pieceLength;

                    synchronized (channel) {
                        channel.position(offset);
                        int read = 0;
                        while (read < length) {
                            int r = channel.read(buffer);
                            if (r < 0)
                                break;
                            read += r;
                        }
                    }

                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    if (verifyPiece(pieceIndex, data)) {
                        verified.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                        failedPieces.add(pieceIndex);
                    }
                    bytesVerified.addAndGet(length);
                } catch (IOException e) {
                    failed.incrementAndGet();
                    failedPieces.add(pieceIndex);
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return new VerificationResult(
                verified.get(), failed.get(), bytesVerified.get(),
                failedPieces, pieceCount);
    }

    public CompletableFuture<Boolean> verifyOnReadAsync(int pieceIndex, byte[] data) {
        if (!verifyOnRead) {
            return CompletableFuture.completedFuture(true);
        }

        Long lastCheck = lastVerified.get(pieceIndex);
        if (lastCheck != null && System.currentTimeMillis() - lastCheck < 60000) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> verifyPiece(pieceIndex, data), executor);
    }

    public boolean isVerified(int pieceIndex) {
        return verifiedPieces.contains(pieceIndex);
    }

    public Set<Integer> getVerifiedPieces() {
        return Collections.unmodifiableSet(verifiedPieces);
    }

    public void markVerified(int pieceIndex) {
        verifiedPieces.add(pieceIndex);
        lastVerified.put(pieceIndex, System.currentTimeMillis());
    }

    public void clearVerification(int pieceIndex) {
        verifiedPieces.remove(pieceIndex);
        lastVerified.remove(pieceIndex);
    }

    public void clearAll() {
        verifiedPieces.clear();
        lastVerified.clear();
    }

    private int getPieceLength(int pieceIndex) {
        if (pieceIndex == pieceHashes.length - 1) {
            long remaining = totalLength - ((long) pieceIndex * pieceLength);
            return (int) remaining;
        }
        return pieceLength;
    }

    private byte[] computeHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record VerificationResult(int verified, int failed, long bytesVerified,
            List<Integer> failedPieces, int totalPieces) {
        public double percentage() {
            return totalPieces > 0 ? (verified * 100.0) / totalPieces : 0;
        }

        public boolean isComplete() {
            return failed == 0 && verified == totalPieces;
        }
    }
}
