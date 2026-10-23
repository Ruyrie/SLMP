package com.zhimu.slmp.finance.model.request;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E23 修改申请预计结果（纯计算，不要求幂等键）
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SaleChangePreviewRequest {

    /** UPDATE / VOID */
    private final String changeType;
    /** unit_price / quantity */
    @Nullable
    private final String fieldCode;
    @Nullable
    private final BigDecimal newUnitPrice;
    @Nullable
    private final Integer newQuantity;
    @Nullable
    private final BigDecimal newTotalWeight;
    @Nullable
    private final List<PenQuantity> penQuantities;

    public SaleChangePreviewRequest(String changeType, @Nullable String fieldCode, @Nullable BigDecimal newUnitPrice, @Nullable Integer newQuantity, @Nullable BigDecimal newTotalWeight, @Nullable List<PenQuantity> penQuantities) {
        this.changeType = changeType;
        this.fieldCode = fieldCode;
        this.newUnitPrice = newUnitPrice;
        this.newQuantity = newQuantity;
        this.newTotalWeight = newTotalWeight;
        this.penQuantities = penQuantities;
    }

    public String getChangeType() {
        return changeType;
    }

    @Nullable
    public String getFieldCode() {
        return fieldCode;
    }

    @Nullable
    public BigDecimal getNewUnitPrice() {
        return newUnitPrice;
    }

    @Nullable
    public Integer getNewQuantity() {
        return newQuantity;
    }

    @Nullable
    public BigDecimal getNewTotalWeight() {
        return newTotalWeight;
    }

    @Nullable
    public List<PenQuantity> getPenQuantities() {
        return penQuantities;
    }
}
