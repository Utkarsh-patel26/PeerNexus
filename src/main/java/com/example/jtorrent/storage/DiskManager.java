package com.example.jtorrent.storage;

import com.example.jtorrent.parser.FileEntry;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Disk I/O manager for torrent files.
 */
public class DiskManager implements AutoCloseable {

  private final Path baseDir;
  private final List<FileEntry> files;
  private final int pieceLength;
  private final int pieceCount;
  private final long totalLength;
  private final Map<String, RandomAccessFile> openFiles;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final DiskCache cache;

  public DiskManager(
      Path baseDir, List<FileEntry> files, int pieceLength, int pieceCount, long totalLength)
      throws IOException {
    this(baseDir, files, pieceLength, pieceCount, totalLength, null);
  }

  public DiskManager(
      Path baseDir, List<FileEntry> files, int pieceLength, int pieceCount, long totalLength,
      DiskCache cache)
      throws IOException {
    this.baseDir = baseDir;
    this.files = files;
    this.pieceLength = pieceLength;
    this.pieceCount = pieceCount;
    this.totalLength = totalLength;
    this.openFiles = new HashMap<>();
    this.cache = cache;

    // Create base directory
    Files.createDirectories(baseDir);

    // Pre-create all file paths (but don't allocate space)
    for (FileEntry file : files) {
      Path filePath = baseDir.resolve(file.path());
      Files.createDirectories(filePath.getParent());
    }
  }

  public void writeBlock(int pieceIndex, int offset, byte[] data) throws IOException {
    lock.writeLock().lock();
    try {
      long globalOffset = (long) pieceIndex * pieceLength + offset;
      writeAtGlobalOffset(globalOffset, data);

      // Invalidate cache for this piece since it's being modified
      if (cache != null) {
        cache.remove(pieceIndex);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Read a piece from disk.
   *
   * @param pieceIndex the piece index
   * @param length     the piece length
   * @return the piece data
   * @throws IOException if read fails
   */
  public byte[] readPiece(int pieceIndex, int length) throws IOException {
    // Try cache first
    if (cache != null) {
      byte[] cachedData = cache.get(pieceIndex);
      if (cachedData != null) {
        return cachedData;
      }
    }

    // Read from disk
    lock.readLock().lock();
    try {
      long globalOffset = (long) pieceIndex * pieceLength;
      byte[] data = readAtGlobalOffset(globalOffset, length);

      // Cache the data
      if (cache != null && data != null) {
        cache.put(pieceIndex, data);
      }

      return data;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Read a block from disk.
   *
   * @param pieceIndex the piece index
   * @param offset     offset within the piece
   * @param length     the block length
   * @return the block data
   * @throws IOException if read fails
   */
  public byte[] readBlock(int pieceIndex, int offset, int length) throws IOException {
    lock.readLock().lock();
    try {
      long globalOffset = (long) pieceIndex * pieceLength + offset;
      return readAtGlobalOffset(globalOffset, length);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Flush (fsync) a piece to disk after completion.
   *
   * @param pieceIndex the piece index
   * @throws IOException if flush fails
   */
  public void flushPiece(int pieceIndex) throws IOException {
    lock.writeLock().lock();
    try {
      long startOffset = (long) pieceIndex * pieceLength;
      int length = getPieceLength(pieceIndex);

      // Find and flush all files that contain this piece
      long currentOffset = 0;
      for (FileEntry file : files) {
        long fileEnd = currentOffset + file.length();

        // Check if piece overlaps with this file
        if (startOffset < fileEnd && (startOffset + length) > currentOffset) {
          RandomAccessFile raf = getOrOpenFile(file);
          raf.getFD().sync(); // Atomic flush
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

  /**
   * Write data at a global byte offset (spans multiple files if needed).
   *
   * @param globalOffset the global byte offset
   * @param data         the data to write
   * @throws IOException if write fails
   */
  private void writeAtGlobalOffset(long globalOffset, byte[] data) throws IOException {
    int dataOffset = 0;
    int remaining = data.length;
    long currentGlobalOffset = globalOffset;

    long fileStartOffset = 0;
    for (FileEntry file : files) {
      long fileEndOffset = fileStartOffset + file.length();

      // Check if current position is in this file
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
      throw new IOException("Write extends beyond total file length");
    }
  }

  /**
   * Read data from a global byte offset (spans multiple files if needed).
   *
   * @param globalOffset the global byte offset
   * @param length       the number of bytes to read
   * @return the data read
   * @throws IOException if read fails
   */
  private byte[] readAtGlobalOffset(long globalOffset, int length) throws IOException {
    byte[] result = new byte[length];
    int resultOffset = 0;
    int remaining = length;
    long currentGlobalOffset = globalOffset;

    long fileStartOffset = 0;
    for (FileEntry file : files) {
      long fileEndOffset = fileStartOffset + file.length();

      // Check if current position is in this file
      if (currentGlobalOffset < fileEndOffset && remaining > 0) {
        long offsetInFile = currentGlobalOffset - fileStartOffset;
        int bytesToRead = (int) Math.min(remaining, fileEndOffset - currentGlobalOffset);

        RandomAccessFile raf = getOrOpenFile(file);
        raf.seek(offsetInFile);
        int bytesRead = raf.read(result, resultOffset, bytesToRead);

        if (bytesRead < 0) {
          // File is empty or position beyond EOF, fill with zeros
          bytesRead = 0;
        }

        if (bytesRead < bytesToRead) {
          // File may not have all data yet, fill remainder with zeros
          for (int i = resultOffset + bytesRead; i < resultOffset + bytesToRead; i++) {
            result[i] = 0;
          }
        }

        resultOffset += bytesToRead;
        remaining -= bytesToRead;
        currentGlobalOffset += bytesToRead;
      }

      fileStartOffset = fileEndOffset;

      if (remaining <= 0) {
        break;
      }
    }

    return result;
  }

  /**
   * Get or open a RandomAccessFile for a file entry.
   *
   * @param file the file entry
   * @return the RandomAccessFile
   * @throws IOException if open fails
   */
  private RandomAccessFile getOrOpenFile(FileEntry file) throws IOException {
    String pathKey = file.path();
    RandomAccessFile raf = openFiles.get(pathKey);
    if (raf == null) {
      Path filePath = baseDir.resolve(file.path());
      raf = new RandomAccessFile(filePath.toFile(), "rw");
      openFiles.put(pathKey, raf);
    }
    return raf;
  }

  /**
   * Get the actual length of a piece.
   *
   * @param pieceIndex the piece index
   * @return piece length
   */
  private int getPieceLength(int pieceIndex) {
    if (pieceIndex == pieceCount - 1) {
      long remaining = totalLength - ((long) pieceIndex * pieceLength);
      return (int) remaining;
    }
    return pieceLength;
  }

  /**
   * Get the base directory.
   *
   * @return base directory path
   */
  public Path getBaseDir() {
    return baseDir;
  }

  /**
   * Get total length.
   *
   * @return total length in bytes
   */
  public long getTotalLength() {
    return totalLength;
  }

  @Override
  public void close() throws IOException {
    lock.writeLock().lock();
    try {
      for (RandomAccessFile raf : openFiles.values()) {
        try {
          raf.close();
        } catch (IOException e) {
          // Ignore close errors
        }
      }
      openFiles.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
