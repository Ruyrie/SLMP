package com.zhimu.server.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 管理员权限点（t_admin_permission，管理端-04 2.3.3）。初始化数据共 19 个页面、37 个权限点，
 * 界面不能增删；super_only = 1 的权限点只能属于超级管理员。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_admin_permission")
public class AdminPermission extends BaseEntity {

    private String permCode;
    private String moduleCode;
    private String pageCode;
    private String pageName;
    private String permAction;
    private String handleDesc;
    private Boolean superOnly;
    private Integer sortNo;
}
