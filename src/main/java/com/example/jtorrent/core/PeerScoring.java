package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks peer misbehavior and scores peers based on their reliability.
 * 
 * Peers are penalized for:
 * - Hash verification failures (bad data)
 * - Invalid messages
 * - Timeouts / slow responses
 * - Protocol violations
 * 
 * Peers with low scores are deprioritized or banned.
 */
public class PeerScoring {

    private static final Logger logger = Logger.getLogger(PeerScoring.class);

    /** Initial score for new peers. */
    private static final int INITIAL_SCORE = 100;

    /** Score at which peer is banned. */
    private static final int BAN_THRESHOLD = 0;

    /** Score at which peer is deprioritized. */
    private static final int DEPRIORITIZE_THRESHOLD = 30;

    /** Penalty amounts. */
    private static final int PENALTY_HASH_FAIL = 50;
    private static final int PENALTY_INVALID_MESSAGE = 20;
    private static final int PENALTY_TIMEOUT = 10;
    private static final int PENALTY_PROTOCOL_VIOLATION = 30;

    /** Reward amounts. */
    private static final int REWARD_GOOD_PIECE = 5;
    private static final int REWARD_FAST_RESPONSE = 2;

    /** Maximum score. */
    private static final int MAX_SCORE = 150;

    /** Ban duration in milliseconds (1 hour). */
    private static final long BAN_DURATION_MS = 60 * 60 * 1000;

    /** Per-peer scores. */
    private final Map<InetSocketAddress, PeerScore> peerScores = new ConcurrentHashMap<>();

    /** Banned peers with ban expiry time. */
    private final Map<InetSocketAddress, Long> bannedPeers = new ConcurrentHashMap<>();

    /** Global statistics. */
    private final AtomicLong totalHashFails = new AtomicLong(0);
    private final AtomicLong totalInvalidMessages = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    private final AtomicLong totalBans = new AtomicLong(0);

    /**
     * Get or create a peer's score record.
     *
     * @param peer the peer address
     * @return peer score record
     */
    public PeerScore getOrCreateScore(InetSocketAddress peer) {
        return peerScores.computeIfAbsent(peer, k -> new PeerScore(INITIAL_SCORE));
    }

    /**
     * Record a hash verification failure from a peer.
     *
     * @param peer       the peer address
     * @param pieceIndex the failed piece
     */
    public void recordHashFailure(InetSocketAddress peer, int pieceIndex) {
        PeerScore score = getOrCreateScore(peer);
        score.hashFailures.incrementAndGet();
        score.adjustScore(-PENALTY_HASH_FAIL);
        totalHashFails.incrementAndGet();

        logger.warn("Peer %s hash failure for piece %d (score: %d)", peer, pieceIndex, score.getScore());

        checkBan(peer, score);
    }

    /**
     * Record an invalid message from a peer.
     *
     * @param peer   the peer address
     * @param reason description of the invalid message
     */
    public void recordInvalidMessage(InetSocketAddress peer, String reason) {
        PeerScore score = getOrCreateScore(peer);
        score.invalidMessages.incrementAndGet();
        score.adjustScore(-PENALTY_INVALID_MESSAGE);
        totalInvalidMessages.incrementAndGet();

        logger.warn("Peer %s invalid message: %s (score: %d)", peer, reason, score.getScore());

        checkBan(peer, score);
    }

    /**
     * Record a timeout from a peer.
     *
     * @param peer the peer address
     */
    public void recordTimeout(InetSocketAddress peer) {
        PeerScore score = getOrCreateScore(peer);
        score.timeouts.incrementAndGet();
        score.adjustScore(-PENALTY_TIMEOUT);
        totalTimeouts.incrementAndGet();

        logger.debug("Peer %s timeout (score: %d)", peer, score.getScore());

        checkBan(peer, score);
    }

    /**
     * Record a protocol violation from a peer.
     *
     * @param peer      the peer address
     * @param violation description of the violation
     */
    public void recordProtocolViolation(InetSocketAddress peer, String violation) {
        PeerScore score = getOrCreateScore(peer);
        score.protocolViolations.incrementAndGet();
        score.adjustScore(-PENALTY_PROTOCOL_VIOLATION);

        logger.warn("Peer %s protocol violation: %s (score: %d)", peer, violation, score.getScore());

        checkBan(peer, score);
    }

    /**
     * Reward a peer for providing a good piece.
     *
     * @param peer the peer address
     */
    public void rewardGoodPiece(InetSocketAddress peer) {
        PeerScore score = getOrCreateScore(peer);
        score.goodPieces.incrementAndGet();
        score.adjustScore(REWARD_GOOD_PIECE);
    }

