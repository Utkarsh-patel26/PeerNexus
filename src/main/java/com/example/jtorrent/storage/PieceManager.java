package com.example.jtorrent.storage;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Piece state tracking, rarest-first selection, and block allocation.
 */
public class PieceManager {

  public static final int BLOCK_SIZE = 16384;

  private static final int ENDGAME_THRESHOLD = 10;

  private final Random random = new Random();

  private final int pieceCount;
  private final int pieceLength;
  private final long totalLength;
  private final PieceState[] pieceStates;
  private final BitSet[] pieceBlockMaps;
  private final int[] pieceAvailability;
  private final Map<String, BitSet> peerBitfields;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public PieceManager(int pieceCount, int pieceLength, long totalLength) {
    this.pieceCount = pieceCount;
    this.pieceLength = pieceLength;
    this.totalLength = totalLength;
    this.pieceStates = new PieceState[pieceCount];
    this.pieceBlockMaps = new BitSet[pieceCount];
    this.pieceAvailability = new int[pieceCount];
    this.peerBitfields = new ConcurrentHashMap<>();

    // Initialize all pieces as missing
    for (int i = 0; i < pieceCount; i++) {
      pieceStates[i] = PieceState.MISSING;
      pieceBlockMaps[i] = new BitSet(getBlockCount(i));
    }
  }

  public int getBlockCount(int pieceIndex) {
    int length = getPieceLength(pieceIndex);
    return (length + BLOCK_SIZE - 1) / BLOCK_SIZE;
  }

  /** Returns actual length of piece (last piece may be smaller). */
  public int getPieceLength(int pieceIndex) {
    if (pieceIndex < 0 || pieceIndex >= pieceCount) {
      throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
    }

    if (pieceIndex == pieceCount - 1) {
      // Last piece may be smaller
      long remaining = totalLength - ((long) pieceIndex * pieceLength);
      return (int) remaining;
    }
    return pieceLength;
  }

  public PieceState getPieceState(int pieceIndex) {
    lock.readLock().lock();
    try {
      return pieceStates[pieceIndex];
    } finally {
      lock.readLock().unlock();
    }
  }

