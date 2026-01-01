package com.example.jtorrent.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BencodeException Tests")
class BencodeExceptionTest {

    @Test
    @DisplayName("Create exception with message")
    void testCreateWithMessage() {
        String message = "Invalid bencode format";
        BencodeException exception = new BencodeException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with message and cause")
    void testCreateWithMessageAndCause() {
        String message = "Parsing failed";
        Throwable cause = new IllegalArgumentException("Invalid input");
        BencodeException exception = new BencodeException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Exception is throwable")
    void testExceptionIsThrowable() {
        assertThrows(BencodeException.class, () -> {
            throw new BencodeException("Test exception");
        });
    }

    @Test
    @DisplayName("Exception can be caught as Exception")
    void testCatchAsException() {
        try {
            throw new BencodeException("Test");
        } catch (Exception e) {
            assertTrue(e instanceof BencodeException);
        }
    }
}
