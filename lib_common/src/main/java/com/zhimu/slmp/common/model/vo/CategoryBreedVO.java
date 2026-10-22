package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 品类下的品种（API-U09 breeds[]）。
 */
public class CategoryBreedVO {

    private long id;
    @Nullable
    private String name;
    @Nullable
    private Integer gestationDays;
    @Nullable
    private Integer incubationDays;
    @Nullable
    private Integer pregnancyCheckDays;
    @Nullable
    private Long offspringBreedId;
    private boolean hasStdWeight;
    private boolean hasFeedStd;
    private boolean hasImmuneTemplate;

    public long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public Integer getGestationDays() {
        return gestationDays;
    }

    @Nullable
    public Integer getIncubationDays() {
        return incubationDays;
    }

    @Nullable
    public Integer getPregnancyCheckDays() {
        return pregnancyCheckDays;
    }

    @Nullable
    public Long getOffspringBreedId() {
        return offspringBreedId;
    }

    public boolean isHasStdWeight() {
        return hasStdWeight;
    }

    public boolean isHasFeedStd() {
        return hasFeedStd;
    }

    public boolean isHasImmuneTemplate() {
        return hasImmuneTemplate;
    }
}
