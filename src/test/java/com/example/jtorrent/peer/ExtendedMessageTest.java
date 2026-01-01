package com.example.jtorrent.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExtendedMessage Tests")
class ExtendedMessageTest {

    @Test
    @DisplayName("Create extended message")
    void testCreateExtendedMessage() {
        byte[] payload = { 1, 2, 3 };
        ExtendedMessage message = new ExtendedMessage(5, payload);

        assertNotNull(message);
        assertEquals(5, message.getExtensionId());
        assertArrayEquals(payload, message.getPayload());
    }

    @Test
    @DisplayName("Get extension ID")
    void testGetExtensionId() {
        ExtendedMessage message = new ExtendedMessage(10, new byte[0]);
        assertEquals(10, message.getExtensionId());
    }

    @Test
    @DisplayName("Get data")
    void testGetData() {
        byte[] data = { 42, 43, 44 };
        ExtendedMessage message = new ExtendedMessage(1, data);
        assertArrayEquals(data, message.getPayload());
    }

    @Test
    @DisplayName("Clone data on creation")
    void testCloneData() {
        byte[] data = { 1, 2, 3 };
        ExtendedMessage message = new ExtendedMessage(1, data);

        data[0] = 99;
        assertEquals(1, message.getPayload()[0]);
    }

    @Test
    @DisplayName("Handle empty data")
    void testEmptyData() {
        ExtendedMessage message = new ExtendedMessage(1, new byte[0]);
        assertEquals(0, message.getPayload().length);
    }

    @Test
    @DisplayName("Handle null data")
    void testNullData() {
        ExtendedMessage message = new ExtendedMessage(1, null);
        assertNotNull(message.getPayload());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    @DisplayName("Handshake ID constant")
    void testHandshakeId() {
        assertEquals(0, ExtendedMessage.HANDSHAKE_ID);
    }
}
