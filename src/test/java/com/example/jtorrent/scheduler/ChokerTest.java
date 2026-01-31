package com.example.jtorrent.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Choker Tests")
class ChokerTest {

    @Test
    @DisplayName("Create choker with upload slots")
    void testCreateChoker() {
        Choker choker = new Choker(4);
        assertNotNull(choker);
    }

    @Test
    @DisplayName("Get upload slots")
    void testGetUploadSlots() {
        Choker choker = new Choker(5);
        // Value is clamped to MIN_UPLOAD_SLOTS (6)
        assertEquals(6, choker.getUploadSlots());
    }

    @Test
    @DisplayName("Choker with different slot counts")
    void testChokerWithDifferentSlots() {
        Choker choker1 = new Choker(2);
        Choker choker2 = new Choker(8);

        // Value is clamped to MIN_UPLOAD_SLOTS (6) when less than minimum
        assertEquals(6, choker1.getUploadSlots());
        assertEquals(8, choker2.getUploadSlots());
    }
}
