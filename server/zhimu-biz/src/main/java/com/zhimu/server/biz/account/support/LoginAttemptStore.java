package com.zhimu.server.biz.account.support;

import java.time.Duration;

/** 密码连续错误计数：按手机号计，每次错误重置 30 分钟过期；达到 5 次后锁定期内拒绝密码登录与修改密码。 */
public interface LoginAttemptStore {

    int failures(String phone);

    /** 错误次数加 1 并把过期时间重置为 ttl，返回加后的次数 */
    int recordFailure(String phone, Duration ttl);

    /** 当前计数剩余秒数，0 表示没有计数/已过期 */
    long remainingSeconds(String phone);

    void clear(String phone);
}
