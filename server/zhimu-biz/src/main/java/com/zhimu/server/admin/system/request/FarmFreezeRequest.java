package com.zhimu.server.admin.system.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ADM-S17 冻结养殖场：原因 1–200 字，version 必填。冻结原因以站内消息发到全部成员（含所有者）的消息中心；
 * 平台没有通知类短信模板，不发短信。
 */
@Data
public class FarmFreezeRequest {

    @Size(max = 200, message = "冻结原因不能超过 200 字")
    private String reason;

    /** 已废弃：旧前端的“短信通知所有者”，传了也忽略（所有者已在站内消息的接收人里） */
    @Deprecated
    private Boolean smsOwner = false;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
