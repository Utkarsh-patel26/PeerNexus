package com.example.jtorrent.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import com.example.jtorrent.config.Config;
import com.example.jtorrent.logging.Logger;

@DisplayName("MetadataFetcher Tests")
class MetadataFetcherTest {

    @Test
    @DisplayName("Create metadata fetcher")
    void testCreateMetadataFetcher() {
        Logger logger = Logger.getLogger("test");
        Config config = new Config();
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];

        MetadataFetcher fetcher = new MetadataFetcher(infoHash, peerId, config, logger);
        assertNotNull(fetcher);
    }

    @Test
    @DisplayName("Get fetcher with valid parameters")
    void testGetFetcherWithValidParameters() {
        Logger logger = Logger.getLogger("test");
        Config config = new Config();
        byte[] infoHash = new byte[20];
        byte[] peerId = new byte[20];

        assertDoesNotThrow(() -> {
            @SuppressWarnings("unused")
            MetadataFetcher fetcher = new MetadataFetcher(infoHash, peerId, config, logger);
        });
    }

    @Test
    @DisplayName("Info hash not null")
    void testInfoHashNotNull() {
        byte[] infoHash = new byte[20];
        assertNotNull(infoHash);
        assertEquals(20, infoHash.length);
    }

    @Test
    @DisplayName("Peer ID not null")
    void testPeerIdNotNull() {
        byte[] peerId = new byte[20];
        assertNotNull(peerId);
        assertEquals(20, peerId.length);
    }

    @Test
    @DisplayName("Config not null")
    void testConfigNotNull() {
        Config config = new Config();
        assertNotNull(config);
    }

    @Test
    @DisplayName("Logger not null")
    void testLoggerNotNull() {
        Logger logger = Logger.getLogger("test");
        assertNotNull(logger);
    }
}
