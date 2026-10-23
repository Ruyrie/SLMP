package com.zhimu.server.biz.health.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** API-D36 消毒任务列表查询：status 取 ALL / OVERDUE / PENDING / DONE，pen_id 选填。 */
@Getter
@Setter
public class DisinfectTaskQuery extends PageQuery {

    private String status;
    private Long penId;
}
