package com.example.jtorrent.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonResponse Tests")
class JsonResponseTest {

    @Test
    @DisplayName("Should build success response")
    void shouldBuildSuccessResponse() {
        String response = JsonResponse.success(Map.of("id", 1));

        assertTrue(response.contains("\"success\":true"));
        assertTrue(response.contains("\"data\""));
    }

    @Test
    @DisplayName("Should build error response")
    void shouldBuildErrorResponse() {
        String response = JsonResponse.error("boom");

        assertTrue(response.contains("\"success\":false"));
        assertTrue(response.contains("\"error\":\"boom\""));
    }

    @Test
    @DisplayName("Should parse invalid payload as empty map")
    void shouldParseInvalidPayloadAsEmptyMap() {
        assertTrue(JsonResponse.parse(null).isEmpty());
        assertTrue(JsonResponse.parse("").isEmpty());
        assertTrue(JsonResponse.parse("not-json").isEmpty());
    }

    @Test
    @DisplayName("Should parse primitive values")
    void shouldParsePrimitiveValues() {
        Map<String, Object> parsed = JsonResponse.parse("{\"a\":1,\"b\":2.5,\"c\":true,\"d\":false,\"e\":null}");

        assertEquals(1L, parsed.get("a"));
        assertEquals(2.5d, (Double) parsed.get("b"), 0.000001);
        assertEquals(true, parsed.get("c"));
        assertEquals(false, parsed.get("d"));
        assertNull(parsed.get("e"));
    }

    @Test
    @DisplayName("Should parse string values with escaped chars")
    void shouldParseStringValuesWithEscapedChars() {
        Map<String, Object> parsed = JsonResponse.parse("{\"msg\":\"line1\\nline2\\t\\\"q\\\"\\\\x\"}");

        assertEquals("line1\nline2\t\"q\"\\x", parsed.get("msg"));
    }

    @Test
    @DisplayName("Should parse nested object and array tokens as raw strings")
    void shouldParseNestedObjectAndArrayTokensAsRawStrings() {
        Map<String, Object> parsed = JsonResponse.parse("{\"obj\":{\"x\":1},\"arr\":[1,2,3]}");

        assertEquals("{\"x\":1}", parsed.get("obj"));
        assertEquals("[1,2,3]", parsed.get("arr"));
    }

    @Test
    @DisplayName("Should serialize map list and scalars")
    void shouldSerializeMapListAndScalars() {
        Map<String, Object> payload = Map.of(
                "name", "peer",
                "count", 2,
                "active", true,
                "items", List.of("a", "b"));

        String json = JsonResponse.toJson(payload);

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"name\":\"peer\""));
        assertTrue(json.contains("\"count\":2"));
        assertTrue(json.contains("\"active\":true"));
        assertTrue(json.contains("\"items\":[\"a\",\"b\"]"));
    }

    @Test
    @DisplayName("Should escape json special characters")
    void shouldEscapeJsonSpecialCharacters() {
        String json = JsonResponse.toJson("a\\b\"c\n\t");

        assertEquals("\"a\\\\b\\\"c\\n\\t\"", json);
    }

    @Test
    @DisplayName("Should serialize null as literal null")
    void shouldSerializeNullAsLiteralNull() {
        assertEquals("null", JsonResponse.toJson(null));
    }
}
