package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** G03 饲喂方案（API-G10、G12）：plan_id 为 API-U14 的 biz_id；未设置方案时 stages 为空数组。 */
public class FeedPlanVO {

    private Long planId;
    private Boolean hasPlan;
    private FeedPlanBatchVO batch;
    private FeedStdCardVO stdCard;
    private BigDecimal abnormalThreshold;
    private List<FeedPlanStageVO> stages;
    private List<FeedOptionBriefVO> feedOptions;
    private Integer todayGeneratedMeals;
    private Integer version;

    @Nullable
    public Long getPlanId() {
        return planId;
    }

    public boolean isHasPlan() {
        return Boolean.TRUE.equals(hasPlan);
    }

    @Nullable
    public FeedPlanBatchVO getBatch() {
        return batch;
    }

    @Nullable
    public FeedStdCardVO getStdCard() {
        return stdCard;
    }

    @Nullable
    public BigDecimal getAbnormalThreshold() {
        return abnormalThreshold;
    }

    @NonNull
    public List<FeedPlanStageVO> getStages() {
        return stages == null ? Collections.emptyList() : stages;
    }

    @NonNull
    public List<FeedOptionBriefVO> getFeedOptions() {
        return feedOptions == null ? Collections.emptyList() : feedOptions;
    }

    @Nullable
    public Integer getTodayGeneratedMeals() {
        return todayGeneratedMeals;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }

    /** 批次：batch_id、batch_no、pen_names、breed_name、day_age、current_count、entry_date、entry_age_days */
    public static class FeedPlanBatchVO {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String breedName;
        private Integer dayAge;
        private Integer currentCount;
        private String entryDate;
        private Integer entryAgeDays;

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

        @Nullable
        public Integer getCurrentCount() {
            return currentCount;
        }

        @Nullable
        public String getEntryDate() {
            return entryDate;
        }

        @Nullable
        public Integer getEntryAgeDays() {
            return entryAgeDays;
        }
    }

    /** 品种标准卡：std_available、std_name、applied_at、applied_by_name、adjusted_count、std_updated */
    public static class FeedStdCardVO {
        private Boolean stdAvailable;
        private String stdName;
        private String appliedAt;
        private String appliedByName;
        private Integer adjustedCount;
        private Boolean stdUpdated;

        public boolean isStdAvailable() {
            return Boolean.TRUE.equals(stdAvailable);
        }

        @Nullable
        public String getStdName() {
            return stdName;
        }

        @Nullable
        public String getAppliedAt() {
            return appliedAt;
        }

        @Nullable
        public String getAppliedByName() {
            return appliedByName;
        }

        public int getAdjustedCount() {
            return adjustedCount == null ? 0 : adjustedCount;
        }

        public boolean isStdUpdated() {
            return Boolean.TRUE.equals(stdUpdated);
        }
    }
}
