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
    /**
     * ALL / UNFINISHED（同义 PENDING）/ CLOSED（已完成 + 已忽略），默认 ALL。
     * ALL 时未完成在前、已关闭在后；响应 tab_counts 固定返回 UNFINISHED 与 CLOSED 两段总数（不受 status 影响）。
     */
    private String status;
}
