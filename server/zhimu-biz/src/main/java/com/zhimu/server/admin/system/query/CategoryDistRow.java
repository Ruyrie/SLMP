package com.zhimu.server.admin.system.query;

import lombok.Getter;
import lombok.Setter;

/** 品类分布（已通过养殖场按主要品类计数，附带畜种用于分组文案）。 */
@Getter
@Setter
public class CategoryDistRow {

    private Long categoryId;
    private String categoryName;
    private String species;
    private Long count;
}
