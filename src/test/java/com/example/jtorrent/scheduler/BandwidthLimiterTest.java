package com.example.jtorrent.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BandwidthLimiter Tests")
class BandwidthLimiterTest {

    @Test
    @DisplayName("Create bandwidth limiter with unlimited rate")
    void testCreateUnlimitedLimiter() {
        BandwidthLimiter limiter = new BandwidthLimiter(0, 0, 0);
        assertNotNull(limiter);
    }

    @Test
    @DisplayName("Create bandwidth limiter with limited rate")
    void testCreateLimitedLimiter() {
        BandwidthLimiter limiter = new BandwidthLimiter(1024 * 1024, 1024 * 1024, 0);
        assertNotNull(limiter);
    }

    @Test
    @DisplayName("Request upload")
    void testRequestUpload() {
        BandwidthLimiter limiter = new BandwidthLimiter(1024 * 1024, 0, 0);
        long allowed = limiter.requestUpload("peer1", 1024);
        assertTrue(allowed > 0);
    }

    @Test
    @DisplayName("Request download")
    void testRequestDownload() {
        BandwidthLimiter limiter = new BandwidthLimiter(0, 1024 * 1024, 0);
        long allowed = limiter.requestDownload("peer1", 1024);
        assertTrue(allowed > 0);
    }

    @Test
    @DisplayName("Request upload unlimited")
    void testRequestUploadUnlimited() {
        BandwidthLimiter limiter = new BandwidthLimiter(0, 0, 0);
        long allowed = limiter.requestUpload("peer1", 1024);
        assertEquals(1024, allowed);
    }

    @Test
    @DisplayName("Request download unlimited")
    void testRequestDownloadUnlimited() {
        BandwidthLimiter limiter = new BandwidthLimiter(0, 0, 0);
        long allowed = limiter.requestDownload("peer1", 1024);
        assertEquals(1024, allowed);
    }

    @Test
    @DisplayName("Multiple peers")
    void testMultiplePeers() {
        BandwidthLimiter limiter = new BandwidthLimiter(1024 * 1024, 1024 * 1024, 0);
        long allowed1 = limiter.requestUpload("peer1", 512);
        long allowed2 = limiter.requestUpload("peer2", 512);
        assertTrue(allowed1 > 0);
        assertTrue(allowed2 > 0);
    }

    @Test
    @DisplayName("Per socket limit")
    void testPerSocketLimit() {
        BandwidthLimiter limiter = new BandwidthLimiter(0, 0, 1024);
        long allowed = limiter.requestUpload("peer1", 2048);
        assertTrue(allowed <= 1024);
    }
}
