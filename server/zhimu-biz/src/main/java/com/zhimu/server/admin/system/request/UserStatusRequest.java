package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ADM-S12 停用账号：原因 1–200 字，默认以站内消息通知本人。
 * 平台没有通知类短信模板，notify_sms 只为兼容旧前端，含义与 notify_message 相同。
 */
@Data
public class UserStatusRequest {

    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;

    /** 停用时把原因发到本人消息中心（站内消息）；为空时默认发送 */
    private Boolean notifyMessage;

    /** 兼容旧字段，等同 notifyMessage */
    private Boolean notifySms;

    @NotNull(message = "version 不能为空")
    private Integer version;

    public boolean shouldNotify() {
        if (notifyMessage != null) {
            return notifyMessage;
        }
        return notifySms == null || notifySms;
    }
}
