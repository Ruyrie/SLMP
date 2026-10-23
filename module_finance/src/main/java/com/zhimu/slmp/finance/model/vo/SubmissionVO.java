package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

/**
 * API-E43 按请求编号查询（saved 为假不报错）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class SubmissionVO {

    private boolean saved;
    /** FINANCE_RECORD / SALE_ORDER / SALE_RECEIPT */
    @Nullable
    private String bizType;
    @Nullable
    private Long bizId;
    @Nullable
    private Long saleOrderId;

    public boolean isSaved() {
        return saved;
    }

    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public Long getBizId() {
        return bizId;
    }

    @Nullable
    public Long getSaleOrderId() {
        return saleOrderId;
    }
}
