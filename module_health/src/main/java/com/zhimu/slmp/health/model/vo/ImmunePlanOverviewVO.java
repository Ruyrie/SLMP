package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-D01 批次免疫概况（D01、D02；API-D03、D05 返回刷新后的同结构）。
 * 员工不输出 pre_entry_items、pre_entry_pending_count。
 */
public class ImmunePlanOverviewVO {

    private long batchId;
    /** 主栏舍（多栏舍时取在栏数最多的） */
    private long penId;
    private long categoryId;
    /** 入栏日龄，供前端算届时日龄 */
    private int entryAgeDays;
    /** 今天日龄 */
    private int dayAge;
    private int currentCount;
    @Nullable
    private String batchNo;
    @Nullable
    private String penName;
    @Nullable
    private String breedName;
    @Nullable
    private String entryDate;
    private boolean batchEnded;
    /** 品种有已发布的免疫模板 */
    private boolean hasTemplate;
    /** 当前用户可管理（所有者、场长） */
    private boolean canManage;
    @Nullable
    private Counts counts;
    @Nullable
    private List<PreEntryItemVO> preEntryItems;
    @Nullable
    private Integer preEntryPendingCount;

    public long getBatchId() {
        return batchId;
    }

    public long getPenId() {
        return penId;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public int getEntryAgeDays() {
        return entryAgeDays;
    }

    public int getDayAge() {
        return dayAge;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getPenName() {
        return penName;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    @Nullable
    public String getEntryDate() {
        return entryDate;
    }

    public boolean isBatchEnded() {
        return batchEnded;
    }

    public boolean isHasTemplate() {
        return hasTemplate;
    }

    public boolean isCanManage() {
        return canManage;
    }

    @Nullable
    public Counts getCounts() {
        return counts;
    }

    @NonNull
    public List<PreEntryItemVO> getPreEntryItems() {
        return preEntryItems == null ? Collections.emptyList() : preEntryItems;
    }

    @Nullable
    public Integer getPreEntryPendingCount() {
        return preEntryPendingCount;
    }

    /** 各状态数量（all 不含入栏前项目） */
    public static class Counts {

        private int all;
        private int pending;
        private int missed;
        private int postponed;
        private int done;
        private int cancelled;

        public int getAll() {
            return all;
        }

        public int getPending() {
            return pending;
        }

        public int getMissed() {
            return missed;
        }

        public int getPostponed() {
            return postponed;
        }

        public int getDone() {
            return done;
        }

        public int getCancelled() {
            return cancelled;
        }
    }

    /** 入栏前项目 */
    public static class PreEntryItemVO {

        private long planId;
        @Nullable
        private String vaccineName;
        @Nullable
        private String immuneRound;
        private int templateAgeDay;
        private int version;

        public long getPlanId() {
            return planId;
        }

        @Nullable
        public String getVaccineName() {
            return vaccineName;
        }

        @Nullable
        public String getImmuneRound() {
            return immuneRound;
        }

        public int getTemplateAgeDay() {
            return templateAgeDay;
        }

        public int getVersion() {
            return version;
        }
    }
}
