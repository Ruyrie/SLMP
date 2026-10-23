package com.zhimu.server.biz.herd.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-L07 栏舍表格 / 下拉参数。App 的 usage 固定传 OPTIONS。 */
@Getter
@Setter
public class PenQuery extends PageQuery {

    /** TABLE / OPTIONS；默认 OPTIONS（App 只使用下拉形态） */
    private String usage = "OPTIONS";
    private String keyword;
    private String penType;
    /** IN_USE / EMPTY / DISABLED */
    private String displayStatus;
    private Long categoryId;
    private Long excludePenId;
}
