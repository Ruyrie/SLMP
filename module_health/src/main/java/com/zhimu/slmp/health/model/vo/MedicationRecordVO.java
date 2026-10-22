package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D18 用药记录列表项（API-D23 详情另带说明、关联与 release_date_now）。
 * 员工他人记录 actions 为空；单价、金额为管理视图。drug_lot_id、executor_id 为客户端补充的可空字段（修改回填用）。
 */
public class MedicationRecordVO {

    private long id;
    private long batchId;
    private long penId;
    private long drugId;
    private int version;
    private int courseDays;
    private int withdrawalDays;
    @Nullable
    private BigDecimal dose;
    @Nullable
    private BigDecimal recommendedDose;
    @Nullable
    private BigDecimal usedQty;
    private boolean overDose;
    private boolean isMine;
    /** 批次已结束 */
    private boolean locked;
    @Nullable
    private String groupNo;
    @Nullable
    private String targetType;
    @Nullable
    private String batchNo;
    @Nullable
    private String penName;
    @Nullable
    private String drugName;
    @Nullable
    private String lotNo;
    @Nullable
    private String lotExpireDate;
    @Nullable
    private String doseUnit;
    @Nullable
    private String firstDate;
    @Nullable
    private String lastDate;
    @Nullable
    private String withdrawalEndDate;
    @Nullable
    private String usedUnit;
    @Nullable
    private String reason;
    @Nullable
    private String creatorName;
    @Nullable
    private String executorName;
    @Nullable
    private ChangeRequestStateVO changeState;
    @Nullable
    private List<String> actions;
    @Nullable
    private String routeCode;
    @Nullable
    private String routeName;
    @Nullable
    private BigDecimal estimatedQty;
    @Nullable
    private BigDecimal unitPrice;
    @Nullable
    private BigDecimal costAmount;
    @Nullable
    private String overDoseNote;
    @Nullable
    private String usageNote;
    @Nullable
    private String reportSummary;
    @Nullable
    private String releaseDateNow;
    @Nullable
    private Boolean categoryMismatch;
    @Nullable
    private Long reportId;
    @Nullable
    private Long drugLotId;
    @Nullable
    private Long executorId;

    public long getId() {
        return id;
    }

    public long getBatchId() {
        return batchId;
    }

    public long getPenId() {
        return penId;
    }

    public long getDrugId() {
        return drugId;
    }

    public int getVersion() {
        return version;
    }

    public int getCourseDays() {
        return courseDays;
    }

    public int getWithdrawalDays() {
        return withdrawalDays;
    }

    @Nullable
    public BigDecimal getDose() {
        return dose;
    }

    @Nullable
    public BigDecimal getRecommendedDose() {
        return recommendedDose;
    }

    @Nullable
    public BigDecimal getUsedQty() {
        return usedQty;
    }

    public boolean isOverDose() {
        return overDose;
    }

    public boolean isMine() {
        return isMine;
    }

    public boolean isLocked() {
        return locked;
    }

    @Nullable
    public String getGroupNo() {
        return groupNo;
    }

    @Nullable
    public String getTargetType() {
        return targetType;
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
    public String getDrugName() {
        return drugName;
    }

    @Nullable
    public String getLotNo() {
        return lotNo;
    }

    @Nullable
    public String getLotExpireDate() {
        return lotExpireDate;
    }

    @Nullable
    public String getDoseUnit() {
        return doseUnit;
    }

    @Nullable
    public String getFirstDate() {
        return firstDate;
    }

    @Nullable
    public String getLastDate() {
        return lastDate;
    }

    @Nullable
    public String getWithdrawalEndDate() {
        return withdrawalEndDate;
    }

    @Nullable
    public String getUsedUnit() {
        return usedUnit;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Nullable
    public String getCreatorName() {
        return creatorName;
    }

    @Nullable
    public String getExecutorName() {
        return executorName;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    @NonNull
    public List<String> getActions() {
        return actions == null ? Collections.emptyList() : actions;
    }

    @Nullable
    public String getRouteCode() {
        return routeCode;
    }

    @Nullable
    public String getRouteName() {
        return routeName;
    }

    @Nullable
    public BigDecimal getEstimatedQty() {
        return estimatedQty;
    }

    @Nullable
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Nullable
    public BigDecimal getCostAmount() {
        return costAmount;
    }

    @Nullable
    public String getOverDoseNote() {
        return overDoseNote;
    }

    @Nullable
    public String getUsageNote() {
        return usageNote;
    }

    @Nullable
    public String getReportSummary() {
        return reportSummary;
    }

    @Nullable
    public String getReleaseDateNow() {
        return releaseDateNow;
    }

    @Nullable
    public Boolean getCategoryMismatch() {
        return categoryMismatch;
    }

    @Nullable
    public Long getReportId() {
        return reportId;
    }

    @Nullable
    public Long getDrugLotId() {
        return drugLotId;
    }

    @Nullable
    public Long getExecutorId() {
        return executorId;
    }
}
