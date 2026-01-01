package com.example.jtorrent.storage;

/**
 * Piece download state.
 */
public enum PieceState {
  /**
   * Piece has not been downloaded.
   */
  MISSING,

  /**
   * Piece is currently being downloaded (some blocks received).
   */
  DOWNLOADING,

  /**
   * Piece has been fully downloaded and verified.
   */
  COMPLETE
}
