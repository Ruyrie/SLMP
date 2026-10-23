package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** 本场在职成员（只读 t_farm_member + t_user）。 */
@Data
public class FarmMemberRow {
    private Long userId;
    private String name;
    private String role;
}
