package com.example.jtorrent.peer;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ut_metadata extension message (BEP 9) for metadata exchange.
 * Used to download torrent metadata from peers when starting from magnet links.
 */
public class MetadataMessage {

  /** Message type: request a metadata piece. */
  public static final int REQUEST = 0;

  /** Message type: metadata piece data. */
  public static final int DATA = 1;

  /** Message type: reject metadata request. */
  public static final int REJECT = 2;

  /** Metadata piece size (16 KB). */
  public static final int PIECE_SIZE = 16384;

  private final int msgType;
  private final int piece;
  private final int totalSize;
  private final byte[] data;

  /**
   * Create a metadata message.
   *
   * @param msgType message type (REQUEST, DATA, or REJECT)
   * @param piece piece index
   * @param totalSize total metadata size (only for DATA messages)
   * @param data metadata piece data (only for DATA messages)
   */
  public MetadataMessage(int msgType, int piece, int totalSize, byte[] data) {
    this.msgType = msgType;
    this.piece = piece;
    this.totalSize = totalSize;
    this.data = data != null ? data.clone() : null;
  }

  /**
   * Get the message type.
   *
   * @return message type (REQUEST, DATA, or REJECT)
   */
  public int getMsgType() {
    return msgType;
  }

  /**
   * Get the piece index.
   *
   * @return piece index
   */
  public int getPiece() {
    return piece;
  }

  /**
   * Get the total metadata size.
   *
   * @return total size in bytes (only valid for DATA messages)
   */
  public int getTotalSize() {
    return totalSize;
  }

  /**
   * Get the metadata piece data.
   *
   * @return piece data (only for DATA messages)
   */
  public byte[] getData() {
    return data != null ? data.clone() : null;
  }

  /**
   * Create a REQUEST message.
   *
   * @param piece piece index to request
   * @return MetadataMessage
   */
  public static MetadataMessage createRequest(int piece) {
    return new MetadataMessage(REQUEST, piece, 0, null);
  }

  /**
   * Create a DATA message.
   *
   * @param piece piece index
   * @param totalSize total metadata size
   * @param data piece data
   * @return MetadataMessage
   */
  public static MetadataMessage createData(int piece, int totalSize, byte[] data) {
    return new MetadataMessage(DATA, piece, totalSize, data);
  }

  /**
   * Create a REJECT message.
   *
   * @param piece piece index that was rejected
   * @return MetadataMessage
   */
  public static MetadataMessage createReject(int piece) {
    return new MetadataMessage(REJECT, piece, 0, null);
  }

  /**
   * Convert to an ExtendedMessage.
   *
   * @param extensionId the ut_metadata extension ID for this peer
   * @return ExtendedMessage
   */
  public ExtendedMessage toExtendedMessage(int extensionId) {
    // Build bencoded dictionary
    Map<String, Object> dict = new HashMap<>();
    dict.put("msg_type", (long) msgType);
    dict.put("piece", (long) piece);

    if (msgType == DATA && totalSize > 0) {
      dict.put("total_size", (long) totalSize);
    }

    byte[] bencoded = bencode(dict);

    // For DATA messages, append raw data after bencoded dict
    byte[] payload;
    if (msgType == DATA && data != null) {
      payload = new byte[bencoded.length + data.length];
      System.arraycopy(bencoded, 0, payload, 0, bencoded.length);
      System.arraycopy(data, 0, payload, bencoded.length, data.length);
    } else {
      payload = bencoded;
    }

    return new ExtendedMessage(extensionId, payload);
  }

  /**
   * Parse a MetadataMessage from an ExtendedMessage.
   *
   * @param extMsg the extended message
   * @return parsed MetadataMessage
   * @throws BencodeException if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static MetadataMessage fromExtendedMessage(ExtendedMessage extMsg) throws BencodeException {
    byte[] payload = extMsg.getPayload();

    if (payload.length == 0) {
      throw new BencodeException("Empty metadata message payload");
    }

    // Parse bencoded dictionary
    BencodeParser parser = new BencodeParser(payload);
    Object parsed = parser.parse();

    if (!(parsed instanceof Map)) {
      throw new BencodeException("Metadata message must be a dictionary");
    }

    Map<String, Object> dict = (Map<String, Object>) parsed;

    // Extract msg_type
    if (!dict.containsKey("msg_type")) {
      throw new BencodeException("Missing msg_type in metadata message");
    }
    int msgType = ((Long) dict.get("msg_type")).intValue();

    // Extract piece
    if (!dict.containsKey("piece")) {
      throw new BencodeException("Missing piece in metadata message");
    }
    int piece = ((Long) dict.get("piece")).intValue();

    // Extract total_size (only for DATA messages)
    int totalSize = 0;
    if (dict.containsKey("total_size")) {
      totalSize = ((Long) dict.get("total_size")).intValue();
    }

    // Extract raw data (only for DATA messages)
    byte[] data = null;
    if (msgType == DATA) {
      // Find where bencoded dict ends
      int bencodedLength = parser.getPosition();
      if (payload.length > bencodedLength) {
        int dataLength = payload.length - bencodedLength;
        data = new byte[dataLength];
        System.arraycopy(payload, bencodedLength, data, 0, dataLength);
      }
    }

    return new MetadataMessage(msgType, piece, totalSize, data);
  }

  /**
   * Calculate the number of pieces needed for given metadata size.
   *
   * @param metadataSize total metadata size in bytes
   * @return number of pieces
   */
  public static int calculatePieceCount(int metadataSize) {
    return (metadataSize + PIECE_SIZE - 1) / PIECE_SIZE;
  }

  /**
   * Simple bencode encoder for metadata message dictionary.
   *
   * @param obj object to encode
   * @return bencoded bytes
   */
  @SuppressWarnings("unchecked")
  private static byte[] bencode(Object obj) {
    StringBuilder sb = new StringBuilder();

    if (obj instanceof String) {
      String str = (String) obj;
      byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
      sb.append(bytes.length).append(':');
      sb.append(str);
    } else if (obj instanceof Long || obj instanceof Integer) {
      long value = obj instanceof Long ? (Long) obj : (Integer) obj;
      sb.append('i').append(value).append('e');
    } else if (obj instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) obj;
      sb.append('d');
      // Sort keys for proper bencode encoding
      java.util.List<String> sortedKeys = new java.util.ArrayList<>(map.keySet());
      java.util.Collections.sort(sortedKeys);
      for (String key : sortedKeys) {
        // Encode key
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        sb.append(keyBytes.length).append(':').append(key);
        // Encode value
        sb.append(new String(bencode(map.get(key)), StandardCharsets.UTF_8));
      }
      sb.append('e');
    }

    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    String typeName = msgType == REQUEST ? "REQUEST" : (msgType == DATA ? "DATA" : "REJECT");
    return "MetadataMessage{type=" + typeName + ", piece=" + piece
        + ", totalSize=" + totalSize + ", dataSize=" + (data != null ? data.length : 0) + "}";
  }
}
