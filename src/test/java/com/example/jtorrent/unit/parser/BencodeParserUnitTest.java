package com.example.jtorrent.unit.parser;

import com.example.jtorrent.parser.BencodeException;
import com.example.jtorrent.parser.BencodeParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for BencodeParser.
 * Tests bencoding/decoding for BitTorrent file format.
 */
class BencodeParserUnitTest {

    @Nested
    class IntegerParsing {

        @Test
        void parsePositiveInteger() throws BencodeException {
            BencodeParser parser = new BencodeParser("i42e".getBytes());
            Object result = parser.parse();
            assertEquals(42L, result);
        }

        @Test
        void parseZero() throws BencodeException {
            BencodeParser parser = new BencodeParser("i0e".getBytes());
            Object result = parser.parse();
            assertEquals(0L, result);
        }

        @Test
        void parseNegativeInteger() throws BencodeException {
            BencodeParser parser = new BencodeParser("i-42e".getBytes());
            Object result = parser.parse();
            assertEquals(-42L, result);
        }

        @Test
        void parseLargeInteger() throws BencodeException {
            BencodeParser parser = new BencodeParser("i9223372036854775807e".getBytes());
            Object result = parser.parse();
            assertEquals(Long.MAX_VALUE, result);
        }

        @Test
        void parseSmallNegativeInteger() throws BencodeException {
            BencodeParser parser = new BencodeParser("i-9223372036854775808e".getBytes());
            Object result = parser.parse();
            assertEquals(Long.MIN_VALUE, result);
        }

