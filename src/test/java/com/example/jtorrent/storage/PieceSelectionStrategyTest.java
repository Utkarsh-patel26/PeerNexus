package com.example.jtorrent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PieceSelectionStrategy Tests")
class PieceSelectionStrategyTest {

    @Test
    @DisplayName("Enum values exist")
    void testEnumValues() {
        assertNotNull(PieceSelectionStrategy.RAREST_FIRST);
    }

    @Test
    @DisplayName("Values method returns all strategies")
    void testValues() {
        PieceSelectionStrategy[] strategies = PieceSelectionStrategy.values();
        assertTrue(strategies.length >= 1);
    }

    @Test
    @DisplayName("ValueOf returns correct enum")
    void testValueOf() {
        assertEquals(PieceSelectionStrategy.RAREST_FIRST,
                PieceSelectionStrategy.valueOf("RAREST_FIRST"));
    }

    @Test
    @DisplayName("Enum name is correct")
    void testEnumName() {
        assertEquals("RAREST_FIRST", PieceSelectionStrategy.RAREST_FIRST.name());
    }
}
