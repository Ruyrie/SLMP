package com.zhimu.slmp.finance.model.request;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-E05 记一笔 / API-E06 修改手工记录（修改时 record_type 置 null 不提交，带 version）
 * <p>Gson 不序列化 null：可空字段置 null 即不提交。
 */
public class FinanceRecordRequest {

    /** EXPENSE / INCOME；修改不传 */
    @Nullable
    private final String recordType;
    private final BigDecimal amount;
    private final long categoryId;
    /** 只限支出 */
    @Nullable
    private final Long subcategoryId;
    /** 只限收入，≤ 30 字 */
    @Nullable
    private final String note;
    private final String recordDate;
    @Nullable
    private final Long batchId;
    /** ≤ 6，全部上传成功 */
    @Nullable
    private final List<Long> fileIds;
    /** 修改时取 detail.version */
    @Nullable
    private final Integer version;

    public FinanceRecordRequest(@Nullable String recordType, BigDecimal amount, long categoryId, @Nullable Long subcategoryId, @Nullable String note, String recordDate, @Nullable Long batchId, @Nullable List<Long> fileIds, @Nullable Integer version) {
        this.recordType = recordType;
        this.amount = amount;
        this.categoryId = categoryId;
        this.subcategoryId = subcategoryId;
        this.note = note;
        this.recordDate = recordDate;
        this.batchId = batchId;
        this.fileIds = fileIds;
        this.version = version;
    }

    @Nullable
    public String getRecordType() {
        return recordType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getCategoryId() {
        return categoryId;
    }

    @Nullable
    public Long getSubcategoryId() {
        return subcategoryId;
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public String getRecordDate() {
        return recordDate;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public List<Long> getFileIds() {
        return fileIds;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
