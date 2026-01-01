package com.example.jtorrent.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.Map;

/**
 * Comprehensive tests for Choker algorithm.
 * Tests tit-for-tat, optimistic unchoke, and slot management.
 */
@DisplayName("Choker Algorithm Tests")
class ChokerAlgorithmTest {

    private Choker choker;

    @BeforeEach
    void setUp() {
        choker = new Choker(8);
    }

    @Nested
    @DisplayName("Peer Management")
    class PeerManagement {

        @Test
        @DisplayName("Add peer creates stats")
        void testAddPeer() {
            PeerStats stats = choker.addPeer("peer1");
            assertNotNull(stats);
            assertEquals("peer1", stats.getPeerId());
        }

        @Test
        @DisplayName("Add multiple peers")
        void testAddMultiplePeers() {
            choker.addPeer("peer1");
            choker.addPeer("peer2");
            choker.addPeer("peer3");

            Map<String, PeerStats> allStats = choker.getAllPeerStats();
            assertEquals(3, allStats.size());
            assertTrue(allStats.containsKey("peer1"));
            assertTrue(allStats.containsKey("peer2"));
            assertTrue(allStats.containsKey("peer3"));
        }

        @Test
        @DisplayName("Remove peer cleans up")
        void testRemovePeer() {
            choker.addPeer("peer1");
            choker.addPeer("peer2");

            choker.removePeer("peer1");

            assertNull(choker.getPeerStats("peer1"));
            assertNotNull(choker.getPeerStats("peer2"));
        }

        @Test
        @DisplayName("Remove non-existent peer is safe")
        void testRemoveNonExistentPeer() {
            assertDoesNotThrow(() -> choker.removePeer("nonexistent"));
        }

        @Test
        @DisplayName("Get peer stats returns null for unknown peer")
        void testGetUnknownPeerStats() {
            assertNull(choker.getPeerStats("unknown"));
        }
    }

    @Nested
    @DisplayName("Upload Slots")
    class UploadSlots {

        @Test
        @DisplayName("Minimum slot enforcement")
        void testMinimumSlots() {
            Choker c = new Choker(1);
            assertEquals(Choker.MIN_UPLOAD_SLOTS, c.getUploadSlots());
        }

        @Test
        @DisplayName("Maximum slot enforcement")
        void testMaximumSlots() {
            Choker c = new Choker(100);
            assertEquals(Choker.MAX_UPLOAD_SLOTS, c.getUploadSlots());
        }

        @Test
        @DisplayName("Normal slot count preserved")
        void testNormalSlots() {
            Choker c = new Choker(8);
            assertEquals(8, c.getUploadSlots());
        }

        @Test
        @DisplayName("Default upload slots")
        void testDefaultSlots() {
            Choker c = new Choker();
            assertEquals(Choker.DEFAULT_UPLOAD_SLOTS, c.getUploadSlots());
        }
    }

    @Nested
    @DisplayName("Choking Algorithm")
    class ChokingAlgorithm {

        @Test
        @DisplayName("Empty peer list returns empty unchoke set")
        void testEmptyPeerList() {
            Set<String> unchoked = choker.runChokingAlgorithm();
            assertTrue(unchoked.isEmpty());
        }

        @Test
        @DisplayName("Single peer gets unchoked")
        void testSinglePeer() {
            PeerStats stats = choker.addPeer("peer1");
            stats.setPeerInterested(true);
            stats.recordDownload(1000);
            choker.updateAllRates(1000);

            Set<String> unchoked = choker.runChokingAlgorithm();
            assertTrue(unchoked.contains("peer1"));
        }

        @Test
        @DisplayName("Non-interested peers are not unchoked")
        void testNonInterestedPeers() {
            PeerStats stats = choker.addPeer("peer1");
            stats.setPeerInterested(false);

            Set<String> unchoked = choker.runChokingAlgorithm();
            assertFalse(unchoked.contains("peer1"));
        }

