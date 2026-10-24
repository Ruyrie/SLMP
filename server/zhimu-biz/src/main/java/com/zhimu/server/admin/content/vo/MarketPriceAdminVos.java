package com.zhimu.server.admin.content.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** WA-C02 市场行情发布的响应模型（ADM-C11–C22）。 */
public final class MarketPriceAdminVos {

    private MarketPriceAdminVos() {
    }

    @Data
    public static class RowVO {
        private Long configId;
        private Long priceId;
        private String provinceCode;
        private String provinceName;
        private Long categoryId;
        private String categoryName;
        private Long breedId;
        private String breedName;
        private String priceType;
        private String priceTypeText;
        private BigDecimal price;
        private String priceUnit;
        private String priceUnitText;
        private BigDecimal changeRate;
        private BigDecimal prevPrice;
        private LocalDate prevPriceDate;
        private Boolean isPrevCorrected = false;
        private Boolean isOverThreshold = false;
        private String rowStatus;
        private String rowStatusText;
        private String reviewKind;
        private String reviewKindText;
        private String enteredByName;
        private LocalDateTime enteredAt;
        private Boolean canReview = false;
        private Boolean canCorrect = false;
        private String unpublishedText;
    }

    @Data
    public static class UnpublishedItem {
        private Long configId;
        private String provinceName;
        private String categoryName;
        private String breedName;
        private String priceType;
        private String priceTypeText;
        private BigDecimal lastPrice;
        private String lastUnit;
        private LocalDate lastDate;
    }

    @Data
    public static class ProvinceProgress {
        private String provinceCode;
        private String provinceName;
        private Integer published = 0;
        private Integer total = 0;
    }

    @Data
    public static class ProgressVO {
        private LocalDate priceDate;
        private Integer unpublishedCount = 0;
        private List<UnpublishedItem> unpublished = new ArrayList<>();
        private List<ProvinceProgress> provinces = new ArrayList<>();
    }

    @Data
    public static class EntryRow {
        private Long configId;
        private String categoryName;
        private String breedName;
        private String priceType;
        private String priceTypeText;
        private BigDecimal lastPrice;
        private String lastUnit;
        private String lastUnitText;
        private LocalDate lastDate;
        private String defaultUnit;
        private String defaultUnitText;
        private String todayStatus;
        private BigDecimal todayPrice;
        private Boolean isReadonly = false;
        private String returnNote;
    }

    @Data
    public static class EntryFormVO {
        private LocalDate priceDate;
        private String provinceCode;
        private String provinceName;
        private List<EntryRow> rows = new ArrayList<>();
    }

    @Data
    public static class BatchItem {
        private Long configId;
        private Long priceId;
        private String status;
        private BigDecimal changeRate;
    }

    @Data
    public static class BatchPublishVO {
        private Integer publishedCount = 0;
        private Integer reviewCount = 0;
        private List<BatchItem> items = new ArrayList<>();
    }

    @Data
    public static class ImportRow {
        private Integer rowNo;
        private Long configId;
        private String categoryName;
        private String breedName;
        private String priceType;
        private BigDecimal price;
        private String priceUnit;
        private BigDecimal changeRate;
        private Boolean isNeedReview = false;
        private List<String> errors = new ArrayList<>();
    }

    @Data
    public static class ImportPreviewVO {
        private LocalDate priceDate;
        private String provinceCode;
        private String provinceName;
        private Integer validCount = 0;
        private Integer errorCount = 0;
        private List<ImportRow> rows = new ArrayList<>();
    }

    @Data
    public static class CorrectionVO {
        private Long correctionId;
        private LocalDate priceDate;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private BigDecimal changeRateAfter;
        private String reason;
        private String status;
        private String statusText;
        private String correctedByName;
        private LocalDateTime correctedAt;
        private String reviewerName;
        private LocalDateTime reviewedAt;
        private String reviewNote;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DetailVO extends RowVO {
        private String status;
        private String statusText;
        private BigDecimal near7Min;
        private BigDecimal near7Max;
        private String dataSource;
        private String remark;
        private String reviewerName;
        private LocalDateTime reviewedAt;
        private String reviewResult;
        private String reviewResultText;
        private String reviewNote;
        private Boolean isCorrected = false;
        private LocalDateTime correctedAt;
        private List<CorrectionVO> corrections = new ArrayList<>();
        private CorrectionVO pendingCorrection;
        private Integer version;
    }

    @Data
    public static class RecalculatedItem {
        private LocalDate priceDate;
        private BigDecimal oldRate;
        private BigDecimal newRate;
    }

    @Data
    public static class CorrectionResultVO {
        private Long correctionId;
        private LocalDate priceDate;
        private String status;
        private String statusText;
        private List<RecalculatedItem> recalculated = new ArrayList<>();
    }

    @Data
    public static class HistoryItemVO {
        private LocalDate priceDate;
        private Long priceId;
        private BigDecimal price;
        private String priceUnit;
        private String priceUnitText;
        private BigDecimal changeRate;
        private String status;
        private String statusText;
        private String enteredByName;
        private LocalDateTime enteredAt;
        private Boolean isCorrected = false;
        private List<CorrectionVO> corrections = new ArrayList<>();
    }

    @Data
    public static class ConfigVO {
        private Long configId;
        private String provinceCode;
        private String provinceName;
        private Long categoryId;
        private String categoryName;
        private Long breedId;
        private String breedName;
        private String priceType;
        private String priceTypeText;
        private String defaultUnit;
        private String defaultUnitText;
        private Integer sortNo;
        private Boolean isEnabled;
    }
}
