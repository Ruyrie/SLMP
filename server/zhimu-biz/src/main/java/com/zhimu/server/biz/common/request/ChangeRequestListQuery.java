package com.zhimu.server.biz.common.request;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * API-U03 我的修改申请查询：status 可空（不传时聚合本人全部养殖场并过滤 CANCELED）。
 * 额外的 farm_id、biz_type、keyword 用于筛选，App 当前只传 status 与分页。
 */
@Getter
@Setter
public class ChangeRequestListQuery extends PageQuery {

    private String status;
    private Long farmId;
    private String bizType;
    private String keyword;
}
