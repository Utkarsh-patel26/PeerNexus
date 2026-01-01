package com.example.jtorrent.peer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BitTorrent peer wire protocol message.
 */
public class Message {

  /** Message type IDs. */
  public static final int CHOKE = 0;
  public static final int UNCHOKE = 1;
  public static final int INTERESTED = 2;
  public static final int NOT_INTERESTED = 3;
  public static final int HAVE = 4;
  public static final int BITFIELD = 5;
  public static final int REQUEST = 6;
  public static final int PIECE = 7;
  public static final int CANCEL = 8;
  public static final int PORT = 9;
  public static final int EXTENDED = 20;
  public static final int KEEP_ALIVE = -1;

  private final int type;
  private final byte[] payload;

  /**
   * Create a message with no payload (CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED).
   *
   * @param type message type
   */
  public Message(int type) {
    this.type = type;
    this.payload = new byte[0];
  }

  /**
   * Create a message with payload.
   *
   * @param type message type
   * @param payload message payload
   */
  public Message(int type, byte[] payload) {
    this.type = type;
    this.payload = payload != null ? payload.clone() : new byte[0];
  }

  /**
   * Get the message type.
   *
   * @return message type ID
   */
  public int type() {
    return type;
  }

  /**
   * Get the message payload.
   *
   * @return payload bytes
   */
  public byte[] payload() {
    return payload.clone();
  }

  /**
   * Get the message type name.
   *
   * @return type name string
   */
  public String typeName() {
    switch (type) {
      case KEEP_ALIVE:
        return "KEEP_ALIVE";
      case CHOKE:
        return "CHOKE";
      case UNCHOKE:
        return "UNCHOKE";
      case INTERESTED:
        return "INTERESTED";
      case NOT_INTERESTED:
        return "NOT_INTERESTED";
      case HAVE:
        return "HAVE";
      case BITFIELD:
        return "BITFIELD";
      case REQUEST:
        return "REQUEST";
      case PIECE:
        return "PIECE";
      case CANCEL:
        return "CANCEL";
      case PORT:
        return "PORT";
      case EXTENDED:
        return "EXTENDED";
      default:
        return "UNKNOWN(" + type + ")";
    }
  }

  /**
   * Create a HAVE message.
   *
   * @param pieceIndex the piece index
   * @return HAVE message
   */
  public static Message have(int pieceIndex) {
    ByteBuffer buf = ByteBuffer.allocate(4);
    buf.putInt(pieceIndex);
    return new Message(HAVE, buf.array());
  }

  /**
   * Create a BITFIELD message.
   *
   * @param bitfield the bitfield bytes
   * @return BITFIELD message
   */
  public static Message bitfield(byte[] bitfield) {
    return new Message(BITFIELD, bitfield);
  }

  /**
   * Create a REQUEST message.
   *
   * @param index piece index
   * @param begin byte offset within piece
   * @param length number of bytes requested
   * @return REQUEST message
   */
  public static Message request(int index, int begin, int length) {
    ByteBuffer buf = ByteBuffer.allocate(12);
    buf.putInt(index);
    buf.putInt(begin);
    buf.putInt(length);
    return new Message(REQUEST, buf.array());
  }

  /**
   * Create a PIECE message.
   *
   * @param index piece index
   * @param begin byte offset within piece
   * @param block the data block
   * @return PIECE message
   */
  public static Message piece(int index, int begin, byte[] block) {
    ByteBuffer buf = ByteBuffer.allocate(8 + block.length);
    buf.putInt(index);
    buf.putInt(begin);
    buf.put(block);
    return new Message(PIECE, buf.array());
  }

  /**
   * Create a CANCEL message.
   *
   * @param index piece index
   * @param begin byte offset within piece
   * @param length number of bytes
   * @return CANCEL message
   */
  public static Message cancel(int index, int begin, int length) {
    ByteBuffer buf = ByteBuffer.allocate(12);
    buf.putInt(index);
    buf.putInt(begin);
    buf.putInt(length);
    return new Message(CANCEL, buf.array());
  }

  /**
   * Create a PORT message.
   *
   * @param port the DHT port
   * @return PORT message
   */
  public static Message port(int port) {
    ByteBuffer buf = ByteBuffer.allocate(2);
    buf.putShort((short) port);
    return new Message(PORT, buf.array());
  }

