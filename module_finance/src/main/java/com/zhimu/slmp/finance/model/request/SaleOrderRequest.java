package com.zhimu.slmp.finance.model.request;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E16 登记出栏单 / API-E20 修改未收款出栏单（修改时 batch_id 置 null，带 version）
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SaleOrderRequest {

    @Nullable
    private final Long batchId;
    private final String saleDate;
    private final int quantity;
    /** 多栏舍时必填，合计等于 quantity */
    @Nullable
    private final List<PenQuantity> penQuantities;
    private final BigDecimal totalWeight;
    /** BY_WEIGHT / BY_HEAD */
    private final String pricingMethod;
    private final BigDecimal unitPrice;
    private final String buyerName;
    @Nullable
    private final String dueDate;
    @Nullable
    private final Integer version;

    public SaleOrderRequest(@Nullable Long batchId, String saleDate, int quantity, @Nullable List<PenQuantity> penQuantities, BigDecimal totalWeight, String pricingMethod, BigDecimal unitPrice, String buyerName, @Nullable String dueDate, @Nullable Integer version) {
        this.batchId = batchId;
        this.saleDate = saleDate;
        this.quantity = quantity;
        this.penQuantities = penQuantities;
        this.totalWeight = totalWeight;
        this.pricingMethod = pricingMethod;
        this.unitPrice = unitPrice;
        this.buyerName = buyerName;
        this.dueDate = dueDate;
        this.version = version;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    public String getSaleDate() {
        return saleDate;
    }

    public int getQuantity() {
        return quantity;
    }

    @Nullable
    public List<PenQuantity> getPenQuantities() {
        return penQuantities;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public String getPricingMethod() {
        return pricingMethod;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getBuyerName() {
        return buyerName;
    }

    @Nullable
    public String getDueDate() {
        return dueDate;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
