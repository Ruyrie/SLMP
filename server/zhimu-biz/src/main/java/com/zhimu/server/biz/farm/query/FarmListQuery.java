package com.zhimu.server.biz.farm.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/** F01 我的养殖场列表：relation = ALL / CREATED / JOINED */
@Getter
@Setter
public class FarmListQuery extends PageQuery {
    private String relation;

    public String relationOrDefault() {
        return relation == null || relation.isBlank() ? "ALL" : relation.trim().toUpperCase();
    }
}