  public void setPieceState(int pieceIndex, PieceState state) {
    lock.writeLock().lock();
    try {
      pieceStates[pieceIndex] = state;
      if (state == PieceState.COMPLETE) {
        // Mark all blocks as received
        pieceBlockMaps[pieceIndex].set(0, getBlockCount(pieceIndex));
      } else if (state == PieceState.MISSING) {
        // Clear all blocks
        pieceBlockMaps[pieceIndex].clear();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /** Marks block as received; returns true if piece is now complete. */
  public boolean markBlockReceived(int pieceIndex, int blockOffset) {
    lock.writeLock().lock();
    try {
      int blockIndex = blockOffset / BLOCK_SIZE;
      pieceBlockMaps[pieceIndex].set(blockIndex);

      if (pieceStates[pieceIndex] == PieceState.MISSING) {
        setPieceState(pieceIndex, PieceState.DOWNLOADING);
      }

      // Check if all blocks received
      int totalBlocks = getBlockCount(pieceIndex);
      return pieceBlockMaps[pieceIndex].cardinality() == totalBlocks;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean hasBlock(int pieceIndex, int blockOffset) {
    lock.readLock().lock();
    try {
      int blockIndex = blockOffset / BLOCK_SIZE;
      return pieceBlockMaps[pieceIndex].get(blockIndex);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Returns next missing block for piece, or null if complete. */
  public BlockInfo getNextBlock(int pieceIndex) {
    lock.readLock().lock();
    try {
      BitSet blockMap = pieceBlockMaps[pieceIndex];
      int totalBlocks = getBlockCount(pieceIndex);

      for (int i = 0; i < totalBlocks; i++) {
        if (!blockMap.get(i)) {
          int offset = i * BLOCK_SIZE;
          int length = BLOCK_SIZE;

          // Last block may be smaller
          if (i == totalBlocks - 1) {
            int pieceLen = getPieceLength(pieceIndex);
            int remaining = pieceLen - offset;
            if (remaining < BLOCK_SIZE) {
              length = remaining;
            }
          }

          return new BlockInfo(pieceIndex, offset, length);
        }
      }

      return null; // All blocks received
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<BlockInfo> getMissingBlocks(int pieceIndex) {
    lock.readLock().lock();
    try {
      List<BlockInfo> missing = new ArrayList<>();
      BitSet blockMap = pieceBlockMaps[pieceIndex];
      int totalBlocks = getBlockCount(pieceIndex);

      for (int i = 0; i < totalBlocks; i++) {
        if (!blockMap.get(i)) {
          int offset = i * BLOCK_SIZE;
          int length = BLOCK_SIZE;

          if (i == totalBlocks - 1) {
            int pieceLen = getPieceLength(pieceIndex);
            int remaining = pieceLen - offset;
            if (remaining < BLOCK_SIZE) {
              length = remaining;
            }
          }

          missing.add(new BlockInfo(pieceIndex, offset, length));
        }
      }

      return missing;
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Updates peer bitfield for rarest-first calculation. */
  public void updatePeerBitfield(String peerId, BitSet bitfield) {
    lock.writeLock().lock();
    try {
      // Remove old availability if peer was known
      BitSet old = peerBitfields.get(peerId);
      if (old != null) {
        for (int i = 0; i < pieceCount; i++) {
          if (old.get(i)) {
            pieceAvailability[i]--;
          }
        }
      }

      // Add new availability
      peerBitfields.put(peerId, (BitSet) bitfield.clone());
      for (int i = 0; i < pieceCount; i++) {
        if (bitfield.get(i)) {
          pieceAvailability[i]++;
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void removePeer(String peerId) {
    lock.writeLock().lock();
    try {
      BitSet bitfield = peerBitfields.remove(peerId);
      if (bitfield != null) {
        for (int i = 0; i < pieceCount; i++) {
          if (bitfield.get(i)) {
            pieceAvailability[i]--;
          }
        }
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /** Selects piece using the given strategy; returns -1 if none suitable. */
  public int selectPiece(BitSet peerBitfield, PieceSelectionStrategy strategy) {
    if (peerBitfield == null) {
      return -1;
    }

    switch (strategy) {
      case RANDOM_FIRST:
        return selectRandomPiece(peerBitfield);
      case RAREST_FIRST:
        return selectRarestPiece(peerBitfield);
      case SEQUENTIAL:
        return selectSequentialPiece(peerBitfield);
      case ENDGAME:
        return selectEndgamePiece(peerBitfield);
      default:
        return selectRarestPiece(peerBitfield);
    }
  }

  /**
   * Determine the best strategy based on download progress.
   *
   * @return recommended strategy
   */
  public PieceSelectionStrategy getRecommendedStrategy() {
    lock.readLock().lock();
    try {
      int completed = getCompletedCount();
      int missing = pieceCount - completed - getDownloadingCount();

      // Random-first until we have at least one complete piece
      if (completed == 0) {
        return PieceSelectionStrategy.RANDOM_FIRST;
      }

      // Endgame when very few pieces remain
      if (missing <= ENDGAME_THRESHOLD) {
        return PieceSelectionStrategy.ENDGAME;
      }

      // Default to rarest-first
      return PieceSelectionStrategy.RAREST_FIRST;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Select a random piece (for startup phase).
   *
   * @param peerBitfield the peer's bitfield
   * @return piece index or -1
   */
  private int selectRandomPiece(BitSet peerBitfield) {
    lock.writeLock().lock();
    try {
      List<Integer> candidates = new ArrayList<>();
      int downloadingCount = 0;
      int downloadingWithBlocksCount = 0;

      // First pass: collect MISSING pieces and count DOWNLOADING pieces
      for (int i = 0; i < pieceCount; i++) {
        if (peerBitfield.get(i)) {
          if (pieceStates[i] == PieceState.MISSING) {
            candidates.add(i);
          } else if (pieceStates[i] == PieceState.DOWNLOADING) {
            downloadingCount++;
            int blockCount = pieceBlockMaps[i].cardinality();
            if (blockCount > 0) {
              downloadingWithBlocksCount++;
            } else {
              // Reset pieces with 0 blocks immediately
              setPieceState(i, PieceState.MISSING);
              candidates.add(i);
            }
          }
        }
      }

      // If no MISSING pieces found and we have DOWNLOADING pieces, reset them ALL
      if (candidates.isEmpty() && downloadingCount > 0) {
        System.err.println(String.format(
            "[PIECE_MANAGER] EMERGENCY RESET: No candidates found but %d pieces DOWNLOADING (%d with blocks). Resetting ALL to MISSING.",
            downloadingCount, downloadingWithBlocksCount));

        for (int i = 0; i < pieceCount; i++) {
          if (peerBitfield.get(i) && pieceStates[i] == PieceState.DOWNLOADING) {
            int blockCount = pieceBlockMaps[i].cardinality();
            System.err.println(String.format(
                "[PIECE_MANAGER] Resetting piece %d (had %d/%d blocks)",
                i, blockCount, getBlockCount(i)));

            // Clear block tracking and reset state
            pieceBlockMaps[i].clear();
            setPieceState(i, PieceState.MISSING);
            candidates.add(i);
          }
        }
      }

      if (candidates.isEmpty()) {
        return -1;
      }

      return candidates.get(random.nextInt(candidates.size()));
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Select any piece with incomplete blocks (for endgame).
   *
   * @param peerBitfield the peer's bitfield
   * @return piece index or -1
   */
  private int selectEndgamePiece(BitSet peerBitfield) {
    lock.readLock().lock();
    try {
      for (int i = 0; i < pieceCount; i++) {
        if (peerBitfield.get(i) && pieceStates[i] != PieceState.COMPLETE) {
          return i;
        }
      }
      return -1;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Select pieces in sequential order from first to last.
   * Useful for streaming/previewing media files.
   *
   * @param peerBitfield the peer's bitfield
   * @return piece index or -1
   */
  private int selectSequentialPiece(BitSet peerBitfield) {
    lock.readLock().lock();
    try {
      // Find first missing or downloading piece that peer has
      for (int i = 0; i < pieceCount; i++) {
        if (peerBitfield.get(i) && pieceStates[i] != PieceState.COMPLETE) {
          return i;
        }
      }
      return -1;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Select the rarest piece that the peer has and we need.
   *
   * @param peerBitfield the peer's bitfield
   * @return piece index or -1 if no suitable piece
   */
  public int selectRarestPiece(BitSet peerBitfield) {
    lock.readLock().lock();
    try {
      int rarestPiece = -1;
      int lowestAvailability = Integer.MAX_VALUE;

      for (int i = 0; i < pieceCount; i++) {
        // Check if peer has piece and we need it
        if (peerBitfield.get(i) && pieceStates[i] != PieceState.COMPLETE) {
          int availability = pieceAvailability[i];
          if (availability < lowestAvailability) {
            lowestAvailability = availability;
            rarestPiece = i;
          }
        }
      }

      return rarestPiece;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the availability count for a piece.
   *
   * @param pieceIndex the piece index
   * @return number of peers that have this piece
   */
  public int getPieceAvailability(int pieceIndex) {
    lock.readLock().lock();
    try {
      return pieceAvailability[pieceIndex];
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get count of completed pieces.
   *
   * @return number of complete pieces
   */
  public int getCompletedCount() {
    lock.readLock().lock();
    try {
      int count = 0;
      for (PieceState state : pieceStates) {
        if (state == PieceState.COMPLETE) {
          count++;
        }
      }
      return count;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get count of pieces in progress.
   *
   * @return number of downloading pieces
   */
  public int getDownloadingCount() {
    lock.readLock().lock();
    try {
      int count = 0;
      for (PieceState state : pieceStates) {
        if (state == PieceState.DOWNLOADING) {
          count++;
        }
      }
      return count;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Check if download is complete.
   *
   * @return true if all pieces complete
   */
  public boolean isComplete() {
    return getCompletedCount() == pieceCount;
  }

  /**
   * Get total piece count.
   *
   * @return piece count
   */
  public int getPieceCount() {
    return pieceCount;
  }

  /**
   * Get standard piece length.
   *
   * @return piece length
   */
  public int getStandardPieceLength() {
    return pieceLength;
  }

  /**
   * Get total length.
   *
   * @return total length
   */
  public long getTotalLength() {
    return totalLength;
  }

  /**
   * Get bitfield representing complete pieces.
   *
   * @return bitset of complete pieces
   */
  public BitSet getCompleteBitfield() {
    lock.readLock().lock();
    try {
      BitSet result = new BitSet(pieceCount);
      for (int i = 0; i < pieceCount; i++) {
        if (pieceStates[i] == PieceState.COMPLETE) {
          result.set(i);
        }
      }
      return result;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Reset a piece to missing state (e.g., after verification failure).
   *
   * @param pieceIndex the piece index
   */
  public void resetPiece(int pieceIndex) {
    setPieceState(pieceIndex, PieceState.MISSING);
  }
}
