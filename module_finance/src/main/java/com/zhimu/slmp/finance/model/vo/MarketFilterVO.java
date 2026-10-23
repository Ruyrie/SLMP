package com.zhimu.slmp.finance.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * API-E30 行情筛选项与默认值
 * <p>字段为 JSON 字段的小驼峰（Gson 下划线命名策略），只读。
 */
public class MarketFilterVO {

    private List<Province> provinces;
    private List<CategoryOption> categories;
    private List<BreedOption> breeds;
    private List<PriceTypeOption> priceTypes;
    private MarketDefaults defaults;
    @Nullable
    private String defaultDesc;

    @NonNull
    public List<Province> getProvinces() {
        return provinces == null ? java.util.Collections.emptyList() : provinces;
    }

    @NonNull
    public List<CategoryOption> getCategories() {
        return categories == null ? java.util.Collections.emptyList() : categories;
    }

    @NonNull
    public List<BreedOption> getBreeds() {
        return breeds == null ? java.util.Collections.emptyList() : breeds;
    }

    @NonNull
    public List<PriceTypeOption> getPriceTypes() {
        return priceTypes == null ? java.util.Collections.emptyList() : priceTypes;
    }

    public MarketDefaults getDefaults() {
        return defaults;
    }

    @Nullable
    public String getDefaultDesc() {
        return defaultDesc;
    }

    /** 省份 */
    public static class Province {

        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    /** 品类 */
    public static class CategoryOption {

        private long id;
        private String name;

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    /** 品种（breed_id 为 0 即全部品种） */
    public static class BreedOption {

        private long categoryId;
        private long breedId;
        private String name;

        public long getCategoryId() {
            return categoryId;
        }

        public long getBreedId() {
            return breedId;
        }

        public String getName() {
            return name;
        }
    }

    /** 价格类型 */
    public static class PriceTypeOption {

        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    /** 默认筛选（养殖场所在省份与主要在养品类） */
    public static class MarketDefaults {

        private String provinceCode;
        private long categoryId;
        private long breedId;
        private String priceType;

        public String getProvinceCode() {
            return provinceCode;
        }

        public long getCategoryId() {
            return categoryId;
        }

        public long getBreedId() {
            return breedId;
        }

        public String getPriceType() {
            return priceType;
        }
    }
}
