package com.example.jtorrent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;

@DisplayName("BlockRequest Tests")
class BlockRequestTest {

    private static final InetSocketAddress PEER = new InetSocketAddress("127.0.0.1", 6881);

    @Test
    @DisplayName("Create block request")
    void testCreateBlockRequest() {
        BlockRequest request = new BlockRequest(5, 16384, 8192, PEER);

        assertNotNull(request);
        assertEquals(5, request.getPieceIndex());
        assertEquals(16384, request.getOffset());
        assertEquals(8192, request.getLength());
    }

    @Test
    @DisplayName("Get piece index")
    void testGetPieceIndex() {
        BlockRequest request = new BlockRequest(10, 0, 16384, PEER);
        assertEquals(10, request.getPieceIndex());
    }

    @Test
    @DisplayName("Get offset")
    void testGetOffset() {
        BlockRequest request = new BlockRequest(0, 32768, 16384, PEER);
        assertEquals(32768, request.getOffset());
    }

    @Test
    @DisplayName("Get length")
    void testGetLength() {
        BlockRequest request = new BlockRequest(0, 0, 8192, PEER);
        assertEquals(8192, request.getLength());
    }

    @Test
    @DisplayName("Equals with same values")
    void testEquals() {
        BlockRequest request1 = new BlockRequest(5, 1024, 512, PEER);
        BlockRequest request2 = new BlockRequest(5, 1024, 512, PEER);

        assertEquals(request1, request2);
    }

    @Test
    @DisplayName("HashCode consistency")
    void testHashCode() {
        BlockRequest request1 = new BlockRequest(5, 1024, 512, PEER);
        BlockRequest request2 = new BlockRequest(5, 1024, 512, PEER);

        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    @DisplayName("ToString contains info")
    void testToString() {
        BlockRequest request = new BlockRequest(3, 1024, 512, PEER);
        String str = request.toString();
        assertNotNull(str);
    }
}
