package com.zhimu.server.framework.idempotent;

import com.zhimu.server.framework.jackson.CacheJson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 幂等记录 idem:{user_id}:{key}（管理员为 idem:a{admin_id}:{key}），24 小时：
 * 处理中为 PROCESSING，成功后为 DONE 并保存响应 JSON（键已是 snake_case）。
 */
@Component
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String KEY_PREFIX = "idem:";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_DONE = "DONE";
    public static final Duration TTL = Duration.ofHours(24);

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;

    /** 没有登录用户和管理员时返回 null（不做幂等） */
    public static String redisKey(Long userId, Long adminId, String idempotencyKey) {
        if (userId != null) {
            return KEY_PREFIX + userId + ":" + idempotencyKey;
        }
        if (adminId != null) {
            return KEY_PREFIX + "a" + adminId + ":" + idempotencyKey;
        }
        return null;
    }

    /** 写入 PROCESSING；键已存在返回 false */
    public boolean tryStart(String redisKey) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey,
                CacheJson.write(new Entry(STATUS_PROCESSING, null)), TTL));
    }

    public void complete(String redisKey, String responseJson) {
        redis.opsForValue().set(redisKey, CacheJson.write(new Entry(STATUS_DONE, responseJson)), TTL);
    }

    public void release(String redisKey) {
        redis.delete(redisKey);
    }

    public Entry read(String redisKey) {
        return CacheJson.read(redis.opsForValue().get(redisKey), Entry.class);
    }

    /**
     * 按请求编号查询提交结果（供各模块“按请求编号查询”接口使用）。
     *
     * @return 没有记录时返回 null；处理中时 response 为 null
     */
    public IdempotencyResult findResult(Long userId, String idempotencyKey) {
        if (userId == null || !StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        Entry entry = read(redisKey(userId, null, idempotencyKey.trim()));
        if (entry == null) {
            return null;
        }
        return new IdempotencyResult(entry.status(), entry.response() == null ? null : parseResponse(entry.response()));
    }

    /** 把缓存的响应 JSON 读成 Map（小数读成 BigDecimal，保留精度与小数位） */
    public Map<String, Object> parseResponse(String json) {
        return CacheJson.mapper().readerFor(MAP_TYPE)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(json);
    }

    /**
     * @param status   PROCESSING / DONE
     * @param response 成功响应的 JSON
     */
    public record Entry(String status, String response) {
    }

    /**
     * @param status   PROCESSING / DONE
     * @param response 第一次请求的完整响应（code、message、data、trace_id），处理中为 null
     */
    public record IdempotencyResult(String status, Map<String, Object> response) {

        public boolean done() {
            return STATUS_DONE.equals(status);
        }
    }
}
