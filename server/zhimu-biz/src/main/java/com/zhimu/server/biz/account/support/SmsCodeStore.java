package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.enums.SmsScene;

/**
 * 短信验证码发送状态：冷却、当天条数、已发标记与连错次数。由 Redis 实现；单元测试可用内存实现。
 */
public interface SmsCodeStore {

    /** 冷却剩余秒数，0 表示可以发送 */
    long cooldownSeconds(SmsScene scene, String phone);

    void startCooldown(SmsScene scene, String phone, int seconds);

    /** 当天已发条数（北京时间自然日） */
    long dailyCount(String phone);

    /** 当天已发条数加 1（北京时间自然日） */
    long increaseDailyCount(String phone);

    /** 记录“该手机号该场景已发码”，用于区分“未获取”与“验证码错误” */
    void markSent(SmsScene scene, String phone, int ttlSeconds);

    boolean isSent(SmsScene scene, String phone);

    /** A10 先校验新手机号验证码、创建申请成功后才消费：记录“已校验通过”，消费时不再调通道 */
    void markVerified(SmsScene scene, String phone, int ttlSeconds);

    boolean isVerified(SmsScene scene, String phone);

    /** 错误次数加 1，返回加后的次数 */
    int recordWrongAttempt(SmsScene scene, String phone, int ttlSeconds);

    int wrongAttempts(SmsScene scene, String phone);

    /** 校验成功后清除已发标记与错误次数 */
    void clear(SmsScene scene, String phone);
}
