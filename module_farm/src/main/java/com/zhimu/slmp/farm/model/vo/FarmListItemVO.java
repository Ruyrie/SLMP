package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * F01 切换养殖场列表项（API-F01）。员工行不输出品类与存栏，只有 my_pen_count。
 */
public class FarmListItemVO {

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
    /** CREATE / INVITE */
    @Nullable
    private String joinType;
    @Nullable
    private String speciesText;
    @Nullable
    private String mainCategoryName;
    @Nullable
    private String countUnit;
    @Nullable
    private String countUnitText;
    /** 所有者、场长且 NORMAL / DISABLED */
    @Nullable
    private Integer currentStock;
    /** PENDING / REJECTED */
    @Nullable
    private Integer scale;
    /** 员工行 */
    @Nullable
    private Integer myPenCount;
    private boolean isCurrent;
    private boolean canSwitch;
    private boolean canQuit;
    @Nullable
    private String lastSwitchAt;

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
    public String getJoinType() {
        return joinType;
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
    public String getCountUnitText() {
        return countUnitText;
    }

    @Nullable
    public Integer getCurrentStock() {
        return currentStock;
    }

    @Nullable
    public Integer getScale() {
        return scale;
    }

    @Nullable
    public Integer getMyPenCount() {
        return myPenCount;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public boolean isCanSwitch() {
        return canSwitch;
    }

    public boolean isCanQuit() {
        return canQuit;
    }

    @Nullable
    public String getLastSwitchAt() {
        return lastSwitchAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FarmListItemVO)) {
            return false;
        }
        FarmListItemVO that = (FarmListItemVO) o;
        return farmId == that.farmId && isCurrent == that.isCurrent && canSwitch == that.canSwitch
                && Objects.equals(name, that.name) && Objects.equals(status, that.status)
                && Objects.equals(role, that.role) && Objects.equals(currentStock, that.currentStock)
                && Objects.equals(myPenCount, that.myPenCount)
                && Objects.equals(mainCategoryName, that.mainCategoryName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(farmId, name, status, role, isCurrent);
    }
}
