package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现：sms:limit:{scene}:{phone} 冷却、sms:count:{phone}:{yyyyMMdd} 当天条数、
 * sms:sent:{scene}:{phone} 已发标记、sms:fail:{scene}:{phone} 连错次数。
 */
@Component
@RequiredArgsConstructor
public class RedisSmsCodeStore implements SmsCodeStore {

    public static final String LIMIT_PREFIX = "sms:limit:";
    public static final String COUNT_PREFIX = "sms:count:";
    public static final String SENT_PREFIX = "sms:sent:";
    public static final String FAIL_PREFIX = "sms:fail:";

    private final StringRedisTemplate redis;

    @Override
    public long cooldownSeconds(SmsScene scene, String phone) {
        Long ttl = redis.getExpire(limitKey(scene, phone), TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    @Override
    public void startCooldown(SmsScene scene, String phone, int seconds) {
        redis.opsForValue().set(limitKey(scene, phone), "1", Duration.ofSeconds(Math.max(1, seconds)));
    }

    @Override
    public long dailyCount(String phone) {
        String value = redis.opsForValue().get(countKey(phone));
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public long increaseDailyCount(String phone) {
        String key = countKey(phone);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            LocalDateTime nextMidnight = LocalDate.now(BeijingTime.clock()).plusDays(1).atStartOfDay();
            long seconds = Math.max(1, Duration.between(BeijingTime.now(), nextMidnight).toSeconds());
            redis.expire(key, Duration.ofSeconds(seconds));
        }
        return count == null ? 0 : count;
    }

    @Override
    public void markSent(SmsScene scene, String phone, int ttlSeconds) {
        redis.opsForValue().set(sentKey(scene, phone), "1", Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public boolean isSent(SmsScene scene, String phone) {
        return Boolean.TRUE.equals(redis.hasKey(sentKey(scene, phone)));
    }

    @Override
    public void markVerified(SmsScene scene, String phone, int ttlSeconds) {
        redis.opsForValue().set(sentKey(scene, phone), "VERIFIED", Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public boolean isVerified(SmsScene scene, String phone) {
        String value = redis.opsForValue().get(sentKey(scene, phone));
        return "VERIFIED".equals(value);
    }

    @Override
    public int recordWrongAttempt(SmsScene scene, String phone, int ttlSeconds) {
        String key = FAIL_PREFIX + scene.name() + ":" + phone;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(Math.max(1, ttlSeconds)));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public int wrongAttempts(SmsScene scene, String phone) {
        String value = redis.opsForValue().get(FAIL_PREFIX + scene.name() + ":" + phone);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void clear(SmsScene scene, String phone) {
        redis.delete(java.util.List.of(sentKey(scene, phone), FAIL_PREFIX + scene.name() + ":" + phone));
    }

    private static String limitKey(SmsScene scene, String phone) {
        return LIMIT_PREFIX + scene.name() + ":" + phone;
    }

    private static String sentKey(SmsScene scene, String phone) {
        return SENT_PREFIX + scene.name() + ":" + phone;
    }

    private static String countKey(String phone) {
        return COUNT_PREFIX + phone + ":" + BeijingTime.today().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }
}
