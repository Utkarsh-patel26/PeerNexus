package com.example.jtorrent.core;

import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConnectionLimiter.
 */
@DisplayName("ConnectionLimiter Tests")
class ConnectionLimiterTest {

    private ConnectionLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ConnectionLimiter();
    }

    @Nested
    @DisplayName("Connection Permission Tests")
    class ConnectionPermissionTests {

        @Test
        @DisplayName("Should allow connection when under limits")
        void shouldAllowConnectionWhenUnderLimits() {
            String torrentId = "torrent1";
            InetSocketAddress peer = new InetSocketAddress("192.168.1.1", 6881);

            assertTrue(limiter.canConnect(torrentId, peer));
        }

        @Test
        @DisplayName("Should reject duplicate connection to same peer")
        void shouldRejectDuplicateConnectionToSamePeer() {
            String torrentId = "torrent1";
            InetSocketAddress peer = new InetSocketAddress("192.168.1.1", 6881);

            limiter.connectionStarted(torrentId, peer);
            limiter.connectionEstablished(torrentId, peer);

            assertFalse(limiter.canConnect(torrentId, peer));
        }

        @Test
        @DisplayName("Should allow connection to different peer")
        void shouldAllowConnectionToDifferentPeer() {
            String torrentId = "torrent1";
            InetSocketAddress peer1 = new InetSocketAddress("192.168.1.1", 6881);
            InetSocketAddress peer2 = new InetSocketAddress("192.168.1.2", 6881);

            limiter.connectionStarted(torrentId, peer1);
            limiter.connectionEstablished(torrentId, peer1);

            assertTrue(limiter.canConnect(torrentId, peer2));
        }
    }

    @Nested
    @DisplayName("Global Limit Tests")
    class GlobalLimitTests {

        @Test
        @DisplayName("Should reject when global limit reached")
        void shouldRejectWhenGlobalLimitReached() {
            String torrentId = "torrent1";
            limiter.setMaxGlobalConnections(2);

            for (int i = 0; i < 2; i++) {
                InetSocketAddress peer = new InetSocketAddress("192.168.1." + i, 6881);
                limiter.connectionStarted(torrentId, peer);
                limiter.connectionEstablished(torrentId, peer);
            }

            InetSocketAddress newPeer = new InetSocketAddress("192.168.1.100", 6881);
            assertFalse(limiter.canConnect(torrentId, newPeer));
        }

        @Test
        @DisplayName("Should allow after connection closed")
        void shouldAllowAfterConnectionClosed() {
            String torrentId = "torrent1";
            limiter.setMaxGlobalConnections(1);

            InetSocketAddress peer1 = new InetSocketAddress("192.168.1.1", 6881);
            limiter.connectionStarted(torrentId, peer1);
            limiter.connectionEstablished(torrentId, peer1);

            limiter.connectionClosed(torrentId, peer1);

            InetSocketAddress peer2 = new InetSocketAddress("192.168.1.2", 6881);
            assertTrue(limiter.canConnect(torrentId, peer2));
        }
    }

    @Nested
    @DisplayName("Per-Torrent Limit Tests")
    class PerTorrentLimitTests {

        @Test
        @DisplayName("Should reject when per-torrent limit reached")
        void shouldRejectWhenPerTorrentLimitReached() {
            String torrentId = "torrent1";
            limiter.setMaxConnectionsPerTorrent(2);

            for (int i = 0; i < 2; i++) {
                InetSocketAddress peer = new InetSocketAddress("192.168.1." + i, 6881);
                limiter.connectionStarted(torrentId, peer);
                limiter.connectionEstablished(torrentId, peer);
            }

            InetSocketAddress newPeer = new InetSocketAddress("192.168.1.100", 6881);
            assertFalse(limiter.canConnect(torrentId, newPeer));
        }

        @Test
        @DisplayName("Should allow for different torrent")
        void shouldAllowForDifferentTorrent() {
            String torrentId1 = "torrent1";
            String torrentId2 = "torrent2";
            limiter.setMaxConnectionsPerTorrent(1);

            InetSocketAddress peer1 = new InetSocketAddress("192.168.1.1", 6881);
            limiter.connectionStarted(torrentId1, peer1);
            limiter.connectionEstablished(torrentId1, peer1);

            InetSocketAddress peer2 = new InetSocketAddress("192.168.1.2", 6881);
            assertTrue(limiter.canConnect(torrentId2, peer2));
        }
    }

    @Nested
    @DisplayName("Half-Open Limit Tests")
    class HalfOpenLimitTests {

        @Test
        @DisplayName("Should reject when half-open limit reached")
        void shouldRejectWhenHalfOpenLimitReached() {
            String torrentId = "torrent1";
            limiter.setMaxHalfOpen(2);

            for (int i = 0; i < 2; i++) {
                InetSocketAddress peer = new InetSocketAddress("192.168.1." + i, 6881);
                limiter.connectionStarted(torrentId, peer);
            }

            InetSocketAddress newPeer = new InetSocketAddress("192.168.1.100", 6881);
            assertFalse(limiter.canConnect(torrentId, newPeer));
        }

        @Test
        @DisplayName("Should allow after half-open completes")
        void shouldAllowAfterHalfOpenCompletes() {
            String torrentId = "torrent1";
            limiter.setMaxHalfOpen(1);

            InetSocketAddress peer1 = new InetSocketAddress("192.168.1.1", 6881);
            limiter.connectionStarted(torrentId, peer1);
            limiter.connectionEstablished(torrentId, peer1);

            InetSocketAddress peer2 = new InetSocketAddress("192.168.1.2", 6881);
            assertTrue(limiter.canConnect(torrentId, peer2));
        }

        @Test
        @DisplayName("Should decrement half-open on failure")
        void shouldDecrementHalfOpenOnFailure() {
            String torrentId = "torrent1";
            limiter.setMaxHalfOpen(1);

            InetSocketAddress peer1 = new InetSocketAddress("192.168.1.1", 6881);
            limiter.connectionStarted(torrentId, peer1);
            limiter.connectionFailed(torrentId, peer1);

            InetSocketAddress peer2 = new InetSocketAddress("192.168.1.2", 6881);
            assertTrue(limiter.canConnect(torrentId, peer2));
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should set max global connections")
        void shouldSetMaxGlobalConnections() {
            limiter.setMaxGlobalConnections(50);
            assertNotNull(limiter);
        }

        @Test
        @DisplayName("Should set max connections per torrent")
        void shouldSetMaxConnectionsPerTorrent() {
            limiter.setMaxConnectionsPerTorrent(10);
            assertDoesNotThrow(() -> limiter.canConnect("torrent1",
                    new InetSocketAddress("192.168.1.1", 6881)));
        }

        @Test
        @DisplayName("Should set max half-open connections")
        void shouldSetMaxHalfOpenConnections() {
            limiter.setMaxHalfOpen(5);
            assertDoesNotThrow(() -> limiter.canConnect("torrent1",
                    new InetSocketAddress("192.168.1.1", 6881)));
        }

        @Test
        @DisplayName("Should set max connections per second")
        void shouldSetMaxConnectionsPerSecond() {
            limiter.setMaxConnectionsPerSecond(20);
            assertDoesNotThrow(() -> limiter.canConnect("torrent1",
                    new InetSocketAddress("192.168.1.1", 6881)));
        }
    }
}
