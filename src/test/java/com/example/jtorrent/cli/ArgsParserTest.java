package com.example.jtorrent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArgsParser Tests")
class ArgsParserTest {

    @Test
    @DisplayName("Parse input file argument")
    void testParseInputFile() {
        String[] args = { "-i", "test.torrent" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("test.torrent", parsed.input);
    }

    @Test
    @DisplayName("Parse output directory argument")
    void testParseOutputDirectory() {
        String[] args = { "-o", "/downloads" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("/downloads", parsed.output);
    }

    @Test
    @DisplayName("Parse config file argument")
    void testParseConfigFile() {
        String[] args = { "--config", "config.json" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("config.json", parsed.configFile);
    }

    @Test
    @DisplayName("Parse max upload argument")
    void testParseMaxUpload() {
        String[] args = { "--max-upload", "1024" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals(1024, parsed.maxUpload);
    }

    @Test
    @DisplayName("Parse max download argument")
    void testParseMaxDownload() {
        String[] args = { "--max-download", "2048" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals(2048, parsed.maxDownload);
    }

    @Test
    @DisplayName("Parse verbose flag")
    void testParseVerbose() {
        String[] args = { "-v" };
        CliArgs parsed = ArgsParser.parse(args);
        assertTrue(parsed.verbose);
    }

    @Test
    @DisplayName("Parse help flag")
    void testParseHelp() {
        String[] args = { "-h" };
        CliArgs parsed = ArgsParser.parse(args);
        assertTrue(parsed.showHelp);
    }

    @Test
    @DisplayName("Parse version flag")
    void testParseVersion() {
        String[] args = { "--version" };
        CliArgs parsed = ArgsParser.parse(args);
        assertTrue(parsed.showVersion);
    }

    @Test
    @DisplayName("Parse multiple arguments")
    void testParseMultipleArguments() {
        String[] args = { "-i", "test.torrent", "-o", "/downloads", "-v" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("test.torrent", parsed.input);
        assertEquals("/downloads", parsed.output);
        assertTrue(parsed.verbose);
    }

    @Test
    @DisplayName("Parse empty arguments")
    void testParseEmptyArguments() {
        String[] args = {};
        CliArgs parsed = ArgsParser.parse(args);
        assertNotNull(parsed);
    }

    @Test
    @DisplayName("Parse magnet URI argument")
    void testParseMagnetUri() {
        String[] args = { "--magnet", "magnet:?xt=urn:btih:abc123" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("magnet:?xt=urn:btih:abc123", parsed.magnetUri);
    }

    @Test
    @DisplayName("Parse tracker argument")
    void testParseTracker() {
        String[] args = { "--tracker", "http://tracker.example.com:8080/announce" };
        CliArgs parsed = ArgsParser.parse(args);
        assertEquals("http://tracker.example.com:8080/announce", parsed.tracker);
    }
}
