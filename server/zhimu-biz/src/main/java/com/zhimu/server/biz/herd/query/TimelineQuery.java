package com.zhimu.server.biz.herd.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-L16 时间线参数。type 为 TimelineFilter，null 为全部。 */
@Getter
@Setter
public class TimelineQuery extends PageQuery {

    /** ALL / FEED / WEIGH / IMMUNE / DRUG / INSPECT / STOCK（STOCK = 入栏 + 转栏 + 死淘 + 出栏） */
    private String type;
}
