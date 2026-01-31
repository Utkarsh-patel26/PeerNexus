package com.example.jtorrent.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JsonResponse {

    private JsonResponse() {
    }

    public static String success(Object data) {
        return toJson(Map.of("success", true, "data", data));
    }

    public static String error(String message) {
        return toJson(Map.of("success", false, "error", message));
    }

    public static Map<String, Object> parse(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }

        json = json.substring(1, json.length() - 1).trim();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        boolean escape = false;

        List<String> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[')
                    depth++;
                else if (c == '}' || c == ']')
                    depth--;
                else if (c == ',' && depth == 0) {
                    pairs.add(json.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        if (start < json.length()) {
            pairs.add(json.substring(start).trim());
        }

        for (String pair : pairs) {
            int colonIdx = findColon(pair);
            if (colonIdx > 0) {
                String key = pair.substring(0, colonIdx).trim();
                String value = pair.substring(colonIdx + 1).trim();
                key = unquote(key);
                result.put(key, parseValue(value));
            }
        }

        return result;
    }

    private static int findColon(String s) {
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            } else if (c == ':' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private static Object parseValue(String value) {
        if (value.equals("null"))
            return null;
        if (value.equals("true"))
            return true;
        if (value.equals("false"))
            return false;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unquote(value);
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String s) {
            return "\"" + escape(s) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first)
                    sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
