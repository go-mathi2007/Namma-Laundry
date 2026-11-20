package com.laundry.service;

import com.laundry.dao.CustomerDao;
import com.laundry.dao.OrderDao;
import com.laundry.model.Customer;
import com.laundry.model.LaundryOrder;
import com.laundry.util.HttpUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Business logic and validation for customer registration, search, and order history.
 */
public class CustomerService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9]{7,15}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CustomerDao customerDao;
    private final OrderDao orderDao;

    public CustomerService(CustomerDao customerDao, OrderDao orderDao) {
        this.customerDao = customerDao;
        this.orderDao = orderDao;
    }

    public Map<String, Object> registerCustomer(Map<String, Object> body) throws SQLException {
        String name = HttpUtil.requireNonBlank(body, "name", "Customer name");
        if (name.length() > 120) {
            throw new HttpUtil.ApiException(400, "Customer name cannot exceed 120 characters");
        }

        String rawPhone = HttpUtil.requireNonBlank(body, "phone", "Phone number");
        String normalizedPhone = rawPhone.replaceAll("[\\s()-]", "");
        if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            throw new HttpUtil.ApiException(400, "Phone number must contain 7 to 15 digits");
        }

        String email = HttpUtil.getString(body, "email");
        if (email != null && !email.isEmpty()) {
            if (email.length() > 150 || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new HttpUtil.ApiException(400, "Invalid email address format");
            }
        } else {
            email = null;
        }

        String address = HttpUtil.getString(body, "address");
        if (address != null && address.isEmpty()) {
            address = null;
        } else if (address != null && address.length() > 300) {
            throw new HttpUtil.ApiException(400, "Address cannot exceed 300 characters");
        }

        Customer existing = customerDao.findByPhone(normalizedPhone);
        if (existing != null) {
            throw new HttpUtil.ApiException(409, "A customer with phone number " + normalizedPhone + " is already registered");
        }

        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(normalizedPhone);
        customer.setEmail(email);
        customer.setAddress(address);

        try {
            Customer created = customerDao.insert(customer);
            return created.toMap();
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new HttpUtil.ApiException(409, "A customer with phone number " + normalizedPhone + " is already registered");
            }
            throw e;
        }
    }

    public List<Map<String, Object>> listCustomers(String searchTerm) throws SQLException {
        List<Customer> customers = (searchTerm != null && !searchTerm.isBlank())
                ? customerDao.search(searchTerm.trim())
                : customerDao.findAll();

        List<Map<String, Object>> result = new ArrayList<>(customers.size());
        for (Customer c : customers) {
            result.add(c.toMap());
        }
        return result;
    }

    public Map<String, Object> getCustomer(long customerId) throws SQLException {
        Customer c = customerDao.findById(customerId);
        if (c == null) {
            throw new HttpUtil.ApiException(404, "Customer #" + customerId + " not found");
        }
        return c.toMap();
    }

    public Map<String, Object> getCustomerOrders(long customerId) throws SQLException {
        Customer c = customerDao.findById(customerId);
        if (c == null) {
            throw new HttpUtil.ApiException(404, "Customer #" + customerId + " not found");
        }
        List<LaundryOrder> orders = orderDao.findByCustomerId(customerId);
        List<Map<String, Object>> orderMaps = new ArrayList<>(orders.size());
        for (LaundryOrder o : orders) {
            orderMaps.add(o.toMap(false));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", c.toMap());
        result.put("orders", orderMaps);
        return result;
    }
}
