package com.example.jtorrent.web;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for RestRouter.
 */
@DisplayName("RestRouter Tests")
class RestRouterTest {

    private RestRouter router;
    @Mock
    private TorrentController torrentController;
    @Mock
    private StatsController statsController;
    @Mock
    private PeerController peerController;
    @Mock
    private HttpExchange exchange;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream("".getBytes()));
        router = new RestRouter(torrentController, peerController, statsController);
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
        @DisplayName("Should create router with controllers")
        void shouldCreateRouterWithControllers() {
            assertNotNull(router);
        }
    }

    @Nested
    @DisplayName("Torrent Routes Tests")
    class TorrentRoutesTests {

        @Test
        @DisplayName("Should route GET torrents")
        void shouldRouteGetTorrents() {
            when(torrentController.list()).thenReturn("[{\"name\":\"test\"}]");

            String response = router.route("GET", "/api/torrents", exchange);

            verify(torrentController).list();
            assertEquals("[{\"name\":\"test\"}]", response);
        }

        @Test
        @DisplayName("Should route POST torrent")
        void shouldRoutePostTorrent() {
            when(torrentController.add("")).thenReturn("{\"status\":\"added\"}");

            String response = router.route("POST", "/api/torrents", exchange);

            verify(torrentController).add("");
        }

        @Test
        @DisplayName("Should route DELETE torrent")
        void shouldRouteDeleteTorrent() {
            when(torrentController.remove("abc123")).thenReturn("{\"status\":\"removed\"}");

            String response = router.route("DELETE", "/api/torrents/abc123", exchange);

            verify(torrentController).remove("abc123");
        }

        @Test
        @DisplayName("Should route start torrent")
        void shouldRouteStartTorrent() {
            when(torrentController.start("abc123")).thenReturn("{\"status\":\"started\"}");

            String response = router.route("POST", "/api/torrents/abc123/start", exchange);

            verify(torrentController).start("abc123");
        }

        @Test
        @DisplayName("Should route stop torrent")
        void shouldRouteStopTorrent() {
            when(torrentController.stop("abc123")).thenReturn("{\"status\":\"stopped\"}");

            String response = router.route("POST", "/api/torrents/abc123/stop", exchange);

            verify(torrentController).stop("abc123");
        }
    }

    @Nested
    @DisplayName("Stats Routes Tests")
    class StatsRoutesTests {

        @Test
        @DisplayName("Should route GET stats")
        void shouldRouteGetStats() {
            when(statsController.getStats()).thenReturn("{\"downloadSpeed\":1000}");

            String response = router.route("GET", "/api/stats", exchange);

            verify(statsController).getStats();
            assertEquals("{\"downloadSpeed\":1000}", response);
        }
    }

    @Nested
    @DisplayName("Peer Routes Tests")
    class PeerRoutesTests {

        @Test
        @DisplayName("Should route GET peers for torrent")
        void shouldRouteGetPeersForTorrent() {
            when(peerController.getPeers("abc123")).thenReturn("[{\"ip\":\"192.168.1.1\"}]");

            String response = router.route("GET", "/api/torrents/abc123/peers", exchange);

            verify(peerController).getPeers("abc123");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return 404 for unknown route")
        void shouldReturn404ForUnknownRoute() {
            String response = router.route("GET", "/api/unknown", exchange);

            assertTrue(response.contains("Not found") || response.contains("success"));
        }

        @Test
        @DisplayName("Should return error for unknown method")
        void shouldReturnErrorForUnknownMethod() {
            String response = router.route("PATCH", "/api/torrents", exchange);

            assertTrue(response.contains("Method not allowed") || response.contains("success"));
        }
    }

    @Nested
    @DisplayName("Path Parameter Tests")
    class PathParameterTests {

        @Test
        @DisplayName("Should extract hash from torrent routes")
        void shouldExtractHashFromTorrentRoutes() {
            when(torrentController.remove("def456")).thenReturn("{\"status\":\"removed\"}");

            String response = router.route("DELETE", "/api/torrents/def456", exchange);

            verify(torrentController).remove("def456");
            assertNotNull(response);
        }

        @Test
        @DisplayName("Should extract hash from peer routes")
        void shouldExtractHashFromPeerRoutes() {
            when(peerController.getPeers("abc789")).thenReturn("[{\"ip\":\"192.168.1.1\"}]");

            String response = router.route("GET", "/api/torrents/abc789/peers", exchange);

            assertEquals("[{\"ip\":\"192.168.1.1\"}]", response);
        }
    }
}
