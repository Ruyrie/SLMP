package com.zhimu.server.integration.sms;

/**
 * 短信验证码。60 秒间隔、每天 10 次、连错 5 次作废等由业务模块用 Redis 控制，这里只负责发送与校验。
 */
public interface SmsClient {

    SmsMode mode();

    /**
     * 发送验证码。
     *
     * @throws com.zhimu.server.common.error.BizException 42901 频率限制（data.cooldown_seconds）、
     *                                                    40001 手机号格式错误、50301 其他失败
     */
    SmsSendResult send(String phone, SmsTemplate template);

    /**
     * 校验验证码；不正确或已过期返回 false。
     *
     * @throws com.zhimu.server.common.error.BizException 42901 频率限制、50301 服务调用失败
     */
    boolean verify(String phone, String code);
}
