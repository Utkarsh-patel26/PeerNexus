package com.example.jtorrent.tracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnnounceRequest Tests")
class AnnounceRequestTest {

    private static final byte[] INFO_HASH = new byte[20];
    private static final byte[] PEER_ID = new byte[20];

    @Test
    @DisplayName("Create announce request")
    void testCreateAnnounceRequest() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 6881, 0, 0, 1024, AnnounceRequest.Event.STARTED);

        assertNotNull(request);
        assertArrayEquals(INFO_HASH, request.infoHash());
        assertArrayEquals(PEER_ID, request.peerId());
        assertEquals(6881, request.port());
        assertEquals(0, request.uploaded());
        assertEquals(0, request.downloaded());
        assertEquals(1024, request.left());
        assertEquals(AnnounceRequest.Event.STARTED, request.event());
    }

    @Test
    @DisplayName("Clone info hash and peer ID")
    void testCloneArrays() {
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];
        infoHash[0] = 1;
        peerId[0] = 2;

        AnnounceRequest request = new AnnounceRequest(
                infoHash, peerId, 6881, 0, 0, 0, AnnounceRequest.Event.NONE);

        // Modify original arrays
        infoHash[0] = 99;
        peerId[0] = 99;

        // Request should have original values
        assertEquals(1, request.infoHash()[0]);
        assertEquals(2, request.peerId()[0]);
    }

    @Test
    @DisplayName("Throw exception on invalid info hash length")
    void testInvalidInfoHashLength() {
        byte[] shortHash = new byte[10];
        assertThrows(IllegalArgumentException.class,
                () -> new AnnounceRequest(shortHash, PEER_ID, 6881, 0, 0, 0, AnnounceRequest.Event.NONE));
    }

    @Test
    @DisplayName("Throw exception on null info hash")
    void testNullInfoHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnnounceRequest(null, PEER_ID, 6881, 0, 0, 0, AnnounceRequest.Event.NONE));
    }

    @Test
    @DisplayName("Throw exception on invalid peer ID length")
    void testInvalidPeerIdLength() {
        byte[] shortPeerId = new byte[10];
        assertThrows(IllegalArgumentException.class,
                () -> new AnnounceRequest(INFO_HASH, shortPeerId, 6881, 0, 0, 0, AnnounceRequest.Event.NONE));
    }

    @Test
    @DisplayName("Throw exception on null peer ID")
    void testNullPeerId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnnounceRequest(INFO_HASH, null, 6881, 0, 0, 0, AnnounceRequest.Event.NONE));
    }

    @Test
    @DisplayName("Default event to NONE when null")
    void testDefaultEvent() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 6881, 0, 0, 0, null);
        assertEquals(AnnounceRequest.Event.NONE, request.event());
    }

    @Test
    @DisplayName("Event STARTED has correct values")
    void testEventStarted() {
        assertEquals(2, AnnounceRequest.Event.STARTED.udpValue());
        assertEquals("started", AnnounceRequest.Event.STARTED.httpValue());
    }

    @Test
    @DisplayName("Event STOPPED has correct values")
    void testEventStopped() {
        assertEquals(3, AnnounceRequest.Event.STOPPED.udpValue());
        assertEquals("stopped", AnnounceRequest.Event.STOPPED.httpValue());
    }

    @Test
    @DisplayName("Event COMPLETED has correct values")
    void testEventCompleted() {
        assertEquals(1, AnnounceRequest.Event.COMPLETED.udpValue());
        assertEquals("completed", AnnounceRequest.Event.COMPLETED.httpValue());
    }

    @Test
    @DisplayName("Event NONE has correct values")
    void testEventNone() {
        assertEquals(0, AnnounceRequest.Event.NONE.udpValue());
        assertEquals("", AnnounceRequest.Event.NONE.httpValue());
    }

    @Test
    @DisplayName("Track uploaded bytes")
    void testUploadedBytes() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 6881, 1024, 0, 0, AnnounceRequest.Event.NONE);
        assertEquals(1024, request.uploaded());
    }

    @Test
    @DisplayName("Track downloaded bytes")
    void testDownloadedBytes() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 6881, 0, 2048, 0, AnnounceRequest.Event.NONE);
        assertEquals(2048, request.downloaded());
    }

    @Test
    @DisplayName("Track bytes left")
    void testBytesLeft() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 6881, 0, 0, 4096, AnnounceRequest.Event.NONE);
        assertEquals(4096, request.left());
    }

    @Test
    @DisplayName("Get port")
    void testGetPort() {
        AnnounceRequest request = new AnnounceRequest(
                INFO_HASH, PEER_ID, 7777, 0, 0, 0, AnnounceRequest.Event.NONE);
        assertEquals(7777, request.port());
    }
}
