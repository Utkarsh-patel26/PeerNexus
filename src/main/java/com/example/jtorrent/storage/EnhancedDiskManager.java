package com.example.jtorrent.storage;

import com.example.jtorrent.parser.FileEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class EnhancedDiskManager implements AutoCloseable {
    private final Path baseDir;
    private final List<FileEntry> files;
    private final int pieceLength;
    private final int pieceCount;
    private final long totalLength;
    private final Map<String, RandomAccessFile> openFiles = new ConcurrentHashMap<>();
    private final Map<String, FileChannel> openChannels = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final DiskCache cache;
    private final FileAllocator allocator;
    private final FileMover mover;
    private final AllocationStrategy allocationStrategy;
    private final byte[][] pieceHashes;
    private volatile boolean initialized = false;
    private volatile boolean verifyOnRead = false;

    private EnhancedDiskManager(Builder builder) throws IOException {
        this.baseDir = builder.baseDir;
        this.files = builder.files;
        this.pieceLength = builder.pieceLength;
        this.pieceCount = builder.pieceCount;
        this.totalLength = builder.totalLength;
        this.cache = builder.cache;
        this.allocationStrategy = builder.allocationStrategy;
        this.pieceHashes = builder.pieceHashes;
        this.allocator = new FileAllocator(baseDir, files, allocationStrategy);
        this.mover = new FileMover();

        Files.createDirectories(baseDir);
    }

    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        if (allocationStrategy != AllocationStrategy.NONE) {
            allocator.allocateAll().join();
        } else {
            for (FileEntry file : files) {
                Path filePath = baseDir.resolve(file.path());
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    Files.createFile(filePath);
                }
            }
        }

        initialized = true;
    }

    public void writeBlock(int pieceIndex, int offset, byte[] data) throws IOException {
        lock.writeLock().lock();
        try {
            long globalOffset = (long) pieceIndex * pieceLength + offset;
            writeAtGlobalOffset(globalOffset, data);
            // Note: cache invalidation would happen here if DiskCache supported it
        } finally {
            lock.writeLock().unlock();
        }
    }

    public byte[] readPiece(int pieceIndex, int length) throws IOException {
        if (cache != null) {
            byte[] cachedData = cache.get(pieceIndex);
            if (cachedData != null) {
                return cachedData;
            }
        }

        lock.readLock().lock();
        try {
            long globalOffset = (long) pieceIndex * pieceLength;
            byte[] data = readAtGlobalOffset(globalOffset, length);

            if (cache != null && data != null) {
                cache.put(pieceIndex, data);
            }

            return data;
        } finally {
            lock.readLock().unlock();
        }
    }

    public byte[] readBlock(int pieceIndex, int offset, int length) throws IOException {
        lock.readLock().lock();
        try {
            long globalOffset = (long) pieceIndex * pieceLength + offset;
            return readAtGlobalOffset(globalOffset, length);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void flushPiece(int pieceIndex) throws IOException {
        lock.writeLock().lock();
        try {
            long startOffset = (long) pieceIndex * pieceLength;
            int length = getPieceLength(pieceIndex);

            long currentOffset = 0;
            for (FileEntry file : files) {
                long fileEnd = currentOffset + file.length();

                if (startOffset < fileEnd && (startOffset + length) > currentOffset) {
                    RandomAccessFile raf = openFiles.get(file.path());
                    if (raf != null) {
                        raf.getFD().sync();
                    }
                }

                currentOffset = fileEnd;
                if (currentOffset > startOffset + length) {
                    break;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public FileMover.MoveResult moveToDirectory(Path destDir) throws IOException {
        return mover.moveFiles(baseDir, destDir, files).join();
    }

    public FileMover.MoveResult moveCompletedFiles(Path destDir, Set<Integer> completedPieces) {
        return mover.moveWhileDownloading(baseDir, destDir, files,
                completedPieces, pieceLength).join();
    }

    public void setVerifyOnRead(boolean enabled) {
        this.verifyOnRead = enabled;
    }

    private void writeAtGlobalOffset(long globalOffset, byte[] data) throws IOException {
        int dataOffset = 0;
        int remaining = data.length;
        long currentGlobalOffset = globalOffset;

        long fileStartOffset = 0;
        for (FileEntry file : files) {
            long fileEndOffset = fileStartOffset + file.length();

            if (currentGlobalOffset < fileEndOffset && remaining > 0) {
                long offsetInFile = currentGlobalOffset - fileStartOffset;
                int bytesToWrite = (int) Math.min(remaining, fileEndOffset - currentGlobalOffset);

                RandomAccessFile raf = getOrOpenFile(file);
                raf.seek(offsetInFile);
                raf.write(data, dataOffset, bytesToWrite);

                dataOffset += bytesToWrite;
                remaining -= bytesToWrite;
                currentGlobalOffset += bytesToWrite;
            }

            fileStartOffset = fileEndOffset;

            if (remaining <= 0) {
                break;
            }
        }

        if (remaining > 0) {
            throw new IOException("Write beyond torrent boundary");
        }
    }

    private byte[] readAtGlobalOffset(long globalOffset, int length) throws IOException {
        byte[] result = new byte[length];
        int resultOffset = 0;
        int remaining = length;
        long currentGlobalOffset = globalOffset;

        long fileStartOffset = 0;
        for (FileEntry file : files) {
            long fileEndOffset = fileStartOffset + file.length();

            if (currentGlobalOffset < fileEndOffset && remaining > 0) {
                long offsetInFile = currentGlobalOffset - fileStartOffset;
                int bytesToRead = (int) Math.min(remaining, fileEndOffset - currentGlobalOffset);

                RandomAccessFile raf = getOrOpenFile(file);
                raf.seek(offsetInFile);
                int bytesRead = raf.read(result, resultOffset, bytesToRead);

                if (bytesRead < 0) {
                    Arrays.fill(result, resultOffset, resultOffset + bytesToRead, (byte) 0);
                    bytesRead = bytesToRead;
                }

                resultOffset += bytesRead;
                remaining -= bytesRead;
                currentGlobalOffset += bytesRead;
            }

            fileStartOffset = fileEndOffset;

            if (remaining <= 0) {
                break;
            }
        }

        return result;
    }

    private RandomAccessFile getOrOpenFile(FileEntry file) throws IOException {
        String pathKey = file.path();
        RandomAccessFile raf = openFiles.get(pathKey);
        if (raf == null) {
            Path filePath = baseDir.resolve(pathKey);
            raf = new RandomAccessFile(filePath.toFile(), "rw");
            openFiles.put(pathKey, raf);
        }
        return raf;
    }

    private int getPieceLength(int pieceIndex) {
        if (pieceIndex == pieceCount - 1) {
            long lastPieceSize = totalLength - (long) pieceIndex * pieceLength;
            return (int) lastPieceSize;
        }
        return pieceLength;
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public AllocationStrategy getAllocationStrategy() {
        return allocationStrategy;
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            for (RandomAccessFile raf : openFiles.values()) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
            openFiles.clear();

            for (FileChannel channel : openChannels.values()) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            openChannels.clear();

            allocator.shutdown();
            mover.shutdown();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path baseDir;
        private List<FileEntry> files;
        private int pieceLength;
        private int pieceCount;
        private long totalLength;
        private DiskCache cache;
        private byte[][] pieceHashes;
        private AllocationStrategy allocationStrategy = AllocationStrategy.SPARSE;

        public Builder baseDir(Path baseDir) {
            this.baseDir = baseDir;
            return this;
        }

        public Builder files(List<FileEntry> files) {
            this.files = files;
            return this;
        }

        public Builder pieceLength(int pieceLength) {
            this.pieceLength = pieceLength;
            return this;
        }

        public Builder pieceCount(int pieceCount) {
            this.pieceCount = pieceCount;
            return this;
        }

        public Builder totalLength(long totalLength) {
            this.totalLength = totalLength;
            return this;
        }

        public Builder cache(DiskCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder pieceHashes(byte[][] pieceHashes) {
            this.pieceHashes = pieceHashes;
            return this;
        }

        public Builder allocationStrategy(AllocationStrategy strategy) {
            this.allocationStrategy = strategy;
            return this;
        }

        public EnhancedDiskManager build() throws IOException {
            Objects.requireNonNull(baseDir, "baseDir is required");
            Objects.requireNonNull(files, "files is required");
            if (pieceLength <= 0) {
                throw new IllegalArgumentException("pieceLength must be positive");
            }
            if (pieceCount <= 0) {
                throw new IllegalArgumentException("pieceCount must be positive");
            }
            return new EnhancedDiskManager(this);
        }
    }
}
