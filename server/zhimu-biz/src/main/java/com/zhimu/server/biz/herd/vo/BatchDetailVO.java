package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.PenBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 批次档案（API-L15；API-L20 修改入栏也返回）。intro_unit_price、pending_change_request_count 为管理视图。 */
@Data
public class BatchDetailVO {

    private Long batchId;
    private Long farmId;
    private Long categoryId;
    private Long breedId;
    private Long entryEventId;
    private String batchNo;
    private String farmName;
    private String categoryName;
    private String breedName;
    private String breedTitle;
    private String purpose;
    private String source;
    private String status;
    private String entryDate;
    private String qrContent;
    private String labelText;
    private String sourceRecordType;
    private String endType;
    private String endDate;
    private String stageName;
    private List<BatchPenItem> pens;
    private PenBriefVO entryPen;
    private Integer dayAge;
    private Integer daysRaised;
    private Integer currentCount;
    private Integer entryCount;
    private Integer deathCount;
    private Integer soldCount;
    private Integer entryAgeDays;
    private Integer version;
    private BigDecimal mortalityRate;
    private BigDecimal entryAvgWeight;

    /** 管理视图，员工不输出 */
    @ManagerView
    private BigDecimal introUnitPrice;

    /** 管理视图，员工不输出；待接入公共修改申请统计（当前返回 0） */
    @ManagerView
    private Integer pendingChangeRequestCount;

    private WithdrawalInfo withdrawal;
    private BatchActions actions;
    private String unit;
    private String unitText;

    /** 休药信息，与模块08 有效用药记录采用相同口径。 */
    @Data
    public static class WithdrawalInfo {
        private Boolean inWithdrawal;
        private Integer remainingDays;
        private Integer withdrawalDays;
        private String sellableDate;
        private String lastDrugName;
        private String lastDrugDate;
    }

    /** 当前用户可执行的批次操作 */
    @Data
    public static class BatchActions {
        private Boolean canTransfer;
        private Boolean canRegisterDeath;
        private Boolean canSubmitChangeRequest;
        private Boolean canEditEntry;
        private Boolean canDelete;
    }
}
