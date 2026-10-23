package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 权限点功能块（管理端-04 2.3.3 / 2.3.10 PERM_MODULE），同时作为菜单分组。 */
@Getter
public enum PermModule implements CodeEnum {

    OVERVIEW("平台概览"),
    AUDIT("审核中心"),
    CONTENT("内容运营"),
    BASE_DATA("基础数据"),
    USER_SYSTEM("用户与系统");

    private final String text;

    PermModule(String text) {
        this.text = text;
    }
}
