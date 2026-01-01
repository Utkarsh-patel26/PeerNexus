package com.example.jtorrent.peer;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Extended message (BEP 10) for extension protocol.
 * Used for extended handshake and extension messages like ut_metadata.
 */
public class ExtendedMessage {

  /** Extension ID for extended handshake. */
  public static final int HANDSHAKE_ID = 0;

  private final int extensionId;
  private final byte[] payload;

  /**
   * Create an extended message.
   *
   * @param extensionId the extension ID
   * @param payload     the payload bytes
   */
  public ExtendedMessage(int extensionId, byte[] payload) {
    this.extensionId = extensionId;
    this.payload = payload != null ? payload.clone() : new byte[0];
  }

  /**
   * Get the extension ID.
   *
   * @return extension ID
   */
  public int getExtensionId() {
    return extensionId;
  }

  /**
   * Get the payload.
   *
   * @return payload bytes
   */
  public byte[] getPayload() {
    return payload.clone();
  }

  /**
   * Parse an extended message from a Message object.
   *
   * @param message the message (must be type EXTENDED)
   * @return parsed ExtendedMessage
   * @throws IllegalArgumentException if message is not EXTENDED type
   */
  public static ExtendedMessage fromMessage(Message message) {
    if (message.type() != Message.EXTENDED) {
      throw new IllegalArgumentException("Message is not EXTENDED type");
    }

    byte[] payload = message.payload();
    if (payload.length == 0) {
      throw new IllegalArgumentException("Extended message has empty payload");
    }

    int extensionId = payload[0] & 0xFF;
    byte[] extensionPayload = new byte[payload.length - 1];
    System.arraycopy(payload, 1, extensionPayload, 0, extensionPayload.length);

    return new ExtendedMessage(extensionId, extensionPayload);
  }

  /**
   * Convert to a Message object.
   *
   * @return Message with type EXTENDED
   */
  public Message toMessage() {
    byte[] fullPayload = new byte[1 + payload.length];
    fullPayload[0] = (byte) extensionId;
    System.arraycopy(payload, 0, fullPayload, 1, payload.length);
    return new Message(Message.EXTENDED, fullPayload);
  }

  /**
   * Create an extended handshake message.
   *
   * @param extensions   map of extension names to local IDs
   * @param metadataSize metadata size in bytes (0 if not available)
   * @return ExtendedMessage for handshake
   */
  public static ExtendedMessage createHandshake(Map<String, Integer> extensions, int metadataSize) {
    Map<String, Object> handshake = new HashMap<>();

    // Add extensions map
    if (extensions != null && !extensions.isEmpty()) {
      Map<String, Object> m = new HashMap<>();
      for (Map.Entry<String, Integer> entry : extensions.entrySet()) {
        m.put(entry.getKey(), (long) entry.getValue());
      }
      handshake.put("m", m);
    } else {
      handshake.put("m", new HashMap<>());
    }

    // Add metadata size if available
    if (metadataSize > 0) {
      handshake.put("metadata_size", (long) metadataSize);
    }

    // Add client version
    handshake.put("v", "JTorrent 1.0.0");

    // Bencode the handshake
    byte[] bencoded = bencode(handshake);

    return new ExtendedMessage(HANDSHAKE_ID, bencoded);
  }

  /**
   * Parse an extended handshake from payload.
   *
   * @return parsed handshake data
   * @throws BencodeException if parsing fails
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> parseHandshake() throws BencodeException {
    if (extensionId != HANDSHAKE_ID) {
      throw new IllegalStateException("Not a handshake message");
    }

    BencodeParser parser = new BencodeParser(payload);
    Object parsed = parser.parse();

    if (!(parsed instanceof Map)) {
      throw new BencodeException("Handshake must be a dictionary");
    }

    return (Map<String, Object>) parsed;
  }

  /**
   * Get the ut_metadata extension ID from handshake.
   *
   * @param handshake parsed handshake dictionary
   * @return ut_metadata extension ID, or -1 if not supported
   */
  @SuppressWarnings("unchecked")
  public static int getUtMetadataId(Map<String, Object> handshake) {
    if (!handshake.containsKey("m")) {
      return -1;
    }

    Object extensionsMap = handshake.get("m");
    if (!(extensionsMap instanceof Map)) {
      return -1;
    }

    Map<String, Object> m = (Map<String, Object>) extensionsMap;
    if (!m.containsKey("ut_metadata")) {
      return -1;
    }

    Object utMetadata = m.get("ut_metadata");
    if (utMetadata instanceof Long) {
      return ((Long) utMetadata).intValue();
    } else if (utMetadata instanceof Integer) {
      return (Integer) utMetadata;
    }

    return -1;
  }

  /**
   * Get the metadata size from handshake.
   *
   * @param handshake parsed handshake dictionary
   * @return metadata size in bytes, or -1 if not available
   */
  public static int getMetadataSize(Map<String, Object> handshake) {
    if (!handshake.containsKey("metadata_size")) {
      return -1;
    }

    Object sizeObj = handshake.get("metadata_size");
    if (sizeObj instanceof Long) {
      return ((Long) sizeObj).intValue();
    } else if (sizeObj instanceof Integer) {
      return (Integer) sizeObj;
    }

    return -1;
  }

  /**
   * Simple bencode encoder for handshake.
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
    return "ExtendedMessage{extensionId=" + extensionId + ", payloadSize=" + payload.length + "}";
  }
}
