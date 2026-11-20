package com.laundry.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP utility methods for CORS headers, request body reading, query parameter
 * parsing, path segment extraction, JSON response formatting, and API exceptions.
 */
public final class HttpUtil {

    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB safety limit

    private HttpUtil() {
    }

    /**
     * Domain exception carrying an HTTP status code so handlers can emit clean JSON errors.
     */
    public static class ApiException extends RuntimeException {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    public static void applyCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    public static boolean handlePreflight(HttpExchange exchange) throws IOException {
        applyCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new ApiException(400, "Request body exceeds maximum allowed size");
                }
                baos.write(buffer, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        if (body == null || body.isBlank()) {
            throw new ApiException(400, "Request body is required");
        }
        try {
            return JsonUtil.parseObject(body);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Invalid JSON payload: " + e.getMessage());
        }
    }

    public static void sendSuccess(HttpExchange exchange, int statusCode, Object data) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", Boolean.TRUE);
        envelope.put("data", data);
        sendJson(exchange, statusCode, envelope);
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", Boolean.FALSE);
        envelope.put("error", message != null ? message : "Unexpected error");
        sendJson(exchange, statusCode, envelope);
    }

    public static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        applyCorsHeaders(exchange);
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            params.put(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return params;
    }

    public static long parsePositiveLong(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(400, fieldName + " is required");
        }
        try {
            long val = Long.parseLong(raw.trim());
            if (val <= 0) {
                throw new ApiException(400, fieldName + " must be a positive integer");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new ApiException(400, fieldName + " must be a valid integer");
        }
    }

    public static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        return String.valueOf(val).trim();
    }

    public static String requireNonBlank(Map<String, Object> map, String key, String label) {
        String val = getString(map, key);
        if (val == null || val.isEmpty()) {
            throw new ApiException(400, label + " is required");
        }
        return val;
    }
}
