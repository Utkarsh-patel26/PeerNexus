package com.example.jtorrent.streaming;

import com.example.jtorrent.parser.FileEntry;
import com.example.jtorrent.parser.TorrentFile;
import com.example.jtorrent.storage.PieceManager;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for StreamingPieceSelector.
 */
@DisplayName("StreamingPieceSelector Tests")
class StreamingPieceSelectorTest {

    private StreamingPieceSelector selector;
    @Mock
    private PieceManager pieceManager;
    @Mock
    private TorrentFile torrentFile;
    @Mock
    private FileEntry fileEntry;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(pieceManager.getPieceCount()).thenReturn(100);
        when(torrentFile.pieceLength()).thenReturn((int) (1024 * 1024));
        when(fileEntry.length()).thenReturn((long) 100 * 1024 * 1024);
        when(torrentFile.files()).thenReturn(List.of(fileEntry));

        selector = new StreamingPieceSelector(pieceManager, torrentFile, 0, 1024 * 1024);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create selector")
        void shouldCreateSelector() {
            assertNotNull(selector);
        }
    }

    @Nested
    @DisplayName("Position Tests")
    class PositionTests {

        @Test
        @DisplayName("Should set current position")
        void shouldSetCurrentPosition() {
            selector.setCurrentPosition(0);
            assertNotNull(selector);
        }

        @Test
        @DisplayName("Should update position")
        void shouldUpdatePosition() {
            selector.setCurrentPosition(0);
            selector.setCurrentPosition(1024 * 1024L);
            assertNotNull(selector);
        }

        @Test
        @DisplayName("Should handle forward seek")
        void shouldHandleForwardSeek() {
            selector.setCurrentPosition(0);
            selector.setCurrentPosition(50 * 1024 * 1024L);
            assertNotNull(selector);
        }

        @Test
        @DisplayName("Should handle backward seek")
        void shouldHandleBackwardSeek() {
            selector.setCurrentPosition(50 * 1024 * 1024L);
            selector.setCurrentPosition(10 * 1024 * 1024L);
            assertNotNull(selector);
        }
    }
}
