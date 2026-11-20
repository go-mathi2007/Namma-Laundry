package com.laundry.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LaundryOrder entity representing an order moving through the processing pipeline.
 */
public class LaundryOrder {

    private long orderId;
    private long customerId;
    private String customerName;
    private String customerPhone;
    private String status;
    private String notes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deliveredAt;
    private int itemCount;
    private BigDecimal totalAmount;
    private String paymentStatus;
    private String invoiceNumber;
    private List<OrderItem> items = new ArrayList<>();

    public LaundryOrder() {
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Timestamp deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public Map<String, Object> toMap(boolean includeItems) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderId", orderId);
        map.put("customerId", customerId);
        map.put("customerName", customerName);
        map.put("customerPhone", customerPhone);
        map.put("status", status);
        map.put("notes", notes);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("deliveredAt", deliveredAt);
        map.put("itemCount", itemCount);
        map.put("totalAmount", totalAmount);
        map.put("paymentStatus", paymentStatus);
        map.put("invoiceNumber", invoiceNumber);
        if (includeItems) {
            List<Map<String, Object>> itemMaps = new ArrayList<>(items.size());
            for (OrderItem item : items) {
                itemMaps.add(item.toMap());
            }
            map.put("items", itemMaps);
        }
        return map;
    }
}
