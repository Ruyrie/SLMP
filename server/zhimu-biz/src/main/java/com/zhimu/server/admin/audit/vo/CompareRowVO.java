package com.zhimu.server.admin.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审核详情的字段变更对比行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareRowVO {

    private String field;
    private String before;
    private String after;
    private Boolean changed;

    public static CompareRowVO of(String field, String before, String after) {
        boolean changed = before == null ? after != null : !before.equals(after);
        return new CompareRowVO(field, before, after, changed);
    }
}
