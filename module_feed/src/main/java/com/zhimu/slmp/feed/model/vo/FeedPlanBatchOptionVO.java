package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.Nullable;

/** 方案批次选项（API-G09）：当前养殖场在养批次，has_plan 为 false 时显示“未设置”。G04 批次 Chip 同源。 */
public class FeedPlanBatchOptionVO {

    private Long batchId;
    private String batchNo;
    private String penNames;
    private String breedName;
    private Integer dayAge;
    private Boolean hasPlan;

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenNames() {
        return penNames;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    @Nullable
    public Integer getDayAge() {
        return dayAge;
    }

    public boolean isHasPlan() {
        return Boolean.TRUE.equals(hasPlan);
    }
}
