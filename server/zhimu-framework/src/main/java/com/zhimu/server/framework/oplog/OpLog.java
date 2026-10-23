package com.zhimu.server.framework.oplog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作留痕：方法执行后写 t_operation_log（成功写 SUCCESS；抛异常时写 FAIL 并继续抛出）。
 * bizId、targetName、reason、farmId 为 SpEL，可引用方法参数名（#request.reason）与返回值（#result）。
 * 复杂场景（前后快照、修改申请回写）直接调用 OperationLogRecorder。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 操作类别，例如 FARM_AUDIT、RECORD_EDIT */
    String opType();

    /** 动作，例如 APPROVE、REJECT、UPDATE、DELETE */
    String opAction();

    /** 被操作记录类型（BizType 编码） */
    String bizType() default "";

    String bizId() default "";

    String targetName() default "";

    String reason() default "";

    String farmId() default "";
}