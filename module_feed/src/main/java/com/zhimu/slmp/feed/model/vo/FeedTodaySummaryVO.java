package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * G01 今日饲喂指标与横幅（API-G01）：员工只统计负责栏舍，plan_shortage 按全场；can_stock_in、can_manage 员工均为 false。
 */
public class FeedTodaySummaryVO {

    /** 员工没有负责栏舍 */
    public static final String EMPTY_NO_PEN = "NO_PEN";
    /** 在养批次都没有设置饲喂方案 */
    public static final String EMPTY_NO_PLAN = "NO_PLAN";
    /** 今天没有任务 */
    public static final String EMPTY_NO_TASK = "NO_TASK";

    private String date;
    private String weekdayText;
    private Integer penCount;
    private Integer taskTotal;
    private Integer doneCount;
    private Integer overdueCount;
    private BigDecimal planTotalQty;
    private BigDecimal actualTotalQty;
    private Integer planShortageMoreCount;
    private FeedFirstOverdueVO firstOverdue;
    private FeedStatusCountsVO statusCounts;
    private List<FeedPenOptionVO> penOptions;
    private List<PlanShortageVO> planShortage;
    private Boolean canStockIn;
    private Boolean canManage;
    private String emptyReason;

    @Nullable
    public String getDate() {
        return date;
    }

    @Nullable
    public String getWeekdayText() {
        return weekdayText;
    }

    public int getPenCount() {
        return penCount == null ? 0 : penCount;
    }

    public int getTaskTotal() {
        return taskTotal == null ? 0 : taskTotal;
    }

    public int getDoneCount() {
        return doneCount == null ? 0 : doneCount;
    }

    public int getOverdueCount() {
        return overdueCount == null ? 0 : overdueCount;
    }

    @Nullable
    public BigDecimal getPlanTotalQty() {
        return planTotalQty;
    }

    @Nullable
    public BigDecimal getActualTotalQty() {
        return actualTotalQty;
    }

    public int getPlanShortageMoreCount() {
        return planShortageMoreCount == null ? 0 : planShortageMoreCount;
    }

    @Nullable
    public FeedFirstOverdueVO getFirstOverdue() {
        return firstOverdue;
    }

    @Nullable
    public FeedStatusCountsVO getStatusCounts() {
        return statusCounts;
    }

    @NonNull
    public List<FeedPenOptionVO> getPenOptions() {
        return penOptions == null ? Collections.emptyList() : penOptions;
    }

    @NonNull
    public List<PlanShortageVO> getPlanShortage() {
        return planShortage == null ? Collections.emptyList() : planShortage;
    }

    public boolean isCanStockIn() {
        return Boolean.TRUE.equals(canStockIn);
    }

    public boolean isCanManage() {
        return Boolean.TRUE.equals(canManage);
    }

    @Nullable
    public String getEmptyReason() {
        return emptyReason;
    }

    /** 第一条逾期：pen_name、meal_name、feed_time、overdue_minutes */
    public static class FeedFirstOverdueVO {
        private String penName;
        private String mealName;
        private String feedTime;
        private Integer overdueMinutes;

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
        public Integer getOverdueMinutes() {
            return overdueMinutes;
        }
    }

    /** 各状态餐数：pending、done、overdue */
    public static class FeedStatusCountsVO {
        private Integer pending;
        private Integer done;
        private Integer overdue;

        public int getPending() {
            return pending == null ? 0 : pending;
        }

        public int getDone() {
            return done == null ? 0 : done;
        }

        public int getOverdue() {
            return overdue == null ? 0 : overdue;
        }
    }

    /** 栏舍筛选项：pen_id、pen_name、meal_count；员工只列负责栏舍 */
    public static class FeedPenOptionVO {
        private Long penId;
        private String penName;
        private Integer mealCount;

        public long getPenId() {
            return penId == null ? 0L : penId;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        public int getMealCount() {
            return mealCount == null ? 0 : mealCount;
        }
    }
}
