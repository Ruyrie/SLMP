package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 收支记录（API-E03 列表项、E05–E07 返回）
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class FinanceRecordVO {

    private long id;
    private long farmId;
    private long categoryId;
    private BigDecimal amount;
    private String recordDate;
    private int voucherCount;
    private String title;
    private String categoryName;
    @Nullable
    private String categoryIcon;
    /** MANUAL / INBOUND / DRUG_LOT / SALE_RECEIPT 等 */
    private String sourceType;
    /** 来自饲料入库单 */
    @Nullable
    private String sourceLabel;
    @Nullable
    private String recordTypeText;
    @Nullable
    private String statusText;
    private boolean editable;
    private String createdAt;
    /** EXPENSE / INCOME */
    private String recordType;
    /** NORMAL / VOID */
    private String status;
    /** 只在 ALL */
    @Nullable
    private String farmName;
    @Nullable
    private String sourceNo;
    @Nullable
    private Long subcategoryId;
    @Nullable
    private String subcategoryName;
    @Nullable
    private String detailText;
    @Nullable
    private String note;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    /** 原单据跳转 */
    @Nullable
    private SourceTarget sourceTarget;

    public long getId() {
        return id;
    }

    public long getFarmId() {
        return farmId;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public int getVoucherCount() {
        return voucherCount;
    }

    public String getTitle() {
        return title;
    }

    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getCategoryIcon() {
        return categoryIcon;
    }

    public String getSourceType() {
        return sourceType;
    }

    @Nullable
    public String getSourceLabel() {
        return sourceLabel;
    }

    @Nullable
    public String getRecordTypeText() {
        return recordTypeText;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    public boolean isEditable() {
        return editable;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getRecordType() {
        return recordType;
    }

    public String getStatus() {
        return status;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public String getSourceNo() {
        return sourceNo;
    }

    @Nullable
    public Long getSubcategoryId() {
        return subcategoryId;
    }

    @Nullable
    public String getSubcategoryName() {
        return subcategoryName;
    }

    @Nullable
    public String getDetailText() {
        return detailText;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public SourceTarget getSourceTarget() {
        return sourceTarget;
    }

    /** 原单据 */
    public static class SourceTarget {

        /** INBOUND / DRUG_LOT / SALE_ORDER */
        private String bizType;
        @Nullable
        private Long bizId;

        public String getBizType() {
            return bizType;
        }

        @Nullable
        public Long getBizId() {
            return bizId;
        }
    }
}
