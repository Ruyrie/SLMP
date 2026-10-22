package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 启用的品类与品种树（API-U09，数组按 sort_no）。
 */
public class CategoryVO {

    private long id;
    @Nullable
    private String name;
    /** PIG / POULTRY / CATTLE / SHEEP */
    @Nullable
    private String species;
    @Nullable
    private String speciesText;
    /** HEAD / BIRD */
    @Nullable
    private String countUnit;
    @Nullable
    private String countUnitText;
    private boolean isBreeding;
    @Nullable
    private List<CategoryBreedVO> breeds;

    public long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getSpecies() {
        return species;
    }

    @Nullable
    public String getSpeciesText() {
        return speciesText;
    }

    @Nullable
    public String getCountUnit() {
        return countUnit;
    }

    @Nullable
    public String getCountUnitText() {
        return countUnitText;
    }

    public boolean isBreeding() {
        return isBreeding;
    }

    @NonNull
    public List<CategoryBreedVO> getBreeds() {
        return breeds == null ? Collections.emptyList() : breeds;
    }
}
