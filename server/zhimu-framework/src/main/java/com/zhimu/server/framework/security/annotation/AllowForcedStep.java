package com.zhimu.server.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 账号有待完成的强制步骤（例如找回后修改密码、保存恢复码；管理员首次登录改密）时仍允许调用的接口。
 * 未标注的接口在有强制步骤时返回 40309，data.next_step 指明步骤。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowForcedStep {
}