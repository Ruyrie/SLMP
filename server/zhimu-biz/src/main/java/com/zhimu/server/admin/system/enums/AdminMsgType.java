package com.zhimu.server.admin.system.enums;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 管理端站内消息类型（管理端-04 2.3.10 ADMIN_MSG_TYPE）。 */
@Getter
public enum AdminMsgType implements CodeEnum {

    AUDIT_OVERDUE("审核超时"),
    MARKET_PRICE_RETURNED("行情被退回"),
    MARKET_PRICE_UNPUBLISHED("行情未发布"),
    KNOWLEDGE_TASK_ASSIGNED("知识补充任务"),
    KNOWLEDGE_DRAFT_PENDING("知识条目草稿待处理"),
    TRANSCODE_FAILED("课程转码失败"),
    SMS_FAILED("短信发送失败");

    private final String text;

    AdminMsgType(String text) {
        this.text = text;
    }
}
