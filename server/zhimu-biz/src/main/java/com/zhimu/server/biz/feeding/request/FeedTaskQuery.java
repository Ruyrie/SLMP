package com.zhimu.server.biz.feeding.request;

import com.zhimu.server.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** G01 今日任务查询：status 为 PENDING / DONE / OVERDUE，可空。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedTaskQuery extends PageQuery {
    private String status;
    private Long penId;
}
