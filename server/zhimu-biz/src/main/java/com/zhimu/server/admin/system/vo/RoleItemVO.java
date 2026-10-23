package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ADM-S27 角色列表行。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleItemVO {
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String description;
    private Long memberCount;
    private Boolean editable;
}
