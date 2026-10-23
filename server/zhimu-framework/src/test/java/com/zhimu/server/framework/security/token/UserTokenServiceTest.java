package com.zhimu.server.framework.security.token;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.security.MutableClock;
import com.zhimu.server.framework.security.RedisTestSupport;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.SecurityTestData;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.spi.UserAuthState;
import com.zhimu.server.framework.web.TestMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTokenServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
    private final SecurityProperties properties = SecurityTestData.properties();
    private final UserJwtService jwtService = new UserJwtService(properties, false, clock);
    private final AuthStateCache authStateCache = mock(AuthStateCache.class);
    private final long userId = SecurityTestData.randomId();
    private StringRedisTemplate redis;
    private UserTokenService service;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(RedisTestSupport.available(), "本机 Redis 未启动，跳过");
    }

    private UserTokenService service() {
        if (service == null) {
            redis = RedisTestSupport.template();
            service = new UserTokenService(redis, jwtService, authStateCache, properties, clock);
        }
        return service;
    }

    @AfterEach
    void cleanup() {
        if (service != null) {
            service.revokeAll(userId);
        }
    }

    @Test
    void 签发令牌() {
        TokenPair pair = service().issue(userId, ClientType.APP_ANDROID, "device-1", 2);

        assertThat(pair.tokenType()).isEqualTo("Bearer");
        assertThat(pair.expiresIn()).isEqualTo(7200);
        assertThat(pair.refreshExpiresIn()).isEqualTo(2_592_000);
        assertThat(pair.refreshToken()).hasSize(43);
        assertThat(jwtService.parse(pair.accessToken()).tokenVersion()).isEqualTo(2);
        assertThat(redis.getExpire("auth:refresh:" + pair.refreshToken())).isBetween(2_591_000L, 2_592_000L);
        assertThat(redis.opsForValue().get("auth:refresh:" + pair.refreshToken()))
                .contains("\"user_id\":" + userId).contains("\"client\":\"APP_ANDROID\"").contains("\"device_id\":\"device-1\"")
                .contains("\"issued_at\":");
        assertThat(redis.opsForSet().isMember("auth:user:" + userId, pair.refreshToken())).isTrue();

        String json = TestMvc.MAPPER.writeValueAsString(pair);
        assertThat(json).contains("\"access_token\"").contains("\"token_type\":\"Bearer\"").contains("\"expires_in\":7200")
                .contains("\"refresh_token\"").contains("\"refresh_expires_in\":2592000");
    }

    @Test
    void 刷新时轮换_宽限期内重复使用旧令牌返回同一组新令牌() {
        TokenPair first = service().issue(userId, ClientType.APP_ANDROID, "device-1", 0);

        TokenPair second = service.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(jwtService.parse(second.accessToken()).userId()).isEqualTo(userId);

        clock.advance(Duration.ofSeconds(20));
        TokenPair again = service.refresh(first.refreshToken());
        assertThat(again).isEqualTo(second);

        TokenPair third = service.refresh(second.refreshToken());
        assertThat(third.refreshToken()).isNotEqualTo(second.refreshToken());
    }

    @Test
    void 超过宽限期再用旧令牌视为盗用_作废该客户端全部刷新令牌() {
        TokenPair first = service().issue(userId, ClientType.APP_ANDROID, "device-1", 0);
        TokenPair web = service.issue(userId, ClientType.WEB, null, 0);
        TokenPair second = service.refresh(first.refreshToken());

        clock.advance(Duration.ofSeconds(31));
        assertReason(() -> service.refresh(first.refreshToken()), "EXPIRED");
        assertReason(() -> service.refresh(second.refreshToken()), "EXPIRED");

        assertThat(service.refresh(web.refreshToken()).refreshToken()).isNotBlank();
    }

    @Test
    void 刷新令牌不存在返回40104_EXPIRED() {
        service();
        assertReason(() -> service.refresh("no-such-token"), "EXPIRED");
        assertReason(() -> service.refresh(""), "EXPIRED");
    }

    @Test
    void 同一类客户端只保留最近一次登录() {
        TokenPair phoneA = service().issue(userId, ClientType.APP_ANDROID, "phone-a", 0);
        TokenPair web = service.issue(userId, ClientType.WEB, null, 0);
        TokenPair phoneB = service.issue(userId, ClientType.APP_ANDROID, "phone-b", 0);

        assertReason(() -> service.refresh(phoneA.refreshToken()), "EXPIRED");
        assertThat(service.refresh(phoneB.refreshToken())).isNotNull();
        assertThat(service.refresh(web.refreshToken())).isNotNull();
    }

    @Test
    void 作废某类客户端与作废全部() {
        TokenPair app = service().issue(userId, ClientType.APP_ANDROID, "phone", 0);
        TokenPair web = service.issue(userId, ClientType.WEB, null, 0);

        service.revokeClient(userId, ClientType.WEB);
        assertReason(() -> service.refresh(web.refreshToken()), "EXPIRED");
        TokenPair appNext = service.refresh(app.refreshToken());

        service.revokeAll(userId);
        assertReason(() -> service.refresh(appNext.refreshToken()), "EXPIRED");
        assertThat(redis.hasKey("auth:user:" + userId)).isFalse();
    }

    @Test
    void 刷新时校验账号状态() {
        when(authStateCache.hasLoader()).thenReturn(true);

        TokenPair disabled = service().issue(userId, ClientType.APP_ANDROID, "phone", 1);
        when(authStateCache.get(anyLong())).thenReturn(new UserAuthState(1, "DISABLED", null, null));
        assertReason(() -> service.refresh(disabled.refreshToken()), "ACCOUNT_DISABLED");

        TokenPair cancelled = service.issue(userId, ClientType.APP_ANDROID, "phone", 1);
        when(authStateCache.get(anyLong())).thenReturn(new UserAuthState(1, "CANCELLED", null, null));
        assertReason(() -> service.refresh(cancelled.refreshToken()), "ACCOUNT_CANCELLED");

        TokenPair missing = service.issue(userId, ClientType.APP_ANDROID, "phone", 1);
        when(authStateCache.get(anyLong())).thenReturn(null);
        assertReason(() -> service.refresh(missing.refreshToken()), "EXPIRED");

        TokenPair changed = service.issue(userId, ClientType.APP_ANDROID, "phone", 1);
        when(authStateCache.get(anyLong())).thenReturn(new UserAuthState(2, "NORMAL", null, "PASSWORD_CHANGED"));
        assertReason(() -> service.refresh(changed.refreshToken()), "PASSWORD_CHANGED");

        TokenPair ok = service.issue(userId, ClientType.APP_ANDROID, "phone", 2);
        when(authStateCache.get(anyLong())).thenReturn(new UserAuthState(2, "NORMAL", "NONE", null));
        TokenPair next = service.refresh(ok.refreshToken());
        assertThat(jwtService.parse(next.accessToken()).tokenVersion()).isEqualTo(2);
    }

    private static void assertReason(Runnable action, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class, e -> {
            assertThat(e.getCode()).isEqualTo(40104);
            assertThat(SecurityTestData.dataOf(e, "reason")).isEqualTo(reason);
        });
    }
}
