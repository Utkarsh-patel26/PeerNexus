package com.example.jtorrent.storage;

/**
 * Block within a piece.
 */
public class BlockInfo {

  private final int pieceIndex;
  private final int offset;
  private final int length;

  public BlockInfo(int pieceIndex, int offset, int length) {
    this.pieceIndex = pieceIndex;
    this.offset = offset;
    this.length = length;
  }

  public int pieceIndex() {
    return pieceIndex;
  }

  public int offset() {
    return offset;
  }

  public int length() {
    return length;
  }

  @Override
  public String toString() {
    return "Block(piece=" + pieceIndex + ", offset=" + offset + ", length=" + length + ")";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BlockInfo other = (BlockInfo) obj;
    return pieceIndex == other.pieceIndex && offset == other.offset && length == other.length;
  }

  @Override
  public int hashCode() {
    return 31 * (31 * pieceIndex + offset) + length;
  }
}
