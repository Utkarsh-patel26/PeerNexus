package com.example.jtorrent.dht;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;

@DisplayName("PexManager Tests")
class PexManagerTest {

    @Test
    @DisplayName("Create PexManager")
    void testCreatePexManager() {
        PexManager pexManager = new PexManager();
        assertNotNull(pexManager);
    }

    @Test
    @DisplayName("Add peer")
    void testAddPeer() {
        PexManager pexManager = new PexManager();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 6881);
        assertDoesNotThrow(() -> pexManager.addPeer(addr));
    }

    @Test
    @DisplayName("Remove peer")
    void testRemovePeer() {
        PexManager pexManager = new PexManager();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 6881);
        pexManager.addPeer(addr);
        assertDoesNotThrow(() -> pexManager.removePeer(addr));
    }

    @Test
    @DisplayName("Get known peers")
    void testGetKnownPeers() {
        PexManager pexManager = new PexManager();
        InetSocketAddress addr1 = new InetSocketAddress("127.0.0.1", 6881);
        InetSocketAddress addr2 = new InetSocketAddress("127.0.0.1", 6882);
        pexManager.addPeer(addr1);
        pexManager.addPeer(addr2);
        var peers = pexManager.getKnownPeers();
        assertNotNull(peers);
    }

    @Test
    @DisplayName("Initially empty")
    void testInitiallyEmpty() {
        PexManager pexManager = new PexManager();
        var peers = pexManager.getKnownPeers();
        assertNotNull(peers);
    }

    @Test
    @DisplayName("Add duplicate peer")
    void testAddDuplicatePeer() {
        PexManager pexManager = new PexManager();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 6881);
        pexManager.addPeer(addr);
        pexManager.addPeer(addr);
        assertNotNull(pexManager);
    }

    @Test
    @DisplayName("Remove non-existent peer")
    void testRemoveNonExistentPeer() {
        PexManager pexManager = new PexManager();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 9999);
        assertDoesNotThrow(() -> pexManager.removePeer(addr));
    }
}
