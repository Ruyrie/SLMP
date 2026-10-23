package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zhimu.server.framework.mybatis.AuditEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理员角色（t_admin_role，管理端-04 2.3.2）。三个内置角色不可删除；
 * SUPER_ADMIN 的 editable = 0，权限固定为全部权限点。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_admin_role")
public class AdminRole extends AuditEntity {

    private String roleCode;
    private String roleName;
    private String description;
    private Boolean builtin;
    private Boolean editable;
    /** JSON 数组文本：默认权限码，“恢复默认”用 */
    private String defaultPermCodes;
    private Integer sortNo;

    @Version
    private Integer version;
}
