package com.zhimu.server.biz.today.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-T06 已逾期分组。 */
@Getter
@Setter
public class TodoOverdueQuery extends PageQuery {

    private String scope;
}
