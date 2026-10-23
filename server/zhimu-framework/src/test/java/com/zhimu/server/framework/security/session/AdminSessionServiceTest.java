package com.zhimu.server.framework.security.session;

import com.zhimu.server.framework.security.RedisTestSupport;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.SecurityTestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AdminSessionServiceTest {

    private final SecurityProperties properties = new SecurityProperties();
    private final long adminId = SecurityTestData.randomId();
    private StringRedisTemplate redis;
    private AdminSessionService service;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(RedisTestSupport.available(), "本机 Redis 未启动，跳过");
    }

    private AdminSessionService service() {
        if (service == null) {
            redis = RedisTestSupport.template();
            service = new AdminSessionService(redis, properties);
        }
        return service;
    }

    @AfterEach
    void cleanup() {
        if (service != null) {
            service.invalidateAll(adminId);
        }
    }

    @Test
    void 创建_读取_续期_作废() {
        String token = service().create(adminId, 3);

        assertThat(token).hasSize(43);
        AdminSession session = service.get(token);
        assertThat(session.adminId()).isEqualTo(adminId);
        assertThat(session.tokenVersion()).isEqualTo(3);
        assertThat(session.createdAt()).isNotNull();
        assertThat(redis.opsForSet().isMember("admin:session:index:" + adminId, token)).isTrue();

        redis.expire("admin:session:" + token, Duration.ofSeconds(10));
        service.touch(token, adminId);
        assertThat(redis.getExpire("admin:session:" + token)).isGreaterThan(1700);

        service.invalidate(token);
        assertThat(service.get(token)).isNull();
        assertThat(redis.opsForSet().isMember("admin:session:index:" + adminId, token)).isFalse();
        assertThat(service.get(null)).isNull();
    }

    @Test
    void 作废管理员全部会话() {
        String a = service().create(adminId, 0);
        String b = service.create(adminId, 0);

        service.invalidateAll(adminId);

        assertThat(service.get(a)).isNull();
        assertThat(service.get(b)).isNull();
        assertThat(redis.hasKey("admin:session:index:" + adminId)).isFalse();
    }

    @Test
    void 管理端Cookie() {
        AdminSessionService cookieOnly = new AdminSessionService(null, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieOnly.writeCookie(response, "session-token");

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("ZMT_ADMIN_SESSION=session-token")
                .contains("Path=/admin/v1")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .doesNotContain("Max-Age");

        MockHttpServletResponse cleared = new MockHttpServletResponse();
        cookieOnly.clearCookie(cleared);
        assertThat(cleared.getHeader("Set-Cookie")).contains("Max-Age=0").contains("Path=/admin/v1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ZMT_ADMIN_SESSION", "abc"));
        assertThat(cookieOnly.readCookie(request)).isEqualTo("abc");
    }
}
