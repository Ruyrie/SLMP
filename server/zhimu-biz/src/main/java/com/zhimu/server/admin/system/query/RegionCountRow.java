package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 地区明细计数。 */
@Getter
@Setter
public class RegionCountRow {

    private Long total;
    private Long approved;
    private Long pending;
    private Long frozen;
}
