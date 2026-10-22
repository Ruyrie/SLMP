package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 当前养殖场上下文（API-F02、F03），由 FarmContext 持久化为 farm_context_json。
 */
public class FarmContextVO {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_STAFF = "STAFF";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_FROZEN = "FROZEN";

    private long farmId;
    @Nullable
    private String name;
    @Nullable
    private String statusText;
    @Nullable
    private String roleText;
    /** PENDING / REJECTED / NORMAL / DISABLED / FROZEN */
    @Nullable
    private String status;
    /** OWNER / MANAGER / STAFF */
    @Nullable
    private String role;
    @Nullable
    private String speciesText;
    @Nullable
    private String mainCategoryName;
    /** HEAD / BIRD */
    @Nullable
    private String countUnit;
    @Nullable
    private String restrictTip;
    @Nullable
    private Long mainCategoryId;
    /** 只对员工 */
    @Nullable
    private List<Long> myPenIds;
    @Nullable
    private FarmPermissionVO permissions;

    public long getFarmId() {
        return farmId;
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
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getRole() {
        return role;
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
    public String getRestrictTip() {
        return restrictTip;
    }

    @Nullable
    public Long getMainCategoryId() {
        return mainCategoryId;
    }

    @NonNull
    public List<Long> getMyPenIds() {
        return myPenIds == null ? Collections.emptyList() : myPenIds;
    }

    @Nullable
    public FarmPermissionVO getPermissions() {
        return permissions;
    }
}
