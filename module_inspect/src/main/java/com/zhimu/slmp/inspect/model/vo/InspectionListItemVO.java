package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-I02 某日栏舍清单的一行（I01）：display_status 为 ABNORMAL 异常 / PENDING 未巡 / DONE 已巡 / MISSED 漏巡；
 * abnormal_source 为 CHECK 检查结果异常 / MANUAL 手动标记；location_status 为 ON_SITE / OFF_SITE / NO_LOCATION。
 */
public class InspectionListItemVO {

    public static final String STATUS_ABNORMAL = "ABNORMAL";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_MISSED = "MISSED";
    public static final String SOURCE_CHECK = "CHECK";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String LOC_ON_SITE = "ON_SITE";
    public static final String LOC_OFF_SITE = "OFF_SITE";
    public static final String LOC_NONE = "NO_LOCATION";

    private long inspectionId;
    private long penId;
    private String penCode;
    private String penName;
    private Long batchId;
    private List<String> batchNos;
    private int stockCount;
    private List<String> responsibleNames;
    private String displayStatus;
    private String displayStatusText;
    private String abnormalSource;
    private String abnormalSummary;
    private String manualReason;
    private String registrantName;
    private String registeredAt;
    private String locationStatus;
    private Integer distanceM;
    private boolean isOverdue;
    private boolean isBackfill;
    private boolean deadlinePassed;
    private Long reportId;
    private String reportStatus;
    private boolean canRegister;
    private boolean canModify;
    private boolean canReport;
    private boolean canViewDiagnosis;
    private int version;

    public long getInspectionId() { return inspectionId; }

    public long getPenId() { return penId; }

    public String getPenCode() { return penCode; }

    public String getPenName() { return penName; }

    @Nullable
    public Long getBatchId() { return batchId; }

    @NonNull
    public List<String> getBatchNos() { return batchNos == null ? Collections.emptyList() : batchNos; }

    public int getStockCount() { return stockCount; }

    /** 空数组表示无负责员工 */
    @NonNull
    public List<String> getResponsibleNames() {
        return responsibleNames == null ? Collections.emptyList() : responsibleNames;
    }

    public String getDisplayStatus() { return displayStatus; }

    public String getDisplayStatusText() { return displayStatusText; }

    @Nullable
    public String getAbnormalSource() { return abnormalSource; }

    @Nullable
    public String getAbnormalSummary() { return abnormalSummary; }

    @Nullable
    public String getManualReason() { return manualReason; }

    @Nullable
    public String getRegistrantName() { return registrantName; }

    @Nullable
    public String getRegisteredAt() { return registeredAt; }

    @Nullable
    public String getLocationStatus() { return locationStatus; }

    @Nullable
    public Integer getDistanceM() { return distanceM; }

    public boolean isOverdue() { return isOverdue; }

    public boolean isBackfill() { return isBackfill; }

    public boolean isDeadlinePassed() { return deadlinePassed; }

    @Nullable
    public Long getReportId() { return reportId; }

    @Nullable
    public String getReportStatus() { return reportStatus; }

    public boolean isCanRegister() { return canRegister; }

    public boolean isCanModify() { return canModify; }

    public boolean isCanReport() { return canReport; }

    public boolean isCanViewDiagnosis() { return canViewDiagnosis; }

    public int getVersion() { return version; }

    public boolean isAbnormal() { return STATUS_ABNORMAL.equals(displayStatus); }

    public boolean isManualAbnormal() { return isAbnormal() && SOURCE_MANUAL.equals(abnormalSource); }

    public boolean isPending() { return STATUS_PENDING.equals(displayStatus); }

    public boolean isDone() { return STATUS_DONE.equals(displayStatus); }

    public boolean isReported() { return reportId != null; }
}
