package com.example.jtorrent.core;

import com.example.jtorrent.peer.PeerConnection;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeepAliveManager.
 */
@DisplayName("KeepAliveManager Tests")
class KeepAliveManagerTest {

    private KeepAliveManager keepAliveManager;
    @Mock
    private PeerConnection connection;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        keepAliveManager = new KeepAliveManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        keepAliveManager.stop();
        if (mocks != null) {
            mocks.close();
        }
    }

    @Nested
    @DisplayName("Start/Stop Tests")
    class StartStopTests {

        @Test
        @DisplayName("Should start manager without errors")
        void shouldStartManager() {
            assertDoesNotThrow(() -> keepAliveManager.start());
        }

        @Test
        @DisplayName("Should stop manager without errors")
        void shouldStopManager() {
            keepAliveManager.start();
            assertDoesNotThrow(() -> keepAliveManager.stop());
        }

        @Test
        @DisplayName("Should handle multiple stops without errors")
        void shouldHandleMultipleStops() {
            keepAliveManager.start();
            assertDoesNotThrow(() -> keepAliveManager.stop());
            assertDoesNotThrow(() -> keepAliveManager.stop());
        }
    }

    @Nested
    @DisplayName("Peer Registration Tests")
    class PeerRegistrationTests {

        @Test
        @DisplayName("Should register peer")
        void shouldRegisterPeer() {
            InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
            keepAliveManager.registerPeer(address, connection);
            assertTrue(keepAliveManager.isPeerRegistered(address));
        }

        @Test
        @DisplayName("Should unregister peer")
        void shouldUnregisterPeer() {
            InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
            keepAliveManager.registerPeer(address, connection);
            keepAliveManager.unregisterPeer(address);
            assertFalse(keepAliveManager.isPeerRegistered(address));
        }

        @Test
        @DisplayName("Should return correct peer count")
        void shouldReturnCorrectPeerCount() {
            assertEquals(0, keepAliveManager.getPeerCount());

            for (int i = 0; i < 5; i++) {
                InetSocketAddress address = new InetSocketAddress("192.168.1." + i, 6881);
                keepAliveManager.registerPeer(address, connection);
            }

            assertEquals(5, keepAliveManager.getPeerCount());
        }

        @Test
        @DisplayName("Should handle duplicate peer registration")
        void shouldHandleDuplicateRegistration() {
            InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
            keepAliveManager.registerPeer(address, connection);
            keepAliveManager.registerPeer(address, connection);
            assertEquals(1, keepAliveManager.getPeerCount());
        }
    }

    @Nested
    @DisplayName("Activity Tracking Tests")
    class ActivityTrackingTests {

        @Test
        @DisplayName("Should mark activity on peer")
        void shouldMarkActivityOnPeer() {
            InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
            keepAliveManager.registerPeer(address, connection);

            assertDoesNotThrow(() -> keepAliveManager.markActivity(address));
            assertTrue(keepAliveManager.isPeerRegistered(address));
        }

        @Test
        @DisplayName("Should handle activity on unregistered peer gracefully")
        void shouldHandleActivityOnUnregisteredPeer() {
            InetSocketAddress address = new InetSocketAddress("192.168.1.1", 6881);
            assertDoesNotThrow(() -> keepAliveManager.markActivity(address));
        }
    }
}
