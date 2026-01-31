package com.example.jtorrent.storage;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks outstanding block requests for pipelining across multiple peers.
 * Enables requesting many blocks in parallel for maximum throughput.
 */
public class BlockRequestTracker {

    private static final long REQUEST_TIMEOUT_MS = 15000; // 15 seconds (reduced from 30s for faster recovery)
    private static final int MAX_REQUESTS_PER_PEER = 25; // Increased from 10 for better pipelining
    private static final int MAX_TOTAL_REQUESTS = 200; // Increased from 50 for modern throughput

    private final List<BlockRequest> outstandingRequests = new CopyOnWriteArrayList<>();
    private final Map<InetSocketAddress, Integer> requestCountPerPeer = new ConcurrentHashMap<>();

    /**
     * Add a block request.
     *
     * @param request the request
     * @return true if added, false if limit reached
     */
    public boolean addRequest(BlockRequest request) {
        // Check total limit
        if (outstandingRequests.size() >= MAX_TOTAL_REQUESTS) {
            return false;
        }

        // Check per-peer limit
        int peerCount = requestCountPerPeer.getOrDefault(request.getPeer(), 0);
        if (peerCount >= MAX_REQUESTS_PER_PEER) {
            return false;
        }

        outstandingRequests.add(request);
        requestCountPerPeer.put(request.getPeer(), peerCount + 1);
        return true;
    }

    /**
     * Mark a block request as fulfilled.
     *
     * @param pieceIndex piece index
     * @param offset     block offset
     * @return true if request was found and fulfilled
     */
    public boolean fulfillRequest(int pieceIndex, int offset) {
        for (BlockRequest request : outstandingRequests) {
            if (request.getPieceIndex() == pieceIndex && request.getOffset() == offset) {
                request.markFulfilled();
                return removeRequest(request);
            }
        }
        return false;
    }

    /**
     * Remove a request.
     *
     * @param request the request to remove
     * @return true if removed
     */
    private boolean removeRequest(BlockRequest request) {
        if (outstandingRequests.remove(request)) {
            InetSocketAddress peer = request.getPeer();
            requestCountPerPeer.computeIfPresent(peer, (k, v) -> v > 1 ? v - 1 : null);
            return true;
        }
        return false;
    }

    /**
     * Get stale requests that have timed out.
     *
     * @return list of stale requests
     */
    public List<BlockRequest> getStaleRequests() {
        List<BlockRequest> stale = new ArrayList<>();
        for (BlockRequest request : outstandingRequests) {
            if (request.isStale(REQUEST_TIMEOUT_MS)) {
                stale.add(request);
            }
        }
        return stale;
    }

    /**
     * Cancel all requests for a specific peer.
     *
     * @param peer the peer address
     * @return number of requests cancelled
     */
    public int cancelPeerRequests(InetSocketAddress peer) {
        // Count requests before removal
        int cancelled = (int) outstandingRequests.stream()
                .filter(request -> request.getPeer().equals(peer))
                .count();

        // Remove all requests for this peer using removeIf (supported by
        // CopyOnWriteArrayList)
        outstandingRequests.removeIf(request -> request.getPeer().equals(peer));

        requestCountPerPeer.remove(peer);
        return cancelled;
    }

    /**
     * Cancel all requests for a specific piece.
     *
     * @param pieceIndex the piece index
     * @return number of requests cancelled
     */
    public int cancelPieceRequests(int pieceIndex) {
        // Collect requests to remove and update peer counts
        List<BlockRequest> toRemove = new ArrayList<>();
        for (BlockRequest request : outstandingRequests) {
            if (request.getPieceIndex() == pieceIndex) {
                toRemove.add(request);
                InetSocketAddress peer = request.getPeer();
                requestCountPerPeer.computeIfPresent(peer, (k, v) -> v > 1 ? v - 1 : null);
            }
        }

        // Remove all at once using removeAll (supported by CopyOnWriteArrayList)
        outstandingRequests.removeAll(toRemove);

        return toRemove.size();
    }

    /**
     * Check if a block is already requested.
     *
     * @param pieceIndex piece index
     * @param offset     block offset
     * @return true if already requested
     */
    public boolean isRequested(int pieceIndex, int offset) {
        for (BlockRequest request : outstandingRequests) {
            if (request.getPieceIndex() == pieceIndex && request.getOffset() == offset && !request.isFulfilled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get number of outstanding requests for a peer.
     *
     * @param peer peer address
     * @return request count
     */
    public int getRequestCount(InetSocketAddress peer) {
        return requestCountPerPeer.getOrDefault(peer, 0);
    }

    /**
     * Get total outstanding requests.
     *
     * @return total count
     */
    public int getTotalRequestCount() {
        return outstandingRequests.size();
    }

    /**
     * Clear all requests.
     */
    public void clear() {
        outstandingRequests.clear();
        requestCountPerPeer.clear();
    }

    /**
     * Get all active requests.
     *
     * @return list of requests
     */
    public List<BlockRequest> getAllRequests() {
        return new ArrayList<>(outstandingRequests);
    }
}
