package com.example.jtorrent.tracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@DisplayName("AnnounceResponse Tests")
class AnnounceResponseTest {

    @Test
    @DisplayName("Create successful response")
    void testSuccessfulResponse() {
        List<PeerEndpoint> peers = Collections.emptyList();
        AnnounceResponse response = new AnnounceResponse(1800, 10, 5, peers);

        assertNotNull(response);
        assertFalse(response.isFailure());
        assertEquals(1800, response.interval());
        assertEquals(10, response.leechers());
        assertEquals(5, response.seeders());
        assertEquals(0, response.peers().size());
    }

    @Test
    @DisplayName("Create response with peers")
    void testResponseWithPeers() {
        PeerEndpoint peer = new PeerEndpoint(new java.net.InetSocketAddress("127.0.0.1", 6881));
        List<PeerEndpoint> peers = Arrays.asList(peer);
        AnnounceResponse response = new AnnounceResponse(1800, 10, 5, peers);

        assertEquals(1, response.peers().size());
    }

    @Test
    @DisplayName("Create response with warning")
    void testResponseWithWarning() {
        List<PeerEndpoint> peers = Collections.emptyList();
        AnnounceResponse response = new AnnounceResponse(1800, 10, 5, peers, "Warning message");

        assertFalse(response.isFailure());
        assertEquals("Warning message", response.warningMessage());
    }

    @Test
    @DisplayName("Create failed response")
    void testFailedResponse() {
        AnnounceResponse response = new AnnounceResponse("Connection failed");

        assertTrue(response.isFailure());
        assertEquals("Connection failed", response.failureReason());
        assertEquals(0, response.interval());
        assertEquals(0, response.leechers());
        assertEquals(0, response.seeders());
    }

    @Test
    @DisplayName("Get interval")
    void testGetInterval() {
        AnnounceResponse response = new AnnounceResponse(3600, 0, 0, null);
        assertEquals(3600, response.interval());
    }

    @Test
    @DisplayName("Get leechers count")
    void testGetLeechers() {
        AnnounceResponse response = new AnnounceResponse(1800, 25, 0, null);
        assertEquals(25, response.leechers());
    }

    @Test
    @DisplayName("Get seeders count")
    void testGetSeeders() {
        AnnounceResponse response = new AnnounceResponse(1800, 0, 15, null);
        assertEquals(15, response.seeders());
    }

    @Test
    @DisplayName("Get peers list")
    void testGetPeers() {
        List<PeerEndpoint> peers = Collections.emptyList();
        AnnounceResponse response = new AnnounceResponse(1800, 0, 0, peers);
        assertNotNull(response.peers());
    }

    @Test
    @DisplayName("Handle null peers list")
    void testNullPeersList() {
        AnnounceResponse response = new AnnounceResponse(1800, 0, 0, null);
        assertNotNull(response.peers());
        assertEquals(0, response.peers().size());
    }

    @Test
    @DisplayName("Check failure status")
    void testIsFailure() {
        AnnounceResponse success = new AnnounceResponse(1800, 0, 0, null);
        assertFalse(success.isFailure());

        AnnounceResponse failure = new AnnounceResponse("Error");
        assertTrue(failure.isFailure());
    }

    @Test
    @DisplayName("Get failure reason")
    void testGetFailureReason() {
        AnnounceResponse response = new AnnounceResponse("Tracker offline");
        assertEquals("Tracker offline", response.failureReason());
    }

    @Test
    @DisplayName("Get warning message")
    void testGetWarningMessage() {
        AnnounceResponse response = new AnnounceResponse(1800, 0, 0, null, "Low peers");
        assertEquals("Low peers", response.warningMessage());
    }

    @Test
    @DisplayName("Success response has no failure reason")
    void testSuccessHasNoFailureReason() {
        AnnounceResponse response = new AnnounceResponse(1800, 0, 0, null);
        assertNull(response.failureReason());
    }

    @Test
    @DisplayName("Failed response has no warning")
    void testFailureHasNoWarning() {
        AnnounceResponse response = new AnnounceResponse("Error");
        assertNull(response.warningMessage());
    }
}
