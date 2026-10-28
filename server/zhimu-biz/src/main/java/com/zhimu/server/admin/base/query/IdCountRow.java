package com.zhimu.server.admin.base.query;

import lombok.Getter;
import lombok.Setter;

/** 通用 {id, cnt} 只读统计行（按分类统计流水数、子类数等）。 */
@Getter
@Setter
public class IdCountRow {

    private Long id;
    private Integer cnt;
}
