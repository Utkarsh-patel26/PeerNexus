package com.example.jtorrent.parser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bencode parser for BitTorrent.
 */
public class BencodeParser {

  private final byte[] data;
  private int position;

  public BencodeParser(byte[] data) {
    this.data = data;
    this.position = 0;
  }

  /**
   * Parses bencode data and returns decoded object (Long, byte[], List, or Map).
   */
  public Object parse() throws BencodeException {
    if (position >= data.length) {
      throw new BencodeException("Unexpected end of data");
    }
    return parseValue();
  }

  public int getPosition() {
    return position;
  }

  private Object parseValue() throws BencodeException {
    if (position >= data.length) {
      throw new BencodeException("Unexpected end of data at position " + position);
    }

    byte marker = data[position];

    if (marker == 'i') {
      return parseInteger();
    } else if (marker == 'l') {
      return parseList();
    } else if (marker == 'd') {
      return parseDictionary();
    } else if (marker >= '0' && marker <= '9') {
      return parseByteString();
    } else {
      throw new BencodeException(
          "Invalid bencode marker '" + (char) marker + "' at position " + position);
    }
  }

  private Long parseInteger() throws BencodeException {
    if (data[position] != 'i') {
      throw new BencodeException("Expected 'i' at position " + position);
    }
    position++;

    int start = position;
    while (position < data.length && data[position] != 'e') {
      position++;
    }

    if (position >= data.length) {
      throw new BencodeException("Unterminated integer at position " + start);
    }

    String numStr = new String(data, start, position - start, StandardCharsets.US_ASCII);
    position++; // skip 'e'

    // Validate integer format
    if (numStr.isEmpty()) {
      throw new BencodeException("Empty integer at position " + start);
    }
    if (numStr.equals("-0")) {
      throw new BencodeException("Invalid integer format '-0' at position " + start);
    }
    if (numStr.length() > 1 && numStr.charAt(0) == '0') {
      throw new BencodeException("Leading zeros in integer at position " + start);
    }
    if (numStr.length() > 1 && numStr.charAt(0) == '-' && numStr.charAt(1) == '0') {
      throw new BencodeException("Leading zeros in negative integer at position " + start);
    }

    try {
      return Long.parseLong(numStr);
    } catch (NumberFormatException e) {
      throw new BencodeException("Invalid integer '" + numStr + "' at position " + start);
    }
  }

  private byte[] parseByteString() throws BencodeException {
    int start = position;
    while (position < data.length && data[position] != ':') {
      if (data[position] < '0' || data[position] > '9') {
        throw new BencodeException("Invalid string length at position " + start);
      }
      position++;
    }

    if (position >= data.length) {
      throw new BencodeException("Unterminated string length at position " + start);
    }

    String lengthStr = new String(data, start, position - start, StandardCharsets.US_ASCII);
    if (lengthStr.isEmpty()) {
      throw new BencodeException("Empty string length at position " + start);
    }

    int length;
    try {
      length = Integer.parseInt(lengthStr);
    } catch (NumberFormatException e) {
      throw new BencodeException("Invalid string length '" + lengthStr + "' at position " + start);
    }

    position++; // skip ':'

    if (position + length > data.length) {
      throw new BencodeException(
          "String length " + length + " exceeds available data at position " + position);
    }

    byte[] result = new byte[length];
    System.arraycopy(data, position, result, 0, length);
    position += length;
    return result;
  }

  private List<Object> parseList() throws BencodeException {
    if (data[position] != 'l') {
      throw new BencodeException("Expected 'l' at position " + position);
    }
    position++;

    List<Object> list = new ArrayList<>();
    while (position < data.length && data[position] != 'e') {
      list.add(parseValue());
    }

    if (position >= data.length) {
      throw new BencodeException("Unterminated list");
    }
    position++; // skip 'e'
    return list;
  }

