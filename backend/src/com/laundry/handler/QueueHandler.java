package com.laundry.handler;

import com.laundry.service.QueueService;
import com.laundry.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST handler for /api/queue endpoints.
 */
public class QueueHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(QueueHandler.class.getName());
    private final QueueService queueService;

    public QueueHandler(QueueService queueService) {
        this.queueService = queueService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handlePreflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();
            String suffix = path.length() > "/api/queue".length()
                    ? path.substring("/api/queue".length())
                    : "";

            if (suffix.isEmpty() || "/".equals(suffix)) {
                if ("GET".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, queueService.getQueueSnapshot());
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            if ("/process-next".equals(suffix) || "/process-next/".equals(suffix)) {
                if ("POST".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, queueService.processNext());
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            if (suffix.startsWith("/advance/")) {
                String idPart = suffix.substring("/advance/".length());
                if (idPart.endsWith("/")) {
                    idPart = idPart.substring(0, idPart.length() - 1);
                }
                if ("POST".equals(method)) {
                    long orderId = HttpUtil.parsePositiveLong(idPart, "orderId");
                    HttpUtil.sendSuccess(exchange, 200, queueService.advanceOrder(orderId));
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            HttpUtil.sendError(exchange, 404, "Queue endpoint not found");
        } catch (HttpUtil.ApiException e) {
            HttpUtil.sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in QueueHandler", e);
            HttpUtil.sendError(exchange, 500, "Internal server error while processing queue request");
        }
    }
}
