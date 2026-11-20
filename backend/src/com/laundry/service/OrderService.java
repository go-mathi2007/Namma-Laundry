package com.laundry.service;

import com.laundry.config.ConnectionPool;
import com.laundry.dao.CustomerDao;
import com.laundry.dao.InvoiceDao;
import com.laundry.dao.OrderDao;
import com.laundry.dao.OrderItemDao;
import com.laundry.dao.PriceDao;
import com.laundry.model.Customer;
import com.laundry.model.Invoice;
import com.laundry.model.LaundryOrder;
import com.laundry.model.OrderItem;
import com.laundry.model.PriceEntry;
import com.laundry.structure.ProcessingPipeline;
import com.laundry.util.HttpUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business logic for tariffs, transactional order creation + invoice generation,
 * order queries, and cancellations.
 */
public class OrderService {

    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());

    private static final Set<String> VALID_ITEM_TYPES = new HashSet<>(Arrays.asList(
            "SHIRT", "TROUSERS", "SAREE", "JACKET", "BEDSHEET", "TOWEL", "DELICATE", "BLANKET"
    ));
    private static final Set<String> VALID_SERVICE_TYPES = new HashSet<>(Arrays.asList(
            "WASH_FOLD", "DRY_CLEAN", "STEAM_PRESS"
    ));
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            "QUEUED", "WASHING", "IRONING", "READY_FOR_PICKUP", "DELIVERED", "CANCELLED"
    ));
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ConnectionPool pool;
    private final CustomerDao customerDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final PriceDao priceDao;
    private final InvoiceDao invoiceDao;
    private final ProcessingPipeline pipeline;

    public OrderService(
            ConnectionPool pool,
            CustomerDao customerDao,
            OrderDao orderDao,
            OrderItemDao orderItemDao,
            PriceDao priceDao,
            InvoiceDao invoiceDao,
            ProcessingPipeline pipeline
    ) {
        this.pool = pool;
        this.customerDao = customerDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.priceDao = priceDao;
        this.invoiceDao = invoiceDao;
        this.pipeline = pipeline;
    }

    public Map<String, Object> getPricesAndSettings() throws SQLException {
        List<PriceEntry> prices = priceDao.findAll();
        List<Map<String, Object>> priceMaps = new ArrayList<>(prices.size());
        for (PriceEntry p : prices) {
            priceMaps.add(p.toMap());
        }
        Map<String, String> settings = invoiceDao.loadSettings();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prices", priceMaps);
        result.put("taxRatePercent", new BigDecimal(settings.get("TAX_RATE_PERCENT")).setScale(2, RoundingMode.HALF_UP));
        result.put("currencySymbol", settings.get("CURRENCY_SYMBOL"));
        result.put("shopName", settings.get("SHOP_NAME"));
        result.put("shopAddress", settings.get("SHOP_ADDRESS"));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createOrder(Map<String, Object> body) throws SQLException {
        long customerId = parseLongField(body.get("customerId"), "customerId");
        Customer customer = customerDao.findById(customerId);
        if (customer == null) {
            throw new HttpUtil.ApiException(404, "Customer #" + customerId + " not found");
        }

        Object rawItemsObj = body.get("items");
        if (!(rawItemsObj instanceof List<?>)) {
            throw new HttpUtil.ApiException(400, "Order items array is required");
        }
        List<Object> rawItems = (List<Object>) rawItemsObj;
        if (rawItems.isEmpty()) {
            throw new HttpUtil.ApiException(400, "Order must contain at least one garment item");
        }

        String notes = HttpUtil.getString(body, "notes");
        if (notes != null && notes.isEmpty()) {
            notes = null;
        } else if (notes != null && notes.length() > 500) {
            throw new HttpUtil.ApiException(400, "Notes cannot exceed 500 characters");
        }

        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (body.get("discount") != null) {
            discount = parseMoneyField(body.get("discount"), "discount");
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                throw new HttpUtil.ApiException(400, "Discount cannot be negative");
            }
        }

        List<OrderItem> preparedItems = new ArrayList<>(rawItems.size());

        try (Connection conn = pool.borrowConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

                for (int i = 0; i < rawItems.size(); i++) {
                    Object entryObj = rawItems.get(i);
                    if (!(entryObj instanceof Map<?, ?>)) {
                        throw new HttpUtil.ApiException(400, "Item at index " + i + " must be a JSON object");
                    }
                    Map<String, Object> itemMap = (Map<String, Object>) entryObj;
                    String itemType = HttpUtil.requireNonBlank(itemMap, "itemType", "itemType").toUpperCase();
                    String serviceType = HttpUtil.requireNonBlank(itemMap, "serviceType", "serviceType").toUpperCase();

                    if (!VALID_ITEM_TYPES.contains(itemType)) {
                        throw new HttpUtil.ApiException(400, "Unsupported itemType: " + itemType);
                    }
                    if (!VALID_SERVICE_TYPES.contains(serviceType)) {
                        throw new HttpUtil.ApiException(400, "Unsupported serviceType: " + serviceType);
                    }

                    int quantity = parseIntField(itemMap.get("quantity"), "quantity");
                    if (quantity <= 0) {
                        throw new HttpUtil.ApiException(400, "Quantity must be greater than 0");
                    }

                    String instructions = HttpUtil.getString(itemMap, "specialInstructions");
                    if (instructions != null && instructions.isEmpty()) {
                        instructions = null;
                    } else if (instructions != null && instructions.length() > 255) {
                        throw new HttpUtil.ApiException(400, "Special instructions cannot exceed 255 characters");
                    }

                    PriceEntry priceEntry = priceDao.findByItemAndService(conn, itemType, serviceType);
                    if (priceEntry == null) {
                        throw new HttpUtil.ApiException(400, "No tariff configured for " + itemType + " / " + serviceType);
                    }

                    BigDecimal unitPrice = priceEntry.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
                    subtotal = subtotal.add(lineTotal).setScale(2, RoundingMode.HALF_UP);

                    OrderItem item = new OrderItem();
                    item.setItemType(itemType);
                    item.setServiceType(serviceType);
                    item.setQuantity(quantity);
                    item.setUnitPrice(unitPrice);
                    item.setLineTotal(lineTotal);
                    item.setSpecialInstructions(instructions);
                    preparedItems.add(item);
                }

                Map<String, String> settings = invoiceDao.loadSettings(conn);
                BigDecimal taxRate = new BigDecimal(settings.get("TAX_RATE_PERCENT")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal taxAmount = subtotal.multiply(taxRate)
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                BigDecimal grossTotal = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

                if (discount.compareTo(grossTotal) > 0) {
                    throw new HttpUtil.ApiException(400, "Discount cannot exceed order gross total (" + grossTotal + ")");
                }
                BigDecimal totalAmount = grossTotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

                long orderId = orderDao.insert(conn, customerId, notes);
                orderItemDao.insertBatch(conn, orderId, preparedItems);

                Invoice invoice = new Invoice();
                invoice.setOrderId(orderId);
                invoice.setSubtotal(subtotal);
                invoice.setTaxRate(taxRate);
                invoice.setTaxAmount(taxAmount);
                invoice.setDiscount(discount);
                invoice.setTotalAmount(totalAmount);
                Invoice savedInvoice = invoiceDao.insert(conn, invoice);

                orderDao.insertStatusHistory(conn, orderId, null, "QUEUED");

                LaundryOrder createdOrder = orderDao.findById(conn, orderId);
                createdOrder.setItems(preparedItems);

                conn.commit();

                // Enqueue only after DB commit succeeds
                pipeline.enqueueNewOrder(orderId);

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("order", createdOrder.toMap(true));
                response.put("invoice", savedInvoice.toMap());
                return response;
            } catch (Exception ex) {
                try {
                    conn.rollback();
                } catch (SQLException rbEx) {
                    LOGGER.log(Level.SEVERE, "Rollback failed during createOrder", rbEx);
                }
                if (ex instanceof HttpUtil.ApiException) {
                    throw (HttpUtil.ApiException) ex;
                }
                if (ex instanceof SQLException) {
                    throw (SQLException) ex;
                }
                throw new SQLException("Order creation failed", ex);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException acEx) {
                    LOGGER.log(Level.WARNING, "Failed to restore autoCommit", acEx);
                }
            }
        }
    }

    public List<Map<String, Object>> listOrders(String statusFilter) throws SQLException {
        if (statusFilter != null && !statusFilter.isBlank()) {
            String normalized = statusFilter.trim().toUpperCase();
            if (!VALID_STATUSES.contains(normalized)) {
                throw new HttpUtil.ApiException(400, "Invalid status filter: " + statusFilter);
            }
            statusFilter = normalized;
        }
        List<LaundryOrder> orders = orderDao.findAll(statusFilter);
        List<Map<String, Object>> list = new ArrayList<>(orders.size());
        for (LaundryOrder order : orders) {
            list.add(order.toMap(false));
        }
        return list;
    }

    public Map<String, Object> getOrderDetail(long orderId) throws SQLException {
        LaundryOrder order = orderDao.findById(orderId);
        if (order == null) {
            throw new HttpUtil.ApiException(404, "Order #" + orderId + " not found");
        }
        List<OrderItem> items = orderItemDao.findByOrderId(orderId);
        order.setItems(items);
        Invoice invoice = invoiceDao.findByOrderId(orderId);
        List<Map<String, Object>> history = orderDao.findStatusHistory(orderId);

        Map<String, Object> detail = order.toMap(true);
        detail.put("invoice", invoice != null ? invoice.toMap() : null);
        detail.put("statusHistory", history);
        return detail;
    }

    public Map<String, Object> cancelOrder(long orderId) throws SQLException {
        synchronized (pipeline) {
            LaundryOrder existing = orderDao.findById(orderId);
            if (existing == null) {
                throw new HttpUtil.ApiException(404, "Order #" + orderId + " not found");
            }
            if ("DELIVERED".equals(existing.getStatus())) {
                throw new HttpUtil.ApiException(409, "Delivered orders cannot be cancelled");
            }
            if ("CANCELLED".equals(existing.getStatus())) {
                throw new HttpUtil.ApiException(409, "Order #" + orderId + " is already cancelled");
            }

            ProcessingPipeline.PipelineSnapshot snapshot = pipeline.captureSnapshot();
            pipeline.cancelOrder(orderId);

            try (Connection conn = pool.borrowConnection()) {
                boolean prevAuto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    orderDao.updateStatus(conn, orderId, "CANCELLED", false);
                    orderDao.insertStatusHistory(conn, orderId, existing.getStatus(), "CANCELLED");
                    LaundryOrder updated = orderDao.findById(conn, orderId);
                    conn.commit();
                    return updated.toMap(false);
                } catch (SQLException ex) {
                    pipeline.restoreSnapshot(snapshot);
                    try {
                        conn.rollback();
                    } catch (SQLException rbEx) {
                        LOGGER.log(Level.SEVERE, "Rollback failed during cancelOrder", rbEx);
                    }
                    throw ex;
                } finally {
                    try {
                        conn.setAutoCommit(prevAuto);
                    } catch (SQLException acEx) {
                        LOGGER.log(Level.WARNING, "Failed to restore autoCommit", acEx);
                    }
                }
            }
        }
    }

    private long parseLongField(Object raw, String field) {
        if (raw == null) {
            throw new HttpUtil.ApiException(400, field + " is required");
        }
        if (raw instanceof Number) {
            long v = ((Number) raw).longValue();
            if (v <= 0) {
                throw new HttpUtil.ApiException(400, field + " must be positive");
            }
            return v;
        }
        return HttpUtil.parsePositiveLong(String.valueOf(raw), field);
    }

    private int parseIntField(Object raw, String field) {
        if (raw == null) {
            throw new HttpUtil.ApiException(400, field + " is required");
        }
        try {
            int val;
            if (raw instanceof Number) {
                val = ((Number) raw).intValue();
            } else {
                val = Integer.parseInt(String.valueOf(raw).trim());
            }
            if (val <= 0) {
                throw new HttpUtil.ApiException(400, field + " must be greater than 0");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new HttpUtil.ApiException(400, field + " must be a valid integer");
        }
    }

    private BigDecimal parseMoneyField(Object raw, String field) {
        try {
            return new BigDecimal(String.valueOf(raw).trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new HttpUtil.ApiException(400, field + " must be a valid decimal amount");
        }
    }
}