        @Test
        void parseNegativeZeroThrows() {
            BencodeParser parser = new BencodeParser("i-0e".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseLeadingZeroThrows() {
            BencodeParser parser = new BencodeParser("i03e".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseEmptyIntegerThrows() {
            BencodeParser parser = new BencodeParser("ie".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseUnterminatedIntegerThrows() {
            BencodeParser parser = new BencodeParser("i42".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }
    }

    @Nested
    class StringParsing {

        @Test
        void parseEmptyString() throws BencodeException {
            BencodeParser parser = new BencodeParser("0:".getBytes());
            Object result = parser.parse();
            assertArrayEquals(new byte[0], (byte[]) result);
        }

        @Test
        void parseSimpleString() throws BencodeException {
            BencodeParser parser = new BencodeParser("4:spam".getBytes());
            Object result = parser.parse();
            assertArrayEquals("spam".getBytes(), (byte[]) result);
        }

        @Test
        void parseLongerString() throws BencodeException {
            BencodeParser parser = new BencodeParser("11:hello world".getBytes());
            Object result = parser.parse();
            assertArrayEquals("hello world".getBytes(), (byte[]) result);
        }

        @Test
        void parseBinaryString() throws BencodeException {
            byte[] binary = new byte[] { 0x00, 0x01, 0x02, (byte) 0xFF };
            byte[] input = ("4:" + new String(binary, StandardCharsets.ISO_8859_1))
                    .getBytes(StandardCharsets.ISO_8859_1);
            BencodeParser parser = new BencodeParser(input);
            Object result = parser.parse();
            assertArrayEquals(binary, (byte[]) result);
        }

        @Test
        void parseStringWithInvalidLengthThrows() {
            BencodeParser parser = new BencodeParser("x:test".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseStringLengthExceedsDataThrows() {
            BencodeParser parser = new BencodeParser("100:test".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }
    }

    @Nested
    class ListParsing {

        @Test
        void parseEmptyList() throws BencodeException {
            BencodeParser parser = new BencodeParser("le".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof List);
            assertTrue(((List<?>) result).isEmpty());
        }

        @Test
        void parseListOfIntegers() throws BencodeException {
            BencodeParser parser = new BencodeParser("li1ei2ei3ee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof List);
            List<?> list = (List<?>) result;
            assertEquals(3, list.size());
            assertEquals(1L, list.get(0));
            assertEquals(2L, list.get(1));
            assertEquals(3L, list.get(2));
        }

        @Test
        void parseListOfStrings() throws BencodeException {
            BencodeParser parser = new BencodeParser("l4:spam4:eggse".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof List);
            List<?> list = (List<?>) result;
            assertEquals(2, list.size());
            assertArrayEquals("spam".getBytes(), (byte[]) list.get(0));
            assertArrayEquals("eggs".getBytes(), (byte[]) list.get(1));
        }

        @Test
        void parseMixedList() throws BencodeException {
            BencodeParser parser = new BencodeParser("l4:spami42ee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof List);
            List<?> list = (List<?>) result;
            assertEquals(2, list.size());
            assertArrayEquals("spam".getBytes(), (byte[]) list.get(0));
            assertEquals(42L, list.get(1));
        }

        @Test
        void parseNestedList() throws BencodeException {
            BencodeParser parser = new BencodeParser("lli1ei2eeli3ei4eee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof List);
            List<?> outer = (List<?>) result;
            assertEquals(2, outer.size());

            List<?> inner1 = (List<?>) outer.get(0);
            assertEquals(2, inner1.size());
            assertEquals(1L, inner1.get(0));
            assertEquals(2L, inner1.get(1));

            List<?> inner2 = (List<?>) outer.get(1);
            assertEquals(2, inner2.size());
            assertEquals(3L, inner2.get(0));
            assertEquals(4L, inner2.get(1));
        }

        @Test
        void parseUnterminatedListThrows() {
            BencodeParser parser = new BencodeParser("li1ei2e".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }
    }

    @Nested
    class DictionaryParsing {

        @Test
        void parseEmptyDictionary() throws BencodeException {
            BencodeParser parser = new BencodeParser("de".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        void parseSimpleDictionary() throws BencodeException {
            BencodeParser parser = new BencodeParser("d3:cow3:moo4:spam4:eggse".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(2, map.size());
            assertArrayEquals("moo".getBytes(), (byte[]) map.get("cow"));
            assertArrayEquals("eggs".getBytes(), (byte[]) map.get("spam"));
        }

        @Test
        void parseDictionaryWithIntegerValue() throws BencodeException {
            BencodeParser parser = new BencodeParser("d3:agei25ee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(25L, map.get("age"));
        }

        @Test
        void parseDictionaryWithListValue() throws BencodeException {
            BencodeParser parser = new BencodeParser("d4:listli1ei2ei3eee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            List<?> list = (List<?>) map.get("list");
            assertEquals(3, list.size());
        }

        @Test
        void parseNestedDictionary() throws BencodeException {
            BencodeParser parser = new BencodeParser("d4:infod4:name4:testee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> outer = (Map<String, Object>) result;
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) outer.get("info");
            assertArrayEquals("test".getBytes(), (byte[]) inner.get("name"));
        }

        @Test
        void parseUnsortedKeysThrows() {
            // Keys must be sorted in bencoding
            BencodeParser parser = new BencodeParser("d4:spam3:eggse".getBytes());
            // "spam" comes after "eggs" alphabetically, but is listed first
            // This violates canonical bencoding
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseDuplicateKeysThrows() {
            BencodeParser parser = new BencodeParser("d3:key1:a3:key1:be".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }
    }

    @Nested
    class EncodingTests {

        @Test
        void encodeInteger() throws BencodeException {
            byte[] result = BencodeParser.encode(42L);
            assertEquals("i42e", new String(result));
        }

        @Test
        void encodeNegativeInteger() throws BencodeException {
            byte[] result = BencodeParser.encode(-42L);
            assertEquals("i-42e", new String(result));
        }

        @Test
        void encodeZero() throws BencodeException {
            byte[] result = BencodeParser.encode(0L);
            assertEquals("i0e", new String(result));
        }

        @Test
        void encodeString() throws BencodeException {
            byte[] result = BencodeParser.encode("spam");
            assertEquals("4:spam", new String(result));
        }

        @Test
        void encodeEmptyString() throws BencodeException {
            byte[] result = BencodeParser.encode("");
            assertEquals("0:", new String(result));
        }

        @Test
        void encodeByteArray() throws BencodeException {
            byte[] result = BencodeParser.encode("test".getBytes());
            assertEquals("4:test", new String(result));
        }

        @Test
        void encodeEmptyList() throws BencodeException {
            byte[] result = BencodeParser.encode(List.of());
            assertEquals("le", new String(result));
        }

        @Test
        void encodeListOfIntegers() throws BencodeException {
            byte[] result = BencodeParser.encode(List.of(1L, 2L, 3L));
            assertEquals("li1ei2ei3ee", new String(result));
        }

        @Test
        void encodeEmptyMap() throws BencodeException {
            byte[] result = BencodeParser.encode(Map.of());
            assertEquals("de", new String(result));
        }

        @Test
        void encodeMapSortsKeys() throws BencodeException {
            // Using LinkedHashMap to preserve insertion order for the test
            Map<String, Object> map = Map.of("b", 1L, "a", 2L);
            byte[] result = BencodeParser.encode(map);
            // Keys should be sorted: a before b
            assertTrue(new String(result).startsWith("d1:a"));
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void roundTripInteger() throws BencodeException {
            Long original = 12345L;
            byte[] encoded = BencodeParser.encode(original);
            BencodeParser parser = new BencodeParser(encoded);
            Object decoded = parser.parse();
            assertEquals(original, decoded);
        }

        @Test
        void roundTripString() throws BencodeException {
            byte[] original = "hello world".getBytes();
            byte[] encoded = BencodeParser.encode(original);
            BencodeParser parser = new BencodeParser(encoded);
            Object decoded = parser.parse();
            assertArrayEquals(original, (byte[]) decoded);
        }

        @Test
        void roundTripList() throws BencodeException {
            List<Object> original = List.of(1L, "test".getBytes(), 3L);
            byte[] encoded = BencodeParser.encode(original);
            BencodeParser parser = new BencodeParser(encoded);
            Object decoded = parser.parse();
            assertTrue(decoded instanceof List);
            List<?> decodedList = (List<?>) decoded;
            assertEquals(3, decodedList.size());
        }

        @Test
        void roundTripComplexStructure() throws BencodeException {
            Map<String, Object> original = Map.of(
                    "announce", "http://tracker.example.com".getBytes(),
                    "info", Map.of(
                            "name", "test.txt".getBytes(),
                            "length", 1024L));
            byte[] encoded = BencodeParser.encode(original);
            BencodeParser parser = new BencodeParser(encoded);
            Object decoded = parser.parse();
            assertTrue(decoded instanceof Map);
        }
    }

    @Nested
    class PositionTrackingTests {

        @Test
        void getPositionAfterParsing() throws BencodeException {
            BencodeParser parser = new BencodeParser("i42e5:hello".getBytes());
            parser.parse();
            assertEquals(4, parser.getPosition()); // After "i42e"
        }

        @Test
        void getPositionAtStart() {
            BencodeParser parser = new BencodeParser("i42e".getBytes());
            assertEquals(0, parser.getPosition());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void parseEmptyDataThrows() {
            BencodeParser parser = new BencodeParser(new byte[0]);
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseInvalidMarkerThrows() {
            BencodeParser parser = new BencodeParser("x".getBytes());
            assertThrows(BencodeException.class, parser::parse);
        }

        @Test
        void parseIntegerAsIntObject() throws BencodeException {
            // Test encoding with Integer instead of Long
            byte[] result = BencodeParser.encode(42);
            assertEquals("i42e", new String(result));
        }

        @Test
        void encodingUnsupportedTypeThrows() {
            assertThrows(BencodeException.class, () -> BencodeParser.encode(3.14));
        }
    }

    @Nested
    class TorrentFileStructureTests {

        @Test
        void parseDictionaryWithIntValue() throws BencodeException {
            // Simple dictionary test
            BencodeParser parser = new BencodeParser("d3:agei25ee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(25L, map.get("age"));
        }

        @Test
        void parseNestedDictionaries() throws BencodeException {
            // Nested dictionary: {info: {name: "test"}}
            // Keys sorted: i < n in inner, no other keys in outer
            BencodeParser parser = new BencodeParser("d4:infod4:name4:testee".getBytes());
            Object result = parser.parse();
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> outer = (Map<String, Object>) result;
            assertTrue(outer.containsKey("info"));
        }
    }
}
