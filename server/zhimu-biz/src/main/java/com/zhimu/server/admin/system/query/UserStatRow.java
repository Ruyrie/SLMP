package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 平台概览账号计数（不含已注销）。 */
@Getter
@Setter
public class UserStatRow {

    private Long total;
    private Long normal;
    private Long disabled;
    private Long newThisMonth;
}
