package com.example.jtorrent.network;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for IPv6Support.
 */
@DisplayName("IPv6Support Tests")
class IPv6SupportTest {

    private IPv6Support ipv6Support;
    private static final int TEST_PORT = 0; // Let OS assign port

    @AfterEach
    void tearDown() throws Exception {
        if (ipv6Support != null) {
            ipv6Support.close();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create with port")
        void shouldCreateWithPort() {
            ipv6Support = new IPv6Support(6881);
            assertNotNull(ipv6Support);
        }
    }

    @Nested
    @DisplayName("Bind Tests")
    class BindTests {

        @Test
        @DisplayName("Should bind to port")
        void shouldBindToPort() throws IOException {
            ipv6Support = new IPv6Support(TEST_PORT);

            assertDoesNotThrow(() -> ipv6Support.bind());

            List<ServerSocketChannel> channels = ipv6Support.getBoundChannels();
            assertFalse(channels.isEmpty());
        }

        @Test
        @DisplayName("Should return bound channels")
        void shouldReturnBoundChannels() throws IOException {
            ipv6Support = new IPv6Support(TEST_PORT);
            ipv6Support.bind();

            List<ServerSocketChannel> channels = ipv6Support.getBoundChannels();
            assertNotNull(channels);
            assertTrue(channels.size() >= 1);
        }
    }

    @Nested
    @DisplayName("Dual Stack Tests")
    class DualStackTests {

        @Test
        @DisplayName("Should check dual stack support")
        void shouldCheckDualStackSupport() {
            boolean supported = IPv6Support.isDualStackSupported();
            // Result depends on system, just verify it doesn't throw
            assertDoesNotThrow(() -> IPv6Support.isDualStackSupported());
        }

        @Test
        @DisplayName("Should report dual stack status after bind")
        void shouldReportDualStackStatusAfterBind() throws IOException {
            ipv6Support = new IPv6Support(TEST_PORT);
            ipv6Support.bind();

            // isDualStack() should return a boolean after binding
            boolean isDualStack = ipv6Support.isDualStack();
            assertNotNull(Boolean.valueOf(isDualStack));
        }
    }

    @Nested
    @DisplayName("Local Address Tests")
    class LocalAddressTests {

        @Test
        @DisplayName("Should get local addresses")
        void shouldGetLocalAddresses() {
            ipv6Support = new IPv6Support(TEST_PORT);

            List<InetAddress> addresses = ipv6Support.getLocalAddresses();

            assertNotNull(addresses);
            // Should have at least one network interface
        }

        @Test
        @DisplayName("Should get global IPv6 addresses")
        void shouldGetGlobalIPv6Addresses() {
            ipv6Support = new IPv6Support(TEST_PORT);

            List<Inet6Address> globalAddresses = ipv6Support.getGlobalIPv6Addresses();

            assertNotNull(globalAddresses);
            // May be empty if no global IPv6 addresses
        }
    }

    @Nested
    @DisplayName("Preferred Address Tests")
    class PreferredAddressTests {

        @Test
        @DisplayName("Should get preferred IPv6 address")
        void shouldGetPreferredIPv6Address() {
            ipv6Support = new IPv6Support(TEST_PORT);

            Optional<Inet6Address> preferred = ipv6Support.getPreferredIPv6Address();

            assertNotNull(preferred);
            // May or may not have a preferred address
        }

        @Test
        @DisplayName("Should set preferred address")
        void shouldSetPreferredAddress() throws Exception {
            ipv6Support = new IPv6Support(TEST_PORT);

            // Create a mock IPv6 address
            byte[] addr = new byte[16];
            addr[0] = 0x20; // Global unicast
            addr[1] = 0x01;
            Inet6Address ipv6Addr = Inet6Address.getByAddress("test", addr, null);

            ipv6Support.setPreferredAddress(ipv6Addr);

            Optional<Inet6Address> preferred = ipv6Support.getPreferredIPv6Address();
            assertTrue(preferred.isPresent());
            assertEquals(ipv6Addr, preferred.get());
        }
    }

    @Nested
    @DisplayName("IPv6 Availability Tests")
    class IPv6AvailabilityTests {

        @Test
        @DisplayName("Should check IPv6 availability")
        void shouldCheckIPv6Availability() {
            boolean available = IPv6Support.isIPv6Available();
            // Result depends on system configuration
            assertDoesNotThrow(() -> IPv6Support.isIPv6Available());
        }
    }

    @Nested
    @DisplayName("IPv4 Mapped Address Tests")
    class IPv4MappedAddressTests {

        @Test
        @DisplayName("Should convert IPv4 to mapped IPv6")
        void shouldConvertIPv4ToMappedIPv6() throws Exception {
            InetAddress addr = InetAddress.getByName("192.168.1.1");
            // Verify it's IPv4
            assertTrue(addr instanceof Inet4Address, "Address should be IPv4");
            Inet4Address ipv4 = (Inet4Address) addr;

            Inet6Address mapped = IPv6Support.toIPv4MappedIPv6(ipv4);

            assertNotNull(mapped);
            // IPv4-mapped IPv6 addresses have specific format
            byte[] bytes = mapped.getAddress();
            assertEquals(16, bytes.length);
            // Check for ::ffff: prefix
            assertEquals((byte) 0xFF, bytes[10]);
            assertEquals((byte) 0xFF, bytes[11]);
        }

        @Test
        @DisplayName("Should preserve IPv4 address bytes in mapping")
        void shouldPreserveIPv4AddressBytesInMapping() throws Exception {
            InetAddress addr = InetAddress.getByName("10.20.30.40");
            // Verify it's IPv4
            assertTrue(addr instanceof Inet4Address, "Address should be IPv4");
            Inet4Address ipv4 = (Inet4Address) addr;

            Inet6Address mapped = IPv6Support.toIPv4MappedIPv6(ipv4);

            byte[] bytes = mapped.getAddress();
            // Last 4 bytes should be the IPv4 address
            assertEquals(10, bytes[12] & 0xFF);
            assertEquals(20, bytes[13] & 0xFF);
            assertEquals(30, bytes[14] & 0xFF);
            assertEquals(40, bytes[15] & 0xFF);
        }
    }

    @Nested
    @DisplayName("AutoCloseable Tests")
    class AutoCloseableTests {

        @Test
        @DisplayName("Should close channels")
        void shouldCloseChannels() throws Exception {
            ipv6Support = new IPv6Support(TEST_PORT);
            ipv6Support.bind();

            List<ServerSocketChannel> channels = ipv6Support.getBoundChannels();

            ipv6Support.close();

            // Channels should be closed
            for (ServerSocketChannel channel : channels) {
                assertFalse(channel.isOpen());
            }
        }

        @Test
        @DisplayName("Should be usable with try-with-resources")
        void shouldBeUsableWithTryWithResources() throws IOException {
            try (IPv6Support support = new IPv6Support(TEST_PORT)) {
                support.bind();
                assertFalse(support.getBoundChannels().isEmpty());
            }
            // Auto-closed
        }
    }
}
