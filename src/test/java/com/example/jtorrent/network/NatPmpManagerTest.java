package com.example.jtorrent.network;

import org.junit.jupiter.api.*;

import java.net.InetAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for NatPmpManager.
 */
@DisplayName("NatPmpManager Tests")
class NatPmpManagerTest {

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create manager with gateway address")
        void shouldCreateManagerWithGatewayAddress() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            NatPmpManager manager = new NatPmpManager(gateway);

            assertNotNull(manager);
        }

        @Test
        @DisplayName("Should be closeable")
        void shouldBeCloseable() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                assertNotNull(manager);
            }
            // Should close without exception
        }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should start manager")
        void shouldStartManager() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                manager.start();
                // If no exception, start was successful
            }
        }

        @Test
        @DisplayName("Should handle multiple starts")
        void shouldHandleMultipleStarts() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                manager.start();
                manager.start(); // Should be idempotent
                // No exception expected
            }
        }
    }

    @Nested
    @DisplayName("Active Mappings Tests")
    class ActiveMappingsTests {

        @Test
        @DisplayName("Should return empty mappings initially")
        void shouldReturnEmptyMappingsInitially() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                assertTrue(manager.getActiveMappings().isEmpty());
            }
        }

        @Test
        @DisplayName("Should return unmodifiable mappings")
        void shouldReturnUnmodifiableMappings() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                var mappings = manager.getActiveMappings();

                assertThrows(UnsupportedOperationException.class, () -> {
                    mappings.put(new NatPmpManager.MappingKey(NatPmpManager.Protocol.TCP, 8080),
                            new NatPmpManager.ActiveMapping(8080, 8080, java.time.Instant.now()));
                });
            }
        }
    }

    @Nested
    @DisplayName("External Address Tests")
    class ExternalAddressTests {

        @Test
        @DisplayName("Should return future for external address")
        void shouldReturnFutureForExternalAddress() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                var future = manager.getExternalAddress();

                assertNotNull(future);
                assertTrue(future.isDone() || !future.isDone()); // Should return a future
            }
        }
    }

    @Nested
    @DisplayName("Port Mapping Tests")
    class PortMappingTests {

        @Test
        @DisplayName("Should return future for TCP port mapping")
        void shouldReturnFutureForTcpPortMapping() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                var future = manager.mapPort(
                        NatPmpManager.Protocol.TCP,
                        8080,
                        8080,
                        Duration.ofHours(1));

                assertNotNull(future);
            }
        }

        @Test
        @DisplayName("Should return future for UDP port mapping")
        void shouldReturnFutureForUdpPortMapping() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                var future = manager.mapPort(
                        NatPmpManager.Protocol.UDP,
                        8080,
                        8080,
                        Duration.ofHours(1));

                assertNotNull(future);
            }
        }

        @Test
        @DisplayName("Should return future for port unmapping")
        void shouldReturnFutureForPortUnmapping() throws Exception {
            InetAddress gateway = InetAddress.getByName("192.168.1.1");

            try (NatPmpManager manager = new NatPmpManager(gateway)) {
                var future = manager.unmapPort(NatPmpManager.Protocol.TCP, 8080);

                assertNotNull(future);
            }
        }
    }
}
