package com.zhimu.server.biz.health.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-D11 登记接种的计划下拉查询：keyword 匹配批次号或疫苗名。 */
@Getter
@Setter
public class ImmunePlanOptionQuery extends PageQuery {

    private String keyword;
}
