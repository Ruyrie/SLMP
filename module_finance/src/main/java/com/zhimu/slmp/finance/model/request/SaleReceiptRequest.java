package com.zhimu.slmp.finance.model.request;

import java.math.BigDecimal;

/**
 * API-E22 登记收款
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class SaleReceiptRequest {

    private final BigDecimal amount;
    private final String receiptDate;
    private final String paymentMethod;

    public SaleReceiptRequest(BigDecimal amount, String receiptDate, String paymentMethod) {
        this.amount = amount;
        this.receiptDate = receiptDate;
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReceiptDate() {
        return receiptDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }
}
