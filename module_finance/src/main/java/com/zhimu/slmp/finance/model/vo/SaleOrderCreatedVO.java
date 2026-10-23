package com.zhimu.slmp.finance.model.vo;

/**
 * API-E16 登记出栏单返回
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SaleOrderCreatedVO {

    private long id;
    private String batchNo;
    private int remainingCount;
    private String countUnitText;
    private boolean batchEnded;

    public long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public String getCountUnitText() {
        return countUnitText;
    }

    public boolean isBatchEnded() {
        return batchEnded;
    }
}
