package com.zhimu.server.framework.security.cache;

import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.security.spi.UserAuthStateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户鉴权状态缓存 auth:state:{user_id}（10 分钟）。未命中时调用模块01 的 {@link UserAuthStateLoader}。
 * 改密、停用、注销、找回等状态变化时，由模块01 调用 {@link #evict(Long)}。
 */
@Component
@RequiredArgsConstructor
public class AuthStateCache {

    public static final String KEY_PREFIX = "auth:state:";

    private final StringRedisTemplate redis;
    private final ObjectProvider<UserAuthStateLoader> loaderProvider;
    private final SecurityProperties properties;

    /** 是否有 Loader 实现；没有时调用方跳过状态校验 */
    public boolean hasLoader() {
        return loaderProvider.getIfAvailable() != null;
    }

    /**
     * @return 鉴权状态；没有 Loader 实现或用户不存在时返回 null（先用 {@link #hasLoader()} 区分）
     */
    public UserAuthState get(Long userId) {
        String key = KEY_PREFIX + userId;
        String json = redis.opsForValue().get(key);
        if (json != null) {
            return CacheJson.read(json, UserAuthState.class);
        }
        UserAuthStateLoader loader = loaderProvider.getIfAvailable();
        if (loader == null) {
            return null;
        }
        UserAuthState state = loader.load(userId);
        if (state != null) {
            redis.opsForValue().set(key, CacheJson.write(state), properties.getAuthStateTtl());
        }
        return state;
    }

    public void evict(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
