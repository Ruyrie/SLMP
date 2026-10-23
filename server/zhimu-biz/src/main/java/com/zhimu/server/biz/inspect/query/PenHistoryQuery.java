package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I11 栏舍巡检历史查询。filter：ALL / ABNORMAL / OVERDUE / BACKFILL / OFF_SITE。 */
@Data
public class PenHistoryQuery {

    private String filter;
    private String startDate;
    private String endDate;
    private String locateDate;
    private Integer page;
    private Integer size;
}
