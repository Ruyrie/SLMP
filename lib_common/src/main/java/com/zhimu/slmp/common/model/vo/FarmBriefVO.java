package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 养殖场切换器与“全部养殖场”汇总中的养殖场。
 */
public class FarmBriefVO {

    private long farmId;
    @Nullable
    private String name;
    @Nullable
    private String speciesText;
    /** PENDING / REJECTED / NORMAL / DISABLED / FROZEN */
    @Nullable
    private String status;
    @Nullable
    private String statusText;
    @Nullable
    private String role;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getSpeciesText() {
        return speciesText;
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
    public String getRole() {
        return role;
    }
}
