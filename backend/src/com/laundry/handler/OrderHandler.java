package com.laundry.handler;

import com.laundry.service.OrderService;
import com.laundry.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST handler for /api/prices and /api/orders endpoints.
 */
public class OrderHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(OrderHandler.class.getName());
    private final OrderService orderService;

    public OrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handlePreflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();

            if ("/api/prices".equals(path) || "/api/prices/".equals(path)) {
                if ("GET".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, orderService.getPricesAndSettings());
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            String suffix = path.length() > "/api/orders".length()
                    ? path.substring("/api/orders".length())
                    : "";

            if (suffix.isEmpty() || "/".equals(suffix)) {
                if ("GET".equals(method)) {
                    Map<String, String> query = HttpUtil.parseQueryParams(exchange.getRequestURI().getRawQuery());
                    HttpUtil.sendSuccess(exchange, 200, orderService.listOrders(query.get("status")));
                    return;
                }
                if ("POST".equals(method)) {
                    Map<String, Object> body = HttpUtil.readJsonBody(exchange);
                    HttpUtil.sendSuccess(exchange, 201, orderService.createOrder(body));
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            if (suffix.startsWith("/")) {
                suffix = suffix.substring(1);
            }
            if (suffix.endsWith("/")) {
                suffix = suffix.substring(0, suffix.length() - 1);
            }
            if (!suffix.contains("/")) {
                long orderId = HttpUtil.parsePositiveLong(suffix, "orderId");
                if ("GET".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, orderService.getOrderDetail(orderId));
                    return;
                }
                if ("DELETE".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, orderService.cancelOrder(orderId));
                    return;
                }
                HttpUtil.sendError(exchange, 405, "Method not allowed");
                return;
            }

            HttpUtil.sendError(exchange, 404, "Order endpoint not found");
        } catch (HttpUtil.ApiException e) {
            HttpUtil.sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in OrderHandler", e);
            HttpUtil.sendError(exchange, 500, "Internal server error while processing order request");
        }
    }
}
