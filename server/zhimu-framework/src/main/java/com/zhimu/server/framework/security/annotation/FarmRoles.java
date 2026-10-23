package com.zhimu.server.framework.security.annotation;

import com.zhimu.server.common.enums.FarmRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许调用的养殖场角色；不在其中返回 40303。隐含 {@link RequireFarm}（默认状态规则）。
 * 方法上的注解覆盖类上的注解。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FarmRoles {

    FarmRole[] value();
}