package com.example.jtorrent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BlockInfo Tests")
class BlockInfoTest {

    @Test
    @DisplayName("Create block info")
    void testCreateBlockInfo() {
        BlockInfo block = new BlockInfo(5, 16384, 8192);

        assertNotNull(block);
        assertEquals(5, block.pieceIndex());
        assertEquals(16384, block.offset());
        assertEquals(8192, block.length());
    }

    @Test
    @DisplayName("Get piece index")
    void testGetPieceIndex() {
        BlockInfo block = new BlockInfo(10, 0, 16384);
        assertEquals(10, block.pieceIndex());
    }

    @Test
    @DisplayName("Get offset")
    void testGetOffset() {
        BlockInfo block = new BlockInfo(0, 32768, 16384);
        assertEquals(32768, block.offset());
    }

    @Test
    @DisplayName("Get length")
    void testGetLength() {
        BlockInfo block = new BlockInfo(0, 0, 8192);
        assertEquals(8192, block.length());
    }

    @Test
    @DisplayName("ToString contains info")
    void testToString() {
        BlockInfo block = new BlockInfo(3, 1024, 512);
        String str = block.toString();

        assertTrue(str.contains("3"));
        assertTrue(str.contains("1024"));
        assertTrue(str.contains("512"));
    }

    @Test
    @DisplayName("Equals with same values")
    void testEquals() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(5, 1024, 512);

        assertEquals(block1, block2);
    }

    @Test
    @DisplayName("Equals with same object")
    void testEqualsSameObject() {
        BlockInfo block = new BlockInfo(5, 1024, 512);
        assertEquals(block, block);
    }

    @Test
    @DisplayName("Not equals with different piece index")
    void testNotEqualsDifferentPiece() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(6, 1024, 512);

        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Not equals with different offset")
    void testNotEqualsDifferentOffset() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(5, 2048, 512);

        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Not equals with different length")
    void testNotEqualsDifferentLength() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(5, 1024, 1024);

        assertNotEquals(block1, block2);
    }

    @Test
    @DisplayName("Not equals with null")
    void testNotEqualsNull() {
        BlockInfo block = new BlockInfo(5, 1024, 512);
        assertNotEquals(block, null);
    }

    @Test
    @DisplayName("HashCode consistency")
    void testHashCode() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(5, 1024, 512);

        assertEquals(block1.hashCode(), block2.hashCode());
    }

    @Test
    @DisplayName("HashCode different for different values")
    void testHashCodeDifferent() {
        BlockInfo block1 = new BlockInfo(5, 1024, 512);
        BlockInfo block2 = new BlockInfo(6, 1024, 512);

        assertNotEquals(block1.hashCode(), block2.hashCode());
    }

    @Test
    @DisplayName("Create block at start of piece")
    void testBlockAtStart() {
        BlockInfo block = new BlockInfo(0, 0, 16384);
        assertEquals(0, block.offset());
    }

    @Test
    @DisplayName("Create block in middle of piece")
    void testBlockInMiddle() {
        BlockInfo block = new BlockInfo(2, 16384, 8192);
        assertEquals(16384, block.offset());
        assertEquals(8192, block.length());
    }
}
