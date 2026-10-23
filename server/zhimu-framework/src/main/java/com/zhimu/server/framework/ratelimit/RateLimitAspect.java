package com.zhimu.server.framework.ratelimit;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.web.SnakeCaseNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

/**
 * {@link RateLimit} 的实现：固定窗口计数，键 rate:{key}:{维度值}，第一次 INCR 时设置过期；超出返回 42901（data.cooldown_seconds）。
 * 维度：USER 取用户 ID（管理员为 a{admin_id}，都没有时退回 IP）；IP 取客户端 IP；
 * PARAM 取 @RequestParam 参数或请求体对象中对应 snake_case 名的属性，取不到值时不计数。
 */
@Slf4j
@Aspect
@Component
@Order(RateLimitAspect.ORDER)
@RequiredArgsConstructor
public class RateLimitAspect {

    public static final int ORDER = 10;
    public static final String KEY_PREFIX = "rate:";

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RedisScript<List> INCR_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "local ttl = redis.call('TTL', KEYS[1]) "
                    + "if c == 1 or ttl < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) ttl = tonumber(ARGV[1]) end "
                    + "return {c, ttl}", List.class);

    private final StringRedisTemplate redis;

    @Around("@annotation(com.zhimu.server.framework.ratelimit.RateLimit) "
            + "|| @annotation(com.zhimu.server.framework.ratelimit.RateLimits)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        for (RateLimit limit : AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class)) {
            String dimension = dimension(limit, method, pjp.getArgs());
            if (dimension == null) {
                log.debug("限流 {} 取不到参数 {}，不计数", limit.key(), limit.param());
                continue;
            }
            check(KEY_PREFIX + limit.key() + ":" + dimension, limit);
        }
        return pjp.proceed();
    }

    private void check(String key, RateLimit limit) {
        List<?> result = redis.execute(INCR_SCRIPT, List.of(key), String.valueOf(limit.windowSeconds()));
        if (result == null || result.size() < 2) {
            return;
        }
        long count = ((Number) result.get(0)).longValue();
        if (count > limit.limit()) {
            long ttl = Math.max(1, ((Number) result.get(1)).longValue());
            throw new BizException(ErrorCode.TOO_FREQUENT).with("cooldown_seconds", ttl);
        }
    }

    private static String dimension(RateLimit limit, Method method, Object[] args) {
        return switch (limit.by()) {
            case USER -> {
                Long userId = UserContext.userId();
                if (userId != null) {
                    yield String.valueOf(userId);
                }
                Long adminId = AdminContext.adminId();
                yield adminId != null ? "a" + adminId : ip();
            }
            case IP -> ip();
            case PARAM -> paramValue(limit.param(), method, args);
        };
    }

    private static String ip() {
        String ip = RequestContext.ip();
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    /** 依次查找：@RequestParam 或同名简单参数、请求体（或查询对象、Map）中的属性、原始请求参数 */
    static String paramValue(String param, Method method, Object[] args) {
        if (!StringUtils.hasText(param)) {
            return null;
        }
        String camel = SnakeCaseNames.toCamel(param);
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || !BeanUtils.isSimpleValueType(arg.getClass())) {
                continue;
            }
            RequestParam rp = AnnotatedElementUtils.findMergedAnnotation(parameters[i], RequestParam.class);
            String name = rp != null && StringUtils.hasText(rp.name()) ? rp.name() : parameters[i].getName();
            if (param.equals(name) || camel.equals(name)) {
                return textOf(arg);
            }
        }
        for (Object arg : args) {
            if (arg instanceof Map<?, ?> map) {
                String value = textOf(map.get(param));
                if (value != null) {
                    return value;
                }
            } else if (arg != null && arg.getClass().getName().startsWith("com.zhimu.server.")) {
                BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(arg);
                if (wrapper.isReadableProperty(camel)) {
                    String value = textOf(wrapper.getPropertyValue(camel));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return textOf(servlet.getRequest().getParameter(param));
        }
        return null;
    }

    private static String textOf(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
