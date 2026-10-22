package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D14–D16 接种记录。unit_price、cost_amount 仅管理视图；todo_completed、overdue_done 仅登记返回。
 * drug_id 为客户端补充的可空字段（修改时取批号选项用），缺失时按疫苗名称匹配 API-D13。
 */
public class ImmuneRecordVO {

    private long id;
    private long planId;
    private long drugLotId;
    private int version;
    private int quantity;
    @Nullable
    private BigDecimal dose;
    @Nullable
    private BigDecimal totalDose;
    private boolean backfill;
    @Nullable
    private String penName;
    @Nullable
    private String vaccineName;
    @Nullable
    private String lotNo;
    @Nullable
    private String lotExpireDate;
    @Nullable
    private String doseUnit;
    @Nullable
    private String methodCode;
    @Nullable
    private String methodName;
    @Nullable
    private String vaccinateDate;
    @Nullable
    private String executorName;
    @Nullable
    private String creatorName;
    @Nullable
    private String createdAt;
    @Nullable
    private ChangeRequestStateVO changeState;
    @Nullable
    private List<String> actions;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private String immuneRound;
    @Nullable
    private String editHint;
    @Nullable
    private Boolean todoCompleted;
    @Nullable
    private Boolean overdueDone;
    @Nullable
    private BigDecimal unitPrice;
    @Nullable
    private BigDecimal costAmount;
    @Nullable
    private Long drugId;
    @Nullable
    private Long executorId;

    public long getId() {
        return id;
    }

    public long getPlanId() {
        return planId;
    }

    public long getDrugLotId() {
        return drugLotId;
    }

    public int getVersion() {
        return version;
    }

    public int getQuantity() {
        return quantity;
    }

    @Nullable
    public BigDecimal getDose() {
        return dose;
    }

    @Nullable
    public BigDecimal getTotalDose() {
        return totalDose;
    }

    public boolean isBackfill() {
        return backfill;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getVaccineName() {
        return vaccineName;
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
    public String getMethodCode() {
        return methodCode;
    }

    @Nullable
    public String getMethodName() {
        return methodName;
    }

    @Nullable
    public String getVaccinateDate() {
        return vaccinateDate;
    }

    @Nullable
    public String getExecutorName() {
        return executorName;
    }

    @Nullable
    public String getCreatorName() {
        return creatorName;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
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
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getImmuneRound() {
        return immuneRound;
    }

    @Nullable
    public String getEditHint() {
        return editHint;
    }

    @Nullable
    public Boolean getTodoCompleted() {
        return todoCompleted;
    }

    @Nullable
    public Boolean getOverdueDone() {
        return overdueDone;
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
    public Long getDrugId() {
        return drugId;
    }

    @Nullable
    public Long getExecutorId() {
        return executorId;
    }
}
