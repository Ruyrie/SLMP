package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** R18 重发审核结果通知（站内消息）的响应；平台没有通知类短信模板，不发短信。 */
@Data
@Builder
public class NoticeResendVO {

    private Long id;
    /** 所有者打码手机号，例如 177****0638 */
    private String phone;
    private String receiverName;
    /** SENT 已写入消息中心 / FAILED 发送失败 */
    private String notifyStatus;
    private String notifyStatusText;
    private LocalDateTime sentAt;
    private String note;
}
