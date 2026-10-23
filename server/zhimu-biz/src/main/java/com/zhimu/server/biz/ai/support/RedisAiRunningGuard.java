package com.zhimu.server.biz.ai.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 实现：ai:running:{userId} = 1，2 分钟过期；Redis 不可用时放行（不阻塞提问，超时巡查与状态机兜底）。
 */
@Slf4j
@Component
public class RedisAiRunningGuard implements AiRunningGuard {

    private static final String KEY_PREFIX = "ai:running:";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final ObjectProvider<StringRedisTemplate> redis;

    public RedisAiRunningGuard(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return true;
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return true;
        }
        try {
            Boolean ok = template.opsForValue().setIfAbsent(KEY_PREFIX + userId, "1", TTL);
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException e) {
            log.warn("ai:running 写入失败，按可提问处理 userId={}", userId, e);
            return true;
        }
    }

    @Override
    public void release(Long userId) {
        if (userId == null) {
            return;
        }
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.delete(KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("ai:running 删除失败 userId={}", userId, e);
        }
    }
}
