package com.zhimu.server.biz.account.test;

import com.zhimu.server.common.util.BeijingTime;

import java.time.Clock;
import java.time.LocalDateTime;

/** 测试固定时钟：2026-10-19 周一 09:30（模块01 文档示例时间）。 */
public final class FixedTime {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 9, 30);

    private FixedTime() {
    }

    public static void set() {
        MybatisTestSupport.init();
        BeijingTime.setClock(Clock.fixed(NOW.atZone(BeijingTime.ZONE).toInstant(), BeijingTime.ZONE));
    }

    public static void reset() {
        BeijingTime.setClock(null);
    }
}
