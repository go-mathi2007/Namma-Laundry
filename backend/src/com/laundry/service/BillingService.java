package com.laundry.service;

import com.laundry.dao.CustomerDao;
import com.laundry.dao.InvoiceDao;
import com.laundry.dao.OrderDao;
import com.laundry.dao.OrderItemDao;
import com.laundry.model.Customer;
import com.laundry.model.Invoice;
import com.laundry.model.LaundryOrder;
import com.laundry.model.OrderItem;
import com.laundry.util.HttpUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business logic for itemized invoice retrieval, payment settlement, and billing summary.
 */
public class BillingService {

    private static final Set<String> VALID_PAYMENT_METHODS = new HashSet<>(Arrays.asList(
            "CASH", "CARD", "UPI"
    ));

    private final InvoiceDao invoiceDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final CustomerDao customerDao;

    public BillingService(
            InvoiceDao invoiceDao,
            OrderDao orderDao,
            OrderItemDao orderItemDao,
            CustomerDao customerDao
    ) {
        this.invoiceDao = invoiceDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.customerDao = customerDao;
    }

    public Map<String, Object> getInvoiceDetail(long orderId) throws SQLException {
        LaundryOrder order = orderDao.findById(orderId);
        if (order == null) {
            throw new HttpUtil.ApiException(404, "Order #" + orderId + " not found");
        }
        Invoice invoice = invoiceDao.findByOrderId(orderId);
        if (invoice == null) {
            throw new HttpUtil.ApiException(404, "Invoice for order #" + orderId + " not found");
        }
        Customer customer = customerDao.findById(order.getCustomerId());
        List<OrderItem> items = orderItemDao.findByOrderId(orderId);
        Map<String, String> settings = invoiceDao.loadSettings();

        List<Map<String, Object>> itemMaps = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            itemMaps.add(item.toMap());
        }

        Map<String, Object> shop = new LinkedHashMap<>();
        shop.put("name", settings.get("SHOP_NAME"));
        shop.put("address", settings.get("SHOP_ADDRESS"));
        shop.put("currencySymbol", settings.get("CURRENCY_SYMBOL"));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("shop", shop);
        detail.put("customer", customer != null ? customer.toMap() : null);
        detail.put("order", order.toMap(false));
        detail.put("items", itemMaps);
        detail.put("invoice", invoice.toMap());
        return detail;
    }

    public Map<String, Object> payInvoice(long orderId, Map<String, Object> body) throws SQLException {
        String method = HttpUtil.requireNonBlank(body, "paymentMethod", "paymentMethod").toUpperCase();
        if (!VALID_PAYMENT_METHODS.contains(method)) {
            throw new HttpUtil.ApiException(400, "Invalid paymentMethod. Allowed: CASH, CARD, UPI");
        }

        LaundryOrder order = orderDao.findById(orderId);
        if (order == null) {
            throw new HttpUtil.ApiException(404, "Order #" + orderId + " not found");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new HttpUtil.ApiException(409, "Cannot collect payment for a cancelled order");
        }

        Invoice existing = invoiceDao.findByOrderId(orderId);
        if (existing == null) {
            throw new HttpUtil.ApiException(404, "Invoice for order #" + orderId + " not found");
        }
        if ("PAID".equalsIgnoreCase(existing.getPaymentStatus())) {
            throw new HttpUtil.ApiException(409, "Invoice " + existing.getInvoiceNumber() + " is already marked PAID");
        }

        int updated = invoiceDao.markPaid(orderId, method);
        if (updated == 0) {
            throw new HttpUtil.ApiException(409, "Invoice could not be marked as paid");
        }
        return getInvoiceDetail(orderId);
    }

    public Map<String, Object> getBillingSummary() throws SQLException {
        Map<String, Object> summary = invoiceDao.getBillingSummary();
        Map<String, String> settings = invoiceDao.loadSettings();
        summary.put("currencySymbol", settings.get("CURRENCY_SYMBOL"));
        summary.put("shopName", settings.get("SHOP_NAME"));
        summary.put("shopAddress", settings.get("SHOP_ADDRESS"));
        summary.put("taxRatePercent", settings.get("TAX_RATE_PERCENT"));
        return summary;
    }
}
