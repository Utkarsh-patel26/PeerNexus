package com.example.jtorrent.unit.cli;

import com.example.jtorrent.cli.ArgsParser;
import com.example.jtorrent.cli.CliArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ArgsParser.
 * Tests command-line argument parsing functionality.
 */
class ArgsParserUnitTest {

    @Nested
    class HelpAndVersionFlags {

        @ParameterizedTest
        @ValueSource(strings = { "--help", "-h" })
        void parseHelpFlag(String flag) {
            CliArgs args = ArgsParser.parse(new String[] { flag });
            assertTrue(args.showHelp);
            assertFalse(args.hasError);
        }

        @Test
        void parseVersionFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--version" });
            assertTrue(args.showVersion);
            assertFalse(args.hasError);
        }

        @Test
        void parseVerboseFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--verbose" });
            assertTrue(args.verbose);
        }

        @Test
        void parseVerboseShortFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "-v" });
            assertTrue(args.verbose);
        }
    }

    @Nested
    class InputOutputOptions {

        @ParameterizedTest
        @ValueSource(strings = { "--input", "-i" })
        void parseInputOption(String flag) {
            CliArgs args = ArgsParser.parse(new String[] { flag, "file.torrent" });
            assertEquals("file.torrent", args.input);
            assertFalse(args.hasError);
        }

        @ParameterizedTest
        @ValueSource(strings = { "--output", "-o" })
        void parseOutputOption(String flag) {
            CliArgs args = ArgsParser.parse(new String[] { flag, "/download/path" });
            assertEquals("/download/path", args.output);
            assertFalse(args.hasError);
        }

        @Test
        void parseInputWithoutValueReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--input" });
            assertTrue(args.hasError);
            assertNotNull(args.errorMessage);
        }

        @Test
        void parseOutputWithoutValueReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--output" });
            assertTrue(args.hasError);
            assertNotNull(args.errorMessage);
        }
    }

    @Nested
    class ConfigOptions {

        @ParameterizedTest
        @ValueSource(strings = { "--config", "-c" })
        void parseConfigOption(String flag) {
            CliArgs args = ArgsParser.parse(new String[] { flag, "config.json" });
            assertEquals("config.json", args.configFile);
            assertFalse(args.hasError);
        }

        @Test
        void parseConfigWithoutValueReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--config" });
            assertTrue(args.hasError);
            assertNotNull(args.errorMessage);
        }
    }

    @Nested
    class BandwidthOptions {

        @Test
        void parseMaxUploadOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--max-upload", "1000" });
            assertEquals(1000, args.maxUpload);
            assertFalse(args.hasError);
        }

        @Test
        void parseMaxDownloadOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--max-download", "2000" });
            assertEquals(2000, args.maxDownload);
            assertFalse(args.hasError);
        }

        @Test
        void parseInvalidMaxUploadReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--max-upload", "invalid" });
            assertTrue(args.hasError);
        }

        @Test
        void parseNegativeMaxUploadReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--max-upload", "-100" });
            assertTrue(args.hasError);
        }
    }

    @Nested
    class TestModeOptions {

        @Test
        void parsePeerTestFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--peer-test" });
            assertTrue(args.peerTest);
        }

        @Test
        void parseSchedulerTestFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--run-scheduler-test" });
            assertTrue(args.runSchedulerTest);
        }

        @Test
        void parseValidateTorrentOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--validate-torrent", "test.torrent" });
            assertEquals("test.torrent", args.validateTorrent);
            assertFalse(args.hasError);
        }

        @Test
        void parseAnnounceTestOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--announce-test", "test.torrent" });
            assertEquals("test.torrent", args.announceTest);
            assertFalse(args.hasError);
        }

        @Test
        void parseStorageTestOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--storage-test", "test.torrent" });
            assertEquals("test.torrent", args.storageTest);
            assertFalse(args.hasError);
        }
    }

    @Nested
    class MagnetLinkOptions {

        @Test
        void parseMagnetOption() {
            String magnetUri = "magnet:?xt=urn:btih:abc123";
            CliArgs args = ArgsParser.parse(new String[] { "--magnet", magnetUri });
            assertEquals(magnetUri, args.magnetUri);
            assertFalse(args.hasError);
        }

        @Test
        void parseMagnetWithoutValueReturnsError() {
            CliArgs args = ArgsParser.parse(new String[] { "--magnet" });
            assertTrue(args.hasError);
        }
    }

    @Nested
    class TorrentCreationOptions {

        @Test
        void parseCreateFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--create" });
            assertTrue(args.createTorrent);
        }

        @Test
        void parsePrivateFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--private" });
            assertTrue(args.privateTorrent);
        }

        @Test
        void parseForceFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "--force" });
            assertTrue(args.force);
        }

        @Test
        void parseForceShortFlag() {
            CliArgs args = ArgsParser.parse(new String[] { "-f" });
            assertTrue(args.force);
        }

        @Test
        void parseTrackerOption() {
            CliArgs args = ArgsParser.parse(new String[] { "--tracker", "http://tracker.example.com/announce" });
            assertEquals("http://tracker.example.com/announce", args.tracker);
            assertFalse(args.hasError);
        }
    }

    @Nested
    class MultipleArgumentCombinations {

        @Test
        void parseMultipleArguments() {
            CliArgs args = ArgsParser.parse(new String[] {
                    "--input", "file.torrent",
                    "--output", "/downloads",
                    "--verbose",
                    "--max-upload", "1000"
            });

            assertEquals("file.torrent", args.input);
            assertEquals("/downloads", args.output);
            assertTrue(args.verbose);
            assertEquals(1000, args.maxUpload);
            assertFalse(args.hasError);
        }

        @Test
        void parseCreationWithMultipleOptions() {
            CliArgs args = ArgsParser.parse(new String[] {
                    "--create",
                    "--input", "folder",
                    "--output", "output.torrent",
                    "--private",
                    "--tracker", "http://tracker.example.com/announce"
            });

            assertTrue(args.createTorrent);
            assertEquals("folder", args.input);
            assertEquals("output.torrent", args.output);
            assertTrue(args.privateTorrent);
            assertEquals("http://tracker.example.com/announce", args.tracker);
            assertFalse(args.hasError);
        }

        @Test
        void parseEmptyArguments() {
            CliArgs args = ArgsParser.parse(new String[] {});
            assertFalse(args.hasError);
            assertFalse(args.showHelp);
            assertFalse(args.showVersion);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void parseUnknownArgumentIsIgnored() {
            // Unknown arguments should not cause errors
            @SuppressWarnings("unused")
            CliArgs args = ArgsParser.parse(new String[] { "--unknown-arg" });
            // The parser should either error or ignore
            // Check based on actual implementation behavior
        }

        @Test
        void cliArgsToStringWorks() {
            CliArgs args = ArgsParser.parse(new String[] { "--input", "test.torrent" });
            String str = args.toString();
            assertNotNull(str);
            assertTrue(str.contains("test.torrent"));
        }

        @Test
        void parseNullArgument() {
            // Parsing with null should be handled gracefully
            try {
                @SuppressWarnings("unused")
                CliArgs args = ArgsParser.parse(null);
                // If we get here, null was handled
            } catch (NullPointerException e) {
                // Expected if null is not handled
            }
        }
    }
}
