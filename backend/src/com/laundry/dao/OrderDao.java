package com.laundry.dao;

import com.laundry.config.ConnectionPool;
import com.laundry.model.LaundryOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC Data Access Object for orders and order_status_history.
 */
public class OrderDao {

    private static final String BASE_SELECT =
            "SELECT o.order_id, o.customer_id, c.name AS customer_name, c.phone AS customer_phone, "
                    + "o.status, o.notes, o.created_at, o.updated_at, o.delivered_at, "
                    + "COALESCE((SELECT SUM(oi.quantity) FROM order_items oi WHERE oi.order_id = o.order_id), 0) AS item_count, "
                    + "inv.total_amount, inv.payment_status, inv.invoice_number "
                    + "FROM orders o "
                    + "JOIN customers c ON o.customer_id = c.customer_id "
                    + "LEFT JOIN invoices inv ON inv.order_id = o.order_id ";

    private final ConnectionPool pool;

    public OrderDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public long insert(Connection conn, long customerId, String notes) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, status, notes) VALUES (?, 'QUEUED', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, customerId);
            ps.setString(2, notes);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated order ID");
    }

    public void updateStatus(Connection conn, long orderId, String newStatus, boolean markDelivered) throws SQLException {
        String sql = markDelivered
                ? "UPDATE orders SET status = ?, delivered_at = CURRENT_TIMESTAMP WHERE order_id = ?"
                : "UPDATE orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setLong(2, orderId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Order #" + orderId + " not found for status update");
            }
        }
    }

    public void insertStatusHistory(Connection conn, long orderId, String oldStatus, String newStatus) throws SQLException {
        String sql = "INSERT INTO order_status_history (order_id, old_status, new_status) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, orderId);
            ps.setString(2, oldStatus);
            ps.setString(3, newStatus);
            ps.executeUpdate();
        }
    }

    public LaundryOrder findById(long orderId) throws SQLException {
        String sql = BASE_SELECT + "WHERE o.order_id = ?";
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public LaundryOrder findById(Connection conn, long orderId) throws SQLException {
        String sql = BASE_SELECT + "WHERE o.order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<LaundryOrder> findAll(String statusFilter) throws SQLException {
        boolean hasFilter = statusFilter != null && !statusFilter.isBlank();
        String sql = hasFilter
                ? BASE_SELECT + "WHERE o.status = ? ORDER BY o.created_at DESC, o.order_id DESC"
                : BASE_SELECT + "ORDER BY o.created_at DESC, o.order_id DESC";
        List<LaundryOrder> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasFilter) {
                ps.setString(1, statusFilter.trim().toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<LaundryOrder> findActiveOrdersFifo() throws SQLException {
        String sql = BASE_SELECT
                + "WHERE o.status IN ('QUEUED', 'WASHING', 'IRONING', 'READY_FOR_PICKUP') "
                + "ORDER BY o.created_at ASC, o.order_id ASC";
        List<LaundryOrder> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<LaundryOrder> findByCustomerId(long customerId) throws SQLException {
        String sql = BASE_SELECT + "WHERE o.customer_id = ? ORDER BY o.created_at DESC, o.order_id DESC";
        List<LaundryOrder> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> findStatusHistory(long orderId) throws SQLException {
        String sql = "SELECT history_id, order_id, old_status, new_status, changed_at "
                + "FROM order_status_history WHERE order_id = ? ORDER BY changed_at ASC, history_id ASC";
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("historyId", rs.getLong("history_id"));
                    row.put("orderId", rs.getLong("order_id"));
                    row.put("oldStatus", rs.getString("old_status"));
                    row.put("newStatus", rs.getString("new_status"));
                    row.put("changedAt", rs.getTimestamp("changed_at"));
                    history.add(row);
                }
            }
        }
        return history;
    }

    private LaundryOrder mapRow(ResultSet rs) throws SQLException {
        LaundryOrder order = new LaundryOrder();
        order.setOrderId(rs.getLong("order_id"));
        order.setCustomerId(rs.getLong("customer_id"));
        order.setCustomerName(rs.getString("customer_name"));
        order.setCustomerPhone(rs.getString("customer_phone"));
        order.setStatus(rs.getString("status"));
        order.setNotes(rs.getString("notes"));
        order.setCreatedAt(rs.getTimestamp("created_at"));
        order.setUpdatedAt(rs.getTimestamp("updated_at"));
        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        order.setDeliveredAt(deliveredAt);
        order.setItemCount(rs.getInt("item_count"));
        BigDecimal total = rs.getBigDecimal("total_amount");
        order.setTotalAmount(total != null ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setPaymentStatus(rs.getString("payment_status"));
        order.setInvoiceNumber(rs.getString("invoice_number"));
        return order;
    }
}
