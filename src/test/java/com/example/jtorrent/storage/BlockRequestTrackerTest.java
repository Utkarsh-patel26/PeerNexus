package com.example.jtorrent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockRequestTracker Tests")
class BlockRequestTrackerTest {

    private static final InetSocketAddress PEER1 = new InetSocketAddress("127.0.0.1", 6881);
    private static final InetSocketAddress PEER2 = new InetSocketAddress("127.0.0.1", 6882);

    @Test
    @DisplayName("Create block request tracker")
    void testCreateTracker() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        assertNotNull(tracker);
    }

    @Test
    @DisplayName("Add block request")
    void testAddRequest() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER1);

        assertDoesNotThrow(() -> tracker.addRequest(request));
    }

    @Test
    @DisplayName("Check if block requested")
    void testIsRequested() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER1);

        tracker.addRequest(request);
        assertTrue(tracker.isRequested(5, 16384));
    }

    @Test
    @DisplayName("Fulfill block request")
    void testFulfillRequest() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER1);

        tracker.addRequest(request);
        tracker.fulfillRequest(5, 16384);
        assertFalse(tracker.isRequested(5, 16384));
    }

    @Test
    @DisplayName("Get all requests")
    void testGetAllRequests() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER1);

        tracker.addRequest(request);
        var all = tracker.getAllRequests();
        assertNotNull(all);
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("Clear all requests")
    void testClear() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        tracker.addRequest(new BlockRequest(5, 16384, 8192, PEER1));
        tracker.addRequest(new BlockRequest(6, 0, 8192, PEER2));

        tracker.clear();
        assertEquals(0, tracker.getTotalRequestCount());
    }

    @Test
    @DisplayName("Get request count")
    void testGetRequestCount() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        tracker.addRequest(new BlockRequest(5, 16384, 8192, PEER1));
        tracker.addRequest(new BlockRequest(6, 0, 8192, PEER1));

        assertEquals(2, tracker.getRequestCount(PEER1));
    }

    @Test
    @DisplayName("Check stale requests")
    void testCheckStaleRequests() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER1);
        tracker.addRequest(request);

        var stale = tracker.getStaleRequests();
        assertNotNull(stale);
    }

    @Test
    @DisplayName("Initially empty")
    void testInitiallyEmpty() {
        BlockRequestTracker tracker = new BlockRequestTracker();
        assertEquals(0, tracker.getTotalRequestCount());
        assertTrue(tracker.getAllRequests().isEmpty());
    }
}
