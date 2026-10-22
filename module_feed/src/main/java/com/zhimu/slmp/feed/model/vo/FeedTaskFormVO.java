package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * G02 登记表单上下文（API-G03）：任务、批次、计划量来源、饲料选项与执行人选项（员工不返回 executor_options）；
 * 已登记时给出 record_id（客户端改调 API-G05），不能登记时 can_register 为 false 并给 block_reason。
 */
public class FeedTaskFormVO {

    public static final String BLOCK_DONE = "DONE";
    public static final String BLOCK_BATCH_ENDED = "BATCH_ENDED";
    public static final String BLOCK_CANCELLED = "CANCELLED";
    public static final String BLOCK_IGNORED = "IGNORED";
    public static final String BLOCK_MISSED = "MISSED";

    private Long taskId;
    private String taskDate;
    private String status;
    private String statusText;
    private Long penId;
    private String penName;
    private String mealName;
    private String feedTime;
    private Long batchId;
    private String batchNo;
    private String breedName;
    private Integer dayAge;
    private Integer batchStock;
    private Integer headCount;
    private BigDecimal dailyQtyPerHead;
    private Integer mealsPerDay;
    private BigDecimal planQty;
    private BigDecimal abnormalThreshold;
    private FeedOptionBriefVO defaultFeed;
    private List<FeedOptionBriefVO> feedOptions;
    private UserBriefVO defaultExecutor;
    private List<UserBriefVO> executorOptions;
    private UserBriefVO currentUser;
    private Boolean canRegister;
    private String blockReason;
    private Boolean isOverdue;
    private String backfillEndAt;
    private Long recordId;
    private Boolean canStockIn;

    public long getTaskId() {
        return taskId == null ? 0L : taskId;
    }

    @Nullable
    public String getTaskDate() {
        return taskDate;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    public long getPenId() {
        return penId == null ? 0L : penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getMealName() {
        return mealName;
    }

    @Nullable
    public String getFeedTime() {
        return feedTime;
    }

    public long getBatchId() {
        return batchId == null ? 0L : batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
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
    public Integer getBatchStock() {
        return batchStock;
    }

    @Nullable
    public Integer getHeadCount() {
        return headCount;
    }

    @Nullable
    public BigDecimal getDailyQtyPerHead() {
        return dailyQtyPerHead;
    }

    @Nullable
    public Integer getMealsPerDay() {
        return mealsPerDay;
    }

    @Nullable
    public BigDecimal getPlanQty() {
        return planQty;
    }

    @Nullable
    public BigDecimal getAbnormalThreshold() {
        return abnormalThreshold;
    }

    @Nullable
    public FeedOptionBriefVO getDefaultFeed() {
        return defaultFeed;
    }

    @NonNull
    public List<FeedOptionBriefVO> getFeedOptions() {
        return feedOptions == null ? Collections.emptyList() : feedOptions;
    }

    @Nullable
    public UserBriefVO getDefaultExecutor() {
        return defaultExecutor;
    }

    /** 员工为 null */
    @Nullable
    public List<UserBriefVO> getExecutorOptions() {
        return executorOptions;
    }

    @Nullable
    public UserBriefVO getCurrentUser() {
        return currentUser;
    }

    public boolean isCanRegister() {
        return Boolean.TRUE.equals(canRegister);
    }

    @Nullable
    public String getBlockReason() {
        return blockReason;
    }

    public boolean isOverdue() {
        return Boolean.TRUE.equals(isOverdue);
    }

    @Nullable
    public String getBackfillEndAt() {
        return backfillEndAt;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    public boolean isCanStockIn() {
        return Boolean.TRUE.equals(canStockIn);
    }
}
