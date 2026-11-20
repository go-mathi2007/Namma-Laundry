package com.laundry.util;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zero-dependency recursive-descent JSON parser and serializer.
 * Parses JSON text into Map&lt;String, Object&gt;, List&lt;Object&gt;, String,
 * Double, Boolean, or null, and serializes Java objects to valid JSON.
 */
public final class JsonUtil {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private JsonUtil() {
    }

    /**
     * Parses a JSON document string into Java structures.
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON input cannot be null");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Unexpected trailing character at index " + parser.pos);
        }
        return value;
    }

    /**
     * Parses a JSON object into a LinkedHashMap.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (!(result instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return (Map<String, Object>) result;
    }

    /**
     * Parses a JSON array into a List.
     */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object result = parse(json);
        if (!(result instanceof List)) {
            throw new IllegalArgumentException("Expected a JSON array");
        }
        return (List<Object>) result;
    }

    /**
     * Serializes any supported Java value (Map, Collection, Array, String, Number,
     * Boolean, Enum, Timestamp, Instant, null) into a valid JSON string.
     */
    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder(256);
        appendJson(sb, value);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean) {
            sb.append((Boolean) value ? "true" : "false");
        } else if (value instanceof BigDecimal) {
            sb.append(((BigDecimal) value).toPlainString());
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(value.toString());
            }
        } else if (value instanceof String) {
            appendEscapedString(sb, (String) value);
        } else if (value instanceof Character) {
            appendEscapedString(sb, String.valueOf(value));
        } else if (value instanceof Enum<?>) {
            appendEscapedString(sb, ((Enum<?>) value).name());
        } else if (value instanceof Timestamp) {
            Instant instant = ((Timestamp) value).toInstant();
            appendEscapedString(sb, ISO_FORMATTER.format(instant));
        } else if (value instanceof Instant) {
            appendEscapedString(sb, ISO_FORMATTER.format((Instant) value));
        } else if (value instanceof LocalDateTime) {
            appendEscapedString(sb, ((LocalDateTime) value).atOffset(ZoneOffset.UTC).toString());
        } else if (value instanceof Map<?, ?>) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendEscapedString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                appendJson(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection<?>) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Collection<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendJson(sb, item);
            }
            sb.append(']');
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendJson(sb, Array.get(value, i));
            }
            sb.append(']');
        } else {
            appendEscapedString(sb, value.toString());
        }
    }

    private static void appendEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    /**
     * Recursive-descent JSON parser.
     */
    private static final class Parser {
        private final String input;
        private final int length;
        private int pos = 0;

        Parser(String input) {
            this.input = input;
            this.length = input.length();
        }

        boolean isAtEnd() {
            return pos >= length;
        }

        void skipWhitespace() {
            while (pos < length) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON input at index " + pos);
            }
            char c = input.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    return parseLiteral("true", Boolean.TRUE);
                case 'f':
                    return parseLiteral("false", Boolean.FALSE);
                case 'n':
                    return parseLiteral("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at index " + pos);
            }
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!isAtEnd() && input.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || input.charAt(pos) != '"') {
                    throw new IllegalArgumentException("Expected string key in object at index " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON object");
                }
                char next = input.charAt(pos);
                if (next == '}') {
                    pos++;
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at index " + pos);
                }
                pos++;
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (!isAtEnd() && input.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated JSON array");
                }
                char next = input.charAt(pos);
                if (next == ']') {
                    pos++;
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at index " + pos);
                }
                pos++;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= length) {
                        throw new IllegalArgumentException("Unterminated escape sequence in string");
                    }
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > length) {
                                throw new IllegalArgumentException("Invalid unicode escape at index " + pos);
                            }
                            String hex = input.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Invalid unicode hex '" + hex + "'", e);
                            }
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape '\\" + esc + "' at index " + (pos - 1));
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("Unescaped control character in string at index " + (pos - 1));
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Double parseNumber() {
            int start = pos;
            if (input.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= length) {
                throw new IllegalArgumentException("Invalid number at index " + start);
            }
            if (input.charAt(pos) == '0') {
                pos++;
            } else if (input.charAt(pos) >= '1' && input.charAt(pos) <= '9') {
                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            } else {
                throw new IllegalArgumentException("Invalid number at index " + start);
            }

            if (pos < length && input.charAt(pos) == '.') {
                pos++;
                if (pos >= length || !Character.isDigit(input.charAt(pos))) {
                    throw new IllegalArgumentException("Expected digit after decimal point at index " + pos);
                }
                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            if (pos < length && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                pos++;
                if (pos < length && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= length || !Character.isDigit(input.charAt(pos))) {
                    throw new IllegalArgumentException("Expected exponent digit at index " + pos);
                }
                while (pos < length && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            String numStr = input.substring(start, pos);
            try {
                return Double.valueOf(numStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed number '" + numStr + "'", e);
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (pos + literal.length() > length || !input.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Invalid literal at index " + pos);
            }
            pos += literal.length();
            return value;
        }

        private void expect(char expected) {
            if (pos >= length || input.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + pos);
            }
            pos++;
        }
    }
}
