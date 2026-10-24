package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** R18 重发结果短信的响应；只返回打码手机号，不返回临时密码或验证码。 */
@Data
@Builder
public class SmsResendVO {

    private Long id;
    /** 打码手机号，例如 177****0638 */
    private String phone;
    private String smsStatus;
    private String smsStatusText;
    private Integer retryCount;
    private LocalDateTime queuedAt;
    /** 为前端补充说明，例如“已重新生成临时密码，等待短信任务发送” */
    private String note;
}
