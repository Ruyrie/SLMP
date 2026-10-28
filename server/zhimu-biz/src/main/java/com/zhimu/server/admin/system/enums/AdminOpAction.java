package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 管理端日志动作（管理端-04 2.3.10 OP_ACTION）。管理端-02、-03 可按需追加。 */
@Getter
public enum AdminOpAction implements CodeEnum {

    APPROVE("通过"),
    REJECT("驳回"),
    PROXY_SUBMIT("代为登记"),
    RESEND_SMS("重发短信"),
    RESEND_NOTICE("重发通知"),
    REGENERATE_PASSWORD("重新生成临时密码"),
    VIEW_PHONE("查看完整号码"),
    FREEZE("冻结"),
    UNFREEZE("解冻"),
    DISABLE("停用"),
    ENABLE("启用"),
    CREATE("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    VOID("作废"),
    PUBLISH("发布"),
    SEND("发送"),
    SCHEDULE("定时发送"),
    SUBMIT_REVIEW("提交复核"),
    CANCEL("取消"),
    WITHDRAW("撤回"),
    REVIEW("复核"),
    CORRECT("更正"),
    IMPORT("导入"),
    TASK_CREATE("创建任务"),
    NO_ACTION("无需处理"),
    ON_SHELF("上架"),
    OFF_SHELF("下架"),
    ROLLBACK("回退"),
    RETRY_TRANSCODE("重试转码"),
    RESET_PASSWORD("重置密码"),
    CHANGE_PASSWORD("修改密码"),
    SAVE_PERMISSION("保存权限"),
    LOGIN("登录"),
    LOGOUT("退出"),
    EXPORT("导出");

    private final String text;

    AdminOpAction(String text) {
        this.text = text;
    }
}
