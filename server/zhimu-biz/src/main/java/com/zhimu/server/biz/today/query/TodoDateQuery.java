package com.zhimu.server.biz.today.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** API-T04 某天待办。 */
@Getter
@Setter
public class TodoDateQuery extends PageQuery {

    private LocalDate date;
    private String scope;
    private String category;
    /** ALL / UNFINISHED，默认 ALL */
    private String status;
}
