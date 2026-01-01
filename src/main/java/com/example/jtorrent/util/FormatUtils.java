package com.example.jtorrent.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Utility class for formatting and conversion operations.
 */
public final class FormatUtils {

  private static final long KB = 1024L;
  private static final long MB = KB * 1024L;
  private static final long GB = MB * 1024L;
  private static final byte[] PEER_ID_PREFIX = "-JT0001-".getBytes(StandardCharsets.US_ASCII);
  private static final ThreadLocal<SecureRandom> RANDOM =
      ThreadLocal.withInitial(SecureRandom::new);

  private FormatUtils() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Convert bytes to hex string.
   *
   * @param bytes the bytes to convert
   * @return hex string
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length << 1);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }

  /**
   * Generate a random peer ID following the Azureus-style convention.
   *
   * @return 20-byte peer ID
   */
  public static byte[] generatePeerId() {
    byte[] peerId = new byte[20];
    System.arraycopy(PEER_ID_PREFIX, 0, peerId, 0, PEER_ID_PREFIX.length);
    SecureRandom random = RANDOM.get();
    for (int i = PEER_ID_PREFIX.length; i < 20; i++) {
      peerId[i] = (byte) ('0' + random.nextInt(10));
    }
    return peerId;
  }

  /**
   * Format a file size in human-readable format.
   *
   * @param bytes size in bytes
   * @return formatted string
   */
  public static String formatSize(long bytes) {
    if (bytes < KB) {
      return bytes + " B";
    } else if (bytes < MB) {
      return String.format("%.2f KB", bytes / (double) KB);
    } else if (bytes < GB) {
      return String.format("%.2f MB", bytes / (double) MB);
    } else {
      return String.format("%.2f GB", bytes / (double) GB);
    }
  }
}
