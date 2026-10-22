package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.OptionVO;

import java.util.Collections;
import java.util.List;

/**
 * API-L10 栏舍表单选项（L05）：栏舍类型与同畜种启用品类。
 */
public class PenFormOptionsVO {

    private List<OptionVO> penTypes;
    private List<CategoryOption> categories;

    public List<OptionVO> getPenTypes() { return penTypes == null ? Collections.emptyList() : penTypes; }

    public List<CategoryOption> getCategories() { return categories == null ? Collections.emptyList() : categories; }

    /** 适用品类选项 */
    public static class CategoryOption {
        private long categoryId;
        private String categoryName;
        /** COUNT_UNIT：HEAD / BIRD */
        private String countUnit;
        private String countUnitText;
        private boolean isBreeding;

        public long getCategoryId() { return categoryId; }

        public String getCategoryName() { return categoryName; }

        public String getCountUnit() { return countUnit; }

        @Nullable
        public String getCountUnitText() { return countUnitText; }

        public boolean isBreeding() { return isBreeding; }
    }
}
