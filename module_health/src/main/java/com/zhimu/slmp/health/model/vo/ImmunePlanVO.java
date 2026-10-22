package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-D02 免疫计划列表项（API-D04、D06–D08 返回）。actions：员工只有 REGISTER。
 */
public class ImmunePlanVO {

    private long id;
    private int version;
    /** 届时日龄 */
    private int ageAtPlan;
    @Nullable
    private BigDecimal dose;
    @Nullable
    private String vaccineName;
    /** BY_DAY_AGE / BY_DATE */
    @Nullable
    private String planMode;
    @Nullable
    private String planDate;
    @Nullable
    private String dueTime;
    @Nullable
    private String responsibleText;
    /** PENDING / MISSED / POSTPONED / DONE / CANCELLED */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String doseUnit;
    @Nullable
    private String methodCode;
    @Nullable
    private String methodName;
    private boolean isToday;
    /** 过截止时刻仍未登记（未到漏免） */
    private boolean overdue;
    @Nullable
    private List<String> actions;
    @Nullable
    private String immuneRound;
    /** 员工不输出 */
    @Nullable
    private String remindText;
    @Nullable
    private String postponeFromDate;
    @Nullable
    private String postponedByName;
    @Nullable
    private String recordDate;
    @Nullable
    private String recordUserName;
    /** USER_DELETED / BATCH_ENDED */
    @Nullable
    private String cancelType;
    @Nullable
    private String cancelledByName;
    @Nullable
    private Long recordId;
    @Nullable
    private Integer recordQuantity;
    @Nullable
    private Boolean postponed;
    @Nullable
    private Boolean backfill;
    @Nullable
    private Boolean manualDone;

    public long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public int getAgeAtPlan() {
        return ageAtPlan;
    }

    @Nullable
    public BigDecimal getDose() {
        return dose;
    }

    @Nullable
    public String getVaccineName() {
        return vaccineName;
    }

    @Nullable
    public String getPlanMode() {
        return planMode;
    }

    @Nullable
    public String getPlanDate() {
        return planDate;
    }

    @Nullable
    public String getDueTime() {
        return dueTime;
    }

    @Nullable
    public String getResponsibleText() {
        return responsibleText;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
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

    public boolean isToday() {
        return isToday;
    }

    public boolean isOverdue() {
        return overdue;
    }

    @NonNull
    public List<String> getActions() {
        return actions == null ? Collections.emptyList() : actions;
    }

    @Nullable
    public String getImmuneRound() {
        return immuneRound;
    }

    @Nullable
    public String getRemindText() {
        return remindText;
    }

    @Nullable
    public String getPostponeFromDate() {
        return postponeFromDate;
    }

    @Nullable
    public String getPostponedByName() {
        return postponedByName;
    }

    @Nullable
    public String getRecordDate() {
        return recordDate;
    }

    @Nullable
    public String getRecordUserName() {
        return recordUserName;
    }

    @Nullable
    public String getCancelType() {
        return cancelType;
    }

    @Nullable
    public String getCancelledByName() {
        return cancelledByName;
    }

    @Nullable
    public Long getRecordId() {
        return recordId;
    }

    @Nullable
    public Integer getRecordQuantity() {
        return recordQuantity;
    }

    @Nullable
    public Boolean getPostponed() {
        return postponed;
    }

    @Nullable
    public Boolean getBackfill() {
        return backfill;
    }

    @Nullable
    public Boolean getManualDone() {
        return manualDone;
    }
}
