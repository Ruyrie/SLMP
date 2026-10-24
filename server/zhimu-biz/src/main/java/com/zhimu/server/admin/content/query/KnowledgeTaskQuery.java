package com.zhimu.server.admin.content.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** ADM-C54 知识补充任务列表筛选。 */
@Getter
@Setter
public class KnowledgeTaskQuery extends PageQuery {

    private String status;
    private Long assigneeId;
}
