package com.zhimu.server.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 新增类接口的幂等：读取请求头 Idempotency-Key（任意非空字符串，不要求 UUID），
 * Redis 键 idem:{user_id}:{key} 保存处理状态与成功响应 24 小时。
 * 同一键正在处理返回 40901；已成功则直接返回第一次的响应；业务错误不缓存。
 * 请求没有带幂等键时 required = false 照常处理，required = true 返回 40001。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    boolean required() default false;
}