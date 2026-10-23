package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 管理端日志操作类别（管理端-04 2.3.10 OP_TYPE 的管理端部分）。 */
@Getter
public enum AdminOpType implements CodeEnum {

    LOGIN("登录与密码"),
    FARM_AUDIT("养殖场注册审核"),
    FARM_CHANGE("资料变更审核"),
    CHANGE_REQUEST("修改申请审核"),
    ACCOUNT_RECOVERY("账号找回"),
    FARM_FREEZE("养殖场冻结 / 解冻"),
    USER_STATUS("用户账号停用 / 启用"),
    NOTICE("通知发布"),
    MARKET_PRICE("市场行情发布"),
    COURSE("养殖课堂"),
    KNOWLEDGE("知识库"),
    AI_CORRECTION("AI 问答更正"),
    BASE_DATA("基础数据"),
    ADMIN_RBAC("管理员与权限"),
    EXPORT("导出");

    private final String text;

    AdminOpType(String text) {
        this.text = text;
    }
}
