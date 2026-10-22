package com.zhimu.slmp.inspect.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * API-I10 巡检详情（I02 / I03 只读、修改时嵌入 InspectionFormVO.record）。edit_mode：NONE / DIRECT / CHANGE_REQUEST。
 */
public class InspectionDetailVO {

    private long inspectionId;
    private long penId;
    private String penName;
    private List<String> batchNos;
    private String inspectDate;
    private String inspectStatus;
    private String inspectStatusText;
    private List<ItemResult> itemResults;
    private boolean isAbnormal;
    private String abnormalSource;
    private String abnormalSummary;
    private Boolean isManualMarked;
    private String manualReason;
    private String manualMarkedByName;
    private String manualMarkedAt;
    private String remark;
    private List<MediaItemVO> media;
    private UserBriefVO registrant;
    private UserBriefVO executor;
    private boolean isProxy;
    private String registeredAt;
    private String locationStatus;
    private String locationStatusText;
    private Integer distanceM;
    private boolean isOverdue;
    private boolean isBackfill;
    private Integer templateVersion;
    private Long reportId;
    private String reportStatus;
    private String editMode;
    private ChangeRequestStateVO changeState;
    private boolean hasRevisions;
    private int version;

    public long getInspectionId() { return inspectionId; }

    public long getPenId() { return penId; }

    public String getPenName() { return penName; }

    @NonNull
    public List<String> getBatchNos() { return batchNos == null ? Collections.emptyList() : batchNos; }

    public String getInspectDate() { return inspectDate; }

    public String getInspectStatus() { return inspectStatus; }

    public String getInspectStatusText() { return inspectStatusText; }

    @NonNull
    public List<ItemResult> getItemResults() { return itemResults == null ? Collections.emptyList() : itemResults; }

    public boolean isAbnormal() { return isAbnormal; }

    @Nullable
    public String getAbnormalSource() { return abnormalSource; }

    @Nullable
    public String getAbnormalSummary() { return abnormalSummary; }

    public boolean isManualMarked() { return Boolean.TRUE.equals(isManualMarked); }

    @Nullable
    public String getManualReason() { return manualReason; }

    @Nullable
    public String getManualMarkedByName() { return manualMarkedByName; }

    @Nullable
    public String getManualMarkedAt() { return manualMarkedAt; }

    @Nullable
    public String getRemark() { return remark; }

    @NonNull
    public List<MediaItemVO> getMedia() { return media == null ? Collections.emptyList() : media; }

    @Nullable
    public UserBriefVO getRegistrant() { return registrant; }

    @Nullable
    public UserBriefVO getExecutor() { return executor; }

    public boolean isProxy() { return isProxy; }

    @Nullable
    public String getRegisteredAt() { return registeredAt; }

    @Nullable
    public String getLocationStatus() { return locationStatus; }

    @Nullable
    public String getLocationStatusText() { return locationStatusText; }

    @Nullable
    public Integer getDistanceM() { return distanceM; }

    public boolean isOverdue() { return isOverdue; }

    public boolean isBackfill() { return isBackfill; }

    @Nullable
    public Integer getTemplateVersion() { return templateVersion; }

    @Nullable
    public Long getReportId() { return reportId; }

    @Nullable
    public String getReportStatus() { return reportStatus; }

    public String getEditMode() { return editMode; }

    @Nullable
    public ChangeRequestStateVO getChangeState() { return changeState; }

    public boolean isHasRevisions() { return hasRevisions; }

    public int getVersion() { return version; }

    public static class ItemResult {
        private long itemId;
        private String itemName;
        private long optionId;
        private String optionName;
        private boolean optionAbnormal;
        private int sortNo;

        public long getItemId() { return itemId; }

        public String getItemName() { return itemName; }

        public long getOptionId() { return optionId; }

        public String getOptionName() { return optionName; }

        public boolean isOptionAbnormal() { return optionAbnormal; }

        public int getSortNo() { return sortNo; }
    }
}
