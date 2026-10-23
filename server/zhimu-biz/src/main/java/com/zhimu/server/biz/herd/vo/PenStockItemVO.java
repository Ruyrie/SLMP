package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.math.BigDecimal;

/** API-L04 按栏舍存栏（L03 栏舍视图列表项）。 */
@Data
public class PenStockItemVO {

    public static final String STATUS_IN_USE = "IN_USE";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_DISABLED = "DISABLED";

    private Long penId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private String categoryName;
    private Integer capacity;
    private Integer stockCount;
    private Boolean isBreedingPen;
    private Boolean inWithdrawal;
    private String displayStatus;
    private BigDecimal occupancyRate;
    private BigDecimal mortalityRate;
    private Integer batchCount;
    private PenFirstBatchItem firstBatch;
    private EndedBatchItem lastEndedBatch;
    private String unitText;

    /** 栏舍内第一个在栏批次 */
    @Data
    public static class PenFirstBatchItem {
        private Long batchId;
        private String batchNo;
        private Integer dayAge;
        private String stageName;
    }

    /** 空栏时最近结束的批次 */
    @Data
    public static class EndedBatchItem {
        private String batchNo;
        private String endDate;
        /** ALL_SOLD / ALL_DEAD */
        private String endType;
    }
}
