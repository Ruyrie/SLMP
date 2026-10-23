package com.zhimu.server.integration.sms;

/**
 * 短信模式：配置中写小写 mock / aliyun。
 */
public enum SmsMode {
    /** 不发送，验证码固定为 mock-code */
    MOCK,
    /** 阿里云号码认证 Dypnsapi：阿里云生成并校验验证码 */
    ALIYUN
}
