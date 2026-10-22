package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * U01 提交修改申请页的表单上下文（API-U01）：原记录卡、可改字段、联动规则、审核提示。
 */
public class ChangeRequestFormVO {

    @Nullable
    private String bizType;
    private long bizId;
    @Nullable
    private String recordLocator;
    /** 原记录卡键值列表 */
    @Nullable
    private List<CardItem> recordCard;
    /** 可改字段 */
    @Nullable
    private List<Field> fields;
    /** 联动规则说明 */
    @Nullable
    private List<String> linkedRules;
    @Nullable
    private String reviewTip;

    @Nullable
    public String getBizType() {
        return bizType;
    }

    public long getBizId() {
        return bizId;
    }

    @Nullable
    public String getRecordLocator() {
        return recordLocator;
    }

    @NonNull
    public List<CardItem> getRecordCard() {
        return recordCard == null ? Collections.emptyList() : recordCard;
    }

    @NonNull
    public List<Field> getFields() {
        return fields == null ? Collections.emptyList() : fields;
    }

    @NonNull
    public List<String> getLinkedRules() {
        return linkedRules == null ? Collections.emptyList() : linkedRules;
    }

    @Nullable
    public String getReviewTip() {
        return reviewTip;
    }

    /** 原记录卡的一行 */
    public static class CardItem {

        @Nullable
        private String label;
        @Nullable
        private String value;

        @Nullable
        public String getLabel() {
            return label;
        }

        @Nullable
        public String getValue() {
            return value;
        }
    }

    /** 可改字段 */
    public static class Field {

        @Nullable
        private String fieldCode;
        @Nullable
        private String fieldName;
        @Nullable
        private String value;
        @Nullable
        private String valueUnit;
        /** 例如 NUMBER / TEXT / SELECT / DATE */
        @Nullable
        private String inputType;
        @Nullable
        private List<OptionVO> options;
        @Nullable
        private BigDecimal min;
        @Nullable
        private BigDecimal max;

        @Nullable
        public String getFieldCode() {
            return fieldCode;
        }

        @Nullable
        public String getFieldName() {
            return fieldName;
        }

        @Nullable
        public String getValue() {
            return value;
        }

        @Nullable
        public String getValueUnit() {
            return valueUnit;
        }

        @Nullable
        public String getInputType() {
            return inputType;
        }

        @NonNull
        public List<OptionVO> getOptions() {
            return options == null ? Collections.emptyList() : options;
        }

        @Nullable
        public BigDecimal getMin() {
            return min;
        }

        @Nullable
        public BigDecimal getMax() {
            return max;
        }
    }
}
