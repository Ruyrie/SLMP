package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FarmPermissionVO;

/**
 * 养殖场信息（API-F09、F11、F12）。pending_transfer 只给所有者。
 */
public class FarmDetailVO {

    private long farmId;
    private int currentStock;
    @Nullable
    private String name;
    @Nullable
    private String statusText;
    @Nullable
    private String roleText;
    @Nullable
    private String speciesText;
    @Nullable
    private String mainCategoryName;
    @Nullable
    private String countUnit;
    @Nullable
    private String status;
    @Nullable
    private String role;
    @Nullable
    private String approvedAt;
    @Nullable
    private String disabledAt;
    @Nullable
    private String restrictTip;
    @Nullable
    private FarmBasicVO basic;
    @Nullable
    private RegistrationVO registration;
    @Nullable
    private MemberSummaryVO memberSummary;
    @Nullable
    private FarmPermissionVO permissions;
    @Nullable
    private ChangeBriefVO pendingChange;
    @Nullable
    private ChangeBriefVO rejectedChange;
    @Nullable
    private TransferVO pendingTransfer;

    public long getFarmId() {
        return farmId;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }

    @Nullable
    public String getRoleText() {
        return roleText;
    }

    @Nullable
    public String getSpeciesText() {
        return speciesText;
    }

    @Nullable
    public String getMainCategoryName() {
        return mainCategoryName;
    }

    @Nullable
    public String getCountUnit() {
        return countUnit;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    @Nullable
    public String getApprovedAt() {
        return approvedAt;
    }

    @Nullable
    public String getDisabledAt() {
        return disabledAt;
    }

    @Nullable
    public String getRestrictTip() {
        return restrictTip;
    }

    @Nullable
    public FarmBasicVO getBasic() {
        return basic;
    }

    @Nullable
    public RegistrationVO getRegistration() {
        return registration;
    }

    @Nullable
    public MemberSummaryVO getMemberSummary() {
        return memberSummary;
    }

    @Nullable
    public FarmPermissionVO getPermissions() {
        return permissions;
    }

    @Nullable
    public ChangeBriefVO getPendingChange() {
        return pendingChange;
    }

    @Nullable
    public ChangeBriefVO getRejectedChange() {
        return rejectedChange;
    }

    @Nullable
    public TransferVO getPendingTransfer() {
        return pendingTransfer;
    }
}
