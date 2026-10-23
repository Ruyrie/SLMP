package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A01、A02 发送验证码的结果（不含验证码本身） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCodeSendVO {
    private String phoneMasked;
    private Integer cooldownSeconds;
    private Integer expireSeconds;
}
