package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** 栏舍负责员工（只读 t_member_pen + t_farm_member + t_user）。 */
@Data
public class PenResponsibleRow {
    private Long penId;
    private Long userId;
    private String name;
}
