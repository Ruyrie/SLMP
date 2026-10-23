package com.zhimu.server.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流：Redis 键 rate:{key}:{维度值}，固定窗口计数，超出返回 42901。可重复标注（例如按手机号和 IP 双重限制）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 限流名称，例如 login、file-upload、ai-ask */
    String key();

    /** 窗口内最多次数 */
    int limit();

    /** 窗口秒数 */
    int windowSeconds() default 60;

    /** 计数维度 */
    LimitBy by() default LimitBy.USER;

    /** by = PARAM 时取的请求参数或请求体字段名（snake_case），例如 phone */
    String param() default "";

    enum LimitBy { USER, IP, PARAM }
}