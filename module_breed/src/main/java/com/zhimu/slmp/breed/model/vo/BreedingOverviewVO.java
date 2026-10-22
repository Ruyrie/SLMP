package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 繁育概况（API-B01）：B01 分段数量与临产横幅、B02 分段数量与跨养殖场横幅。
 * 员工不输出 counts、due_soon、due_soon_total、pen_options。
 */
public class BreedingOverviewVO {

    /** LIVESTOCK / POULTRY */
    @Nullable
    private String farmKind;
    @Nullable
    private String farmKindText;
    /** 母猪 / 母牛 / 母羊 */
    @Nullable
    private String animalLabel;
    @Nullable
    private String countUnit;
    @Nullable
    private String countUnitText;
    @Nullable
    private Counts counts;
    private int farrowDueCount;
    private int hatchDueCount;
    @Nullable
    private List<DueSoonItem> dueSoon;
    @Nullable
    private Integer dueSoonTotal;
    @Nullable
    private List<PenBriefVO> penOptions;
    private boolean hasIncubation;
    @Nullable
    private List<SwitchFarm> switchFarms;

    @Nullable
    public String getFarmKind() {
        return farmKind;
    }

    @Nullable
    public String getFarmKindText() {
        return farmKindText;
    }

    @Nullable
    public String getAnimalLabel() {
        return animalLabel;
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
    public Counts getCounts() {
        return counts;
    }

    public int getFarrowDueCount() {
        return farrowDueCount;
    }

    public int getHatchDueCount() {
        return hatchDueCount;
    }

    @NonNull
    public List<DueSoonItem> getDueSoon() {
        return dueSoon == null ? Collections.emptyList() : dueSoon;
    }

    @Nullable
    public Integer getDueSoonTotal() {
        return dueSoonTotal;
    }

    @NonNull
    public List<PenBriefVO> getPenOptions() {
        return penOptions == null ? Collections.emptyList() : penOptions;
    }

    public boolean isHasIncubation() {
        return hasIncubation;
    }

    @NonNull
    public List<SwitchFarm> getSwitchFarms() {
        return switchFarms == null ? Collections.emptyList() : switchFarms;
    }

    /** 分段计数：all 不含已淘汰和死亡 */
    public static class Counts {

        private int all;
        private int empty;
        private int mated;
        private int pregnant;
        private int lactating;
        private int exited;

        public int getAll() {
            return all;
        }

        public int getEmpty() {
            return empty;
        }

        public int getMated() {
            return mated;
        }

        public int getPregnant() {
            return pregnant;
        }

        public int getLactating() {
            return lactating;
        }

        public int getExited() {
            return exited;
        }
    }

    /** 距预产 ≤ 3 天（含今天与已超期）的母猪 */
    public static class DueSoonItem {

        private long animalId;
        @Nullable
        private String earTag;
        @Nullable
        private String expectedDueDate;
        private int daysToDue;

        public long getAnimalId() {
            return animalId;
        }

        @Nullable
        public String getEarTag() {
            return earTag;
        }

        @Nullable
        public String getExpectedDueDate() {
            return expectedDueDate;
        }

        public int getDaysToDue() {
            return daysToDue;
        }
    }

    /** 本账号其他有孵化记录的禽类养殖场 */
    public static class SwitchFarm {

        private long farmId;
        @Nullable
        private String farmName;
        private int hatchDueCount;

        public long getFarmId() {
            return farmId;
        }

        @Nullable
        public String getFarmName() {
            return farmName;
        }

        public int getHatchDueCount() {
            return hatchDueCount;
        }
    }
}
