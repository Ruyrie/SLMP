package com.zhimu.server.common.enums;

import lombok.Getter;

/** 管理员内置角色 */
@Getter
public enum AdminRoleCode implements CodeEnum {
    SUPER_ADMIN("超级管理员"),
    AUDITOR("审核员"),
    OPERATOR("内容运营");

    private final String text;

    AdminRoleCode(String text) {
        this.text = text;
    }
}