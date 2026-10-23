package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 栏舍详情（API-L08，L11、L12、L13、L27 也返回本结构）。 */
@Data
public class PenDetailVO {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_IN_USE = "IN_USE";
    public static final String STATUS_EMPTY = "EMPTY";

    private Long penId;
    private Long categoryId;
    private String penCode;
    private String penName;
    private String penType;
    private String penTypeText;
    private String categoryName;
    private Integer capacity;
    private Integer stockCount;
    /** 还可转入（不小于 0） */
    private Integer remainingCapacity;
    /** 已超出容量的数量 */
    private Integer overCapacityCount;
    private Integer version;
    private BigDecimal occupancyRate;
    /** ENABLED / DISABLED */
    private String status;
    private String displayStatus;
    private String displayStatusText;
    private Boolean isBreedingPen;
    private Boolean canDisable;
    private Boolean canEnable;
    private String disableBlockReason;
    private String disabledAt;
    private List<UserBriefVO> staff;
    private List<PenBatchItem> batches;
    private String unitText;

    /** 栏舍内的在栏批次 */
    @Data
    public static class PenBatchItem {
        private Long batchId;
        private String batchNo;
        private String breedTitle;
        private Integer dayAge;
        private Integer qtyInPen;
        private Boolean inWithdrawal;
    }
}
