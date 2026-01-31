package com.example.jtorrent.scheduler;

import com.example.jtorrent.logging.Logger;
import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SuperSeedingManager {

    private static final Logger logger = Logger.getLogger(SuperSeedingManager.class);

    private final int totalPieces;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Map<Integer, Set<InetSocketAddress>> piecePropagation = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, BitSet> peerBitfields = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, Integer> lastSentPiece = new ConcurrentHashMap<>();
    private final BitSet fullyPropagated = new BitSet();
    private int propagationThreshold = 2;

    public SuperSeedingManager(int totalPieces) {
        this.totalPieces = totalPieces;
    }

    public void enable() {
        enabled.set(true);
        logger.info("Super seeding enabled");
    }

    public void disable() {
        enabled.set(false);
        logger.info("Super seeding disabled");
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setPropagationThreshold(int threshold) {
        this.propagationThreshold = threshold;
    }

    public void registerPeer(InetSocketAddress peer, BitSet bitfield) {
        peerBitfields.put(peer, bitfield != null ? (BitSet) bitfield.clone() : new BitSet());
    }

    public void unregisterPeer(InetSocketAddress peer) {
        peerBitfields.remove(peer);
        lastSentPiece.remove(peer);
    }

    public void updatePeerBitfield(InetSocketAddress peer, BitSet bitfield) {
        BitSet oldBitfield = peerBitfields.get(peer);
        if (oldBitfield == null) {
            registerPeer(peer, bitfield);
            return;
        }

        for (int i = bitfield.nextSetBit(0); i >= 0; i = bitfield.nextSetBit(i + 1)) {
            if (!oldBitfield.get(i)) {
                recordPropagation(i, peer);
            }
        }

        peerBitfields.put(peer, (BitSet) bitfield.clone());
    }

    public void recordHave(InetSocketAddress peer, int pieceIndex) {
        BitSet bitfield = peerBitfields.get(peer);
        if (bitfield != null && !bitfield.get(pieceIndex)) {
            bitfield.set(pieceIndex);
            recordPropagation(pieceIndex, peer);
        }
    }

    private void recordPropagation(int pieceIndex, InetSocketAddress peer) {
        Set<InetSocketAddress> peers = piecePropagation.computeIfAbsent(pieceIndex, k -> ConcurrentHashMap.newKeySet());
        peers.add(peer);

        if (peers.size() >= propagationThreshold && !fullyPropagated.get(pieceIndex)) {
            fullyPropagated.set(pieceIndex);
            logger.debug("Piece %d fully propagated", pieceIndex);
        }
    }

    public int selectPieceForPeer(InetSocketAddress peer) {
        if (!enabled.get()) {
            return -1;
        }

        BitSet peerHas = peerBitfields.getOrDefault(peer, new BitSet());

        int bestPiece = -1;
        int lowestPropagation = Integer.MAX_VALUE;

        for (int i = 0; i < totalPieces; i++) {
            if (peerHas.get(i)) {
                continue;
            }

            Set<InetSocketAddress> propagated = piecePropagation.get(i);
            int count = (propagated == null) ? 0 : propagated.size();

            if (count < lowestPropagation) {
                lowestPropagation = count;
                bestPiece = i;
            }
        }

        if (bestPiece >= 0) {
            lastSentPiece.put(peer, bestPiece);
        }

        return bestPiece;
    }

    public boolean shouldAllowRequest(InetSocketAddress peer, int pieceIndex) {
        if (!enabled.get()) {
            return true;
        }

        Integer lastSent = lastSentPiece.get(peer);
        if (lastSent == null) {
            return false;
        }

        return lastSent == pieceIndex;
    }

    public int getPropagationCount(int pieceIndex) {
        Set<InetSocketAddress> peers = piecePropagation.get(pieceIndex);
        return peers == null ? 0 : peers.size();
    }

    public int getFullyPropagatedCount() {
        return fullyPropagated.cardinality();
    }

    public double getPropagationProgress() {
        return totalPieces > 0 ? (double) fullyPropagated.cardinality() / totalPieces * 100.0 : 0.0;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled.get());
        stats.put("totalPieces", totalPieces);
        stats.put("fullyPropagated", fullyPropagated.cardinality());
        stats.put("propagationProgress", getPropagationProgress());
        stats.put("activePeers", peerBitfields.size());
        stats.put("threshold", propagationThreshold);
        return stats;
    }
}
