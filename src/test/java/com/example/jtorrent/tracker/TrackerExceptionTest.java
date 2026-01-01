package com.example.jtorrent.tracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TrackerException Tests")
class TrackerExceptionTest {

    @Test
    @DisplayName("Create exception with message")
    void testCreateWithMessage() {
        String message = "Tracker connection failed";
        TrackerException exception = new TrackerException(message);

        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with message and cause")
    void testCreateWithMessageAndCause() {
        String message = "Tracker timeout";
        Throwable cause = new java.net.SocketTimeoutException();
        TrackerException exception = new TrackerException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Exception is throwable")
    void testExceptionIsThrowable() {
        assertThrows(TrackerException.class, () -> {
            throw new TrackerException("Test");
        });
    }

    @Test
    @DisplayName("Exception extends Exception")
    void testExtendsException() {
        TrackerException exception = new TrackerException("Test");
        assertTrue(exception instanceof Exception);
    }
}
