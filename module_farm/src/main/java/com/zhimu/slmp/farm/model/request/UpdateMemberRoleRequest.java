package com.zhimu.slmp.farm.model.request;

/**
 * 修改角色（API-F23）：role 为 MANAGER / STAFF，version 取 member.version。
 */
public class UpdateMemberRoleRequest {

    private final String role;
    private final int version;

    public UpdateMemberRoleRequest(String role, int version) {
        this.role = role;
        this.version = version;
    }

    public String getRole() {
        return role;
    }

    public int getVersion() {
        return version;
    }
}
