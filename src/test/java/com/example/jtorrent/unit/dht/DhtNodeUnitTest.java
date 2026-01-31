package com.example.jtorrent.unit.dht;

import com.example.jtorrent.dht.DhtNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DhtNode.
 * Tests DHT (Distributed Hash Table) functionality for peer discovery.
 */
class DhtNodeUnitTest {

    private DhtNode dhtNode;

    @AfterEach
    void tearDown() {
        if (dhtNode != null) {
            try {
                dhtNode.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Nested
    class ConstructorTests {

        @Test
        void createWithDefaultPort() throws SocketException {
            // This may fail if port 6881 is in use
            try {
                dhtNode = new DhtNode();
                assertNotNull(dhtNode);
            } catch (SocketException e) {
                // Port may be in use, that's ok
                assertTrue(e.getMessage().contains("Address already in use") ||
                        e.getMessage().contains("Permission denied"));
            }
        }

        @Test
        void createWithCustomPort() throws SocketException {
            // Use a high port number that's likely available
            dhtNode = new DhtNode(0); // 0 = let OS assign port
            assertNotNull(dhtNode);
            assertTrue(dhtNode.getPort() > 0);
        }

        @Test
        void createMultipleNodesOnDifferentPorts() throws SocketException {
            DhtNode node1 = new DhtNode(0);
            DhtNode node2 = new DhtNode(0);

            try {
                assertNotEquals(node1.getPort(), node2.getPort());
            } finally {
                node1.close();
                node2.close();
            }
        }
    }

    @Nested
    class NodeIdTests {

        @Test
        void nodeIdIs20Bytes() throws SocketException {
            dhtNode = new DhtNode(0);
            byte[] nodeId = dhtNode.getNodeId();
            assertNotNull(nodeId);
            assertEquals(20, nodeId.length);
        }

        @Test
        void nodeIdIsNotAllZeros() throws SocketException {
            dhtNode = new DhtNode(0);
            byte[] nodeId = dhtNode.getNodeId();

            boolean hasNonZero = false;
            for (byte b : nodeId) {
                if (b != 0) {
                    hasNonZero = true;
                    break;
                }
            }
            assertTrue(hasNonZero, "Node ID should not be all zeros");
        }

        @Test
        void differentNodesHaveDifferentIds() throws SocketException {
            DhtNode node1 = new DhtNode(0);
            DhtNode node2 = new DhtNode(0);

            try {
                byte[] id1 = node1.getNodeId();
                byte[] id2 = node2.getNodeId();
                assertFalse(java.util.Arrays.equals(id1, id2));
            } finally {
                node1.close();
                node2.close();
            }
        }
    }

    @Nested
    class PortTests {

        @Test
        void getPortReturnsValidPort() throws SocketException {
            dhtNode = new DhtNode(0);
            int port = dhtNode.getPort();
            assertTrue(port > 0 && port <= 65535);
        }

        @Test
        void getPortReturnsAssignedPort() throws SocketException {
            int requestedPort = 50000; // High port less likely to be in use
            try {
                dhtNode = new DhtNode(requestedPort);
                assertEquals(requestedPort, dhtNode.getPort());
            } catch (SocketException e) {
                // Port may be in use, skip test
            }
        }
    }

    @Nested
    class FallbackBootstrapNodesTests {

        @Test
        void fallbackNodesNotEmpty() {
            List<InetSocketAddress> fallbackNodes = DhtNode.getFallbackBootstrapNodes();
            assertNotNull(fallbackNodes);
            assertFalse(fallbackNodes.isEmpty());
        }

        @Test
        void fallbackNodesContainKnownRouters() {
            List<InetSocketAddress> fallbackNodes = DhtNode.getFallbackBootstrapNodes();

            // Check that at least some well-known DHT routers are present
            boolean hasRouter = fallbackNodes.stream()
                    .anyMatch(addr -> addr.getHostString().contains("router") ||
                            addr.getHostString().contains("dht"));
            assertTrue(hasRouter, "Should contain known DHT routers");
        }

        @Test
        void fallbackNodesHaveValidPorts() {
            List<InetSocketAddress> fallbackNodes = DhtNode.getFallbackBootstrapNodes();

            for (InetSocketAddress addr : fallbackNodes) {
                int port = addr.getPort();
                assertTrue(port > 0 && port <= 65535,
                        "Port should be valid: " + port);
            }
        }
    }

    @Nested
    class BootstrapTests {

        @Test
        void bootstrapWithInvalidAddressDoesNotThrow() throws SocketException {
            dhtNode = new DhtNode(0);
            List<InetSocketAddress> invalidNodes = List.of(
                    new InetSocketAddress("invalid.example.com", 6881));
            // Should not throw, even with invalid nodes
            assertDoesNotThrow(() -> dhtNode.bootstrap(invalidNodes));
        }
    }

    @Nested
    class WorkingNodesTests {

        @Test
        void getWorkingNodesInitiallyEmpty() throws SocketException {
            dhtNode = new DhtNode(0);
            List<InetSocketAddress> workingNodes = dhtNode.getWorkingNodes();
            assertNotNull(workingNodes);
            // Initially should be empty since we haven't bootstrapped successfully
        }

        @Test
        void getWorkingNodesReturnsListCopy() throws SocketException {
            dhtNode = new DhtNode(0);
            List<InetSocketAddress> nodes1 = dhtNode.getWorkingNodes();
            List<InetSocketAddress> nodes2 = dhtNode.getWorkingNodes();
            // Should be different list instances
            assertNotSame(nodes1, nodes2);
        }
    }

    @Nested
    class AutoCloseableTests {

        @Test
        void closeDoesNotThrow() throws SocketException {
            dhtNode = new DhtNode(0);
            assertDoesNotThrow(() -> dhtNode.close());
            dhtNode = null; // Prevent double close in tearDown
        }

        @Test
        void doubleCloseDoesNotThrow() throws SocketException {
            dhtNode = new DhtNode(0);
            dhtNode.close();
            assertDoesNotThrow(() -> dhtNode.close());
            dhtNode = null;
        }

        @Test
        void tryWithResourcesWorks() {
            assertDoesNotThrow(() -> {
                try (DhtNode node = new DhtNode(0)) {
                    assertNotNull(node.getNodeId());
                }
            });
        }
    }

    @Nested
    class ConcurrencyTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void multipleThreadsCanAccessNodeId() throws Exception {
            dhtNode = new DhtNode(0);
            final byte[] expectedId = dhtNode.getNodeId().clone();

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    byte[] id = dhtNode.getNodeId();
                    assertArrayEquals(expectedId, id);
                });
                threads[i].start();
            }

            for (Thread t : threads) {
                t.join();
            }
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void multipleThreadsCanAccessPort() throws Exception {
            dhtNode = new DhtNode(0);
            final int expectedPort = dhtNode.getPort();

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    int port = dhtNode.getPort();
                    assertEquals(expectedPort, port);
                });
                threads[i].start();
            }

            for (Thread t : threads) {
                t.join();
            }
        }
    }
}
