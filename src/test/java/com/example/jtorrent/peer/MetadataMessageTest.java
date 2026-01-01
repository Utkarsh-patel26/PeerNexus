package com.example.jtorrent.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataMessage Tests")
class MetadataMessageTest {

    @Test
    @DisplayName("Create metadata request message")
    void testCreateRequestMessage() {
        MetadataMessage message = new MetadataMessage(MetadataMessage.REQUEST, 5, 0, null);

        assertNotNull(message);
        assertEquals(MetadataMessage.REQUEST, message.getMsgType());
        assertEquals(5, message.getPiece());
    }

    @Test
    @DisplayName("Create metadata data message")
    void testCreateDataMessage() {
        byte[] data = new byte[16384];
        MetadataMessage message = new MetadataMessage(MetadataMessage.DATA, 3, 32768, data);

        assertNotNull(message);
        assertEquals(MetadataMessage.DATA, message.getMsgType());
        assertEquals(3, message.getPiece());
        assertEquals(32768, message.getTotalSize());
        assertArrayEquals(data, message.getData());
    }

    @Test
    @DisplayName("Create metadata reject message")
    void testCreateRejectMessage() {
        MetadataMessage message = new MetadataMessage(MetadataMessage.REJECT, 7, 0, null);

        assertNotNull(message);
        assertEquals(MetadataMessage.REJECT, message.getMsgType());
        assertEquals(7, message.getPiece());
    }

    @Test
    @DisplayName("Get message type")
    void testGetType() {
        MetadataMessage message = new MetadataMessage(MetadataMessage.REQUEST, 0, 0, null);
        assertEquals(MetadataMessage.REQUEST, message.getMsgType());
    }

    @Test
    @DisplayName("Get piece index")
    void testGetPiece() {
        MetadataMessage message = new MetadataMessage(MetadataMessage.REQUEST, 42, 0, null);
        assertEquals(42, message.getPiece());
    }

    @Test
    @DisplayName("Constants defined")
    void testConstants() {
        assertEquals(0, MetadataMessage.REQUEST);
        assertEquals(1, MetadataMessage.DATA);
        assertEquals(2, MetadataMessage.REJECT);
        assertEquals(16384, MetadataMessage.PIECE_SIZE);
    }

    @Test
    @DisplayName("Handle null data")
    void testNullData() {
        MetadataMessage message = new MetadataMessage(MetadataMessage.REQUEST, 0, 0, null);
        assertNull(message.getData());
    }
}
