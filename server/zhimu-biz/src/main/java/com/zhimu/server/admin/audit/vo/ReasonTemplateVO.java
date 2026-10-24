package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 常用驳回原因（label 短名，text 填入原因框的完整句子）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonTemplateVO {

    private String label;
    private String text;

    public static ReasonTemplateVO of(String label, String text) {
        return new ReasonTemplateVO(label, text);
    }
}
