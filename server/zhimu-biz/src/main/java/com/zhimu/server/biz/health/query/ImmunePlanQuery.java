package com.zhimu.server.biz.health.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-D02 免疫计划列表查询：status 取 ALL / PENDING / MISSED / POSTPONED / DONE / CANCELLED。 */
@Getter
@Setter
public class ImmunePlanQuery extends PageQuery {

    private String status;
}
