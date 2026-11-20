package com.laundry.handler;

import com.laundry.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves static frontend assets from the configured frontend directory
 * with MIME type mapping and path-traversal protection.
 */
public class StaticFileHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(StaticFileHandler.class.getName());
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put(".html", "text/html; charset=UTF-8");
        MIME_TYPES.put(".css", "text/css; charset=UTF-8");
        MIME_TYPES.put(".js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put(".json", "application/json; charset=UTF-8");
        MIME_TYPES.put(".svg", "image/svg+xml");
        MIME_TYPES.put(".png", "image/png");
        MIME_TYPES.put(".jpg", "image/jpeg");
        MIME_TYPES.put(".jpeg", "image/jpeg");
        MIME_TYPES.put(".ico", "image/x-icon");
    }

    private final Path rootDir;

    public StaticFileHandler(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handlePreflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            String rawPath = exchange.getRequestURI().getPath();
            if (rawPath.startsWith("/api/")) {
                HttpUtil.sendError(exchange, 404, "API endpoint not found");
                return;
            }

            String relative = rawPath;
            if (relative == null || relative.isEmpty() || "/".equals(relative)) {
                relative = "index.html";
            } else if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }

            if (relative.contains("..")) {
                HttpUtil.sendError(exchange, 403, "Access denied");
                return;
            }

            Path target = rootDir.resolve(relative).normalize();
            if (!target.startsWith(rootDir)) {
                HttpUtil.sendError(exchange, 403, "Access denied");
                return;
            }
            if (Files.isDirectory(target)) {
                target = target.resolve("index.html").normalize();
            }
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                HttpUtil.sendError(exchange, 404, "Resource not found");
                return;
            }

            String contentType = resolveMimeType(target.getFileName().toString());
            byte[] bytes = Files.readAllBytes(target);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, "HEAD".equals(method) ? -1 : bytes.length);
            if (!"HEAD".equals(method)) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error serving static file", e);
            HttpUtil.sendError(exchange, 500, "Failed to serve static file");
        }
    }

    private String resolveMimeType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            String ext = filename.substring(dot).toLowerCase();
            String mime = MIME_TYPES.get(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }
}
