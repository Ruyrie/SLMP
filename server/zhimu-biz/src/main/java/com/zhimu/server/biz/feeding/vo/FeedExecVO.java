package com.zhimu.server.biz.feeding.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 饲喂执行情况 VO（API-G14–G16）。 */
public final class FeedExecVO {

    private FeedExecVO() {
    }

    /** G04 执行情况汇总 */
    @Data
    public static class Summary {
        private String startDate;
        private String endDate;
        private String range;
        private Integer batchCount;
        private Integer plannedMeals;
        private Integer doneMeals;
        private BigDecimal execRate;
        private BigDecimal deviationRatio;
        private String deviationDirection;
        private BigDecimal actualQty;
        private BigDecimal planQty;
        private Integer abnormalCount;
        private Integer underCount;
        private Integer overCount;
        private BigDecimal abnormalSameRatio;
        private String planLineType;
        private BigDecimal planDailyQty;
        private List<Daily> daily = new ArrayList<>();
        private Boolean hasData;
        private String emptyReason;
    }

    /** 每日柱 */
    @Data
    public static class Daily {
        private String date;
        private BigDecimal actualQty;
        private BigDecimal planQty;
        private Integer plannedMeals;
        private Integer doneMeals;
        private BigDecimal deviationRatio;
        private Boolean overThreshold;
    }

    /** G04 需要关注 */
    @Data
    public static class AttentionItem {
        private String type;
        private String date;
        private String mealName;
        private Long batchId;
        private String batchNo;
        private String penName;
        private BigDecimal planQty;
        private BigDecimal actualQty;
        private String executorName;
        private String abnormalType;
        private BigDecimal deviationRatio;
        private String taskStatus;
        private String taskStatusText;
        private Long taskId;
        private Long recordId;
        private Boolean canBackfill;
        private Boolean isToday;
    }

    /** G04 单日各餐明细 */
    @Data
    public static class Day {
        private String date;
        private BigDecimal actualQty;
        private BigDecimal planQty;
        private Integer doneMeals;
        private Integer plannedMeals;
        private List<DayItem> items = new ArrayList<>();
    }

    /** 单日任务行 */
    @Data
    public static class DayItem {
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
    }
}
