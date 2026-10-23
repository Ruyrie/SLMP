package com.zhimu.server.biz.feeding.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 投喂记录与饲料选项 VO（API-G04–G08）。 */
public final class FeedRecordVO {

    private FeedRecordVO() {
    }

    /** 饲料简要信息 */
    @Data
    public static class OptionBrief {
        private Long feedId;
        private String feedName;
        private String unit;
        private BigDecimal stockQty;
        private Integer remainingDays;
        private Boolean lowStock;
        private Boolean selectable;
        private String disabledReason;
    }

    /** 投喂记录详情 */
    @Data
    public static class Detail {
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
        private OptionBrief feed;
        private BigDecimal planQty;
        private PlanFormula planFormula;
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
        private List<String> changeFields = new ArrayList<>();
        private List<String> recalcTags = new ArrayList<>();
        private Boolean todoCompleted;
        private String proxyExecutorName;
        private Integer version;
    }

    /** 计划量来源 */
    @Data
    public static class PlanFormula {
        private Integer headCount;
        private BigDecimal dailyQtyPerHead;
        private Integer mealsPerDay;
    }

    /** API-G08 按幂等键查询 */
    @Data
    public static class Lookup {
        private Boolean saved;
        private Detail record;
    }
}
