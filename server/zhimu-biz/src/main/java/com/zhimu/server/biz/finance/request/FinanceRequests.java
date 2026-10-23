package com.zhimu.server.biz.finance.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 模块10 请求体：字段与 App model/request 一致（JSON snake_case）。 */
public final class FinanceRequests {

    private FinanceRequests() {
    }

    @Data
    public static class FinanceRecordRequest {
        private String recordType;
        private BigDecimal amount;
        private Long categoryId;
        private Long subcategoryId;
        private String note;
        private String recordDate;
        private Long batchId;
        private List<Long> fileIds;
        private Integer version;
    }

    @Data
    public static class SubcategoryRequest {
        private Long categoryId;
        private String name;
    }

    @Data
    public static class VersionRequest {
        private Integer version;
    }

    @Data
    public static class PenQuantity {
        private Long penId;
        private Integer quantity;
    }

    @Data
    public static class SaleOrderRequest {
        private Long batchId;
        private String saleDate;
        private Integer quantity;
        private List<PenQuantity> penQuantities;
        private BigDecimal totalWeight;
        private String pricingMethod;
        private BigDecimal unitPrice;
        private String buyerName;
        private String dueDate;
        private Integer version;
    }

    @Data
    public static class SaleOrderVoidRequest {
        private String reason;
        private Integer version;
    }

    @Data
    public static class SaleReceiptRequest {
        private BigDecimal amount;
        private String receiptDate;
        private String paymentMethod;
    }

    @Data
    public static class SaleChangePreviewRequest {
        private String changeType;
        private String fieldCode;
        private BigDecimal newUnitPrice;
        private Integer newQuantity;
        private BigDecimal newTotalWeight;
        private List<PenQuantity> penQuantities;
    }

    @Data
    public static class SupplementConfirmRequest {
        private List<Long> supplementIds;
        private Integer version;
    }

    @Data
    public static class SaleAdviceRequest {
        private Long batchId;
    }

    @Data
    public static class ReportQuery {
        private String reportType;
        private String periodStart;
        private String scope;
        private String rangeType;
        private Long rangeRefId;
        private String metric;
        private String format;
    }

    @Data
    public static class ReportExportRequest {
        private String reportType;
        private String periodStart;
        private String scope;
        private String rangeType;
        private Long rangeRefId;
        private String format;
    }
}
