package com.zhimu.server.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理视图字段：单价、均价、金额、成本、利润、收入等员工看不到的字段。
 * 当前养殖场角色为员工（STAFF）时，序列化直接省略该字段（JSON 中没有这个键，而不是 null）。
 * 标注在 VO 的字段上。
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagerView {
}
