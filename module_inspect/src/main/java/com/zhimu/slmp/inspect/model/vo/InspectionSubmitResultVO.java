package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.Nullable;

/**
 * API-I07 / I08 / I09 / I25 登记、修改、补录的结果。
 */
public class InspectionSubmitResultVO {

    private long inspectionId;
    private int version;
    private String penName;
    private String inspectDate;
    private boolean isAbnormal;
    private boolean isOverdue;
    private boolean isBackfill;
    private boolean todoCompleted;
    private String locationStatus;
    private Integer distanceM;

    public long getInspectionId() { return inspectionId; }

    public int getVersion() { return version; }

    public String getPenName() { return penName; }

    public String getInspectDate() { return inspectDate; }

    public boolean isAbnormal() { return isAbnormal; }

    public boolean isOverdue() { return isOverdue; }

    public boolean isBackfill() { return isBackfill; }

    public boolean isTodoCompleted() { return todoCompleted; }

    @Nullable
    public String getLocationStatus() { return locationStatus; }

    @Nullable
    public Integer getDistanceM() { return distanceM; }
}
