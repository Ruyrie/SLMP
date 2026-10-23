package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 权限动作（管理端-04 2.3.3 / 2.3.10 PERM_ACTION）。 */
@Getter
public enum PermAction implements CodeEnum {

    VIEW("查看"),
    HANDLE("处理");

    private final String text;

    PermAction(String text) {
        this.text = text;
    }
}
