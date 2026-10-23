package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 角色权限（t_admin_role_permission，管理端-04 2.3.4）。保存权限时按角色整体替换；
 * 该表没有 updated_at / deleted / version，因此不继承 BaseEntity / AuditEntity。
 * 超级管理员角色不写本表，权限判定直接视为拥有全部权限点。
 */
@Getter
@Setter
@ToString
@TableName("t_admin_role_permission")
public class AdminRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;
    private Long permissionId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
