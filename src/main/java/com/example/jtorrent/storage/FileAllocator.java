package com.example.jtorrent.storage;

import com.example.jtorrent.parser.FileEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

public final class FileAllocator {
    private static final int ALLOCATION_CHUNK_SIZE = 16 * 1024 * 1024;

    private final Path baseDir;
    private final List<FileEntry> files;
    private final AllocationStrategy strategy;
    private final ExecutorService executor;

    public FileAllocator(Path baseDir, List<FileEntry> files, AllocationStrategy strategy) {
        this.baseDir = baseDir;
        this.files = files;
        this.strategy = strategy;
        this.executor = Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "file-allocator");
                    t.setDaemon(true);
                    return t;
                });
    }

    public CompletableFuture<AllocationResult> allocateAll() {
        return switch (strategy) {
            case NONE -> CompletableFuture.completedFuture(
                    new AllocationResult(true, 0, files.size()));
            case SPARSE -> allocateSparse();
            case FULL_PREALLOC -> allocateFull();
            case FALLOCATE -> allocateFallocate();
        };
    }

    private CompletableFuture<AllocationResult> allocateSparse() {
        List<CompletableFuture<Boolean>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Path filePath = baseDir.resolve(file.path());
                        Files.createDirectories(filePath.getParent());
                        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
                            raf.setLength(file.length());
                        }
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                }, executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    long allocated = futures.stream().filter(f -> f.join()).count();
                    return new AllocationResult(allocated == files.size(),
                            allocated * ALLOCATION_CHUNK_SIZE, files.size());
                });
    }

    private CompletableFuture<AllocationResult> allocateFull() {
        List<CompletableFuture<Boolean>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Path filePath = baseDir.resolve(file.path());
                        Files.createDirectories(filePath.getParent());
                        ByteBuffer zeros = ByteBuffer.allocate(ALLOCATION_CHUNK_SIZE);

                        try (FileChannel channel = FileChannel.open(filePath,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                            long remaining = file.length();
                            while (remaining > 0) {
                                zeros.clear();
                                int toWrite = (int) Math.min(remaining, ALLOCATION_CHUNK_SIZE);
                                zeros.limit(toWrite);
                                channel.write(zeros);
                                remaining -= toWrite;
                            }
                            channel.force(true);
                        }
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                }, executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    long totalAllocated = files.stream()
                            .mapToLong(FileEntry::length)
                            .sum();
                    long allocated = futures.stream().filter(f -> f.join()).count();
                    return new AllocationResult(allocated == files.size(),
                            totalAllocated, files.size());
                });
    }

    private CompletableFuture<AllocationResult> allocateFallocate() {
        List<CompletableFuture<Boolean>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Path filePath = baseDir.resolve(file.path());
                        Files.createDirectories(filePath.getParent());

                        if (isPosixFallocateAvailable()) {
                            return fallocatePosix(filePath, file.length());
                        } else {
                            try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
                                raf.setLength(file.length());
                            }
                            return true;
                        }
                    } catch (IOException e) {
                        return false;
                    }
                }, executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    long totalAllocated = files.stream()
                            .mapToLong(FileEntry::length)
                            .sum();
                    long allocated = futures.stream().filter(f -> f.join()).count();
                    return new AllocationResult(allocated == files.size(),
                            totalAllocated, files.size());
                });
    }

    private boolean isPosixFallocateAvailable() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("linux") || os.contains("mac");
    }

    private boolean fallocatePosix(Path path, long size) {
        try {
            ProcessBuilder pb = new ProcessBuilder("fallocate", "-l",
                    String.valueOf(size), path.toString());
            Process p = pb.start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            try {
                try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
                    raf.setLength(size);
                }
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    public void shutdown() {
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

    public record AllocationResult(boolean success, long bytesAllocated, int filesProcessed) {
    }
}
