package com.laundry.dao;

import com.laundry.config.ConnectionPool;
import com.laundry.model.PriceEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for price_list.
 */
public class PriceDao {

    private final ConnectionPool pool;

    public PriceDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public List<PriceEntry> findAll() throws SQLException {
        String sql = "SELECT price_id, item_type, service_type, unit_price FROM price_list ORDER BY item_type ASC, service_type ASC";
        List<PriceEntry> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public PriceEntry findByItemAndService(Connection conn, String itemType, String serviceType) throws SQLException {
        String sql = "SELECT price_id, item_type, service_type, unit_price FROM price_list WHERE item_type = ? AND service_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemType);
            ps.setString(2, serviceType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private PriceEntry mapRow(ResultSet rs) throws SQLException {
        BigDecimal price = rs.getBigDecimal("unit_price");
        return new PriceEntry(
                rs.getLong("price_id"),
                rs.getString("item_type"),
                rs.getString("service_type"),
                price != null ? price.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
        );
    }
}
