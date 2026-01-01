package com.example.jtorrent.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;

@DisplayName("DownloadState Tests")
class DownloadStateTest {

    @Test
    @DisplayName("Create download state")
    void testCreateDownloadState() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 100);
        assertNotNull(state);
    }

    @Test
    @DisplayName("Mark piece complete")
    void testMarkPieceComplete() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 100);

        state.markPieceComplete(5);
        assertTrue(state.isPieceComplete(5));
    }

    @Test
    @DisplayName("Check piece incomplete")
    void testCheckPieceIncomplete() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 100);

        assertFalse(state.isPieceComplete(10));
    }

    @Test
    @DisplayName("Get completed piece count")
    void testGetCompletedPieceCount() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 100);

        state.markPieceComplete(0);
        state.markPieceComplete(1);
        state.markPieceComplete(2);

        assertEquals(3, state.getCompletedPieceCount());
    }

    @Test
    @DisplayName("Get download progress")
    void testGetProgress() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 10);

        state.markPieceComplete(0);
        state.markPieceComplete(1);
        state.markPieceComplete(2);

        assertEquals(0.3, state.getProgress(), 0.01);
    }

    @Test
    @DisplayName("Initial progress is zero")
    void testInitialProgress() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 100);

        assertEquals(0.0, state.getProgress(), 0.001);
    }

    @Test
    @DisplayName("Complete download progress")
    void testCompleteProgress() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 5);

        for (int i = 0; i < 5; i++) {
            state.markPieceComplete(i);
        }

        assertEquals(1.0, state.getProgress(), 0.001);
    }

    @Test
    @DisplayName("Get torrent name")
    void testGetTorrentName() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "ubuntu.iso", 1024000, 100);

        assertEquals("ubuntu.iso", state.getTorrentName());
    }

    @Test
    @DisplayName("Get total bytes")
    void testGetTotalBytes() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 2048000, 100);

        assertEquals(2048000, state.getTotalBytes());
    }

    @Test
    @DisplayName("Get piece count")
    void testGetPieceCount() {
        byte[] infoHash = new byte[20];
        DownloadState state = new DownloadState(infoHash, "test.torrent", 1024000, 50);

        assertEquals(50, state.getPieceCount());
    }
}
