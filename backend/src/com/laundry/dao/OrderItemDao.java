package com.laundry.dao;

import com.laundry.config.ConnectionPool;
import com.laundry.model.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for order_items.
 */
public class OrderItemDao {

    private final ConnectionPool pool;

    public OrderItemDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public void insertBatch(Connection conn, long orderId, List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, item_type, service_type, quantity, unit_price, line_total, special_instructions) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (OrderItem item : items) {
                ps.setLong(1, orderId);
                ps.setString(2, item.getItemType());
                ps.setString(3, item.getServiceType());
                ps.setInt(4, item.getQuantity());
                ps.setBigDecimal(5, item.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
                ps.setBigDecimal(6, item.getLineTotal().setScale(2, RoundingMode.HALF_UP));
                ps.setString(7, item.getSpecialInstructions());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        item.setItemId(keys.getLong(1));
                    }
                }
                item.setOrderId(orderId);
            }
        }
    }

    public List<OrderItem> findByOrderId(long orderId) throws SQLException {
        String sql = "SELECT item_id, order_id, item_type, service_type, quantity, unit_price, line_total, special_instructions "
                + "FROM order_items WHERE order_id = ? ORDER BY item_id ASC";
        List<OrderItem> items = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
            }
        }
        return items;
    }

    private OrderItem mapRow(ResultSet rs) throws SQLException {
        OrderItem item = new OrderItem();
        item.setItemId(rs.getLong("item_id"));
        item.setOrderId(rs.getLong("order_id"));
        item.setItemType(rs.getString("item_type"));
        item.setServiceType(rs.getString("service_type"));
        item.setQuantity(rs.getInt("quantity"));
        BigDecimal price = rs.getBigDecimal("unit_price");
        BigDecimal total = rs.getBigDecimal("line_total");
        item.setUnitPrice(price != null ? price.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        item.setLineTotal(total != null ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        item.setSpecialInstructions(rs.getString("special_instructions"));
        return item;
    }
}
