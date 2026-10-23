package com.zhimu.server.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 白名单接口：不需要登录（登录、注册、验证码、找回、协议、管理端登录与图形验证码等）。
 * 标注在 Controller 类或方法上。带了有效令牌时仍会解析并写入 UserContext / AdminContext。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}