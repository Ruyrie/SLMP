package com.zhimu.server.common.enums;

import lombok.Getter;

/** 养殖场角色 */
@Getter
public enum FarmRole implements CodeEnum {
    OWNER("所有者"),
    MANAGER("场长"),
    STAFF("员工");

    private final String text;

    FarmRole(String text) {
        this.text = text;
    }
}