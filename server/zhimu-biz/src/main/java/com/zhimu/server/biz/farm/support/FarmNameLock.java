package com.zhimu.server.biz.farm.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.redis.RedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 名称并发锁 lock:farm-name:{user_id}（10 秒）：新建、修改注册信息、资料变更、接受转让都在锁内查重并写入。
 * 拿不到锁返回 42901。
 */
@Component
@RequiredArgsConstructor
public class FarmNameLock {

    public static final String KEY_PREFIX = "lock:farm-name:";
    static final long LEASE_SECONDS = 10;

    private final RedisLock redisLock;

    public <T> T call(Long userId, Supplier<T> action) {
        String key = KEY_PREFIX + userId;
        String token;
        try {
            token = redisLock.tryLock(key, LEASE_SECONDS);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.TOO_FREQUENT);
        }
        if (token == null) {
            throw new BizException(ErrorCode.TOO_FREQUENT);
        }
        try {
            return action.get();
        } finally {
            redisLock.unlock(key, token);
        }
    }
}
