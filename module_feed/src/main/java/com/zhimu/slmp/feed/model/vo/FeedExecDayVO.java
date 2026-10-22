package com.zhimu.slmp.feed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/** G04 点柱后的单日各餐明细（API-G16）。 */
public class FeedExecDayVO {

    private String date;
    private BigDecimal actualQty;
    private BigDecimal planQty;
    private Integer doneMeals;
    private Integer plannedMeals;
    private List<FeedExecDayItemVO> items;

    @Nullable
    public String getDate() {
        return date;
    }

    @Nullable
    public BigDecimal getActualQty() {
        return actualQty;
    }

    @Nullable
    public BigDecimal getPlanQty() {
        return planQty;
    }

    public int getDoneMeals() {
        return doneMeals == null ? 0 : doneMeals;
    }

    public int getPlannedMeals() {
        return plannedMeals == null ? 0 : plannedMeals;
    }

    @NonNull
    public List<FeedExecDayItemVO> getItems() {
        return items == null ? Collections.emptyList() : items;
    }

    /** 当天一餐：task_id、batch_no、pen_name、meal_name、plan_qty、actual_qty、status、status_text、executor_name、abnormal_type */
    public static class FeedExecDayItemVO {
        private Long taskId;
        private String batchNo;
        private String penName;
        private String mealName;
        private BigDecimal planQty;
        private BigDecimal actualQty;
        private String status;
        private String statusText;
        private String executorName;
        private String abnormalType;

        public long getTaskId() {
            return taskId == null ? 0L : taskId;
        }

        @Nullable
        public String getBatchNo() {
            return batchNo;
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
        public BigDecimal getPlanQty() {
            return planQty;
        }

        @Nullable
        public BigDecimal getActualQty() {
            return actualQty;
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
        public String getExecutorName() {
            return executorName;
        }

        @Nullable
        public String getAbnormalType() {
            return abnormalType;
        }
    }
}
