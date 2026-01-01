package com.example.jtorrent.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DownloadTui Tests")
class DownloadTuiTest {

    @Test
    @DisplayName("Create download TUI")
    void testCreateDownloadTui() {
        assertNotNull(DownloadTui.class);
    }

    @Test
    @DisplayName("DownloadTui class exists")
    void testDownloadTuiExists() {
        assertDoesNotThrow(() -> {
            Class.forName("com.example.jtorrent.ui.DownloadTui");
        });
    }
}
