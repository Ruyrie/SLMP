package com.zhimu.server.biz.finance.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 收支（API-E03–E07、E09、E10）响应结构，字段与 App model/vo 一致。 */
public final class FinanceRecordVos {

    private FinanceRecordVos() {
    }

    @Data
    public static class RecordVO {
        private Long id;
        private Long farmId;
        private Long categoryId;
        @ManagerView
        private BigDecimal amount;
        private String recordDate;
        private Integer voucherCount;
        private String title;
        private String categoryName;
        private String categoryIcon;
        private String sourceType;
        private String sourceLabel;
        private String recordTypeText;
        private String statusText;
        private Boolean editable;
        private String createdAt;
        private String recordType;
        private String status;
        private String farmName;
        private String sourceNo;
        private Long subcategoryId;
        private String subcategoryName;
        private String detailText;
        private String note;
        private Long batchId;
        private String batchNo;
        private SourceTarget sourceTarget;
    }

    @Data
    public static class SourceTarget {
        private String bizType;
        private Long bizId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DetailVO extends RecordVO {
        private List<MediaItemVO> vouchers = new ArrayList<>();
        private UserBriefVO createdBy;
        private Integer version;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RecordPageVO extends PageVO<RecordVO> {
        private Totals totals;
    }

    @Data
    public static class Totals {
        @ManagerView
        private BigDecimal income;
        @ManagerView
        private BigDecimal expenseAmount;
    }

    @Data
    public static class DuplicateCheckVO {
        private Boolean hit;
        private Long recordId;
        private String categoryName;
        @ManagerView
        private BigDecimal amount;
        private String recordDate;
        private String sourceLabel;
    }

    @Data
    public static class CategoryVO {
        private Long id;
        private String code;
        private String name;
        private String icon;
        private String direction;
        private Integer sortNo;
        private Boolean autoSource;
        private Boolean markOnly;
        private Boolean isFallback;
        private List<SubcategoryVO> subcategories = new ArrayList<>();
    }

    @Data
    public static class SubcategoryVO {
        private Long id;
        private String name;
        private String createdAt;
    }

    @Data
    public static class SubmissionVO {
        private Boolean saved;
        private String bizType;
        private Long bizId;
        private Long saleOrderId;
    }
}
