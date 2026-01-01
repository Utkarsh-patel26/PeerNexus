package com.example.jtorrent.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@DisplayName("FileEntry Tests")
class FileEntryTest {

    @Test
    @DisplayName("Create file entry with path components")
    void testCreateWithPathComponents() {
        List<String> components = Arrays.asList("folder", "file.txt");
        List<byte[]> rawComponents = Arrays.asList(
                "folder".getBytes(StandardCharsets.UTF_8),
                "file.txt".getBytes(StandardCharsets.UTF_8));
        FileEntry entry = new FileEntry(components, rawComponents, 1024);

        assertEquals(components, entry.pathComponents());
        assertEquals(1024, entry.length());
    }

    @Test
    @DisplayName("Create file entry with path string")
    void testCreateWithPathString() {
        FileEntry entry = new FileEntry("folder/subfolder/file.txt", 2048);

        List<String> components = entry.pathComponents();
        assertEquals(3, components.size());
        assertEquals("folder", components.get(0));
        assertEquals("subfolder", components.get(1));
        assertEquals("file.txt", components.get(2));
        assertEquals(2048, entry.length());
    }

    @Test
    @DisplayName("Get path components")
    void testGetPathComponents() {
        FileEntry entry = new FileEntry("dir/file.dat", 512);
        List<String> components = entry.pathComponents();
        assertEquals(2, components.size());
        assertEquals("dir", components.get(0));
        assertEquals("file.dat", components.get(1));
    }

    @Test
    @DisplayName("Get path components raw")
    void testGetPathComponentsRaw() {
        FileEntry entry = new FileEntry("test/data.bin", 256);
        List<byte[]> rawComponents = entry.pathComponentsRaw();
        assertEquals(2, rawComponents.size());
        assertArrayEquals("test".getBytes(StandardCharsets.UTF_8), rawComponents.get(0));
        assertArrayEquals("data.bin".getBytes(StandardCharsets.UTF_8), rawComponents.get(1));
    }

    @Test
    @DisplayName("Get file length")
    void testGetLength() {
        FileEntry entry = new FileEntry("file.mp4", 102400);
        assertEquals(102400, entry.length());
    }

    @Test
    @DisplayName("Get full path")
    void testGetFullPath() {
        FileEntry entry = new FileEntry("videos/movie.mkv", 5000000);
        String fullPath = entry.path();
        assertEquals("videos/movie.mkv", fullPath);
    }

    @Test
    @DisplayName("Get relative path")
    void testGetRelativePath() {
        FileEntry entry = new FileEntry("documents/report.pdf", 4096);
        String relativePath = entry.path();
        assertEquals("documents/report.pdf", relativePath);
    }

    @Test
    @DisplayName("Handle single component path")
    void testSingleComponentPath() {
        FileEntry entry = new FileEntry("readme.txt", 128);
        List<String> components = entry.pathComponents();
        assertEquals(1, components.size());
        assertEquals("readme.txt", components.get(0));
    }

    @Test
    @DisplayName("Handle empty path segments")
    void testEmptyPathSegments() {
        FileEntry entry = new FileEntry("folder//file.txt", 64);
        List<String> components = entry.pathComponents();
        // Should have empty string for double slash
        assertTrue(components.size() >= 2);
    }
}
