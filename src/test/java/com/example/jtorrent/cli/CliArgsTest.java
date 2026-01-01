package com.example.jtorrent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliArgs Tests")
class CliArgsTest {

    @Test
    @DisplayName("Create CliArgs")
    void testCreateCliArgs() {
        CliArgs args = new CliArgs();
        assertNotNull(args);
    }

    @Test
    @DisplayName("Set and get input")
    void testSetAndGetInput() {
        CliArgs args = new CliArgs();
        args.input = "test.torrent";
        assertEquals("test.torrent", args.input);
    }

    @Test
    @DisplayName("Set and get output")
    void testSetAndGetOutput() {
        CliArgs args = new CliArgs();
        args.output = "/downloads";
        assertEquals("/downloads", args.output);
    }

    @Test
    @DisplayName("Set and get config file")
    void testSetAndGetConfigFile() {
        CliArgs args = new CliArgs();
        args.configFile = "config.json";
        assertEquals("config.json", args.configFile);
    }

    @Test
    @DisplayName("Set and get max upload")
    void testSetAndGetMaxUpload() {
        CliArgs args = new CliArgs();
        args.maxUpload = 1024;
        assertEquals(1024, args.maxUpload);
    }

    @Test
    @DisplayName("Set and get max download")
    void testSetAndGetMaxDownload() {
        CliArgs args = new CliArgs();
        args.maxDownload = 2048;
        assertEquals(2048, args.maxDownload);
    }

    @Test
    @DisplayName("Set and get magnet URI")
    void testSetAndGetMagnetUri() {
        CliArgs args = new CliArgs();
        args.magnetUri = "magnet:?xt=urn:btih:abc123";
        assertEquals("magnet:?xt=urn:btih:abc123", args.magnetUri);
    }

    @Test
    @DisplayName("Set and get verbose flag")
    void testSetAndGetVerbose() {
        CliArgs args = new CliArgs();
        args.verbose = true;
        assertTrue(args.verbose);
    }

    @Test
    @DisplayName("Set and get help flag")
    void testSetAndGetHelp() {
        CliArgs args = new CliArgs();
        args.showHelp = true;
        assertTrue(args.showHelp);
    }

    @Test
    @DisplayName("Default values")
    void testDefaultValues() {
        CliArgs args = new CliArgs();
        assertNull(args.input);
        assertNull(args.output);
        assertFalse(args.verbose);
        assertFalse(args.showHelp);
    }
}
