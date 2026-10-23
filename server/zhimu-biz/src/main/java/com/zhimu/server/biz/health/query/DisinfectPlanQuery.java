package com.zhimu.server.biz.health.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-D28 消毒计划列表查询：status 取 ALL / ACTIVE / PAUSED。 */
@Getter
@Setter
public class DisinfectPlanQuery extends PageQuery {

    private String status;
}
