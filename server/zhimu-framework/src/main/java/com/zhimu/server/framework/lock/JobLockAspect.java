package com.zhimu.server.framework.lock;

import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.framework.redis.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link JobLock} 切面：获取 Redis 锁 lock:job:{任务名}，拿不到（其他实例正在执行）就跳过；
 * 执行失败自动重试一次，仍失败只写错误日志不再抛出；结束记录耗时与结果，最后释放锁。
 * 顺序在事务之外，重试时会开启新事务。执行期间写入 trace_id（job- 开头）便于按任务检索日志。
 */
@Slf4j
@Aspect
@Component
@Order(JobLockAspect.ORDER)
@RequiredArgsConstructor
public class JobLockAspect {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 20;
    public static final String KEY_PREFIX = "lock:job:";

    private static final String MDC_TRACE_ID = "trace_id";

    private final RedisLock redisLock;

    @Around("@annotation(jobLock)")
    public Object around(ProceedingJoinPoint pjp, JobLock jobLock) throws Throwable {
        String name = jobLock.value();
        String key = KEY_PREFIX + name;
        String token;
        try {
            token = redisLock.tryLock(key, jobLock.leaseSeconds());
        } catch (RuntimeException e) {
            log.error("定时任务 {} 获取锁失败，本次跳过", name, e);
            return null;
        }
        if (token == null) {
            log.debug("定时任务 {} 未获取到锁（其他实例正在执行），本次跳过", name);
            return null;
        }

        boolean traceOwner = TraceContext.get() == null;
        if (traceOwner) {
            String traceId = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            TraceContext.set(traceId);
            MDC.put(MDC_TRACE_ID, traceId);
        }
        long start = System.currentTimeMillis();
        try {
            return execute(pjp, name, start);
        } finally {
            try {
                redisLock.unlock(key, token);
            } catch (RuntimeException e) {
                log.warn("定时任务 {} 释放锁失败，锁将在 {} 秒内自动过期", name, jobLock.leaseSeconds(), e);
            }
            if (traceOwner) {
                TraceContext.clear();
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }

    private Object execute(ProceedingJoinPoint pjp, String name, long start) {
        try {
            Object result = pjp.proceed();
            log.info("定时任务 {} 执行成功，耗时 {} ms", name, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable first) {
            log.warn("定时任务 {} 执行失败，立即重试一次", name, first);
        }
        try {
            Object result = pjp.proceed();
            log.info("定时任务 {} 重试成功，总耗时 {} ms", name, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable second) {
            log.error("定时任务 {} 重试后仍失败，总耗时 {} ms", name, System.currentTimeMillis() - start, second);
            return null;
        }
    }
}
