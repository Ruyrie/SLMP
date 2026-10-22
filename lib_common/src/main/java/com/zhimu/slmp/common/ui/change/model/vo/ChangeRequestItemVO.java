package com.zhimu.slmp.common.ui.change.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestVO;

/**
 * U02 列表项与 U04 详情：在公共 ChangeRequestVO 之上补充养殖场、定位与重新提交需要的字段
 * （公共 VO 尚未定义，建议合并进 common.model.vo.ChangeRequestVO）。
 */
public class ChangeRequestItemVO extends ChangeRequestVO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String CHANGE_VOID = "VOID";

    /** 原记录 ID（重新提交时取 API-U01 表单） */
    private long bizId;
    private long farmId;
    /** 东山猪场 */
    @Nullable
    private String farmName;
    @Nullable
    private Long batchId;
    /** 主修改项编码，重新提交时带入 */
    @Nullable
    private String fieldCode;
    /** 新值（不含单位），重新提交时带入 */
    @Nullable
    private String afterValue;
    /** 通过后的重新计算结果：已重新计算批次收益 */
    @Nullable
    private String recalcSummary;

    public long getBizId() {
        return bizId;
    }

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getFieldCode() {
        return fieldCode;
    }

    @Nullable
    public String getAfterValue() {
        return afterValue;
    }

    @Nullable
    public String getRecalcSummary() {
        return recalcSummary;
    }

    public boolean isVoid() {
        return CHANGE_VOID.equals(getChangeType());
    }
}
