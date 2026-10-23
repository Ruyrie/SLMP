package com.zhimu.slmp.finance.model.request;

/**
 * 多栏舍出栏分配
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class PenQuantity {

    private final long penId;
    private final int quantity;

    public PenQuantity(long penId, int quantity) {
        this.penId = penId;
        this.quantity = quantity;
    }

    public long getPenId() {
        return penId;
    }

    public int getQuantity() {
        return quantity;
    }
}
