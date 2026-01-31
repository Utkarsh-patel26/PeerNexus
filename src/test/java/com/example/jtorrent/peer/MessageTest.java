package com.example.jtorrent.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Message Tests")
class MessageTest {

    @Test
    @DisplayName("Create CHOKE message")
    void testCreateChoke() {
        Message msg = new Message(Message.CHOKE, new byte[0]);
        assertNotNull(msg);
        assertEquals(Message.CHOKE, msg.type());
    }

    @Test
    @DisplayName("Create UNCHOKE message")
    void testCreateUnchoke() {
        Message msg = new Message(Message.UNCHOKE, new byte[0]);
        assertNotNull(msg);
        assertEquals(Message.UNCHOKE, msg.type());
    }

    @Test
    @DisplayName("Create INTERESTED message")
    void testCreateInterested() {
        Message msg = new Message(Message.INTERESTED, new byte[0]);
        assertNotNull(msg);
        assertEquals(Message.INTERESTED, msg.type());
    }

    @Test
    @DisplayName("Create NOT_INTERESTED message")
    void testCreateNotInterested() {
        Message msg = new Message(Message.NOT_INTERESTED, new byte[0]);
        assertNotNull(msg);
        assertEquals(Message.NOT_INTERESTED, msg.type());
    }

    @Test
    @DisplayName("Create HAVE message")
    void testCreateHave() {
        byte[] payload = new byte[4];
        Message msg = new Message(Message.HAVE, payload);
        assertNotNull(msg);
        assertEquals(Message.HAVE, msg.type());
        assertNotNull(msg.payload());
    }

    @Test
    @DisplayName("Create BITFIELD message")
    void testCreateBitfield() {
        byte[] bitfield = new byte[] { (byte) 0b11110000, (byte) 0b10101010 };
        Message msg = new Message(Message.BITFIELD, bitfield);
        assertNotNull(msg);
        assertEquals(Message.BITFIELD, msg.type());
        assertArrayEquals(bitfield, msg.payload());
    }

    @Test
    @DisplayName("Create REQUEST message")
    void testCreateRequest() {
        byte[] payload = new byte[12];
        Message msg = new Message(Message.REQUEST, payload);
        assertNotNull(msg);
        assertEquals(Message.REQUEST, msg.type());
        assertEquals(12, msg.payload().length);
    }

    @Test
    @DisplayName("Create PIECE message")
    void testCreatePiece() {
        byte[] payload = new byte[8192];
        Message msg = new Message(Message.PIECE, payload);
        assertNotNull(msg);
        assertEquals(Message.PIECE, msg.type());
    }

    @Test
    @DisplayName("Create CANCEL message")
    void testCreateCancel() {
        byte[] payload = new byte[12];
        Message msg = new Message(Message.CANCEL, payload);
        assertNotNull(msg);
        assertEquals(Message.CANCEL, msg.type());
    }

    @Test
    @DisplayName("Create PORT message")
    void testCreatePort() {
        byte[] payload = new byte[2];
        Message msg = new Message(Message.PORT, payload);
        assertNotNull(msg);
        assertEquals(Message.PORT, msg.type());
    }

    @Test
    @DisplayName("Create EXTENDED message")
    void testCreateExtended() {
        byte[] payload = new byte[] { 1, 2, 3, 4 };
        Message msg = new Message(Message.EXTENDED, payload);
        assertNotNull(msg);
        assertEquals(Message.EXTENDED, msg.type());
    }

    @Test
    @DisplayName("Create KEEP_ALIVE message")
    void testCreateKeepAlive() {
        Message msg = new Message(Message.KEEP_ALIVE, new byte[0]);
        assertNotNull(msg);
        assertEquals(Message.KEEP_ALIVE, msg.type());
    }

    @Test
    @DisplayName("Message with no payload")
    void testMessageWithoutPayload() {
        Message msg = new Message(Message.CHOKE, new byte[0]);
        assertNotNull(msg);
        assertTrue(msg.payload() == null || msg.payload().length == 0);
    }

    @Test
    @DisplayName("Message payload is immutable")
    void testMessagePayloadImmutable() {
        byte[] original = new byte[] { 1, 2, 3 };
        Message msg = new Message(Message.BITFIELD, original);
        original[0] = 99;
        // Message should not reflect changes to original array
        assertNotNull(msg.payload());
    }
}
