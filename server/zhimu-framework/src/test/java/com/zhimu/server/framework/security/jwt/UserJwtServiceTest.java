package com.zhimu.server.framework.security.jwt;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.security.MutableClock;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.SecurityTestData;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtServiceTest {

    private final MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
    private final UserJwtService service = new UserJwtService(SecurityTestData.properties(), false, clock);

    @Test
    void 签发与解析() {
        UserJwtService.AccessToken token = service.issue(1001L, ClientType.APP_ANDROID, 3);

        assertThat(token.expiresIn()).isEqualTo(7200);
        UserJwtService.UserClaims claims = service.parse(token.token());
        assertThat(claims.userId()).isEqualTo(1001L);
        assertThat(claims.tokenVersion()).isEqualTo(3);
        assertThat(claims.clientType()).isEqualTo(ClientType.APP_ANDROID);
        assertThat(claims.jti()).isEqualTo(token.jti()).hasSize(32);

        String payload = new String(Base64.getUrlDecoder().decode(token.token().split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"1001\"").contains("\"tv\":3").contains("\"typ\":\"USER\"")
                .contains("\"cli\":\"APP_ANDROID\"").contains("\"jti\":");
    }

    @Test
    void 过期返回40101() {
        String token = service.issue(1001L, ClientType.WEB, 0).token();
        clock.advance(Duration.ofHours(2).plusSeconds(1));
        assertCode(() -> service.parse(token), 40101);
    }

    @Test
    void 签名错误_格式错误_缺失都返回40101() {
        SecurityProperties other = SecurityTestData.properties();
        other.setJwtUserSecret("another-unit-test-only-secret-0123456789");
        String foreign = new UserJwtService(other, false, Clock.systemUTC()).issue(1L, ClientType.WEB, 0).token();

        assertCode(() -> service.parse(foreign), 40101);
        assertCode(() -> service.parse("not-a-jwt"), 40101);
        assertCode(() -> service.parse(""), 40101);
        assertCode(() -> service.parse(null), 40101);
    }

    @Test
    void 令牌类型不是USER返回40101() {
        String adminToken = Jwts.builder()
                .subject("1")
                .claim("tv", 0)
                .claim("typ", "ADMIN")
                .expiration(Date.from(clock.instant().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SecurityTestData.TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertCode(() -> service.parse(adminToken), 40101);
    }

    @Test
    void 密钥不足32字节启动失败_为空时只有开发环境随机生成() {
        SecurityProperties shortSecret = new SecurityProperties();
        shortSecret.setJwtUserSecret("too-short");
        assertThatThrownBy(() -> new UserJwtService(shortSecret, true, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);

        SecurityProperties empty = new SecurityProperties();
        assertThatThrownBy(() -> new UserJwtService(empty, false, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);

        UserJwtService dev = new UserJwtService(empty, true, Clock.systemUTC());
        assertThat(dev.parse(dev.issue(5L, ClientType.WEB, 1).token()).userId()).isEqualTo(5L);
    }

    private static void assertCode(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
