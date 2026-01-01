package com.example.jtorrent.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BencodeParser Comprehensive Tests")
class BencodeParserTest {

    // ==================== Integer Parsing Tests ====================

    @Test
    @DisplayName("parseInt_shouldParsePositiveInteger_whenValidInput")
    void parseInt_shouldParsePositiveInteger_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("i42e".getBytes());
        Object result = parser.parse();
        assertEquals(42L, result);
    }

    @Test
    @DisplayName("parseInt_shouldParseZero_whenValidInput")
    void parseInt_shouldParseZero_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("i0e".getBytes());
        Object result = parser.parse();
        assertEquals(0L, result);
    }

    @Test
    @DisplayName("parseInt_shouldParseNegativeInteger_whenValidInput")
    void parseInt_shouldParseNegativeInteger_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("i-42e".getBytes());
        Object result = parser.parse();
        assertEquals(-42L, result);
    }

    @Test
    @DisplayName("parseInt_shouldParseLargeInteger_whenValidInput")
    void parseInt_shouldParseLargeInteger_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("i9223372036854775807e".getBytes());
        Object result = parser.parse();
        assertEquals(9223372036854775807L, result);
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenEmptyInteger")
    void parseInt_shouldThrowException_whenEmptyInteger() {
        BencodeParser parser = new BencodeParser("ie".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Empty integer"));
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenNegativeZero")
    void parseInt_shouldThrowException_whenNegativeZero() {
        BencodeParser parser = new BencodeParser("i-0e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Invalid integer format '-0'"));
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenLeadingZeros")
    void parseInt_shouldThrowException_whenLeadingZeros() {
        BencodeParser parser = new BencodeParser("i042e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Leading zeros"));
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenLeadingZerosInNegative")
    void parseInt_shouldThrowException_whenLeadingZerosInNegative() {
        BencodeParser parser = new BencodeParser("i-042e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Leading zeros in negative integer"));
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenUnterminatedInteger")
    void parseInt_shouldThrowException_whenUnterminatedInteger() {
        BencodeParser parser = new BencodeParser("i42".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Unterminated integer"));
    }

    @Test
    @DisplayName("parseInt_shouldThrowException_whenInvalidCharacters")
    void parseInt_shouldThrowException_whenInvalidCharacters() {
        BencodeParser parser = new BencodeParser("i4a2e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Invalid integer"));
    }

    // ==================== Byte String Parsing Tests ====================

    @Test
    @DisplayName("parseByteString_shouldParseSimpleString_whenValidInput")
    void parseByteString_shouldParseSimpleString_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("4:spam".getBytes());
        Object result = parser.parse();
        assertArrayEquals("spam".getBytes(), (byte[]) result);
    }

    @Test
    @DisplayName("parseByteString_shouldParseEmptyString_whenValidInput")
    void parseByteString_shouldParseEmptyString_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("0:".getBytes());
        Object result = parser.parse();
        assertArrayEquals(new byte[0], (byte[]) result);
    }

    @Test
    @DisplayName("parseByteString_shouldParseLongString_whenValidInput")
    void parseByteString_shouldParseLongString_whenValidInput() throws BencodeException {
        String longString = "a".repeat(1000);
        BencodeParser parser = new BencodeParser(("1000:" + longString).getBytes());
        Object result = parser.parse();
        assertArrayEquals(longString.getBytes(), (byte[]) result);
    }

    @Test
    @DisplayName("parseByteString_shouldParseBinaryData_whenValidInput")
    void parseByteString_shouldParseBinaryData_whenValidInput() throws BencodeException {
        byte[] binaryData = new byte[] { 0x00, 0x01, 0x02, (byte) 0xFF };
        byte[] input = "4:".getBytes();
        byte[] combined = new byte[input.length + binaryData.length];
        System.arraycopy(input, 0, combined, 0, input.length);
        System.arraycopy(binaryData, 0, combined, input.length, binaryData.length);

        BencodeParser parser = new BencodeParser(combined);
        Object result = parser.parse();
        assertArrayEquals(binaryData, (byte[]) result);
    }

    @Test
    @DisplayName("parseByteString_shouldThrowException_whenLengthTooLarge")
    void parseByteString_shouldThrowException_whenLengthTooLarge() {
        BencodeParser parser = new BencodeParser("100:short".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("exceeds available data"));
    }

    @Test
    @DisplayName("parseByteString_shouldThrowException_whenEmptyLength")
    void parseByteString_shouldThrowException_whenEmptyLength() {
        BencodeParser parser = new BencodeParser(":test".getBytes());
        // This might parse as empty key in dictionary context, or throw different error
        assertThrows(BencodeException.class, parser::parse);
    }

    @Test
    @DisplayName("parseByteString_shouldThrowException_whenInvalidLengthCharacters")
    void parseByteString_shouldThrowException_whenInvalidLengthCharacters() {
        BencodeParser parser = new BencodeParser("4a:test".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Invalid string length"));
    }

    @Test
    @DisplayName("parseByteString_shouldThrowException_whenUnterminatedLength")
    void parseByteString_shouldThrowException_whenUnterminatedLength() {
        BencodeParser parser = new BencodeParser("4".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Unterminated string length"));
    }

    // ==================== List Parsing Tests ====================

    @Test
    @DisplayName("parseList_shouldParseEmptyList_whenValidInput")
    void parseList_shouldParseEmptyList_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("le".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof List);
        assertEquals(0, ((List<?>) result).size());
    }

    @Test
    @DisplayName("parseList_shouldParseListWithIntegers_whenValidInput")
    void parseList_shouldParseListWithIntegers_whenValidInput() throws BencodeException {
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
    @DisplayName("parseList_shouldParseListWithStrings_whenValidInput")
    void parseList_shouldParseListWithStrings_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("l4:spam4:eggse".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof List);
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(), (byte[]) list.get(0));
        assertArrayEquals("eggs".getBytes(), (byte[]) list.get(1));
    }

    @Test
    @DisplayName("parseList_shouldParseListWithMixedTypes_whenValidInput")
    void parseList_shouldParseListWithMixedTypes_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("li42e4:testlee".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof List);
        List<?> list = (List<?>) result;
        assertEquals(3, list.size());
        assertEquals(42L, list.get(0));
        assertArrayEquals("test".getBytes(), (byte[]) list.get(1));
        assertTrue(list.get(2) instanceof List);
        assertEquals(0, ((List<?>) list.get(2)).size());
    }

    @Test
    @DisplayName("parseList_shouldParseNestedLists_whenValidInput")
    void parseList_shouldParseNestedLists_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("lli1eeli2eee".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof List);
        List<?> outer = (List<?>) result;
        assertEquals(2, outer.size());
        List<?> inner1 = (List<?>) outer.get(0);
        List<?> inner2 = (List<?>) outer.get(1);
        assertEquals(1, inner1.size());
        assertEquals(1L, inner1.get(0));
        assertEquals(1, inner2.size());
        assertEquals(2L, inner2.get(0));
    }

    @Test
    @DisplayName("parseList_shouldThrowException_whenUnterminated")
    void parseList_shouldThrowException_whenUnterminated() {
        BencodeParser parser = new BencodeParser("li1ei2e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Unterminated list"));
    }

    // ==================== Dictionary Parsing Tests ====================

    @Test
    @DisplayName("parseDictionary_shouldParseEmptyDictionary_whenValidInput")
    void parseDictionary_shouldParseEmptyDictionary_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("de".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof Map);
        assertEquals(0, ((Map<?, ?>) result).size());
    }

    @Test
    @DisplayName("parseDictionary_shouldParseSimpleDictionary_whenValidInput")
    void parseDictionary_shouldParseSimpleDictionary_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("d3:bar4:spam3:fooi42ee".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof Map);
        Map<?, ?> dict = (Map<?, ?>) result;
        assertEquals(2, dict.size());
        assertArrayEquals("spam".getBytes(), (byte[]) dict.get("bar"));
        assertEquals(42L, dict.get("foo"));
    }

    @Test
    @DisplayName("parseDictionary_shouldParseNestedDictionary_whenValidInput")
    void parseDictionary_shouldParseNestedDictionary_whenValidInput() throws BencodeException {
        BencodeParser parser = new BencodeParser("d5:innerd3:keyi1eee".getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof Map);
        Map<?, ?> outer = (Map<?, ?>) result;
        assertEquals(1, outer.size());
        assertTrue(outer.get("inner") instanceof Map);
        Map<?, ?> inner = (Map<?, ?>) outer.get("inner");
        assertEquals(1L, inner.get("key"));
    }

    @Test
    @DisplayName("parseDictionary_shouldThrowException_whenKeysNotSorted")
    void parseDictionary_shouldThrowException_whenKeysNotSorted() {
        BencodeParser parser = new BencodeParser("d3:fooi1e3:bari2ee".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("keys not sorted"));
    }

    @Test
    @DisplayName("parseDictionary_shouldThrowException_whenDuplicateKeys")
    void parseDictionary_shouldThrowException_whenDuplicateKeys() {
        BencodeParser parser = new BencodeParser("d3:fooi1e3:fooi2ee".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("keys not sorted"));
    }

    @Test
    @DisplayName("parseDictionary_shouldThrowException_whenKeyNotString")
    void parseDictionary_shouldThrowException_whenKeyNotString() {
        BencodeParser parser = new BencodeParser("di1ei2ee".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("key must be a string"));
    }

    @Test
    @DisplayName("parseDictionary_shouldThrowException_whenUnterminated")
    void parseDictionary_shouldThrowException_whenUnterminated() {
        BencodeParser parser = new BencodeParser("d3:fooi1e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Unterminated dictionary"));
    }

    // ==================== Invalid Marker Tests ====================

    @Test
    @DisplayName("parse_shouldThrowException_whenInvalidMarker")
    void parse_shouldThrowException_whenInvalidMarker() {
        BencodeParser parser = new BencodeParser("x".getBytes());
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Invalid bencode marker"));
    }

    @Test
    @DisplayName("parse_shouldThrowException_whenEmptyData")
    void parse_shouldThrowException_whenEmptyData() {
        BencodeParser parser = new BencodeParser(new byte[0]);
        BencodeException exception = assertThrows(BencodeException.class, parser::parse);
        assertTrue(exception.getMessage().contains("Unexpected end of data"));
    }

    // ==================== Position Tests ====================

    @Test
    @DisplayName("getPosition_shouldReturnCorrectPosition_afterParsing")
    void getPosition_shouldReturnCorrectPosition_afterParsing() throws BencodeException {
        BencodeParser parser = new BencodeParser("i42e".getBytes());
        parser.parse();
        assertEquals(4, parser.getPosition());
    }

    @Test
    @DisplayName("getPosition_shouldReturnZero_beforeParsing")
    void getPosition_shouldReturnZero_beforeParsing() {
        BencodeParser parser = new BencodeParser("i42e".getBytes());
        assertEquals(0, parser.getPosition());
    }

    // ==================== findDictionaryValueRange Tests ====================

    @Test
    @DisplayName("findDictionaryValueRange_shouldFindValue_whenKeyExists")
    void findDictionaryValueRange_shouldFindValue_whenKeyExists() throws BencodeException {
        BencodeParser parser = new BencodeParser("d3:bar4:spam3:fooi42ee".getBytes());
        int[] range = parser.findDictionaryValueRange("foo");
        assertNotNull(range);
        assertEquals(2, range.length);
        assertTrue(range[1] > range[0]);
    }

    @Test
    @DisplayName("findDictionaryValueRange_shouldReturnNull_whenKeyNotFound")
    void findDictionaryValueRange_shouldReturnNull_whenKeyNotFound() throws BencodeException {
        BencodeParser parser = new BencodeParser("d3:fooi1ee".getBytes());
        int[] range = parser.findDictionaryValueRange("bar");
        assertNull(range);
    }

    @Test
    @DisplayName("findDictionaryValueRange_shouldThrowException_whenNotDictionary")
    void findDictionaryValueRange_shouldThrowException_whenNotDictionary() {
        BencodeParser parser = new BencodeParser("i42e".getBytes());
        BencodeException exception = assertThrows(BencodeException.class,
                () -> parser.findDictionaryValueRange("key"));
        assertTrue(exception.getMessage().contains("Expected dictionary"));
    }

    // ==================== Encoding Tests ====================

    @Test
    @DisplayName("encode_shouldEncodeInteger_whenValidInput")
    void encode_shouldEncodeInteger_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode(42L);
        assertArrayEquals("i42e".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeIntegerType_whenValidInput")
    void encode_shouldEncodeIntegerType_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode(Integer.valueOf(42));
        assertArrayEquals("i42e".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeByteArray_whenValidInput")
    void encode_shouldEncodeByteArray_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode("spam".getBytes());
        assertArrayEquals("4:spam".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeString_whenValidInput")
    void encode_shouldEncodeString_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode("spam");
        assertArrayEquals("4:spam".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeEmptyList_whenValidInput")
    void encode_shouldEncodeEmptyList_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode(new ArrayList<>());
        assertArrayEquals("le".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeList_whenValidInput")
    void encode_shouldEncodeList_whenValidInput() throws BencodeException {
        List<Object> list = Arrays.asList(1L, "spam");
        byte[] result = BencodeParser.encode(list);
        assertArrayEquals("li1e4:spame".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeEmptyDictionary_whenValidInput")
    void encode_shouldEncodeEmptyDictionary_whenValidInput() throws BencodeException {
        byte[] result = BencodeParser.encode(new LinkedHashMap<>());
        assertArrayEquals("de".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldEncodeDictionary_whenValidInput")
    void encode_shouldEncodeDictionary_whenValidInput() throws BencodeException {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("bar", "spam");
        dict.put("foo", 42L);
        byte[] result = BencodeParser.encode(dict);
        assertArrayEquals("d3:bar4:spam3:fooi42ee".getBytes(), result);
    }

    @Test
    @DisplayName("encode_shouldSortKeys_whenEncodingDictionary")
    void encode_shouldSortKeys_whenEncodingDictionary() throws BencodeException {
        Map<String, Object> dict = new LinkedHashMap<>();
        dict.put("z", 1L);
        dict.put("a", 2L);
        byte[] result = BencodeParser.encode(dict);
        // Keys should be sorted: a before z
        String encoded = new String(result);
        assertTrue(encoded.indexOf("a") < encoded.indexOf("z"));
    }

    @Test
    @DisplayName("encode_shouldThrowException_whenInvalidType")
    void encode_shouldThrowException_whenInvalidType() {
        BencodeException exception = assertThrows(BencodeException.class,
                () -> BencodeParser.encode(new Object()));
        assertTrue(exception.getMessage().contains("Cannot encode object of type"));
    }

    // ==================== Round-trip Tests ====================

    @ParameterizedTest
    @DisplayName("roundTrip_shouldPreserveData_forVariousInputs")
    @MethodSource("provideRoundTripData")
    void roundTrip_shouldPreserveData_forVariousInputs(Object data) throws BencodeException {
        byte[] encoded = BencodeParser.encode(data);
        BencodeParser parser = new BencodeParser(encoded);
        Object decoded = parser.parse();

        if (data instanceof byte[]) {
            assertArrayEquals((byte[]) data, (byte[]) decoded);
        } else if (data instanceof List) {
            assertEquals(data, decoded);
        } else if (data instanceof Map) {
            assertEquals(data, decoded);
        } else {
            assertEquals(data, decoded);
        }
    }

    private static Stream<Arguments> provideRoundTripData() {
        return Stream.of(
                Arguments.of(42L),
                Arguments.of(0L),
                Arguments.of(-42L),
                Arguments.of("test".getBytes()),
                Arguments.of(Arrays.asList(1L, 2L, 3L)),
                Arguments.of(new LinkedHashMap<String, Object>() {
                    {
                        put("key", 42L);
                    }
                }));
    }

    // ==================== Complex Scenario Tests ====================

    @Test
    @DisplayName("parse_shouldHandleComplexNestedStructure_whenValidInput")
    void parse_shouldHandleComplexNestedStructure_whenValidInput() throws BencodeException {
        // d4:listli1ei2ee4:dictd3:keyi3eee
        String encoded = "d4:dictd3:keyi3ee4:listli1ei2eee";
        BencodeParser parser = new BencodeParser(encoded.getBytes());
        Object result = parser.parse();

        assertTrue(result instanceof Map);
        Map<?, ?> dict = (Map<?, ?>) result;
        assertEquals(2, dict.size());
        assertTrue(dict.containsKey("dict"));
        assertTrue(dict.containsKey("list"));

        Map<?, ?> innerDict = (Map<?, ?>) dict.get("dict");
        assertEquals(3L, innerDict.get("key"));

        List<?> list = (List<?>) dict.get("list");
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0));
        assertEquals(2L, list.get(1));
    }

    @Test
    @DisplayName("parse_shouldHandleUTF8Strings_whenValidInput")
    void parse_shouldHandleUTF8Strings_whenValidInput() throws BencodeException {
        String utf8 = "こんにちは"; // Japanese "Hello"
        byte[] utf8Bytes = utf8.getBytes(StandardCharsets.UTF_8);
        String bencode = utf8Bytes.length + ":";
        byte[] bencodeBytes = bencode.getBytes(StandardCharsets.US_ASCII);

        byte[] combined = new byte[bencodeBytes.length + utf8Bytes.length];
        System.arraycopy(bencodeBytes, 0, combined, 0, bencodeBytes.length);
        System.arraycopy(utf8Bytes, 0, combined, bencodeBytes.length, utf8Bytes.length);

        BencodeParser parser = new BencodeParser(combined);
        Object result = parser.parse();
        assertArrayEquals(utf8Bytes, (byte[]) result);
    }

    @Test
    @DisplayName("parse_shouldHandleLongDictionary_whenValidInput")
    void parse_shouldHandleLongDictionary_whenValidInput() throws BencodeException {
        StringBuilder sb = new StringBuilder("d");
        for (int i = 0; i < 100; i++) {
            String key = String.format("key%03d", i);
            sb.append(key.length()).append(":").append(key).append("i").append(i).append("e");
        }
        sb.append("e");

        BencodeParser parser = new BencodeParser(sb.toString().getBytes());
        Object result = parser.parse();
        assertTrue(result instanceof Map);
        Map<?, ?> dict = (Map<?, ?>) result;
        assertEquals(100, dict.size());

        for (int i = 0; i < 100; i++) {
            String key = String.format("key%03d", i);
            assertEquals((long) i, dict.get(key));
        }
    }

    @Test
    @DisplayName("parse_shouldHandleDeepNesting_whenValidInput")
    void parse_shouldHandleDeepNesting_whenValidInput() throws BencodeException {
        // Create deeply nested list: [[[[42]]]]
        StringBuilder sb = new StringBuilder();
        int depth = 10;
        for (int i = 0; i < depth; i++) {
            sb.append("l");
        }
        sb.append("i42e");
        for (int i = 0; i < depth; i++) {
            sb.append("e");
        }

        BencodeParser parser = new BencodeParser(sb.toString().getBytes());
        Object result = parser.parse();

        assertTrue(result instanceof List);
        Object current = result;
        for (int i = 0; i < depth - 1; i++) {
            assertTrue(current instanceof List);
            List<?> list = (List<?>) current;
            assertEquals(1, list.size());
            current = list.get(0);
        }
        assertTrue(current instanceof List);
        assertEquals(42L, ((List<?>) current).get(0));
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("parse_shouldHandleMaxLongValue_whenValidInput")
    void parse_shouldHandleMaxLongValue_whenValidInput() throws BencodeException {
        long maxLong = Long.MAX_VALUE;
        BencodeParser parser = new BencodeParser(("i" + maxLong + "e").getBytes());
        Object result = parser.parse();
        assertEquals(maxLong, result);
    }

    @Test
    @DisplayName("parse_shouldHandleMinLongValue_whenValidInput")
    void parse_shouldHandleMinLongValue_whenValidInput() throws BencodeException {
        long minLong = Long.MIN_VALUE;
        BencodeParser parser = new BencodeParser(("i" + minLong + "e").getBytes());
        Object result = parser.parse();
        assertEquals(minLong, result);
    }

    @Test
    @DisplayName("parse_shouldThrowException_whenValueOverflowsLong")
    void parse_shouldThrowException_whenValueOverflowsLong() {
        // Number larger than Long.MAX_VALUE
        String bigNumber = "9223372036854775808"; // Long.MAX_VALUE + 1
        BencodeParser parser = new BencodeParser(("i" + bigNumber + "e").getBytes());
        assertThrows(BencodeException.class, parser::parse);
    }
}
