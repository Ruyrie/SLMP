package com.zhimu.server.biz.farm.request;

import lombok.Data;

/** 修改角色（API-F23） */
@Data
public class UpdateMemberRoleRequest {
    private String role;
    private Integer version;
}
