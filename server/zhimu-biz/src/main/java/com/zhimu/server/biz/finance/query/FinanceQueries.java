package com.zhimu.server.biz.finance.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 模块10 查询参数：继承 PageQuery（page/size），字段名与 App 查询参数 snake_case 对应。 */
public final class FinanceQueries {

    private FinanceQueries() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FinanceRecordQuery extends PageQuery {
        private String month;
        private String type;
        private String categoryIds;
        private Long batchId;
        private Boolean hasVoucher;
        private String keyword;
        private String scope;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SaleOrderQuery extends PageQuery {
        private String status;
        private String keyword;
        /** Web 补充；App 不传即不限月份 */
        private String month;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ReceivableQuery extends PageQuery {
        private String dueFilter;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SettlementQuery extends PageQuery {
        private String keyword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MarketPriceQuery extends PageQuery {
        private String provinceCode;
        private Long categoryId;
        private Long breedId;
        private String priceType;
        private Integer rangeDays;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SaleAdviceQuery extends PageQuery {
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
    }
}
