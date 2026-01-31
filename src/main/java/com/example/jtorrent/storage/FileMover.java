package com.example.jtorrent.storage;

import com.example.jtorrent.parser.FileEntry;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class FileMover {
    private static final int BUFFER_SIZE = 8 * 1024 * 1024;

    private final ExecutorService executor;
    private final Map<Path, MoveOperation> activeOperations = new ConcurrentHashMap<>();

    public FileMover() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "file-mover");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<MoveResult> moveFiles(Path sourceBase, Path destBase,
            List<FileEntry> files) {
        MoveOperation op = new MoveOperation(sourceBase, destBase, files);
        activeOperations.put(sourceBase, op);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(destBase);

                for (FileEntry file : files) {
                    if (op.cancelled) {
                        return new MoveResult(false, op.bytesMoved.get(),
                                "Cancelled", op.movedFiles);
                    }

                    Path source = sourceBase.resolve(file.path());
                    Path dest = destBase.resolve(file.path());

                    if (!Files.exists(source)) {
                        continue;
                    }

                    Files.createDirectories(dest.getParent());

                    if (tryAtomicMove(source, dest)) {
                        op.bytesMoved.addAndGet(file.length());
                        op.movedFiles.add(file.path());
                    } else if (copyAndDelete(source, dest, op)) {
                        op.movedFiles.add(file.path());
                    } else {
                        return new MoveResult(false, op.bytesMoved.get(),
                                "Failed to move: " + file.path(), op.movedFiles);
                    }
                }

                cleanEmptyDirs(sourceBase);
                return new MoveResult(true, op.bytesMoved.get(), null, op.movedFiles);
            } catch (IOException e) {
                return new MoveResult(false, op.bytesMoved.get(),
                        e.getMessage(), op.movedFiles);
            } finally {
                activeOperations.remove(sourceBase);
            }
        }, executor);
    }

    public CompletableFuture<MoveResult> moveWhileDownloading(Path sourceBase, Path destBase,
            List<FileEntry> files,
            Set<Integer> completedPieces,
            int pieceLength) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(destBase);
                List<String> movedFiles = new ArrayList<>();
                AtomicLong bytesMoved = new AtomicLong(0);

                long currentOffset = 0;
                for (FileEntry file : files) {
                    Path source = sourceBase.resolve(file.path());
                    Path dest = destBase.resolve(file.path());

                    if (!Files.exists(source)) {
                        currentOffset += file.length();
                        continue;
                    }

                    boolean fileComplete = isFileComplete(currentOffset, file.length(),
                            completedPieces, pieceLength);

                    if (fileComplete) {
                        Files.createDirectories(dest.getParent());
                        if (tryAtomicMove(source, dest)) {
                            bytesMoved.addAndGet(file.length());
                            movedFiles.add(file.path());
                        }
                    }

                    currentOffset += file.length();
                }

                return new MoveResult(true, bytesMoved.get(), null, movedFiles);
            } catch (IOException e) {
                return new MoveResult(false, 0, e.getMessage(), List.of());
            }
        }, executor);
    }

    private boolean isFileComplete(long fileOffset, long fileLength,
            Set<Integer> completedPieces, int pieceLength) {
        int startPiece = (int) (fileOffset / pieceLength);
        int endPiece = (int) ((fileOffset + fileLength - 1) / pieceLength);

        for (int i = startPiece; i <= endPiece; i++) {
            if (!completedPieces.contains(i)) {
                return false;
            }
        }
        return true;
    }

    public void cancelMove(Path sourceBase) {
        MoveOperation op = activeOperations.get(sourceBase);
        if (op != null) {
            op.cancelled = true;
        }
    }

    public Optional<MoveProgress> getProgress(Path sourceBase) {
        MoveOperation op = activeOperations.get(sourceBase);
        if (op == null) {
            return Optional.empty();
        }
        long total = op.files.stream().mapToLong(FileEntry::length).sum();
        return Optional.of(new MoveProgress(op.bytesMoved.get(), total,
                op.movedFiles.size(), op.files.size()));
    }

    private boolean tryAtomicMove(Path source, Path dest) {
        try {
            Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean copyAndDelete(Path source, Path dest, MoveOperation op) throws IOException {
        try (var in = Files.newInputStream(source);
                var out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (op.cancelled) {
                    Files.deleteIfExists(dest);
                    return false;
                }
                out.write(buffer, 0, read);
                op.bytesMoved.addAndGet(read);
            }
        }
        Files.delete(source);
        return true;
    }

    private void cleanEmptyDirs(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (isEmpty(d)) {
                    Files.delete(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isEmpty(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class MoveOperation {
        final Path sourceBase;
        final Path destBase;
        final List<FileEntry> files;
        final AtomicLong bytesMoved = new AtomicLong(0);
        final List<String> movedFiles = new CopyOnWriteArrayList<>();
        volatile boolean cancelled = false;

        MoveOperation(Path sourceBase, Path destBase, List<FileEntry> files) {
            this.sourceBase = sourceBase;
            this.destBase = destBase;
            this.files = files;
        }
    }

    public record MoveResult(boolean success, long bytesMoved, String error,
            List<String> movedFiles) {
    }

    public record MoveProgress(long bytesMoved, long totalBytes,
            int filesMoved, int totalFiles) {
        public double percentage() {
            return totalBytes > 0 ? (bytesMoved * 100.0) / totalBytes : 0;
        }
    }
}
