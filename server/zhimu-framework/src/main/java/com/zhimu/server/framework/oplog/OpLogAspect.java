package com.zhimu.server.framework.oplog;

import com.zhimu.server.common.error.BizException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link OpLog} 切面：方法正常返回记 SUCCESS；抛异常记 FAIL（extra_text 为失败原因）并继续抛出原异常。
 * <p>
 * bizId、targetName、reason、farmId 为 SpEL，可引用参数名（#request.reason、#id）与返回值（#result，失败时为 null）；
 * 表达式解析失败只写警告日志，该字段留空。操作人、养殖场由 {@link OperationLogSupport#fillContext} 按上下文补齐。
 * <p>
 * 有 {@link OperationLogRecorder} 实现时交给它写库，否则以 JSON 写到 OPLOG 日志。留痕本身出错不影响业务结果。
 * 切面顺序在事务之外：业务事务回滚时 FAIL 记录不会随之回滚（记录器自行决定事务）。
 */
@Slf4j
@Aspect
@Component
@Order(OpLogAspect.ORDER)
public class OpLogAspect {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 10;
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";

    private static final int MAX_TEXT = 500;

    private final ObjectProvider<OperationLogRecorder> recorderProvider;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public OpLogAspect(ObjectProvider<OperationLogRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            record(pjp, opLog, null, ex);
            throw ex;
        }
        record(pjp, opLog, result, null);
        return result;
    }

    private void record(ProceedingJoinPoint pjp, OpLog opLog, Object result, Throwable ex) {
        try {
            EvaluationContext context = evaluationContext(pjp, result);
            boolean failed = ex != null;
            OperationLogEntry entry = OperationLogEntry.builder()
                    .opType(opLog.opType())
                    .opAction(opLog.opAction())
                    .result(failed ? RESULT_FAIL : RESULT_SUCCESS)
                    .bizType(opLog.bizType().isBlank() ? null : opLog.bizType())
                    .bizId(evaluate(opLog.bizId(), context, Long.class, failed))
                    .targetName(evaluate(opLog.targetName(), context, String.class, failed))
                    .reason(evaluate(opLog.reason(), context, String.class, failed))
                    .farmId(evaluate(opLog.farmId(), context, Long.class, failed))
                    .extraText(failed ? failText(ex) : null)
                    .build();
            OperationLogSupport.fillContext(entry);
            OperationLogRecorder recorder = recorderProvider.getIfAvailable();
            if (recorder != null) {
                recorder.record(entry);
            } else {
                OperationLogSupport.writeToLog(entry);
            }
        } catch (RuntimeException e) {
            log.error("操作留痕写入失败：{}.{}", opLog.opType(), opLog.opAction(), e);
        }
    }

    private EvaluationContext evaluationContext(ProceedingJoinPoint pjp, Object result) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (pjp.getTarget() != null) {
            method = AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(pjp.getTarget()));
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                pjp.getTarget(), method, pjp.getArgs(), parameterNameDiscoverer);
        context.setVariable("result", result);
        return context;
    }

    /** 失败分支里 #result 为 null，引用它的表达式取不到值属于预期，只记 debug */
    private <T> T evaluate(String expression, EvaluationContext context, Class<T> type, boolean failed) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            Expression parsed = expressionCache.computeIfAbsent(expression, parser::parseExpression);
            return parsed.getValue(context, type);
        } catch (RuntimeException e) {
            if (failed && expression.contains("#result")) {
                log.debug("留痕表达式在失败分支取不到值：{}", expression);
            } else {
                log.warn("留痕表达式解析失败：{}，{}", expression, e.getMessage());
            }
            return null;
        }
    }

    /** 业务异常记文案；其他异常只记类型，不把 SQL 等内部信息写进留痕 */
    private static String failText(Throwable ex) {
        String text = ex instanceof BizException biz
                ? biz.getCode() + " " + biz.getMessage()
                : "系统异常（" + ex.getClass().getSimpleName() + "）";
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }
}