  private Map<String, Object> parseDictionary() throws BencodeException {
    if (data[position] != 'd') {
      throw new BencodeException("Expected 'd' at position " + position);
    }
    position++;

    Map<String, Object> dict = new LinkedHashMap<>();
    String lastKey = null;

    while (position < data.length && data[position] != 'e') {
      // Keys must be byte strings
      if (data[position] < '0' || data[position] > '9') {
        throw new BencodeException("Dictionary key must be a string at position " + position);
      }

      byte[] keyBytes = parseByteString();
      String key = new String(keyBytes, StandardCharsets.UTF_8);

      // Keys must be sorted (canonical bencoding)
      if (lastKey != null && key.compareTo(lastKey) <= 0) {
        throw new BencodeException("Dictionary keys not sorted: '" + lastKey + "' >= '" + key + "'");
      }
      lastKey = key;

      Object value = parseValue();
      dict.put(key, value);
    }

    if (position >= data.length) {
      throw new BencodeException("Unterminated dictionary");
    }
    position++; // skip 'e'
    return dict;
  }

  /**
   * Parse bencode data and track the byte range of a specific dictionary key.
   * Returns the start and end positions (exclusive) of the value for the given
   * key.
   *
   * @param key the dictionary key to find
   * @return int array with [startPosition, endPosition] or null if not found
   * @throws BencodeException if parsing fails
   */
  public int[] findDictionaryValueRange(String key) throws BencodeException {
    if (position >= data.length || data[position] != 'd') {
      throw new BencodeException("Expected dictionary at position " + position);
    }
    position++;

    while (position < data.length && data[position] != 'e') {
      byte[] keyBytes = parseByteString();
      String currentKey = new String(keyBytes, StandardCharsets.UTF_8);

      int valueStart = position;
      parseValue(); // Skip the value
      int valueEnd = position;

      if (currentKey.equals(key)) {
        return new int[] { valueStart, valueEnd };
      }
    }

    return null;
  }

  /**
   * Encode an object to bencode format.
   *
   * @param obj the object to encode
   * @return bencode encoded bytes
   * @throws BencodeException if encoding fails
   */
  public static byte[] encode(Object obj) throws BencodeException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    encodeValue(obj, out);
    return out.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static void encodeValue(Object obj, ByteArrayOutputStream out) throws BencodeException {
    if (obj instanceof Long || obj instanceof Integer) {
      long value = obj instanceof Long ? (Long) obj : ((Integer) obj).longValue();
      out.write('i');
      byte[] numBytes = String.valueOf(value).getBytes(StandardCharsets.US_ASCII);
      out.writeBytes(numBytes);
      out.write('e');
    } else if (obj instanceof byte[]) {
      byte[] bytes = (byte[]) obj;
      byte[] lengthBytes = String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII);
      out.writeBytes(lengthBytes);
      out.write(':');
      out.writeBytes(bytes);
    } else if (obj instanceof String) {
      byte[] bytes = ((String) obj).getBytes(StandardCharsets.UTF_8);
      byte[] lengthBytes = String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII);
      out.writeBytes(lengthBytes);
      out.write(':');
      out.writeBytes(bytes);
    } else if (obj instanceof List) {
      out.write('l');
      for (Object item : (List<?>) obj) {
        encodeValue(item, out);
      }
      out.write('e');
    } else if (obj instanceof Map) {
      out.write('d');
      Map<String, Object> map = (Map<String, Object>) obj;
      // Sort keys for canonical encoding
      List<String> keys = new ArrayList<>(map.keySet());
      keys.sort(String::compareTo);
      for (String key : keys) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] lengthBytes = String.valueOf(keyBytes.length).getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(lengthBytes);
        out.write(':');
        out.writeBytes(keyBytes);
        encodeValue(map.get(key), out);
      }
      out.write('e');
    } else {
      throw new BencodeException("Cannot encode object of type " + obj.getClass().getName());
    }
  }
}