    /**
     * Reward a peer for fast response.
     *
     * @param peer           the peer address
     * @param responseTimeMs response time in milliseconds
     */
    public void rewardFastResponse(InetSocketAddress peer, long responseTimeMs) {
        if (responseTimeMs < 1000) { // Under 1 second
            PeerScore score = getOrCreateScore(peer);
            score.adjustScore(REWARD_FAST_RESPONSE);
        }
    }

    /**
     * Check if peer should be banned and ban if necessary.
     */
    private void checkBan(InetSocketAddress peer, PeerScore score) {
        if (score.getScore() <= BAN_THRESHOLD) {
            banPeer(peer);
        }
    }

    /**
     * Ban a peer.
     *
     * @param peer the peer to ban
     */
    public void banPeer(InetSocketAddress peer) {
        long expiryTime = System.currentTimeMillis() + BAN_DURATION_MS;
        bannedPeers.put(peer, expiryTime);
        totalBans.incrementAndGet();
        logger.warn("Peer %s banned until %s", peer, new Date(expiryTime));
    }

    /**
     * Check if a peer is banned.
     *
     * @param peer the peer address
     * @return true if banned
     */
    public boolean isBanned(InetSocketAddress peer) {
        Long expiryTime = bannedPeers.get(peer);
        if (expiryTime == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiryTime) {
            // Ban expired
            bannedPeers.remove(peer);
            peerScores.remove(peer); // Reset score
            logger.info("Peer %s ban expired", peer);
            return false;
        }

        return true;
    }

    /**
     * Check if a peer should be deprioritized.
     *
     * @param peer the peer address
     * @return true if should deprioritize
     */
    public boolean shouldDeprioritize(InetSocketAddress peer) {
        PeerScore score = peerScores.get(peer);
        return score != null && score.getScore() <= DEPRIORITIZE_THRESHOLD;
    }

    /**
     * Get a peer's current score.
     *
     * @param peer the peer address
     * @return score (0-150) or -1 if banned
     */
    public int getPeerScore(InetSocketAddress peer) {
        if (isBanned(peer)) {
            return -1;
        }
        PeerScore score = peerScores.get(peer);
        return score != null ? score.getScore() : INITIAL_SCORE;
    }

    /**
     * Remove a peer from tracking.
     *
     * @param peer the peer address
     */
    public void removePeer(InetSocketAddress peer) {
        peerScores.remove(peer);
    }

    /**
     * Get all banned peer addresses.
     *
     * @return set of banned peers
     */
    public Set<InetSocketAddress> getBannedPeers() {
        // Clean expired bans
        long now = System.currentTimeMillis();
        bannedPeers.entrySet().removeIf(e -> e.getValue() < now);
        return new HashSet<>(bannedPeers.keySet());
    }

    /**
     * Get global statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("trackedPeers", peerScores.size());
        stats.put("bannedPeers", bannedPeers.size());
        stats.put("totalHashFails", totalHashFails.get());
        stats.put("totalInvalidMessages", totalInvalidMessages.get());
        stats.put("totalTimeouts", totalTimeouts.get());
        stats.put("totalBans", totalBans.get());
        return stats;
    }

    /**
     * Get detailed info for a specific peer.
     *
     * @param peer the peer address
     * @return peer info or null
     */
    public Map<String, Object> getPeerInfo(InetSocketAddress peer) {
        PeerScore score = peerScores.get(peer);
        if (score == null) {
            return null;
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("score", score.getScore());
        info.put("hashFailures", score.hashFailures.get());
        info.put("invalidMessages", score.invalidMessages.get());
        info.put("timeouts", score.timeouts.get());
        info.put("protocolViolations", score.protocolViolations.get());
        info.put("goodPieces", score.goodPieces.get());
        info.put("banned", isBanned(peer));
        info.put("deprioritized", shouldDeprioritize(peer));
        return info;
    }

    /**
     * Per-peer score tracking.
     */
    public static class PeerScore {
        private final AtomicInteger score;
        private final AtomicInteger hashFailures = new AtomicInteger(0);
        private final AtomicInteger invalidMessages = new AtomicInteger(0);
        private final AtomicInteger timeouts = new AtomicInteger(0);
        private final AtomicInteger protocolViolations = new AtomicInteger(0);
        private final AtomicInteger goodPieces = new AtomicInteger(0);

        PeerScore(int initialScore) {
            this.score = new AtomicInteger(initialScore);
        }

        public int getScore() {
            return score.get();
        }

        void adjustScore(int delta) {
            score.updateAndGet(current -> Math.max(BAN_THRESHOLD, Math.min(MAX_SCORE, current + delta)));
        }
    }
}
