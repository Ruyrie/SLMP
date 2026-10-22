package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 登记结果（API-B12、B15、B17、B20、B26、B31 及 B32 的 result）：成功提示由客户端按字段拼出。
 */
public class RegisterResultVO {

    @Nullable
    private String recordType;
    private long recordId;
    @Nullable
    private Long animalId;
    @Nullable
    private String earTag;
    @Nullable
    private String animalStatus;
    @Nullable
    private String animalStatusText;
    @Nullable
    private Long cycleId;
    @Nullable
    private Boolean isFirst;
    @Nullable
    private String expectedDueDate;
    @Nullable
    private Long incubationId;
    @Nullable
    private String expectedHatchDate;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private Boolean todoCompleted;

    @Nullable
    public String getRecordType() {
        return recordType;
    }

    public long getRecordId() {
        return recordId;
    }

    @Nullable
    public Long getAnimalId() {
        return animalId;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public String getAnimalStatus() {
        return animalStatus;
    }

    @Nullable
    public String getAnimalStatusText() {
        return animalStatusText;
    }

    @Nullable
    public Long getCycleId() {
        return cycleId;
    }

    @Nullable
    public Boolean getIsFirst() {
        return isFirst;
    }

    @Nullable
    public String getExpectedDueDate() {
        return expectedDueDate;
    }

    @Nullable
    public Long getIncubationId() {
        return incubationId;
    }

    @Nullable
    public String getExpectedHatchDate() {
        return expectedHatchDate;
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
    public Boolean getTodoCompleted() {
        return todoCompleted;
    }
}
