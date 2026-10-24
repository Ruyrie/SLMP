package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 平台概览活跃度：近 7 天（昨天往前 7 个自然日）与今天。 */
@Getter
@Setter
public class ActiveStatRow {

    private Long users7d;
    private Long usersToday;
    private Long farms7d;
}
