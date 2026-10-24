package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 平台概览养殖场计数（已通过含已停用与已冻结，正常为未停用且未冻结）。 */
@Getter
@Setter
public class FarmStatRow {

    private Long total;
    private Long approved;
    private Long normal;
    private Long disabled;
    private Long frozen;
    private Long pending;
    private Long rejected;
}
