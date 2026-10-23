package com.zhimu.server.biz.account.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Redis 实现：login:fail:{phone} 连续密码错误次数。 */
@Component
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {

    public static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redis;

    @Override
    public int failures(String phone) {
        String value = redis.opsForValue().get(KEY_PREFIX + phone);
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
    public int recordFailure(String phone, Duration ttl) {
        String key = KEY_PREFIX + phone;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, ttl);
        return count == null ? 0 : count.intValue();
    }

    @Override
    public long remainingSeconds(String phone) {
        Long ttl = redis.getExpire(KEY_PREFIX + phone, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    @Override
    public void clear(String phone) {
        redis.delete(KEY_PREFIX + phone);
    }
}
