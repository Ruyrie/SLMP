package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 待产列表项（API-B22，B01 待产分段与 B02 待产）。
 */
public class FarrowDueItemVO {

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
    private long animalId;
    @Nullable
    private String earTag;
    @Nullable
    private String breedName;
    private int parity;
    @Nullable
    private String firstMatingDate;
    @Nullable
    private String expectedDueDate;
    /** 可为负 */
    private int daysToDue;
    /** 员工恒为 false */
    private boolean canOpenArchive;

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

    public long getAnimalId() {
        return animalId;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    public int getParity() {
        return parity;
    }

    @Nullable
    public String getFirstMatingDate() {
        return firstMatingDate;
    }

    @Nullable
    public String getExpectedDueDate() {
        return expectedDueDate;
    }

    public int getDaysToDue() {
        return daysToDue;
    }

    public boolean isCanOpenArchive() {
        return canOpenArchive;
    }
}
