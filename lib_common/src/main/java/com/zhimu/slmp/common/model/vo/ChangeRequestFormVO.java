package com.zhimu.slmp.common.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    // ===== 以下字段由 U01 / U02 补充（2026-10-22 从 ui.change.model.vo 合并） =====

    /** 提交时带回的原记录版本（API-U02 record_version） */
    @Nullable
    private String recordVersion;
    /** 投喂记录 */
    @Nullable
    private String bizTypeText;
    /** 原记录卡主文字：投喂记录 · 3 号育肥舍 */
    @Nullable
    private String recordTitle;
    /** 原记录卡辅助文字：ZM2610-031 · 10月18日 晚餐 · 李明登记 */
    @Nullable
    private String recordSubtitle;
    /** 同一条记录已有待审核申请时的申请 ID */
    @Nullable
    private Long pendingRequestId;
    /** 是否允许“作废这条记录”（仅投喂记录） */
    private boolean voidAllowed;
    /** 作废说明（warning Notice 正文） */
    @Nullable
    private String voidNotice;
    /** 作废时的重新计算范围 */
    @Nullable
    private List<String> voidRecalcTags;
    /** field_code → 重新计算范围 */
    @Nullable
    private Map<String, List<String>> recalcTags;
    /** field_code → 新值参考（“计划 160 千克”） */
    @Nullable
    private Map<String, String> fieldHints;

    @Nullable
    public String getRecordVersion() {
        return recordVersion;
    }

    @Nullable
    public String getBizTypeText() {
        return bizTypeText;
    }

    @Nullable
    public String getRecordTitle() {
        return recordTitle;
    }

    @Nullable
    public String getRecordSubtitle() {
        return recordSubtitle;
    }

    @Nullable
    public Long getPendingRequestId() {
        return pendingRequestId;
    }

    public boolean isVoidAllowed() {
        return voidAllowed;
    }

    @Nullable
    public String getVoidNotice() {
        return voidNotice;
    }

    @NonNull
    public List<String> getVoidRecalcTags() {
        return voidRecalcTags == null ? Collections.emptyList() : voidRecalcTags;
    }

    @NonNull
    public List<String> recalcTagsOf(@Nullable String fieldCode) {
        if (recalcTags == null || fieldCode == null) {
            return Collections.emptyList();
        }
        List<String> l = recalcTags.get(fieldCode);
        return l == null ? Collections.emptyList() : l;
    }

    @Nullable
    public String hintOf(@Nullable String fieldCode) {
        return fieldHints == null || fieldCode == null ? null : fieldHints.get(fieldCode);
    }

    /** 按 field_code 查可改字段 */
    @Nullable
    public Field fieldOf(@Nullable String fieldCode) {
        if (fieldCode == null) {
            return null;
        }
        for (Field f : getFields()) {
            if (fieldCode.equals(f.getFieldCode())) {
                return f;
            }
        }
        return null;
    }
}
