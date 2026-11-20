package com.laundry.handler;

import com.laundry.service.BillingService;
import com.laundry.util.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST handler for /api/billing endpoints.
 */
public class BillingHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(BillingHandler.class.getName());
    private final BillingService billingService;

    public BillingHandler(BillingService billingService) {
        this.billingService = billingService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (HttpUtil.handlePreflight(exchange)) {
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase();
            String path = exchange.getRequestURI().getPath();
            String suffix = path.length() > "/api/billing".length()
                    ? path.substring("/api/billing".length())
                    : "";

            if ("/summary".equals(suffix) || "/summary/".equals(suffix)) {
                if ("GET".equals(method)) {
                    HttpUtil.sendSuccess(exchange, 200, billingService.getBillingSummary());
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

            if (parts.length == 1 && !parts[0].isEmpty() && "GET".equals(method)) {
                long orderId = HttpUtil.parsePositiveLong(parts[0], "orderId");
                HttpUtil.sendSuccess(exchange, 200, billingService.getInvoiceDetail(orderId));
                return;
            }

            if (parts.length == 2 && "pay".equals(parts[1]) && "PUT".equals(method)) {
                long orderId = HttpUtil.parsePositiveLong(parts[0], "orderId");
                Map<String, Object> body = HttpUtil.readJsonBody(exchange);
                HttpUtil.sendSuccess(exchange, 200, billingService.payInvoice(orderId, body));
                return;
            }

            HttpUtil.sendError(exchange, 404, "Billing endpoint not found");
        } catch (HttpUtil.ApiException e) {
            HttpUtil.sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error in BillingHandler", e);
            HttpUtil.sendError(exchange, 500, "Internal server error while processing billing request");
        }
    }
}
