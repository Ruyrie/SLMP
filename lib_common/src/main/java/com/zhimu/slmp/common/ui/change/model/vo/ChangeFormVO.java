package com.zhimu.slmp.common.ui.change.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * U01 表单上下文（API-U01）：在公共 ChangeRequestFormVO 之上补充 U01 页面需要、公共 VO 尚未定义的字段
 * （原记录版本、记录类型文字与原记录卡标题、待审核申请、作废项、各修改项的重新计算范围与参考值）。
 * 这些字段建议合并进 common.model.vo.ChangeRequestFormVO（待服务端确认字段名）。
 */
public class ChangeFormVO extends ChangeRequestFormVO {

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