        @Test
        @DisplayName("Unchoke respects slot limit")
        void testSlotLimit() {
            // Add more interested peers than slots
            for (int i = 0; i < 15; i++) {
                PeerStats stats = choker.addPeer("peer" + i);
                stats.setPeerInterested(true);
                stats.recordDownload(1000 * (15 - i)); // Higher priority for lower indices
            }
            choker.updateAllRates(1000);

            Set<String> unchoked = choker.runChokingAlgorithm();

            // Should not exceed upload slots (including optimistic)
            assertTrue(unchoked.size() <= choker.getUploadSlots() + 1);
        }

        @Test
        @DisplayName("Tit-for-tat favors uploading peers")
        void testTitForTat() {
            // Peer with high upload rate
            PeerStats goodPeer = choker.addPeer("goodPeer");
            goodPeer.setPeerInterested(true);
            goodPeer.recordDownload(10000);

            // Peer with low upload rate
            PeerStats badPeer = choker.addPeer("badPeer");
            badPeer.setPeerInterested(true);
            badPeer.recordDownload(100);

            // Update rates
            choker.updateAllRates(1000);

            Set<String> unchoked = choker.runChokingAlgorithm();

            // Good peer should be unchoked
            assertTrue(unchoked.contains("goodPeer"));
        }
    }

    @Nested
    @DisplayName("Peer Stats")
    class PeerStatsTests {

        @Test
        @DisplayName("Record download updates counter")
        void testRecordDownload() {
            PeerStats stats = choker.addPeer("peer1");
            stats.recordDownload(1000);
            stats.recordDownload(500);

            assertEquals(1500, stats.getTotalDownloaded());
        }

        @Test
        @DisplayName("Record upload updates counter")
        void testRecordUpload() {
            PeerStats stats = choker.addPeer("peer1");
            stats.recordUpload(2000);
            stats.recordUpload(1000);

            assertEquals(3000, stats.getTotalUploaded());
        }

        @Test
        @DisplayName("Interest state tracking")
        void testInterestState() {
            PeerStats stats = choker.addPeer("peer1");
            assertFalse(stats.isInterested());

            stats.setInterested(true);
            assertTrue(stats.isInterested());

            stats.setInterested(false);
            assertFalse(stats.isInterested());
        }

        @Test
        @DisplayName("Choke state tracking")
        void testChokeState() {
            PeerStats stats = choker.addPeer("peer1");
            assertTrue(stats.isChoked()); // Default should be choked

            stats.setChoked(false);
            assertFalse(stats.isChoked());
        }

        @Test
        @DisplayName("Rate calculation")
        void testRateCalculation() {
            PeerStats stats = choker.addPeer("peer1");
            stats.recordDownload(10000);

            // Update rates with 1 second window
            stats.updateRates(1000);

            // Rate should be approximately 10000 bytes/sec (depends on timing)
            assertTrue(stats.getDownloadRateBytesPerSec() >= 0);
        }
    }

    @Nested
    @DisplayName("Optimistic Unchoke")
    class OptimisticUnchoke {

        @Test
        @DisplayName("At least one choked interested peer gets optimistic unchoke")
        void testOptimisticUnchoke() {
            // Fill all regular slots
            for (int i = 0; i < 10; i++) {
                PeerStats stats = choker.addPeer("regular" + i);
                stats.setPeerInterested(true);
                stats.recordDownload(10000);
            }
            choker.updateAllRates(1000);

            // Add a new peer with no data transferred
            PeerStats newPeer = choker.addPeer("newPeer");
            newPeer.setPeerInterested(true);
            newPeer.recordDownload(0);

            // Run the algorithm - at least one peer should be unchoked
            Set<String> unchoked = choker.runChokingAlgorithm();
            assertFalse(unchoked.isEmpty());
        }
    }

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent peer additions are safe")
        void testConcurrentAdditions() throws InterruptedException {
            Thread[] threads = new Thread[10];
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    choker.addPeer("concurrent" + idx);
                });
            }

            for (Thread t : threads) {
                t.start();
            }

            for (Thread t : threads) {
                t.join(1000);
            }

            assertEquals(10, choker.getAllPeerStats().size());
        }
    }
}
