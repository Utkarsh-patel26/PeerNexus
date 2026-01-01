package com.example.jtorrent.dht;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MagnetLink Tests")
class MagnetLinkTest {

    @Test
    @DisplayName("Parse magnet link")
    void testParseMagnetLink() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678&dn=test";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
    }

    @Test
    @DisplayName("Get info hash from hex")
    void testGetInfoHashFromHex() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
        assertNotNull(magnet.getInfoHash());
        assertEquals(20, magnet.getInfoHash().length);
    }

    @Test
    @DisplayName("Get display name")
    void testGetDisplayName() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678&dn=MyFile";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
    }

    @Test
    @DisplayName("Parse with trackers")
    void testParseWithTrackers() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678&tr=http://tracker.example.com/announce";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
    }

    @Test
    @DisplayName("Parse minimal magnet link")
    void testParseMinimalMagnetLink() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
        assertNotNull(magnet.getInfoHash());
    }

    @Test
    @DisplayName("Parse with multiple trackers")
    void testParseWithMultipleTrackers() {
        String magnetUri = "magnet:?xt=urn:btih:1234567890abcdef1234567890abcdef12345678&tr=http://tracker1.com&tr=http://tracker2.com";
        MagnetLink magnet = MagnetLink.parse(magnetUri);
        assertNotNull(magnet);
    }

    @Test
    @DisplayName("Invalid magnet link")
    void testInvalidMagnetLink() {
        assertThrows(Exception.class, () -> {
            MagnetLink.parse("invalid");
        });
    }

    @Test
    @DisplayName("Missing info hash")
    void testMissingInfoHash() {
        assertThrows(Exception.class, () -> {
            MagnetLink.parse("magnet:?dn=test");
        });
    }

    @Test
    @DisplayName("Base32 encoded info hash")
    void testBase32EncodedInfoHash() {
        // Base32 encoded info hash (exactly 32 characters = 20 bytes)
        // MFRGG2LMNJWGS3THEBSWY3DPEBXXKZAA = 32 chars
        String magnetUri = "magnet:?xt=urn:btih:MFRGG2LMNJWGS3THEBSWY3DPEBXXKZAA";
        assertDoesNotThrow(() -> {
            MagnetLink magnet = MagnetLink.parse(magnetUri);
            assertNotNull(magnet);
        });
    }
}
