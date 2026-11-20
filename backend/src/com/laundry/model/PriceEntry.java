package com.laundry.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PriceEntry entity representing a garment-type + service-type tariff.
 */
public class PriceEntry {

    private long priceId;
    private String itemType;
    private String serviceType;
    private BigDecimal unitPrice;

    public PriceEntry() {
    }

    public PriceEntry(long priceId, String itemType, String serviceType, BigDecimal unitPrice) {
        this.priceId = priceId;
        this.itemType = itemType;
        this.serviceType = serviceType;
        this.unitPrice = unitPrice;
    }

    public long getPriceId() {
        return priceId;
    }

    public void setPriceId(long priceId) {
        this.priceId = priceId;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("priceId", priceId);
        map.put("itemType", itemType);
        map.put("serviceType", serviceType);
        map.put("unitPrice", unitPrice);
        return map;
    }
}
