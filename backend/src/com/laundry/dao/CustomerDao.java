package com.laundry.dao;

import com.laundry.config.ConnectionPool;
import com.laundry.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for customers.
 */
public class CustomerDao {

    private final ConnectionPool pool;

    public CustomerDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public Customer insert(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, email, address) VALUES (?, ?, ?, ?)";
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getAddress());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    return findById(id);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated customer ID");
    }

    public Customer findById(long customerId) throws SQLException {
        String sql = "SELECT customer_id, name, phone, email, address, created_at FROM customers WHERE customer_id = ?";
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Customer findByPhone(String phone) throws SQLException {
        String sql = "SELECT customer_id, name, phone, email, address, created_at FROM customers WHERE phone = ?";
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Customer> findAll() throws SQLException {
        String sql = "SELECT customer_id, name, phone, email, address, created_at FROM customers ORDER BY customer_id DESC";
        List<Customer> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Customer> search(String term) throws SQLException {
        String sql = "SELECT customer_id, name, phone, email, address, created_at "
                + "FROM customers "
                + "WHERE LOWER(name) LIKE ? OR phone LIKE ? OR CAST(customer_id AS CHAR) = ? "
                + "ORDER BY customer_id DESC";
        String likeTerm = "%" + term.toLowerCase() + "%";
        List<Customer> list = new ArrayList<>();
        try (Connection conn = pool.borrowConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, likeTerm);
            ps.setString(2, "%" + term + "%");
            ps.setString(3, term.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getLong("customer_id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }
}
