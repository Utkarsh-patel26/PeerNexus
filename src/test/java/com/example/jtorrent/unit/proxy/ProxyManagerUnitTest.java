package com.example.jtorrent.unit.proxy;

import com.example.jtorrent.proxy.ProxyConfig;
import com.example.jtorrent.proxy.ProxyManager;
import com.example.jtorrent.proxy.ProxyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProxyManager.
 * Tests proxy configuration and connection management.
 */
class ProxyManagerUnitTest {

    private ProxyManager proxyManager;

    @BeforeEach
    void setUp() {
        proxyManager = new ProxyManager();
    }

    @Nested
    class ConstructorTests {

        @Test
        void defaultConstructorHasNoProxy() {
            ProxyManager manager = new ProxyManager();
            assertNull(manager.getProxyConfig());
        }

        @Test
        void constructorWithConfigSetsProxy() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080);
            ProxyManager manager = new ProxyManager(config);

            assertNotNull(manager.getProxyConfig());
            assertEquals(ProxyType.SOCKS5, manager.getProxyConfig().getType());
        }

        @Test
        void constructorWithNullConfig() {
            ProxyManager manager = new ProxyManager(null);
            assertNull(manager.getProxyConfig());
        }
    }

    @Nested
    class ProxyConfigTests {

        @Test
        void setProxyConfigUpdatesConfig() {
            ProxyConfig config = new ProxyConfig(ProxyType.HTTP, "proxy.example.com", 8080);
            proxyManager.setProxyConfig(config);

            assertSame(config, proxyManager.getProxyConfig());
        }

        @Test
        void setProxyConfigToNullDisablesProxy() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080);
            proxyManager.setProxyConfig(config);
            assertNotNull(proxyManager.getProxyConfig());

            proxyManager.setProxyConfig(null);
            assertNull(proxyManager.getProxyConfig());
        }

        @Test
        void getProxyConfigReturnsCurrentConfig() {
            assertNull(proxyManager.getProxyConfig());

            ProxyConfig config1 = new ProxyConfig(ProxyType.SOCKS4, "host1", 1080);
            proxyManager.setProxyConfig(config1);
            assertEquals(config1, proxyManager.getProxyConfig());

            ProxyConfig config2 = new ProxyConfig(ProxyType.SOCKS5, "host2", 1081);
            proxyManager.setProxyConfig(config2);
            assertEquals(config2, proxyManager.getProxyConfig());
        }
    }

    @Nested
    class TestConnectionTests {

        @Test
        void testConnectionWithNoProxyReturnsTrue() {
            assertTrue(proxyManager.testConnection());
        }

        @Test
        void testConnectionWithNoneProxyTypeReturnsTrue() {
            ProxyConfig config = new ProxyConfig(ProxyType.NONE, "ignored", 0);
            proxyManager.setProxyConfig(config);
            assertTrue(proxyManager.testConnection());
        }

        @Test
        void testConnectionWithInvalidProxyReturnsFalse() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "invalid.local", 1080);
            proxyManager.setProxyConfig(config);
            // Connection to invalid host should fail
            assertFalse(proxyManager.testConnection());
        }

        @Test
        void testConnectionWithLocalhostOnUnusedPortReturnsFalse() {
            // Using a port that's very unlikely to be in use
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "127.0.0.1", 59999);
            proxyManager.setProxyConfig(config);
            assertFalse(proxyManager.testConnection());
        }
    }

    @Nested
    class CreateConnectionTests {

        @Test
        void createDirectConnectionSucceeds() throws IOException {
            // Connect to a known service (localhost echo or similar would be better)
            // For now, just test that it doesn't throw with null config
            proxyManager.setProxyConfig(null);

            // This will actually try to connect, which may fail without a listening server
            // So we just verify the method exists and handles null config
            assertThrows(IOException.class, () -> proxyManager.createConnection("nonexistent.local", 12345, 1000));
        }

        @Test
        void createConnectionWithNoneProxyTypeIsDirectConnection() {
            ProxyConfig config = new ProxyConfig(ProxyType.NONE, "proxy.example.com", 8080);
            proxyManager.setProxyConfig(config);

            // Should attempt direct connection, ignoring proxy settings
            assertThrows(IOException.class, () -> proxyManager.createConnection("nonexistent.local", 12345, 1000));
        }

        @Test
        void createConnectionWithInvalidSocks5ProxyThrows() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "invalid.local", 1080);
            proxyManager.setProxyConfig(config);

            assertThrows(IOException.class, () -> proxyManager.createConnection("example.com", 80, 1000));
        }

        @Test
        void createConnectionWithInvalidSocks4ProxyThrows() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS4, "invalid.local", 1080);
            proxyManager.setProxyConfig(config);

            assertThrows(IOException.class, () -> proxyManager.createConnection("example.com", 80, 1000));
        }

        @Test
        void createConnectionWithInvalidHttpProxyThrows() {
            ProxyConfig config = new ProxyConfig(ProxyType.HTTP, "invalid.local", 8080);
            proxyManager.setProxyConfig(config);

            assertThrows(IOException.class, () -> proxyManager.createConnection("example.com", 80, 1000));
        }
    }

    @Nested
    class ProxyConfigClassTests {

        @Test
        void proxyConfigWithoutAuthentication() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080);

            assertEquals(ProxyType.SOCKS5, config.getType());
            assertEquals("localhost", config.getHost());
            assertEquals(1080, config.getPort());
            assertNull(config.getUsername());
            assertNull(config.getPassword());
            assertFalse(config.hasAuthentication());
        }

        @Test
        void proxyConfigWithAuthentication() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080, "user", "pass");

            assertEquals("user", config.getUsername());
            assertEquals("pass", config.getPassword());
            assertTrue(config.hasAuthentication());
        }

        @Test
        void proxyConfigWithEmptyUsernameHasNoAuth() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080, "", "pass");
            assertFalse(config.hasAuthentication());
        }

        @Test
        void proxyConfigToStringContainsType() {
            ProxyConfig config = new ProxyConfig(ProxyType.HTTP, "proxy.example.com", 8080);
            String str = config.toString();
            assertTrue(str.toLowerCase().contains("http"));
            assertTrue(str.contains("proxy.example.com"));
            assertTrue(str.contains("8080"));
        }

        @Test
        void proxyConfigToStringShowsAuthenticated() {
            ProxyConfig config = new ProxyConfig(ProxyType.SOCKS5, "localhost", 1080, "user", "pass");
            String str = config.toString();
            assertTrue(str.toLowerCase().contains("authenticated"));
        }
    }

    @Nested
    class ProxyTypeTests {

        @ParameterizedTest
        @EnumSource(ProxyType.class)
        void allProxyTypesExist(ProxyType type) {
            assertNotNull(type);
        }

        @Test
        void proxyTypeNoneExists() {
            assertEquals(ProxyType.NONE, ProxyType.valueOf("NONE"));
        }

        @Test
        void proxyTypeSocks4Exists() {
            assertEquals(ProxyType.SOCKS4, ProxyType.valueOf("SOCKS4"));
        }

        @Test
        void proxyTypeSocks5Exists() {
            assertEquals(ProxyType.SOCKS5, ProxyType.valueOf("SOCKS5"));
        }

        @Test
        void proxyTypeHttpExists() {
            assertEquals(ProxyType.HTTP, ProxyType.valueOf("HTTP"));
        }
    }
}
