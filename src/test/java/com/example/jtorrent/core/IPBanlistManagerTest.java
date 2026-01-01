package com.example.jtorrent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Comprehensive tests for IPBanlistManager.
 */
@DisplayName("IPBanlistManager Tests")
class IPBanlistManagerTest {

    private IPBanlistManager manager;

    @BeforeEach
    void setUp() {
        manager = new IPBanlistManager();
    }

    @Nested
    @DisplayName("Single IP Banning")
    class SingleIPBanning {

        @Test
        @DisplayName("Ban single IP")
        void testBanSingleIP() {
            manager.banIP("192.168.1.100");
            assertTrue(manager.isBanned("192.168.1.100"));
        }

        @Test
        @DisplayName("Unbanned IP is not banned")
        void testUnbannedIP() {
            assertFalse(manager.isBanned("192.168.1.100"));
        }

        @Test
        @DisplayName("Unban removes IP from banlist")
        void testUnbanIP() {
            manager.banIP("192.168.1.100");
            assertTrue(manager.isBanned("192.168.1.100"));
            manager.unbanIP("192.168.1.100");
            assertFalse(manager.isBanned("192.168.1.100"));
        }
    }

    @Nested
    @DisplayName("IP Range Banning")
    class IPRangeBanning {

        @Test
        @DisplayName("Ban IP range")
        void testBanRange() {
            manager.banRange("192.168.1.0", "192.168.1.255");
            assertTrue(manager.isBanned("192.168.1.128"));
            assertFalse(manager.isBanned("192.168.2.1"));
        }
    }

    @Nested
    @DisplayName("Blocklist File Loading")
    class BlocklistFileLoading {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Load simple IP list")
        void testLoadSimpleIPList() throws IOException {
            File blocklist = tempDir.resolve("blocklist.txt").toFile();
            try (FileWriter writer = new FileWriter(blocklist)) {
                writer.write("192.168.1.1\n");
                writer.write("192.168.1.2\n");
            }
            int count = manager.loadBlocklist(blocklist);
            assertEquals(2, count);
            assertTrue(manager.isBanned("192.168.1.1"));
        }

        @Test
        @DisplayName("Non-existent file returns 0")
        void testNonExistentFile() {
            File missing = new File("nonexistent.txt");
            assertEquals(0, manager.loadBlocklist(missing));
        }
    }

    @Nested
    @DisplayName("Statistics and Cleanup")
    class StatisticsAndCleanup {

        @Test
        @DisplayName("Clear resets everything")
        void testClear() {
            manager.banIP("192.168.1.1");
            manager.clear();
            assertFalse(manager.isBanned("192.168.1.1"));
        }

        @Test
        @DisplayName("Get banned IPs returns list")
        void testGetBannedIPs() {
            manager.banIP("192.168.1.1");
            manager.banIP("192.168.1.2");
            assertTrue(manager.getBannedIPs().contains("192.168.1.1"));
            assertTrue(manager.getBannedIPs().contains("192.168.1.2"));
        }
    }
}
