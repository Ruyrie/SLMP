package com.zhimu.server.framework.security.cache;

import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.security.RedisTestSupport;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.SecurityTestData;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.security.spi.UserAuthStateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SecurityCacheTest {

    private final SecurityProperties properties = new SecurityProperties();
    private final long userId = SecurityTestData.randomId();
    private final long farmId = SecurityTestData.randomId();
    private StringRedisTemplate redis;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(RedisTestSupport.available(), "本机 Redis 未启动，跳过");
    }

    private StringRedisTemplate redis() {
        if (redis == null) {
            redis = RedisTestSupport.template();
        }
        return redis;
    }

    @AfterEach
    void cleanup() {
        if (redis != null) {
            redis.delete("auth:state:" + userId);
            RedisTestSupport.deleteByPattern("farm:member:" + farmId + ":*");
            RedisTestSupport.deleteByPattern("farm:member:" + (farmId + 1) + ":*");
        }
    }

    @Test
    void 鉴权状态_未命中调用Loader并缓存10分钟_删除后重新加载() {
        AtomicInteger calls = new AtomicInteger();
        UserAuthStateLoader loader = id -> {
            calls.incrementAndGet();
            return new UserAuthState(4, "NORMAL", "SAVE_RECOVERY_CODE", null);
        };
        AuthStateCache cache = new AuthStateCache(redis(), SecurityTestData.provider(UserAuthStateLoader.class, loader), properties);

        assertThat(cache.hasLoader()).isTrue();
        UserAuthState first = cache.get(userId);
        UserAuthState second = cache.get(userId);

        assertThat(first).isEqualTo(second);
        assertThat(first.hasForcedStep()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(redis.getExpire("auth:state:" + userId)).isBetween(590L, 600L);

        cache.evict(userId);
        cache.get(userId);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 鉴权状态_没有Loader返回null() {
        AuthStateCache cache = new AuthStateCache(redis(), SecurityTestData.provider(UserAuthStateLoader.class, null), properties);
        assertThat(cache.hasLoader()).isFalse();
        assertThat(cache.get(userId)).isNull();
    }

    @Test
    void 成员缓存_读写与按养殖场删除() {
        AtomicInteger calls = new AtomicInteger();
        FarmMemberLoader loader = (f, u) -> {
            calls.incrementAndGet();
            return u == userId + 99 ? null
                    : new FarmContext.FarmMember(f, "东山猪场", u, 7L, FarmRole.STAFF, Set.of(11L, 12L), FarmStatus.NORMAL);
        };
        FarmMemberCache cache = new FarmMemberCache(redis(), SecurityTestData.provider(FarmMemberLoader.class, loader), properties);

        FarmContext.FarmMember member = cache.get(farmId, userId);
        FarmContext.FarmMember cached = cache.get(farmId, userId);
        assertThat(cached).isEqualTo(member);
        assertThat(cached.penIds()).containsExactlyInAnyOrder(11L, 12L);
        assertThat(cached.role()).isEqualTo(FarmRole.STAFF);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(cache.get(farmId, userId + 99)).isNull();

        cache.get(farmId, userId + 1);
        cache.get(farmId + 1, userId);
        cache.evictFarm(farmId);
        assertThat(redis.hasKey("farm:member:" + farmId + ":" + userId)).isFalse();
        assertThat(redis.hasKey("farm:member:" + farmId + ":" + (userId + 1))).isFalse();
        assertThat(redis.hasKey("farm:member:" + (farmId + 1) + ":" + userId)).isTrue();

        cache.evict(farmId + 1, userId);
        assertThat(redis.hasKey("farm:member:" + (farmId + 1) + ":" + userId)).isFalse();
    }

    @Test
    void 成员缓存_没有Loader返回null() {
        FarmMemberCache cache = new FarmMemberCache(redis(), SecurityTestData.provider(FarmMemberLoader.class, null), properties);
        assertThat(cache.get(farmId, userId)).isNull();
    }
}
