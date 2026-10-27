package com.zhimu.server.biz.today.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-T02 今天和已逾期。 */
@Getter
@Setter
public class TodayTodoQuery extends PageQuery {

    /** ALL（今日未完成）/ OVERDUE / WORK / BIZ_WARN；HOME 合并今日未完成和历史逾期，供 App 首页使用。 */
    // WARNING：当前场所有未解除预警，不按产生日期截断。
    private String filter;

    /** Web 员工按栏舍筛选；App 不传 */
    private Long penId;
}
