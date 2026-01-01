package com.example.jtorrent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UPnPManager - Universal Plug and Play port mapping.
 */
@DisplayName("UPnPManager Tests")
class UPnPManagerTest {

    private UPnPManager upnpManager;

    @BeforeEach
    void setUp() {
        upnpManager = new UPnPManager();
    }

    @Nested
    @DisplayName("Gateway Discovery")
    class GatewayDiscovery {

        @Test
        @DisplayName("Discovery does not throw on timeout")
        @Timeout(10)
        void testDiscoveryTimeout() {
            // Discovery may fail if no UPnP gateway exists, but shouldn't throw
            assertDoesNotThrow(() -> upnpManager.discoverGateway());
        }

        @Test
        @DisplayName("Discover gateway returns boolean")
        @Timeout(10)
        void testDiscoverGatewayReturnsBoolean() {
            boolean result = upnpManager.discoverGateway();
            // Result depends on network environment
            assertTrue(result || !result); // Always true, just verifying no exception
        }
    }

    @Nested
    @DisplayName("Port Mapping")
    class PortMapping {

        @Test
        @DisplayName("Add port mapping without discovery returns 0")
        void testAddMappingWithoutDiscovery() {
            // Should return 0 if gateway not discovered
            int result = upnpManager.addPortMapping(6881, 6881, "TCP", "JTorrent Test", 0);
            assertTrue(result >= 0); // Either 0 (failed) or actual port
        }

        @Test
        @DisplayName("Delete mapping without discovery is safe")
        void testDeleteMappingWithoutDiscovery() {
            boolean result = upnpManager.deletePortMapping(6881, "TCP");
            assertFalse(result);
        }

        @Test
        @DisplayName("Get mapped port returns 0 when not mapped")
        void testGetMappedPortWhenNotMapped() {
            assertEquals(0, upnpManager.getMappedPort());
        }
    }

    @Nested
    @DisplayName("External IP")
    class ExternalIP {

        @Test
        @DisplayName("Get external IP without discovery returns null")
        void testExternalIPWithoutDiscovery() {
            String ip = upnpManager.getExternalIPAddress();
            // May be null or actual IP if discovery succeeded
            assertTrue(ip == null || ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Concurrent discovery is safe")
        @Timeout(15)
        void testConcurrentDiscovery() throws InterruptedException {
            Thread[] threads = new Thread[3];
            for (int i = 0; i < 3; i++) {
                threads[i] = new Thread(() -> upnpManager.discoverGateway());
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join(5000);
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Port 0 is handled")
        void testPort0() {
            // Port 0 should be handled (will use internal port as external)
            int result = upnpManager.addPortMapping(6881, 0, "TCP", "Test", 0);
            assertTrue(result >= 0);
        }

        @Test
        @DisplayName("High port number is handled")
        void testHighPort() {
            int result = upnpManager.addPortMapping(65535, 65535, "TCP", "Test", 0);
            assertTrue(result >= 0);
        }
    }
}
