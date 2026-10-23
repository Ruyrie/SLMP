package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 管理员状态（管理端-04 2.3.10 ADMIN_STATUS）；“已锁定”由 locked_until 派生，不存库。 */
@Getter
public enum AdminStatus implements CodeEnum {

    NORMAL("正常"),
    DISABLED("已停用");

    private final String text;

    AdminStatus(String text) {
        this.text = text;
    }
}
