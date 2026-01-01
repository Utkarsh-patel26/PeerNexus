package com.example.jtorrent.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;

@DisplayName("PeerStats Tests")
class PeerStatsTest {

    private PeerStats stats;

    @BeforeEach
    void setUp() {
        stats = new PeerStats("test-peer-id");
    }

    @Test
    @DisplayName("Create peer stats with ID")
    void testCreateWithId() {
        PeerStats stats = new PeerStats("peer123");
        assertEquals("peer123", stats.getPeerId());
    }

    @Test
    @DisplayName("Create peer stats with address")
    void testCreateWithAddress() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
        PeerStats stats = new PeerStats("peer123", address);

        assertEquals("peer123", stats.getPeerId());
        assertNotNull(stats.getAddress());
    }

    @Test
    @DisplayName("Record uploaded bytes")
    void testRecordUpload() {
        stats.recordUpload(1024);
        assertEquals(1024, stats.getTotalUploaded());
    }

    @Test
    @DisplayName("Record downloaded bytes")
    void testRecordDownload() {
        stats.recordDownload(2048);
        assertEquals(2048, stats.getTotalDownloaded());
    }

    @Test
    @DisplayName("Record multiple uploads")
    void testRecordMultipleUploads() {
        stats.recordUpload(1024);
        stats.recordUpload(512);
        assertEquals(1536, stats.getTotalUploaded());
    }

    @Test
    @DisplayName("Record multiple downloads")
    void testRecordMultipleDownloads() {
        stats.recordDownload(2048);
        stats.recordDownload(1024);
        assertEquals(3072, stats.getTotalDownloaded());
    }

    @Test
    @DisplayName("Get uploaded bytes initially zero")
    void testInitialUploadedBytes() {
        assertEquals(0, stats.getTotalUploaded());
    }

    @Test
    @DisplayName("Get downloaded bytes initially zero")
    void testInitialDownloadedBytes() {
        assertEquals(0, stats.getTotalDownloaded());
    }

    @Test
    @DisplayName("Record successful request")
    void testRecordSuccessfulRequest() {
        stats.recordSuccess(100);
        assertTrue(stats.getSuccessRate() > 0);
    }

    @Test
    @DisplayName("Record failed request")
    void testRecordFailedRequest() {
        stats.recordFailure();
        assertTrue(stats.getSuccessRate() < 1.0);
    }

    @Test
    @DisplayName("Get upload rate")
    void testGetUploadRate() {
        double rate = stats.getUploadRateBytesPerSec();
        assertTrue(rate >= 0);
    }

    @Test
    @DisplayName("Get download rate")
    void testGetDownloadRate() {
        double rate = stats.getDownloadRateBytesPerSec();
        assertTrue(rate >= 0);
    }

    @Test
    @DisplayName("Check if choked initially")
    void testInitiallyChoked() {
        assertTrue(stats.isChoked());
    }

    @Test
    @DisplayName("Set choked status")
    void testSetChoked() {
        stats.setChoked(false);
        assertFalse(stats.isChoked());
    }

    @Test
    @DisplayName("Check if interested initially")
    void testInitiallyNotInterested() {
        assertFalse(stats.isInterested());
    }

    @Test
    @DisplayName("Set interested status")
    void testSetInterested() {
        stats.setInterested(true);
        assertTrue(stats.isInterested());
    }

    @Test
    @DisplayName("Check peer choked status")
    void testPeerChokedInitially() {
        assertTrue(stats.isPeerChoked());
    }

    @Test
    @DisplayName("Set peer choked status")
    void testSetPeerChoked() {
        stats.setPeerChoked(false);
        assertFalse(stats.isPeerChoked());
    }

    @Test
    @DisplayName("Check peer interested status")
    void testPeerInterestedInitially() {
        assertFalse(stats.isPeerInterested());
    }

    @Test
    @DisplayName("Set peer interested status")
    void testSetPeerInterested() {
        stats.setPeerInterested(true);
        assertTrue(stats.isPeerInterested());
    }

    @Test
    @DisplayName("Check if seeder")
    void testIsSeederInitially() {
        assertFalse(stats.isSeeder());
    }

    @Test
    @DisplayName("Set seeder status")
    void testSetSeeder() {
        stats.setSeeder(true);
        assertTrue(stats.isSeeder());
    }

    @Test
    @DisplayName("Get connection duration")
    void testGetConnectionDuration() {
        long duration = stats.getConnectionAge();
        assertTrue(duration >= 0);
    }

    @Test
    @DisplayName("Get average latency")
    void testGetAverageLatency() {
        stats.recordSuccess(100);
        stats.recordSuccess(200);
        double avg = stats.getAverageLatency();
        assertTrue(avg >= 0);
    }
}
