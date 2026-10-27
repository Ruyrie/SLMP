package com.zhimu.server.biz.feeding.vo;

import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 饲喂任务相关 VO（API-G01–G03）。 */
public final class FeedTaskVO {

    private FeedTaskVO() {
    }

    /** G01 今日饲喂指标与横幅（API-G01） */
    @Data
    public static class TodaySummary {
        private String date;
        private String weekdayText;
        private Integer penCount;
        private Integer taskTotal;
        private Integer doneCount;
        private Integer overdueCount;
        private BigDecimal planTotalQty;
        private BigDecimal actualTotalQty;
        private Integer planShortageMoreCount;
        private FirstOverdue firstOverdue;
        private StatusCounts statusCounts;
        private List<PenOption> penOptions = new ArrayList<>();
        private List<PlanShortage> planShortage = new ArrayList<>();
        private Boolean canStockIn;
        private Boolean canManage;
        private String emptyReason;
    }

    /** 第一条逾期 */
    @Data
    public static class FirstOverdue {
        private String penName;
        private String mealName;
        private String feedTime;
        private Integer overdueMinutes;
    }

    /** 各状态餐数 */
    @Data
    public static class StatusCounts {
        private Integer pending;
        private Integer done;
        private Integer overdue;
    }

    /** 栏舍筛选项 */
    @Data
    public static class PenOption {
        private Long penId;
        private String penName;
        private Integer mealCount;
    }

    /** 计划量超过结存的饲料 */
    @Data
    public static class PlanShortage {
        private Long feedId;
        private String feedName;
        private BigDecimal stockQty;
        private BigDecimal todayNeedQty;
        private BigDecimal shortQty;
        private String restockBeforeMealName;
    }

    /** G01 今日任务行（API-G02） */
    @Data
    public static class Item {
        private Long taskId;
        private String taskDate;
        private Integer mealNo;
        private String mealCode;
        private String mealName;
        private String feedTime;
        private Long penId;
        private String penName;
        private Long batchId;
        private String batchNo;
        private Long feedId;
        private String feedName;
        private Boolean lowStock;
        private BigDecimal planQty;
        private BigDecimal actualQty;
        private String assigneeName;
        private String executorName;
        private String registeredAt;
        private String status;
        private String statusText;
        private String abnormalType;
        private Boolean isBackfill;
        private Integer overdueMinutes;
        private Long recordId;
        private String action;
    }

    /** 今日任务补生成结果（POST /feed-tasks/generate-today） */
    @Data
    public static class GenerateToday {
        /** 新生成的任务数 */
        private Integer generated;
        /** 已存在而跳过的任务数 */
        private Integer skipped;
    }

    /** G02 登记表单上下文（API-G03） */
    @Data
    public static class Form {
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
        private FeedRecordVO.OptionBrief defaultFeed;
        private List<FeedRecordVO.OptionBrief> feedOptions = new ArrayList<>();
        private UserBriefVO defaultExecutor;
        /** 员工固定为本人，省略选项；所有者、场长由表单服务提供成员列表。 */
        private List<UserBriefVO> executorOptions;
        private UserBriefVO currentUser;
        private Boolean canRegister;
        private String blockReason;
        private Boolean isOverdue;
        private String backfillEndAt;
        private Long recordId;
        private Boolean canStockIn;
    }
}
