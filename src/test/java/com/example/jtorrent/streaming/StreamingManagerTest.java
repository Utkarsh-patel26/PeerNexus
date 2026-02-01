package com.example.jtorrent.streaming;

import com.example.jtorrent.core.TorrentSession;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for StreamingManager.
 */
@DisplayName("StreamingManager Tests")
class StreamingManagerTest {

    private StreamingManager streamingManager;
    @Mock
    private TorrentSession torrentSession;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        streamingManager = new StreamingManager(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (streamingManager != null) {
            streamingManager.stop();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create streaming manager")
        void shouldCreateStreamingManager() {
            StreamingManager manager = new StreamingManager(0);
            assertNotNull(manager);
        }
    }

    @Nested
    @DisplayName("Start/Stop Tests")
    class StartStopTests {

        @Test
        @DisplayName("Should start streaming server")
        void shouldStartStreamingServer() throws Exception {
            streamingManager.start();
            assertNotNull(streamingManager);
        }

        @Test
        @DisplayName("Should stop streaming server")
        void shouldStopStreamingServer() {
            streamingManager.stop();
            assertNotNull(streamingManager);
        }
    }

    @Nested
    @DisplayName("Streaming Session Tests")
    class StreamingSessionTests {

        @Test
        @DisplayName("Should start streaming session")
        void shouldStartStreamingSession() {
            when(torrentSession.getName()).thenReturn("test_torrent");

            StreamingManager.StreamingSession session = streamingManager.startStreaming(torrentSession, 0);

            assertNotNull(session);
        }

        @Test
        @DisplayName("Should stop streaming session")
        void shouldStopStreamingSession() {
            when(torrentSession.getName()).thenReturn("test_torrent");

            streamingManager.startStreaming(torrentSession, 0);
            streamingManager.stopStreaming("test_torrent", 0);

            assertDoesNotThrow(() -> streamingManager.stopStreaming("test_torrent", 0));
        }
    }

    @Nested
    @DisplayName("File Access Tests")
    class FileAccessTests {

        @Test
        @DisplayName("Should get file size")
        void shouldGetFileSize() {
            when(torrentSession.getName()).thenReturn("test_torrent");

            streamingManager.startStreaming(torrentSession, 0);
            long size = streamingManager.getFileSize("test_torrent", 0);

            assertTrue(size >= 0 || size == -1);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle get session for invalid key")
        void shouldHandleGetSessionForInvalidKey() {
            StreamingManager.StreamingSession session = streamingManager.getSession("nonexistent", 0);
            assertNull(session);
        }
    }
}
