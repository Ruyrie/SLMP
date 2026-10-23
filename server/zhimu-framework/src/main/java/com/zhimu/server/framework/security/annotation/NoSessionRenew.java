package com.zhimu.server.framework.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记在管理端轮询/角标接口上：校验会话但不续期 30 分钟滑动 TTL。
 * 用于 ADM-S41（未读数）与 ADM-R01（待审核数）等高频轮询接口。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoSessionRenew {
}
