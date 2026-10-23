package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** 栏舍负责员工（t_member_pen + t_farm_member + t_user）。 */
@Getter
@Setter
public class PenResponsibleRow {
    private Long penId;
    private Long userId;
    private String name;
}
