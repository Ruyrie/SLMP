package com.zhimu.server.biz.feeding.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 称重与生长曲线 VO（API-G18–G26）。 */
public final class WeighVO {

    private WeighVO() {
    }

    /** G05/G06 批次选项 */
    @Data
    public static class BatchOption {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String breedName;
        private Integer dayAge;
        private String status;
        private BigDecimal lastAvgWeight;
        private String growthStatus;
        private String growthStatusText;
    }

    /** G05 称重表单 */
    @Data
    public static class Form {
        private Batch batch;
        private String weighDate;
        private Integer dayAge;
        private Integer stockCount;
        private Boolean firstWeigh;
        private LastWeigh lastWeigh;
        private Long existingRecordId;
        private Boolean canRegister;
        private String blockMessage;
        private Boolean stdAvailable;
        private List<StdPoint> stdPoints = new ArrayList<>();
        private BigDecimal stdWeightAtDate;
        private BigDecimal allowedDeviation;
        private String currentGrowthStatus;
        private BigDecimal sampleRatio;
        private Integer suggestedSample;
        private Cycle weighCycle;
        private Boolean canEditCycle;
    }

    /** 称重批次信息 */
    @Data
    public static class Batch {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String breedName;
        private Integer dayAge;
        private Integer currentCount;
        private String entryDate;
        private Integer entryAgeDays;
        private BigDecimal entryAvgWeight;
        private String status;
    }

    /** 上一次称重 */
    @Data
    public static class LastWeigh {
        private String date;
        private BigDecimal avgWeight;
    }

    /** 标准体重日龄点 */
    @Data
    public static class StdPoint {
        private Integer dayAge;
        private BigDecimal stdWeight;
        private BigDecimal lower;
        private BigDecimal upper;
    }

    /** 称重周期 */
    @Data
    public static class Cycle {
        private Integer perMonth;
        private Integer intervalDays;
        private Boolean customized;
        private Integer suggestedPerMonth;
        private String nextWeighDate;
    }

    /** 称重记录详情 */
    @Data
    public static class Record {
        private Long id;
        private Long batchId;
        private String batchNo;
        private String weighDate;
        private Integer dayAge;
        private Integer stockCount;
        private String mode;
        private Integer sampleCount;
        private BigDecimal totalWeight;
        private List<Item> items = new ArrayList<>();
        private BigDecimal avgWeight;
        private Integer suggestedSample;
        private Boolean sampleLow;
        private BigDecimal stdWeight;
        private BigDecimal allowedDeviation;
        private BigDecimal deviationRatio;
        private Boolean outOfRange;
        private String growthResult;
        private String growthResultText;
        private String gainBaseType;
        private String gainBaseDate;
        private BigDecimal gainBaseWeight;
        private BigDecimal dailyGain;
        private UserBriefVO recorder;
        private String createdAt;
        private ChangeRequestStateVO changeState;
        private Boolean canRequestChange;
        private List<String> changeFields = new ArrayList<>();
        private List<String> recalcTags = new ArrayList<>();
        private String growthChange;
        private Boolean todoCompleted;
        private String nextWeighDate;
        private Integer version;
    }

    /** 逐头体重 */
    @Data
    public static class Item {
        private Integer seqNo;
        private BigDecimal weight;
    }

    /** G06 称重记录列表行 */
    @Data
    public static class RecordItem {
        private Long id;
        private String weighDate;
        private Integer dayAge;
        private BigDecimal avgWeight;
        private Integer sampleCount;
        private String mode;
        private String recorderName;
        private BigDecimal stdWeight;
        private BigDecimal deviationRatio;
        private Boolean outOfRange;
        private BigDecimal dailyGain;
        private String action;
    }

    /** G06 生长曲线 */
    @Data
    public static class Curve {
        private Batch batch;
        private String growthStatus;
        private String growthStatusText;
        private Metrics metrics;
        private List<ActualPoint> actualPoints = new ArrayList<>();
        private Boolean stdAvailable;
        private List<StdPoint> stdPoints = new ArrayList<>();
        private Integer xMin;
        private Integer xMax;
        private Integer currentDayAge;
        private List<CompareSeries> compareSeries = new ArrayList<>();
        private Boolean stdUpdatedNotice;
    }

    /** 曲线指标 */
    @Data
    public static class Metrics {
        /** RECORDED：原称重快照；CURRENT_STANDARD：旧记录缺快照，按该次日龄与当前标准作展示对比。 */
        private String comparisonSource;
        private BigDecimal avgWeight;
        private Integer dayAge;
        private String weighDate;
        private BigDecimal deviationRatio;
        private BigDecimal stdWeight;
        private BigDecimal allowedDeviation;
        private BigDecimal dailyGain;
        private BigDecimal prevDailyGain;
        private String prevGainBaseType;
        private BigDecimal cumulativeGain;
    }

    /** 实际点 */
    @Data
    public static class ActualPoint {
        private String date;
        private Integer dayAge;
        private BigDecimal avgWeight;
        private BigDecimal stdWeight;
        private Boolean isEntry;
    }

    /** 对比曲线 */
    @Data
    public static class CompareSeries {
        private Long batchId;
        private String batchNo;
        private String status;
        private List<ComparePoint> points = new ArrayList<>();
    }

    /** 对比点 */
    @Data
    public static class ComparePoint {
        private Integer dayAge;
        private BigDecimal avgWeight;
    }

    /** G06 对比批次选项 */
    @Data
    public static class CompareOption {
        private Long batchId;
        private String batchNo;
        private String penNames;
        private String status;
        private BigDecimal latestAvgWeight;
        private Integer latestDayAge;
    }
}
