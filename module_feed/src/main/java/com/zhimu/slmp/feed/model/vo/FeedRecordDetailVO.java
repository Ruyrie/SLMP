package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 投喂记录详情（API-G04–G08 返回）：view_mode 为 EDIT（所有者、场长当天记录）或 READONLY（原因见 readonly_reason）；
 * todo_completed、proxy_executor_name 只在 API-G04 返回。
 */
public class FeedRecordDetailVO {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_VOID = "VOID";
    public static final String VIEW_EDIT = "EDIT";
    public static final String VIEW_READONLY = "READONLY";
    public static final String READONLY_STAFF_CONFIRMED = "STAFF_CONFIRMED";
    public static final String READONLY_NOT_TODAY = "NOT_TODAY";
    public static final String READONLY_VOIDED = "VOIDED";
    public static final String READONLY_PENDING_REQUEST = "PENDING_REQUEST";

    private Long recordId;
    private Long taskId;
    private String status;
    private String statusText;
    private String penName;
    private String mealName;
    private String feedTime;
    private String feedDate;
    private Long batchId;
    private String batchNo;
    private String breedName;
    private Integer dayAge;
    private Integer batchStock;
    private FeedOptionBriefVO feed;
    private BigDecimal planQty;
    private FeedPlanFormulaVO planFormula;
    private BigDecimal actualQty;
    private BigDecimal deviationRatio;
    private String abnormalType;
    private String abnormalTypeText;
    private BigDecimal abnormalThreshold;
    private UserBriefVO executor;
    private UserBriefVO recorder;
    private Boolean isProxy;
    private Boolean isBackfill;
    private String registeredAt;
    private String remark;
    private String viewMode;
    private String readonlyReason;
    private ChangeRequestStateVO changeState;
    private Boolean canVoid;
    private Boolean canRequestChange;
    private List<String> changeFields;
    private List<String> recalcTags;
    private Boolean todoCompleted;
    private String proxyExecutorName;
    private Integer version;

    public long getRecordId() {
        return recordId == null ? 0L : recordId;
    }

    public long getTaskId() {
        return taskId == null ? 0L : taskId;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
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

    @Nullable
    public String getFeedDate() {
        return feedDate;
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
    public FeedOptionBriefVO getFeed() {
        return feed;
    }

    @Nullable
    public BigDecimal getPlanQty() {
        return planQty;
    }

    @Nullable
    public FeedPlanFormulaVO getPlanFormula() {
        return planFormula;
    }

    @Nullable
    public BigDecimal getActualQty() {
        return actualQty;
    }

    @Nullable
    public BigDecimal getDeviationRatio() {
        return deviationRatio;
    }

    @Nullable
    public String getAbnormalType() {
        return abnormalType;
    }

    @Nullable
    public String getAbnormalTypeText() {
        return abnormalTypeText;
    }

    @Nullable
    public BigDecimal getAbnormalThreshold() {
        return abnormalThreshold;
    }

    @Nullable
    public UserBriefVO getExecutor() {
        return executor;
    }

    @Nullable
    public UserBriefVO getRecorder() {
        return recorder;
    }

    public boolean isProxy() {
        return Boolean.TRUE.equals(isProxy);
    }

    public boolean isBackfill() {
        return Boolean.TRUE.equals(isBackfill);
    }

    @Nullable
    public String getRegisteredAt() {
        return registeredAt;
    }

    @Nullable
    public String getRemark() {
        return remark;
    }

    @Nullable
    public String getViewMode() {
        return viewMode;
    }

    @Nullable
    public String getReadonlyReason() {
        return readonlyReason;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    public boolean isCanVoid() {
        return Boolean.TRUE.equals(canVoid);
    }

    public boolean isCanRequestChange() {
        return Boolean.TRUE.equals(canRequestChange);
    }

    @NonNull
    public List<String> getChangeFields() {
        return changeFields == null ? Collections.emptyList() : changeFields;
    }

    @NonNull
    public List<String> getRecalcTags() {
        return recalcTags == null ? Collections.emptyList() : recalcTags;
    }

    @Nullable
    public Boolean getTodoCompleted() {
        return todoCompleted;
    }

    @Nullable
    public String getProxyExecutorName() {
        return proxyExecutorName;
    }

    public int getVersion() {
        return version == null ? 0 : version;
    }

    public boolean isEditable() {
        return VIEW_EDIT.equals(viewMode);
    }

    public boolean isAbnormal() {
        return abnormalType != null && !"NONE".equals(abnormalType);
    }

    /** 计划量来源：head_count × daily_qty_per_head ÷ meals_per_day */
    public static class FeedPlanFormulaVO {
        private Integer headCount;
        private BigDecimal dailyQtyPerHead;
        private Integer mealsPerDay;

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
    }
}
