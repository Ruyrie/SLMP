package com.zhimu.server.biz.common.change;

import java.util.List;

/**
 * U01 表单上下文（来源模块负责与记录相关的内容，公共能力补 biz_type_text、record_version、pending_request_id 等）。
 *
 * @param deleteAllowed 是否允许提交 DELETE 申请
 * @param voidAllowed   是否允许提交 VOID 申请（本批只有投喂记录为 true）
 * @param voidNotice    作废说明（warning Notice 正文）
 */
public record ChangeForm(
        List<ChangeCardItem> recordCard,
        List<ChangeField> fields,
        List<String> linkedRules,
        String reviewTip,
        boolean deleteAllowed,
        boolean voidAllowed,
        String voidNotice,
        List<String> voidRecalcTags) {

    public ChangeForm {
        recordCard = recordCard == null ? List.of() : List.copyOf(recordCard);
        fields = fields == null ? List.of() : List.copyOf(fields);
        linkedRules = linkedRules == null ? List.of() : List.copyOf(linkedRules);
        voidRecalcTags = voidRecalcTags == null ? List.of() : List.copyOf(voidRecalcTags);
    }

    public ChangeField fieldOf(String fieldCode) {
        if (fieldCode == null) {
            return null;
        }
        for (ChangeField field : fields) {
            if (fieldCode.equals(field.fieldCode())) {
                return field;
            }
        }
        return null;
    }
}
