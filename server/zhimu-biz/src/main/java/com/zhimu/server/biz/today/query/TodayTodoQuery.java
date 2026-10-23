package com.zhimu.server.biz.today.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-T02 今天和已逾期。 */
@Getter
@Setter
public class TodayTodoQuery extends PageQuery {

    /** ALL / OVERDUE / WORK / BIZ_WARN，默认 ALL */
    private String filter;

    /** Web 员工按栏舍筛选；App 不传 */
    private Long penId;
}
