package com.zhimu.server.framework.idempotent;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Idempotent} 的实现：SET NX 写入 PROCESSING；重复请求处理中返回 40901，已成功直接返回第一次的响应；
 * 方法成功后保存 DONE 与响应 JSON；抛异常或返回失败的 ApiResponse 时删除记录（业务错误不缓存）。
 * 响应用 HTTP 的 JsonMapper 序列化，重放结果与第一次返回给该用户的内容一致（含员工字段裁剪）。
 */
@Slf4j
@Aspect
@Component
@Order(IdempotentAspect.ORDER)
@RequiredArgsConstructor
public class IdempotentAspect {

    public static final int ORDER = 20;

    private static final int MAX_KEY_LENGTH = 128;

    private final IdempotencyService service;
    private final JsonMapper jsonMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = RequestContext.idempotencyKey();
        if (!StringUtils.hasText(key)) {
            if (idempotent.required()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少请求头 Idempotency-Key");
            }
            return pjp.proceed();
        }
        key = key.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key 过长");
        }
        String redisKey = IdempotencyService.redisKey(UserContext.userId(), AdminContext.adminId(), key);
        if (redisKey == null) {
            return pjp.proceed();
        }
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        if (!service.tryStart(redisKey)) {
            return replay(redisKey, method);
        }
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable e) {
            service.release(redisKey);
            throw e;
        }
        try {
            if (result instanceof ApiResponse<?> response && !response.isSuccess()) {
                service.release(redisKey);
            } else {
                service.complete(redisKey, jsonMapper.writeValueAsString(result));
            }
        } catch (RuntimeException e) {
            log.warn("保存幂等结果失败 key={}", redisKey, e);
            service.release(redisKey);
        }
        return result;
    }

    private Object replay(String redisKey, Method method) {
        IdempotencyService.Entry entry = service.read(redisKey);
        if (entry == null || !IdempotencyService.STATUS_DONE.equals(entry.status()) || entry.response() == null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == ApiResponse.class) {
            Map<String, Object> cached = service.parseResponse(entry.response());
            Object code = cached.get("code");
            Object message = cached.get("message");
            return new ApiResponse<>(code instanceof Number n ? n.intValue() : 0,
                    message == null ? ApiResponse.OK_MESSAGE : message.toString(), cached.get("data"));
        }
        if (returnType.isAssignableFrom(LinkedHashMap.class)) {
            return service.parseResponse(entry.response());
        }
        return jsonMapper.readValue(entry.response(), jsonMapper.constructType(method.getGenericReturnType()));
    }
}
