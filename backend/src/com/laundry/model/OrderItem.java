package com.laundry.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OrderItem entity storing a garment line item with snapshotted unit price.
 */
public class OrderItem {

    private long itemId;
    private long orderId;
    private String itemType;
    private String serviceType;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String specialInstructions;

    public OrderItem() {
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("itemId", itemId);
        map.put("orderId", orderId);
        map.put("itemType", itemType);
        map.put("serviceType", serviceType);
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("lineTotal", lineTotal);
        map.put("specialInstructions", specialInstructions);
        return map;
    }
}
