package com.example.jtorrent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("Config Tests")
class ConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Create default config")
    void testDefaultConfig() {
        Config config = new Config();
        assertNotNull(config);
        assertEquals(6881, config.getPort());
        assertEquals(50, config.getMaxPeers());
        assertTrue(config.isDhtEnabled());
    }

    @Test
    @DisplayName("Load config from JSON file")
    void testLoadFromFile() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        String json = "{\"port\": 7777, \"maxPeers\": 100}";
        Files.writeString(configFile, json);

        Config config = Config.fromFile(configFile);
        assertNotNull(config);
        assertEquals(7777, config.getPort());
        assertEquals(100, config.getMaxPeers());
    }

    @Test
    @DisplayName("Parse config from JSON string")
    void testFromJson() {
        String json = "{\"port\": 8888, \"uploadSlots\": 8}";
        Config config = Config.fromJson(json);

        assertNotNull(config);
        assertEquals(8888, config.getPort());
        assertEquals(8, config.getUploadSlots());
    }

    @Test
    @DisplayName("Get port")
    void testGetPort() {
        Config config = new Config();
        int port = config.getPort();
        assertTrue(port > 0);
    }

    @Test
    @DisplayName("Set port")
    void testSetPort() {
        Config config = new Config();
        config.setPort(9999);
        assertEquals(9999, config.getPort());
    }

    @Test
    @DisplayName("Get max peers")
    void testGetMaxPeers() {
        Config config = new Config();
        int maxPeers = config.getMaxPeers();
        assertTrue(maxPeers > 0);
    }

    @Test
    @DisplayName("Set max peers")
    void testSetMaxPeers() {
        Config config = new Config();
        config.setMaxPeers(75);
        assertEquals(75, config.getMaxPeers());
    }

    @Test
    @DisplayName("Get upload slots")
    void testGetUploadSlots() {
        Config config = new Config();
        int slots = config.getUploadSlots();
        assertTrue(slots > 0);
    }

    @Test
    @DisplayName("Get download directory")
    void testGetDownloadDirectory() {
        Config config = new Config();
        String dir = config.getDownloadDirectory();
        assertNotNull(dir);
        assertFalse(dir.isEmpty());
    }

    @Test
    @DisplayName("Set download directory")
    void testSetDownloadDirectory() {
        Config config = new Config();
        config.setDownloadDirectory("/custom/path");
        assertEquals("/custom/path", config.getDownloadDirectory());
    }

    @Test
    @DisplayName("DHT enabled by default")
    void testDhtEnabledByDefault() {
        Config config = new Config();
        assertTrue(config.isDhtEnabled());
    }

    @Test
    @DisplayName("Set DHT enabled")
    void testSetDhtEnabled() {
        Config config = new Config();
        config.setDhtEnabled(false);
        assertFalse(config.isDhtEnabled());
    }

    @Test
    @DisplayName("PEX enabled by default")
    void testPexEnabledByDefault() {
        Config config = new Config();
        assertTrue(config.isPexEnabled());
    }

    @Test
    @DisplayName("Get state directory")
    void testGetStateDirectory() {
        Config config = new Config();
        String dir = config.getStateDirectory();
        assertNotNull(dir);
    }

    @Test
    @DisplayName("Get log level")
    void testGetLogLevel() {
        Config config = new Config();
        String level = config.getLogLevel();
        assertNotNull(level);
    }

    @Test
    @DisplayName("Get max upload rate")
    void testGetMaxUploadRate() {
        Config config = new Config();
        long rate = config.getMaxUploadBytesPerSec();
        assertTrue(rate >= 0);
    }

    @Test
    @DisplayName("Get max download rate")
    void testGetMaxDownloadRate() {
        Config config = new Config();
        long rate = config.getMaxDownloadBytesPerSec();
        assertTrue(rate >= 0);
    }

    @Test
    @DisplayName("Get DHT port")
    void testGetDhtPort() {
        Config config = new Config();
        int port = config.getDhtPort();
        assertTrue(port > 0);
    }

    @Test
    @DisplayName("Parse JSON with nested values")
    void testParseNestedJson() {
        String json = "{\"port\": 6881, \"maxPeers\": 50, \"dhtEnabled\": true}";
        Config config = Config.fromJson(json);

        assertEquals(6881, config.getPort());
        assertEquals(50, config.getMaxPeers());
        assertTrue(config.isDhtEnabled());
    }

    @Test
    @DisplayName("Handle missing config file")
    void testMissingConfigFile() {
        Path nonExistent = tempDir.resolve("nonexistent.json");
        assertThrows(IOException.class, () -> Config.fromFile(nonExistent));
    }

    @Test
    @DisplayName("Get metadata connect timeout")
    void testGetMetadataConnectTimeout() {
        Config config = new Config();
        int timeout = config.getMetadataConnectTimeoutMs();
        assertTrue(timeout > 0);
    }

    @Test
    @DisplayName("Get request queue size")
    void testGetRequestQueueSize() {
        Config config = new Config();
        int size = config.getRequestQueueSize();
        assertTrue(size > 0);
    }

    @Test
    @DisplayName("Encryption disabled by default")
    void testEncryptionDisabledByDefault() {
        Config config = new Config();
        assertFalse(config.isEncryptionEnabled());
    }
}
