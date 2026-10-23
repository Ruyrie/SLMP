package com.zhimu.server.framework.security.annotation;

import com.zhimu.server.common.enums.FarmStatus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要养殖场上下文的接口。养殖场 ID 优先取路径变量 {farmId}，没有时取请求头 X-Farm-Id。
 * 不是成员返回 40302；养殖场状态不允许返回 40305（冻结返回 40306）。
 * <p>
 * allowStatus 为空时按默认规则：GET 请求允许 NORMAL、DISABLED；其他方法只允许 NORMAL。
 * 指定 allowStatus 时以指定值为准（例如模块02 的“重新提交注册信息”允许 PENDING、REJECTED）。
 * 方法上的注解覆盖类上的注解。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireFarm {

    FarmStatus[] allowStatus() default {};
}