package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** 本场成员（t_farm_member + t_user）：执行人、创建人姓名与角色。 */
@Getter
@Setter
public class FarmMemberRow {
    private Long userId;
    private String name;
    private String role;
}