  /**
   * Parse a HAVE message to get the piece index.
   *
   * @return piece index
   */
  public int havePieceIndex() {
    if (type != HAVE || payload.length < 4) {
      throw new IllegalStateException("Not a HAVE message");
    }
    return ByteBuffer.wrap(payload).getInt();
  }

  /**
   * Parse a REQUEST message to get index, begin, length.
   *
   * @return int array [index, begin, length]
   */
  public int[] requestParams() {
    if (type != REQUEST || payload.length < 12) {
      throw new IllegalStateException("Not a REQUEST message");
    }
    ByteBuffer buf = ByteBuffer.wrap(payload);
    return new int[] {buf.getInt(), buf.getInt(), buf.getInt()};
  }

  /**
   * Parse a PIECE message to get index and begin.
   *
   * @return int array [index, begin]
   */
  public int[] pieceParams() {
    if (type != PIECE || payload.length < 8) {
      throw new IllegalStateException("Not a PIECE message");
    }
    ByteBuffer buf = ByteBuffer.wrap(payload);
    return new int[] {buf.getInt(), buf.getInt()};
  }

  /**
   * Get the block data from a PIECE message.
   *
   * @return block data
   */
  public byte[] pieceBlock() {
    if (type != PIECE || payload.length < 8) {
      throw new IllegalStateException("Not a PIECE message");
    }
    byte[] block = new byte[payload.length - 8];
    System.arraycopy(payload, 8, block, 0, block.length);
    return block;
  }

  /**
   * Parse a CANCEL message to get index, begin, length.
   *
   * @return int array [index, begin, length]
   */
  public int[] cancelParams() {
    if (type != CANCEL || payload.length < 12) {
      throw new IllegalStateException("Not a CANCEL message");
    }
    ByteBuffer buf = ByteBuffer.wrap(payload);
    return new int[] {buf.getInt(), buf.getInt(), buf.getInt()};
  }

  /**
   * Parse a PORT message to get the port number.
   *
   * @return port number
   */
  public int portNumber() {
    if (type != PORT || payload.length < 2) {
      throw new IllegalStateException("Not a PORT message");
    }
    return ByteBuffer.wrap(payload).getShort() & 0xFFFF;
  }

  /**
   * Serialize the message to bytes with 4-byte length prefix.
   *
   * @return serialized message
   */
  public byte[] serialize() {
    if (type == KEEP_ALIVE) {
      return new byte[] {0, 0, 0, 0};
    }

    int length = 1 + payload.length;
    ByteBuffer buf = ByteBuffer.allocate(4 + length);
    buf.putInt(length);
    buf.put((byte) type);
    buf.put(payload);
    return buf.array();
  }

  /**
   * Deserialize a message from bytes (without length prefix).
   *
   * @param data message data (type + payload)
   * @return deserialized message
   */
  public static Message deserialize(byte[] data) {
    if (data == null || data.length == 0) {
      return new Message(KEEP_ALIVE);
    }
    int type = data[0] & 0xFF;
    byte[] payload = new byte[data.length - 1];
    if (payload.length > 0) {
      System.arraycopy(data, 1, payload, 0, payload.length);
    }
    return new Message(type, payload);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(typeName());

    switch (type) {
      case HAVE:
        if (payload.length >= 4) {
          sb.append("(piece=").append(havePieceIndex()).append(")");
        }
        break;
      case REQUEST:
      case CANCEL:
        if (payload.length >= 12) {
          int[] params = type == REQUEST ? requestParams() : cancelParams();
          sb.append("(index=").append(params[0]);
          sb.append(", begin=").append(params[1]);
          sb.append(", length=").append(params[2]).append(")");
        }
        break;
      case PIECE:
        if (payload.length >= 8) {
          int[] params = pieceParams();
          sb.append("(index=").append(params[0]);
          sb.append(", begin=").append(params[1]);
          sb.append(", length=").append(payload.length - 8).append(")");
        }
        break;
      case BITFIELD:
        sb.append("(").append(payload.length).append(" bytes)");
        break;
      case PORT:
        if (payload.length >= 2) {
          sb.append("(").append(portNumber()).append(")");
        }
        break;
      default:
        break;
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Message other = (Message) obj;
    return type == other.type && Arrays.equals(payload, other.payload);
  }

  @Override
  public int hashCode() {
    int result = type;
    result = 31 * result + Arrays.hashCode(payload);
    return result;
  }
}
