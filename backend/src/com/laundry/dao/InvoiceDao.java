package com.laundry.dao;

import com.laundry.config.ConnectionPool;
import com.laundry.model.Invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC Data Access Object for invoices, shop settings, and billing summaries.
 */
public class InvoiceDao {

    private final ConnectionPool pool;

    public InvoiceDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public Invoice insert(Connection conn, Invoice invoice) throws SQLException {
        String invoiceNumber = String.format("INV-%d-%04d", Year.now().getValue(), invoice.getOrderId());
        invoice.setInvoiceNumber(invoiceNumber);

        String sql = "INSERT INTO invoices (order_id, invoice_number, subtotal, tax_rate, tax_amount, discount, total_amount, payment_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'UNPAID')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, invoice.getOrderId());
            ps.setString(2, invoiceNumber);
            ps.setBigDecimal(3, invoice.getSubtotal().setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(4, invoice.getTaxRate().setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(5, invoice.getTaxAmount().setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(6, invoice.getDiscount().setScale(2, RoundingMode.HALF_UP));
            ps.setBigDecimal(7, invoice.getTotalAmount().setScale(2, RoundingMode.HALF_UP));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    invoice.setInvoiceId(keys.getLong(1));
                }
            }
        }
        return findByOrderId(conn, invoice.getOrderId());
    }

    public Invoice findByOrderId(long orderId) throws SQLException {
        try (Connection conn = pool.borrowConnection()) {
            return findByOrderId(conn, orderId);
        }
    }

    public Invoice findByOrderId(Connection conn, long orderId) throws SQLException {
        String sql = "SELECT invoice_id, order_id, invoice_number, subtotal, tax_rate, tax_amount, discount, total_amount, "
                + "payment_status, payment_method, paid_at, created_at FROM invoices WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int markPaid(long orderId, String paymentMethod) throws SQLException {
        String sql = "UPDATE invoices SET payment_status = 'PAID', payment_method = ?, paid_at = CURRENT_TIMESTAMP "
                + "WHERE order_id = ? AND payment_status = 'UNPAID'";
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentMethod);
            ps.setLong(2, orderId);
            return ps.executeUpdate();
        }
    }

    public Map<String, String> loadSettings() throws SQLException {
        try (Connection conn = pool.borrowConnection()) {
            return loadSettings(conn);
        }
    }

    public Map<String, String> loadSettings(Connection conn) throws SQLException {
        String sql = "SELECT setting_key, setting_value FROM settings";
        Map<String, String> settings = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        }
        settings.putIfAbsent("TAX_RATE_PERCENT", "18");
        settings.putIfAbsent("CURRENCY_SYMBOL", "₹");
        settings.putIfAbsent("SHOP_NAME", "Namma Laundry & Dry Care");
        settings.putIfAbsent("SHOP_ADDRESS", "42 Anna Salai, Teynampet, Chennai 600018");
        return settings;
    }

    public Map<String, Object> getBillingSummary() throws SQLException {
        String totalsSql = "SELECT "
                + "COALESCE(SUM(CASE WHEN inv.payment_status = 'PAID' THEN inv.total_amount ELSE 0 END), 0.00) AS revenue_collected, "
                + "COALESCE(SUM(CASE WHEN inv.payment_status = 'UNPAID' AND o.status <> 'CANCELLED' THEN inv.total_amount ELSE 0 END), 0.00) AS unpaid_amount "
                + "FROM invoices inv JOIN orders o ON inv.order_id = o.order_id";

        String countsSql = "SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status";

        BigDecimal revenueCollected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        statusCounts.put("QUEUED", 0);
        statusCounts.put("WASHING", 0);
        statusCounts.put("IRONING", 0);
        statusCounts.put("READY_FOR_PICKUP", 0);
        statusCounts.put("DELIVERED", 0);
        statusCounts.put("CANCELLED", 0);

        int totalOrders = 0;

        try (Connection conn = pool.borrowConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(totalsSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    revenueCollected = scaleMoney(rs.getBigDecimal("revenue_collected"));
                    unpaidAmount = scaleMoney(rs.getBigDecimal("unpaid_amount"));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(countsSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String st = rs.getString("status");
                    int cnt = rs.getInt("cnt");
                    statusCounts.put(st, cnt);
                    totalOrders += cnt;
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("revenueCollected", revenueCollected);
        summary.put("unpaidAmount", unpaidAmount);
        summary.put("totalOrders", totalOrders);
        summary.put("statusCounts", statusCounts);
        return summary;
    }

    private Invoice mapRow(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        inv.setInvoiceId(rs.getLong("invoice_id"));
        inv.setOrderId(rs.getLong("order_id"));
        inv.setInvoiceNumber(rs.getString("invoice_number"));
        inv.setSubtotal(scaleMoney(rs.getBigDecimal("subtotal")));
        inv.setTaxRate(scaleMoney(rs.getBigDecimal("tax_rate")));
        inv.setTaxAmount(scaleMoney(rs.getBigDecimal("tax_amount")));
        inv.setDiscount(scaleMoney(rs.getBigDecimal("discount")));
        inv.setTotalAmount(scaleMoney(rs.getBigDecimal("total_amount")));
        inv.setPaymentStatus(rs.getString("payment_status"));
        inv.setPaymentMethod(rs.getString("payment_method"));
        inv.setPaidAt(rs.getTimestamp("paid_at"));
        inv.setCreatedAt(rs.getTimestamp("created_at"));
        return inv;
    }

    private BigDecimal scaleMoney(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
