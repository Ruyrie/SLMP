package com.zhimu.slmp.breed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.CategoryBreedVO;
import com.zhimu.slmp.common.model.vo.CategoryVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 在品类品种树（API-U09）中查找品种与所属品类：B04 改选栏舍的 category_id、B07 孵化期、档案表单的品种候选。
 */
public final class BreedLookup {

    private BreedLookup() {
    }

    @Nullable
    public static CategoryVO categoryOfBreed(@Nullable List<CategoryVO> categories, @Nullable Long breedId,
                                             @Nullable String breedName) {
        if (categories == null) {
            return null;
        }
        for (CategoryVO c : categories) {
            for (CategoryBreedVO b : c.getBreeds()) {
                if ((breedId != null && b.getId() == breedId)
                        || (breedId == null && breedName != null && breedName.equals(b.getName()))) {
                    return c;
                }
            }
        }
        return null;
    }

    @Nullable
    public static CategoryBreedVO breed(@Nullable List<CategoryVO> categories, @Nullable Long breedId,
                                        @Nullable String breedName) {
        CategoryVO c = categoryOfBreed(categories, breedId, breedName);
        if (c == null) {
            return null;
        }
        for (CategoryBreedVO b : c.getBreeds()) {
            if ((breedId != null && b.getId() == breedId) || (breedId == null && breedName != null
                    && breedName.equals(b.getName()))) {
                return b;
            }
        }
        return null;
    }

    /** 档案表单品种候选：与本场主要品类同畜种、用途为种用、设有妊娠期的品种 */
    @NonNull
    public static List<CategoryBreedVO> breedingBreeds(@Nullable List<CategoryVO> categories,
                                                       @Nullable Long mainCategoryId) {
        List<CategoryBreedVO> out = new ArrayList<>();
        if (categories == null) {
            return out;
        }
        String species = null;
        for (CategoryVO c : categories) {
            if (mainCategoryId != null && c.getId() == mainCategoryId) {
                species = c.getSpecies();
            }
        }
        for (CategoryVO c : categories) {
            if (!c.isBreeding() || (species != null && !species.equals(c.getSpecies()))
                    || "POULTRY".equals(c.getSpecies())) {
                continue;
            }
            for (CategoryBreedVO b : c.getBreeds()) {
                if (b.getGestationDays() != null) {
                    out.add(b);
                }
            }
        }
        return out;
    }
}
