package com.example.jtorrent.storage;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Verifies piece integrity using SHA-1 hashes.
 */
public class PieceVerifier {

  private final DiskManager diskManager;
  private final PieceManager pieceManager;
  private final byte[] pieceHashes;

  public PieceVerifier(DiskManager diskManager, PieceManager pieceManager, byte[] pieceHashes) {
    this.diskManager = diskManager;
    this.pieceManager = pieceManager;
    this.pieceHashes = pieceHashes.clone();

    if (pieceHashes.length != pieceManager.getPieceCount() * 20) {
      throw new IllegalArgumentException(
          "Piece hashes length mismatch: expected "
              + (pieceManager.getPieceCount() * 20)
              + ", got "
              + pieceHashes.length);
    }
  }

  /** Verifies piece by computing SHA-1 hash. */
  public boolean verifyPiece(int pieceIndex) throws IOException {
    int pieceLength = pieceManager.getPieceLength(pieceIndex);
    byte[] pieceData = diskManager.readPiece(pieceIndex, pieceLength);

    byte[] computedHash = computeSha1(pieceData);
    byte[] expectedHash = getExpectedHash(pieceIndex);

    return Arrays.equals(computedHash, expectedHash);
  }

  /** Verifies piece and updates piece manager state. */
  public boolean verifyAndMarkComplete(int pieceIndex) throws IOException {
    if (verifyPiece(pieceIndex)) {
      pieceManager.setPieceState(pieceIndex, PieceState.COMPLETE);
      return true;
    } else {
      // Corrupt piece, reset to missing for re-download
      pieceManager.resetPiece(pieceIndex);
      return false;
    }
  }

  /** Re-checks all pieces on startup; returns number of valid pieces. */
  public int recheckAllPieces() throws IOException {
    int validCount = 0;
    int totalPieces = pieceManager.getPieceCount();

    for (int i = 0; i < totalPieces; i++) {
      try {
        if (verifyPiece(i)) {
          pieceManager.setPieceState(i, PieceState.COMPLETE);
          validCount++;
        } else {
          pieceManager.setPieceState(i, PieceState.MISSING);
        }
      } catch (IOException e) {
        // File doesn't exist or read error, mark as missing
        pieceManager.setPieceState(i, PieceState.MISSING);
      }
    }

    return validCount;
  }

  /**
   * Get the expected SHA-1 hash for a piece.
   *
   * @param pieceIndex the piece index
   * @return 20-byte hash
   */
  public byte[] getExpectedHash(int pieceIndex) {
    byte[] hash = new byte[20];
    System.arraycopy(pieceHashes, pieceIndex * 20, hash, 0, 20);
    return hash;
  }

  /**
   * Compute SHA-1 hash of data.
   *
   * @param data the data to hash
   * @return 20-byte SHA-1 hash
   */
  public static byte[] computeSha1(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return digest.digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-1 not available", e);
    }
  }

  /**
   * Compute SHA-1 hash and return as hex string.
   *
   * @param data the data to hash
   * @return hex string of SHA-1 hash
   */
  public static String computeSha1Hex(byte[] data) {
    byte[] hash = computeSha1(data);
    return bytesToHex(hash);
  }

  /**
   * Convert bytes to hex string.
   *
   * @param bytes the bytes
   * @return hex string
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }
}
