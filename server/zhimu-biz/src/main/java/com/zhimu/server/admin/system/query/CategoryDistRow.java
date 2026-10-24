package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 品类分布（已通过养殖场按主要品类畜种计数）。 */
@Getter
@Setter
public class CategoryDistRow {

    private String species;
    private Long count;
}
