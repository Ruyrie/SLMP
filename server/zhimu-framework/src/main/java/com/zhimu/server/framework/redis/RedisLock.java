package com.zhimu.server.framework.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 互斥锁（单节点，SET NX EX + Lua 比较后删除）。不可重入；锁到期自动释放，持有时间应不小于业务最长执行时间。
 * <pre>
 * String token = redisLock.tryLock("lock:xxx:" + id, 30);
 * if (token == null) { throw new BizException(ErrorCode.DUPLICATE_SUBMIT); }
 * try { ... } finally { redisLock.unlock("lock:xxx:" + id, token); }
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    /** 实例编号：主机名-进程号，写在锁的值里便于排查 */
    public static final String INSTANCE_ID = resolveInstanceId();

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试加锁。
     *
     * @return 锁令牌（解锁时传回）；锁已被占用时返回 null
     */
    public String tryLock(String key, long leaseSeconds) {
        String token = INSTANCE_ID + ":" + UUID.randomUUID().toString().replace("-", "");
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(leaseSeconds));
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /**
     * 解锁：只有令牌一致（仍是自己持有的锁）才删除。
     *
     * @return 是否删除了锁；锁已过期或被他人持有时返回 false
     */
    public boolean unlock(String key, String token) {
        if (token == null) {
            return false;
        }
        Long deleted = stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        return deleted != null && deleted > 0;
    }

    private static String resolveInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = System.getenv("COMPUTERNAME");
        }
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "unknown";
            }
        }
        return host + "-" + ProcessHandle.current().pid();
    }
}
