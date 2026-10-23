package com.zhimu.server.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端权限点，格式“页面编号:动作”，例如 WA-R01:VIEW、WA-R01:HANDLE；满足任意一个即可。
 * 没有权限返回 40308。只用于 /admin/v1 下的接口。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminPermission {

    String[] value();
}