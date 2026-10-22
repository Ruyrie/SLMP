package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 待出雏列表项（API-B23，B02 待出雏）。
 */
public class HatchDueItemVO {

    /** DUE_GROUP */
    @Nullable
    private String group;
    @Nullable
    private String groupText;
    private long penId;
    @Nullable
    private String penName;
    @Nullable
    private String remindBeforeDate;
    @Nullable
    private String remindBeforeSentAt;
    @Nullable
    private String remindDueSentAt;
    @Nullable
    private String reminderText;
    @Nullable
    private List<String> staffNames;
    /** 员工恒为 false */
    private boolean canRegister;
    private long incubationId;
    private long breedingBatchId;
    /** 栏舍名 · 批次号 */
    @Nullable
    private String flockText;
    @Nullable
    private String setDate;
    private int setCount;
    /** 未照蛋不输出 */
    @Nullable
    private Integer fertileCount;
    @Nullable
    private String expectedHatchDate;
    private int daysToHatch;
    /** 员工恒为 false */
    private boolean canOpen;

    @Nullable
    public String getGroup() {
        return group;
    }

    @Nullable
    public String getGroupText() {
        return groupText;
    }

    public long getPenId() {
        return penId;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getRemindBeforeDate() {
        return remindBeforeDate;
    }

    @Nullable
    public String getRemindBeforeSentAt() {
        return remindBeforeSentAt;
    }

    @Nullable
    public String getRemindDueSentAt() {
        return remindDueSentAt;
    }

    @Nullable
    public String getReminderText() {
        return reminderText;
    }

    @NonNull
    public List<String> getStaffNames() {
        return staffNames == null ? Collections.emptyList() : staffNames;
    }

    public boolean isCanRegister() {
        return canRegister;
    }

    public long getIncubationId() {
        return incubationId;
    }

    public long getBreedingBatchId() {
        return breedingBatchId;
    }

    @Nullable
    public String getFlockText() {
        return flockText;
    }

    @Nullable
    public String getSetDate() {
        return setDate;
    }

    public int getSetCount() {
        return setCount;
    }

    @Nullable
    public Integer getFertileCount() {
        return fertileCount;
    }

    @Nullable
    public String getExpectedHatchDate() {
        return expectedHatchDate;
    }

    public int getDaysToHatch() {
        return daysToHatch;
    }

    public boolean isCanOpen() {
        return canOpen;
    }
}
