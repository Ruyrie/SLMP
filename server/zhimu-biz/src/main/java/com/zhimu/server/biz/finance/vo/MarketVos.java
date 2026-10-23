package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 行情（API-E30–E32）响应结构。 */
public final class MarketVos {

    private MarketVos() {
    }

    @Data
    public static class FilterVO {
        private List<Province> provinces = new ArrayList<>();
        private List<CategoryOption> categories = new ArrayList<>();
        private List<BreedOption> breeds = new ArrayList<>();
        private List<PriceTypeOption> priceTypes = new ArrayList<>();
        private MarketDefaults defaults;
        private String defaultDesc;
    }

    @Data
    public static class Province {
        private String code;
        private String name;
    }

    @Data
    public static class CategoryOption {
        private Long id;
        private String name;
    }

    @Data
    public static class BreedOption {
        private Long categoryId;
        private Long breedId;
        private String name;
    }

    @Data
    public static class PriceTypeOption {
        private String code;
        private String name;
    }

    @Data
    public static class MarketDefaults {
        private String provinceCode;
        private Long categoryId;
        private Long breedId;
        private String priceType;
    }

    @Data
    public static class TrendVO {
        private Latest latest;
        private PriceChange change;
        private List<PricePoint> series = new ArrayList<>();
        private PriceStats stats;
        private Boolean empty;
    }

    @Data
    public static class Latest {
        @ManagerView
        private BigDecimal price;
        private String priceUnit;
        private String priceUnitText;
        private String priceDate;
    }

    @Data
    public static class PriceChange {
        private BigDecimal changeRate;
        private String direction;
        private String compareDate;
        @ManagerView
        private BigDecimal comparePrice;
    }

    @Data
    public static class PricePoint {
        private String date;
        @ManagerView
        private BigDecimal price;
        private Boolean corrected;
    }

    @Data
    public static class PriceStats {
        @ManagerView
        private BigDecimal maxPrice;
        private String maxDate;
        @ManagerView
        private BigDecimal minPrice;
        private String minDate;
        @ManagerView
        private BigDecimal avgPrice;
    }

    @Data
    public static class DailyPriceVO {
        private String priceDate;
        @ManagerView
        private BigDecimal price;
        private String priceUnitText;
        private BigDecimal changeRate;
        private String direction;
        private Boolean corrected;
        private String correctedAt;
    }
}
