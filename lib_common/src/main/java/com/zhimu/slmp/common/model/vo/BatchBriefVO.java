package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 记录中的批次；day_age 由后端按入栏日龄和今天计算。
 */
public class BatchBriefVO {

    private long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private String breedText;
    @Nullable
    private Integer dayAge;
    @Nullable
    private Integer headCount;
    @Nullable
    private String penName;

    public long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getBreedText() {
        return breedText;
    }

    @Nullable
    public Integer getDayAge() {
        return dayAge;
    }

    @Nullable
    public Integer getHeadCount() {
        return headCount;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }
}
