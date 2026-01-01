package com.example.jtorrent.tracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;

@DisplayName("PeerEndpoint Tests")
class PeerEndpointTest {

    @Test
    @DisplayName("Create peer endpoint with address only")
    void testCreateWithAddress() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerEndpoint endpoint = new PeerEndpoint(address);

        assertNotNull(endpoint);
        assertEquals(address, endpoint.address());
        assertNull(endpoint.peerId());
        assertFalse(endpoint.hasPeerId());
    }

    @Test
    @DisplayName("Create peer endpoint with address and peer ID")
    void testCreateWithAddressAndPeerId() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];
        for (int i = 0; i < 20; i++) {
            peerId[i] = (byte) i;
        }

        PeerEndpoint endpoint = new PeerEndpoint(address, peerId);

        assertNotNull(endpoint);
        assertEquals(address, endpoint.address());
        assertArrayEquals(peerId, endpoint.peerId());
        assertTrue(endpoint.hasPeerId());
    }

    @Test
    @DisplayName("Clone peer ID on creation")
    void testClonePeerId() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];
        peerId[0] = 42;

        PeerEndpoint endpoint = new PeerEndpoint(address, peerId);

        // Modify original
        peerId[0] = 99;

        // Endpoint should have original value
        assertEquals(42, endpoint.peerId()[0]);
    }

    @Test
    @DisplayName("Handle null peer ID")
    void testNullPeerId() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerEndpoint endpoint = new PeerEndpoint(address, null);

        assertNull(endpoint.peerId());
        assertFalse(endpoint.hasPeerId());
    }

    @Test
    @DisplayName("Get peer ID as hex string")
    void testPeerIdHex() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];
        peerId[0] = 0x0A;
        peerId[1] = 0x1B;

        PeerEndpoint endpoint = new PeerEndpoint(address, peerId);
        String hex = endpoint.peerIdHex();

        assertNotNull(hex);
        assertTrue(hex.startsWith("0a1b"));
        assertEquals(40, hex.length()); // 20 bytes = 40 hex chars
    }

    @Test
    @DisplayName("Peer ID hex returns null when no peer ID")
    void testPeerIdHexNull() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerEndpoint endpoint = new PeerEndpoint(address);

        assertNull(endpoint.peerIdHex());
    }

    @Test
    @DisplayName("ToString without peer ID")
    void testToStringWithoutPeerId() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerEndpoint endpoint = new PeerEndpoint(address);

        String str = endpoint.toString();
        assertTrue(str.contains("192.168.1.1"));
        assertTrue(str.contains("6881"));
    }

    @Test
    @DisplayName("ToString with peer ID")
    void testToStringWithPeerId() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];
        PeerEndpoint endpoint = new PeerEndpoint(address, peerId);

        String str = endpoint.toString();
        assertTrue(str.contains("192.168.1.1"));
        assertTrue(str.contains("6881"));
        assertTrue(str.contains("[")); // Should include peer ID in brackets
    }

    @Test
    @DisplayName("Equals with same address and peer ID")
    void testEquals() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];

        PeerEndpoint endpoint1 = new PeerEndpoint(address, peerId);
        PeerEndpoint endpoint2 = new PeerEndpoint(address, peerId);

        assertEquals(endpoint1, endpoint2);
    }

    @Test
    @DisplayName("Equals with same object")
    void testEqualsSameObject() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerEndpoint endpoint = new PeerEndpoint(address);

        assertEquals(endpoint, endpoint);
    }

    @Test
    @DisplayName("Not equals with different address")
    void testNotEqualsDifferentAddress() {
        InetSocketAddress address1 = new InetSocketAddress("192.168.1.1", 6881);
        InetSocketAddress address2 = new InetSocketAddress("192.168.1.2", 6881);

        PeerEndpoint endpoint1 = new PeerEndpoint(address1);
        PeerEndpoint endpoint2 = new PeerEndpoint(address2);

        assertNotEquals(endpoint1, endpoint2);
    }

    @Test
    @DisplayName("HashCode consistency")
    void testHashCode() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        byte[] peerId = new byte[20];

        PeerEndpoint endpoint1 = new PeerEndpoint(address, peerId);
        PeerEndpoint endpoint2 = new PeerEndpoint(address, peerId);

        assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
    }

    @Test
    @DisplayName("Get address")
    void testGetAddress() {
        InetSocketAddress address = new InetSocketAddress("10.0.0.1", 7777);
        PeerEndpoint endpoint = new PeerEndpoint(address);

        assertEquals("10.0.0.1", endpoint.address().getAddress().getHostAddress());
        assertEquals(7777, endpoint.address().getPort());
    }
}
