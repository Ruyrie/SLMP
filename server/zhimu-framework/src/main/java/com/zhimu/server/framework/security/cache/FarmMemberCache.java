package com.zhimu.server.framework.security.cache;

import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 成员缓存 farm:member:{farm_id}:{user_id}（10 分钟）：角色、负责栏舍、养殖场状态。
 * 未命中时调用模块02 的 {@link FarmMemberLoader}；成员角色、负责栏舍、养殖场状态变化时由模块02 删除缓存。
 */
@Component
@RequiredArgsConstructor
public class FarmMemberCache {

    public static final String KEY_PREFIX = "farm:member:";

    private static final int DELETE_BATCH = 500;

    private final StringRedisTemplate redis;
    private final ObjectProvider<FarmMemberLoader> loaderProvider;
    private final SecurityProperties properties;

    /** 不是成员或没有 Loader 实现时返回 null */
    public FarmContext.FarmMember get(Long farmId, Long userId) {
        String key = key(farmId, userId);
        String json = redis.opsForValue().get(key);
        if (json != null) {
            return CacheJson.read(json, FarmContext.FarmMember.class);
        }
        FarmMemberLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            return null;
        }
        FarmContext.FarmMember member = loader.load(farmId, userId);
        if (member != null) {
            redis.opsForValue().set(key, CacheJson.write(member), properties.getFarmMemberTtl());
        }
        return member;
    }

    public void evict(Long farmId, Long userId) {
        redis.delete(key(farmId, userId));
    }

    /** 删除该养殖场全部成员的缓存（养殖场状态变化时用），按前缀 SCAN 后删除 */
    public void evictFarm(Long farmId) {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + farmId + ":*").count(DELETE_BATCH).build();
        List<String> batch = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= DELETE_BATCH) {
                    redis.delete(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            redis.delete(batch);
        }
    }

    private static String key(Long farmId, Long userId) {
        return KEY_PREFIX + farmId + ":" + userId;
    }
}
