package com.example.jtorrent.dht;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketException;

@DisplayName("DhtNode Tests")
class DhtNodeTest {

    @Test
    @DisplayName("Create DHT node with port")
    void testCreateDhtNode() throws SocketException {
        // Use port 0 to let the OS assign an available port
        DhtNode node = new DhtNode(0);
        assertNotNull(node);
    }

    @Test
    @DisplayName("Create DHT node with default constructor")
    void testCreateDefaultDhtNode() throws SocketException {
        DhtNode node = new DhtNode();
        assertNotNull(node);
    }

    @Test
    @DisplayName("DhtNode class exists")
    void testDhtNodeExists() {
        assertDoesNotThrow(() -> {
            Class.forName("com.example.jtorrent.dht.DhtNode");
        });
    }

    @Test
    @DisplayName("DhtNode is not null as class")
    void testDhtNodeClass() {
        assertNotNull(DhtNode.class);
    }
}
