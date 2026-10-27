package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

/**
 * 登记表单带入（API-B11）：按 type 返回不同字段。
 */
public class RegisterContextVO {

    /** REGISTER_TYPE */
    @Nullable
    private String type;
    @Nullable
    private BreedingAnimalOptionVO animal;
    @Nullable
    private String breedName;
    @Nullable
    private Integer gestationDays;
    @Nullable
    private String minMatingDate;
    @Nullable
    private Long cycleId;
    @Nullable
    private String firstMatingDate;
    @Nullable
    private Integer daysSinceMating;
    @Nullable
    private String expectedDueDate;
    @Nullable
    private Integer daysToDue;
    @Nullable
    private DefaultPen defaultPen;
    @Nullable
    private OffspringBreed offspringBreed;
    @Nullable
    private Long farrowingId;
    @Nullable
    private String farrowDate;
    @Nullable
    private Integer nursingDays;
    @Nullable
    private Integer liveBorn;

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public BreedingAnimalOptionVO getAnimal() {
        return animal;
    }

    @Nullable
    public String getBreedName() {
        return breedName;
    }

    @Nullable
    public Integer getGestationDays() {
        return gestationDays;
    }

    @Nullable
    public String getMinMatingDate() {
        return minMatingDate;
    }

    @Nullable
    public Long getCycleId() {
        return cycleId;
    }

    @Nullable
    public String getFirstMatingDate() {
        return firstMatingDate;
    }

    @Nullable
    public Integer getDaysSinceMating() {
        return daysSinceMating;
    }

    @Nullable
    public String getExpectedDueDate() {
        return expectedDueDate;
    }

    @Nullable
    public Integer getDaysToDue() {
        return daysToDue;
    }

    @Nullable
    public DefaultPen getDefaultPen() {
        return defaultPen;
    }

    @Nullable
    public OffspringBreed getOffspringBreed() {
        return offspringBreed;
    }

    @Nullable
    public Long getFarrowingId() {
        return farrowingId;
    }

    @Nullable
    public String getFarrowDate() {
        return farrowDate;
    }

    @Nullable
    public Integer getNursingDays() {
        return nursingDays;
    }

    @Nullable
    public Integer getLiveBorn() {
        return liveBorn;
    }

    /** 产仔默认入栏栏舍（模块04 口径，只计批次存栏） */
    public static class DefaultPen {

        private long penId;
        @Nullable
        private String penName;
        private int capacity;
        private int currentCount;
        @Nullable
        private String unit;

        public long getPenId() {
            return penId;
        }

        @Nullable
        public String getPenName() {
            return penName;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getCurrentCount() {
            return currentCount;
        }

        @Nullable
        public String getUnit() {
            return unit;
        }
    }

    /** 后代品种（按后代品种规则推导） */
    public static class OffspringBreed {

        private long breedId;
        @Nullable
        private Long categoryId;

        @Nullable
        public Long getCategoryId() { return categoryId; }
        @Nullable
        private String breedName;

        public long getBreedId() {
            return breedId;
        }

        @Nullable
        public String getBreedName() {
            return breedName;
        }
    }

    /** 改选入栏栏舍时由下拉选项构造 */
    public static DefaultPen pen(long penId, @Nullable String penName, int capacity, int currentCount,
                                 @Nullable String unit) {
        DefaultPen p = new DefaultPen();
        p.penId = penId;
        p.penName = penName;
        p.capacity = capacity;
        p.currentCount = currentCount;
        p.unit = unit;
        return p;
    }
}
