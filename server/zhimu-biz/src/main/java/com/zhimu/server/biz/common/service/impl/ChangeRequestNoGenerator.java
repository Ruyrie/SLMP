package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * 修改申请编号：Redis 序号 seq:cr:yyMMdd 每天从 1 开始，request_no = CR + yyMMdd + 4 位序号（例如 CR2610190008，
 * 与 App Mock / 管理端-01 示例一致）。序号保留 2 天，避免跨天时旧键过早丢失。
 */
@Component
@RequiredArgsConstructor
public class ChangeRequestNoGenerator {

    public static final String KEY_PREFIX = "seq:cr:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Duration TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;

    public String next() {
        String day = BeijingTime.today().format(DAY);
        String key = KEY_PREFIX + day;
        Long seq = redis.opsForValue().increment(key);
        if (seq == null) {
            seq = 1L;
        }
        if (seq == 1L) {
            redis.expire(key, TTL);
        }
        return "CR" + day + String.format("%04d", seq % 10000);
    }
}
