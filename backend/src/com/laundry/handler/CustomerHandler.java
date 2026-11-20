package com.laundry.handler;

import com.laundry.service.CustomerService;
import com.laundry.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST handler for /api/customers endpoints.
 */
public class CustomerHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(CustomerHandler.class.getName());
    private final CustomerService customerService;

    public CustomerHandler(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handlePreflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();
            String suffix = path.length() > "/api/customers".length()
                    ? path.substring("/api/customers".length())
                    : "";

            if (suffix.isEmpty() || "/".equals(suffix)) {
                if ("GET".equals(method)) {
                    Map<String, String> query = HttpUtil.parseQueryParams(exchange.getRequestURI().getRawQuery());
                    HttpUtil.sendSuccess(exchange, 200, customerService.listCustomers(query.get("search")));
                    return;
                }
                if ("POST".equals(method)) {
                    Map<String, Object> body = HttpUtil.readJsonBody(exchange);
                    HttpUtil.sendSuccess(exchange, 201, customerService.registerCustomer(body));
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
            String[] parts = suffix.split("/");

            if (parts.length == 1 && "GET".equals(method)) {
                long id = HttpUtil.parsePositiveLong(parts[0], "customerId");
                HttpUtil.sendSuccess(exchange, 200, customerService.getCustomer(id));
                return;
            }
            if (parts.length == 2 && "orders".equals(parts[1]) && "GET".equals(method)) {
                long id = HttpUtil.parsePositiveLong(parts[0], "customerId");
                HttpUtil.sendSuccess(exchange, 200, customerService.getCustomerOrders(id));
                return;
            }

            HttpUtil.sendError(exchange, 404, "Customer endpoint not found");
        } catch (HttpUtil.ApiException e) {
            HttpUtil.sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in CustomerHandler", e);
            HttpUtil.sendError(exchange, 500, "Internal server error while processing customer request");
        }
    }
}
