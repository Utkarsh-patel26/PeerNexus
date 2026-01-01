package com.example.jtorrent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

@DisplayName("FormatUtils Tests")
class FormatUtilsTest {

    @Test
    @DisplayName("Convert bytes to hex string")
    void testBytesToHex() {
        byte[] bytes = { 0x0A, 0x1B, 0x2C, 0x3D };
        String hex = FormatUtils.bytesToHex(bytes);
        assertEquals("0a1b2c3d", hex);
    }

    @Test
    @DisplayName("Convert empty bytes to hex")
    void testEmptyBytesToHex() {
        byte[] bytes = {};
        String hex = FormatUtils.bytesToHex(bytes);
        assertEquals("", hex);
    }

    @Test
    @DisplayName("Convert single byte to hex")
    void testSingleByteToHex() {
        byte[] bytes = { (byte) 0xFF };
        String hex = FormatUtils.bytesToHex(bytes);
        assertEquals("ff", hex);
    }

    @Test
    @DisplayName("Generate peer ID")
    void testGeneratePeerId() {
        byte[] peerId = FormatUtils.generatePeerId();
        assertNotNull(peerId);
        assertEquals(20, peerId.length);

        // Check prefix
        String prefix = new String(peerId, 0, 8, StandardCharsets.US_ASCII);
        assertEquals("-JT0001-", prefix);

        // Check remaining bytes are digits
        for (int i = 8; i < 20; i++) {
            assertTrue(peerId[i] >= '0' && peerId[i] <= '9');
        }
    }

    @Test
    @DisplayName("Generate unique peer IDs")
    void testGenerateUniquePeerIds() {
        byte[] peerId1 = FormatUtils.generatePeerId();
        byte[] peerId2 = FormatUtils.generatePeerId();

        assertFalse(java.util.Arrays.equals(peerId1, peerId2));
    }

    @Test
    @DisplayName("Format bytes size")
    void testFormatBytesSize() {
        assertEquals("512 B", FormatUtils.formatSize(512));
    }

    @Test
    @DisplayName("Format kilobytes size")
    void testFormatKilobytesSize() {
        String formatted = FormatUtils.formatSize(2048);
        assertTrue(formatted.contains("KB"));
    }

    @Test
    @DisplayName("Format megabytes size")
    void testFormatMegabytesSize() {
        String formatted = FormatUtils.formatSize(5 * 1024 * 1024);
        assertTrue(formatted.contains("MB"));
    }

    @Test
    @DisplayName("Format gigabytes size")
    void testFormatGigabytesSize() {
        String formatted = FormatUtils.formatSize(3L * 1024 * 1024 * 1024);
        assertTrue(formatted.contains("GB"));
    }

    @Test
    @DisplayName("Format zero size")
    void testFormatZeroSize() {
        assertEquals("0 B", FormatUtils.formatSize(0));
    }

    @Test
    @DisplayName("Format exact kilobyte boundary")
    void testFormatExactKilobyte() {
        String formatted = FormatUtils.formatSize(1024);
        assertEquals("1.00 KB", formatted);
    }

    @Test
    @DisplayName("Format exact megabyte boundary")
    void testFormatExactMegabyte() {
        String formatted = FormatUtils.formatSize(1024 * 1024);
        assertEquals("1.00 MB", formatted);
    }

    @Test
    @DisplayName("Format exact gigabyte boundary")
    void testFormatExactGigabyte() {
        String formatted = FormatUtils.formatSize(1024L * 1024 * 1024);
        assertEquals("1.00 GB", formatted);
    }

    @Test
    @DisplayName("Utility class cannot be instantiated")
    void testCannotInstantiate() {
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            var constructor = FormatUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
    }

    @Test
    @DisplayName("Format large size")
    void testFormatLargeSize() {
        String formatted = FormatUtils.formatSize(123456789L);
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
    }

    @Test
    @DisplayName("Hex conversion handles all byte values")
    void testHexAllByteValues() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < 256; i++) {
            bytes[i] = (byte) i;
        }
        String hex = FormatUtils.bytesToHex(bytes);
        assertEquals(512, hex.length()); // 2 chars per byte
    }
}
