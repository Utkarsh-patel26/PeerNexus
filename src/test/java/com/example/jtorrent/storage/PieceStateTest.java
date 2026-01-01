package com.example.jtorrent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PieceState Tests")
class PieceStateTest {

    @Test
    @DisplayName("Enum values exist")
    void testEnumValues() {
        assertNotNull(PieceState.MISSING);
        assertNotNull(PieceState.DOWNLOADING);
        assertNotNull(PieceState.COMPLETE);
    }

    @Test
    @DisplayName("Values method returns all states")
    void testValues() {
        PieceState[] states = PieceState.values();
        assertEquals(3, states.length);
    }

    @Test
    @DisplayName("ValueOf returns correct enum")
    void testValueOf() {
        assertEquals(PieceState.MISSING, PieceState.valueOf("MISSING"));
        assertEquals(PieceState.DOWNLOADING, PieceState.valueOf("DOWNLOADING"));
        assertEquals(PieceState.COMPLETE, PieceState.valueOf("COMPLETE"));
    }

    @Test
    @DisplayName("Enum names are correct")
    void testEnumNames() {
        assertEquals("MISSING", PieceState.MISSING.name());
        assertEquals("DOWNLOADING", PieceState.DOWNLOADING.name());
        assertEquals("COMPLETE", PieceState.COMPLETE.name());
    }
}
