package com.example.jtorrent.storage;

/**
 * Piece selection strategy for optimizing download performance.
 */
public enum PieceSelectionStrategy {
    /**
     * Random-first: Select random pieces until first completion.
     * Good for quickly getting something to share.
     */
    RANDOM_FIRST,

    /**
     * Rarest-first: Select pieces with lowest availability.
     * Standard BitTorrent strategy for efficient swarming.
     */
    RAREST_FIRST,

    /**
     * Endgame: Request all remaining blocks from multiple peers.
     * Used when close to completion to finish quickly.
     */
    ENDGAME
}
