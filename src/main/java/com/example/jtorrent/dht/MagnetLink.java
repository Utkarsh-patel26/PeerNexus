package com.example.jtorrent.dht;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for BitTorrent magnet links (magnet:?xt=urn:btih:...).
 */
public class MagnetLink {

  private final byte[] infoHash;
  private final String displayName;
  private final List<String> trackers;

  /**
   * Create a magnet link.
   *
   * @param infoHash the 20-byte info hash
   * @param displayName optional display name
   * @param trackers list of tracker URLs
   */
  public MagnetLink(byte[] infoHash, String displayName, List<String> trackers) {
    if (infoHash.length != 20) {
      throw new IllegalArgumentException("Info hash must be 20 bytes");
    }
    // Make defensive copies
    this.infoHash = new byte[20];
    System.arraycopy(infoHash, 0, this.infoHash, 0, 20);
    this.displayName = displayName;
    this.trackers = new ArrayList<>(trackers);
  }

  /**
   * Parse a magnet URI.
   *
   * @param magnetUri the magnet URI string
   * @return parsed magnet link
   * @throws IllegalArgumentException if URI is invalid
   */
  public static MagnetLink parse(String magnetUri) {
    if (!magnetUri.startsWith("magnet:?")) {
      throw new IllegalArgumentException("Invalid magnet URI: must start with 'magnet:?'");
    }

    String query = magnetUri.substring(8); // Skip "magnet:?"
    String[] params = query.split("&");

    byte[] infoHash = null;
    String displayName = null;
    List<String> trackers = new ArrayList<>();

    for (String param : params) {
      int equalsIndex = param.indexOf('=');
      if (equalsIndex < 0) {
        continue;
      }

      String key = param.substring(0, equalsIndex);
      String value = param.substring(equalsIndex + 1);

      try {
        value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
        // Should not happen for UTF-8
        throw new RuntimeException(e);
      }

      switch (key) {
        case "xt":
          infoHash = parseInfoHash(value);
          break;
        case "dn":
          displayName = value;
          break;
        case "tr":
          trackers.add(value);
          break;
        default:
          // Ignore unknown parameters
          break;
      }
    }

    if (infoHash == null) {
      throw new IllegalArgumentException("Magnet URI missing xt parameter");
    }

    return new MagnetLink(infoHash, displayName, trackers);
  }

  /**
   * Parse info hash from xt parameter.
   *
   * @param xt the xt value (e.g., "urn:btih:...")
   * @return 20-byte info hash
   */
  private static byte[] parseInfoHash(String xt) {
    if (!xt.startsWith("urn:btih:")) {
      throw new IllegalArgumentException("xt parameter must be urn:btih:...");
    }

    String hash = xt.substring(9); // Skip "urn:btih:"

    // Try to parse as hex (40 characters)
    if (hash.length() == 40) {
      return parseHexHash(hash);
    }

    // Try to parse as base32 (32 characters)
    if (hash.length() == 32) {
      return parseBase32Hash(hash);
    }

    throw new IllegalArgumentException("Info hash must be 40 hex or 32 base32 characters");
  }

  /**
   * Parse hex-encoded info hash.
   *
   * @param hex 40-character hex string
   * @return 20-byte info hash
   */
  private static byte[] parseHexHash(String hex) {
    if (hex.length() != 40) {
      throw new IllegalArgumentException("Hex hash must be 40 characters");
    }

    byte[] result = new byte[20];
    for (int i = 0; i < 20; i++) {
      int high = Character.digit(hex.charAt(i * 2), 16);
      int low = Character.digit(hex.charAt(i * 2 + 1), 16);
      if (high < 0 || low < 0) {
        throw new IllegalArgumentException("Invalid hex character in hash");
      }
      result[i] = (byte) ((high << 4) | low);
    }
    return result;
  }

  /**
   * Parse base32-encoded info hash.
   *
   * @param base32 32-character base32 string
   * @return 20-byte info hash
   */
  private static byte[] parseBase32Hash(String base32) {
    base32 = base32.toUpperCase();
    if (base32.length() != 32) {
      throw new IllegalArgumentException("Base32 hash must be 32 characters");
    }

    byte[] result = new byte[20];
    int resultIndex = 0;
    int buffer = 0;
    int bitsInBuffer = 0;

    for (int i = 0; i < base32.length(); i++) {
      char c = base32.charAt(i);
      int value;

      if (c >= 'A' && c <= 'Z') {
        value = c - 'A';
      } else if (c >= '2' && c <= '7') {
        value = 26 + (c - '2');
      } else {
        throw new IllegalArgumentException("Invalid base32 character: " + c);
      }

      buffer = (buffer << 5) | value;
      bitsInBuffer += 5;

      if (bitsInBuffer >= 8) {
        result[resultIndex++] = (byte) (buffer >> (bitsInBuffer - 8));
        bitsInBuffer -= 8;
        buffer &= (1 << bitsInBuffer) - 1;
      }
    }

    return result;
  }

  /**
   * Get the info hash.
   *
   * @return 20-byte info hash (copy)
   */
  public byte[] getInfoHash() {
    byte[] copy = new byte[20];
    System.arraycopy(infoHash, 0, copy, 0, 20);
    return copy;
  }

  /**
   * Get the display name.
   *
   * @return display name or null
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get tracker URLs.
   *
   * @return list of tracker URLs
   */
  public List<String> getTrackers() {
    return new ArrayList<>(trackers);
  }

  /**
   * Convert info hash to hex string.
   *
   * @return 40-character hex string
   */
  public String getInfoHashHex() {
    StringBuilder sb = new StringBuilder(40);
    for (byte b : infoHash) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("MagnetLink[hash=%s, name=%s, trackers=%d]",
        getInfoHashHex(), displayName, trackers.size());
  }
}
