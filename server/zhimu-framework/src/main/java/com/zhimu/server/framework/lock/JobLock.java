package com.zhimu.server.framework.lock;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务锁：执行前获取 Redis 锁 lock:job:{value}，拿不到则跳过本次执行，保证多实例只执行一次。
 * 与 @Scheduled 一起标注在任务方法上；执行失败自动重试一次；结果与耗时写日志。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobLock {

    /** 任务名，例如 stock-snapshot */
    String value();

    /** 锁的最长持有时间（秒），应不小于任务最长执行时间 */
    long leaseSeconds() default 600;
}