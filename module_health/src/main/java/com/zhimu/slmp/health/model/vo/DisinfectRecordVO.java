package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D38–D40 消毒记录。单价、金额为管理视图；toast、todo_completed 仅登记返回。
 * drug_id、executor_id 为客户端补充的可空字段（修改回填用）。
 */
public class DisinfectRecordVO {

    private long id;
    private long penId;
    private long drugLotId;
    private int version;
    @Nullable
    private BigDecimal usageQty;
    private boolean concentrationConfirmed;
    private boolean inBatchCost;
    private boolean inExecRate;
    private boolean backfill;
    @Nullable
    private String recordType;
    @Nullable
    private String recordTypeText;
    @Nullable
    private String penName;
    @Nullable
    private String actualTime;
    @Nullable
    private String executorName;
    @Nullable
    private String creatorName;
    @Nullable
    private String lotNo;
    @Nullable
    private String disinfectantName;
    @Nullable
    private String usageUnit;
    @Nullable
    private String concentration;
    @Nullable
    private String methodCode;
    @Nullable
    private String methodName;
    @Nullable
    private ChangeRequestStateVO changeState;
    @Nullable
    private List<String> actions;
    @Nullable
    private Long taskId;
    @Nullable
    private String taskDate;
    @Nullable
    private String planText;
    @Nullable
    private String planConcentration;
    @Nullable
    private String toast;
    @Nullable
    private Boolean todoCompleted;
    @Nullable
    private List<MediaItemVO> photos;
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

    public long getPenId() {
        return penId;
    }

    public long getDrugLotId() {
        return drugLotId;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public BigDecimal getUsageQty() {
        return usageQty;
    }

    public boolean isConcentrationConfirmed() {
        return concentrationConfirmed;
    }

    public boolean isInBatchCost() {
        return inBatchCost;
    }

    public boolean isInExecRate() {
        return inExecRate;
    }

    public boolean isBackfill() {
        return backfill;
    }

    @Nullable
    public String getRecordType() {
        return recordType;
    }

    @Nullable
    public String getRecordTypeText() {
        return recordTypeText;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getActualTime() {
        return actualTime;
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
    public String getLotNo() {
        return lotNo;
    }

    @Nullable
    public String getDisinfectantName() {
        return disinfectantName;
    }

    @Nullable
    public String getUsageUnit() {
        return usageUnit;
    }

    @Nullable
    public String getConcentration() {
        return concentration;
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
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    @NonNull
    public List<String> getActions() {
        return actions == null ? Collections.emptyList() : actions;
    }

    @Nullable
    public Long getTaskId() {
        return taskId;
    }

    @Nullable
    public String getTaskDate() {
        return taskDate;
    }

    @Nullable
    public String getPlanText() {
        return planText;
    }

    @Nullable
    public String getPlanConcentration() {
        return planConcentration;
    }

    @Nullable
    public String getToast() {
        return toast;
    }

    @Nullable
    public Boolean getTodoCompleted() {
        return todoCompleted;
    }

    @NonNull
    public List<MediaItemVO> getPhotos() {
        return photos == null ? Collections.emptyList() : photos;
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
