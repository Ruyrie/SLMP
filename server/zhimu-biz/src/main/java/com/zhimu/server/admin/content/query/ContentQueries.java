package com.zhimu.server.admin.content.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 管理端-02 内容运营的列表查询参数。 */
public final class ContentQueries {

    private ContentQueries() {
    }

    /** ADM-C02 通知列表：status 可空 = 全部；tab_counts 受 level/scope/keyword 影响，不受 status 影响。 */
    @Getter
    @Setter
    public static class NoticeQuery extends PageQuery {
        private String status;
        private String level;
        private String scopeType;
        private String keyword;
    }

    /** ADM-C11 某日行情：date 默认今天，不能晚于今天。 */
    @Getter
    @Setter
    public static class MarketPriceQuery extends PageQuery {
        private LocalDate date;
        private String status;
        private String provinceCode;
        private Long categoryId;
    }

    /** ADM-C12 未发布清单与进度。 */
    @Getter
    @Setter
    public static class MarketDateQuery {
        private LocalDate date;
    }

    /** ADM-C13 按地区录入表。 */
    @Getter
    @Setter
    public static class EntryFormQuery {
        private LocalDate date;
        private String provinceCode;
    }

    /** ADM-C19 组合近 N 天历史。 */
    @Getter
    @Setter
    public static class MarketHistoryQuery {
        private String provinceCode;
        private Long categoryId;
        private Long breedId;
        private String priceType;
        private Integer days = 90;
    }

    /** ADM-C20 发布配置。 */
    @Getter
    @Setter
    public static class MarketConfigQuery {
        private String provinceCode;
    }

    /** ADM-C58 问答记录：segment 默认 ALL，日期默认近 7 天。 */
    @Getter
    @Setter
    public static class QaQuery extends PageQuery {
        private String segment = "ALL";
        private String keyword;
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Boolean isSpotCheck;
    }

    /** ADM-C57 问答指标卡。 */
    @Getter
    @Setter
    public static class QaSummaryQuery {
        private LocalDate dateFrom;
        private LocalDate dateTo;
    }
}
