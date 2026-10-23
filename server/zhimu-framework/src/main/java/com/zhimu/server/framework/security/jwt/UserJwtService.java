package com.zhimu.server.framework.security.jwt;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.security.SecureTokens;
import com.zhimu.server.framework.security.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 用户端访问令牌：JWT HS256，载荷 sub=用户 ID、tv=token_version、typ=USER、cli=客户端、jti。
 * 解析失败（缺失、格式错误、签名错误、过期、typ 不是 USER）一律抛 40101。
 */
@Slf4j
@Component
public class UserJwtService {

    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_CLIENT = "cli";
    public static final String TYPE_USER = "USER";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public UserJwtService(SecurityProperties properties, Environment environment) {
        this(properties, environment.matchesProfiles("dev", "it"), Clock.systemUTC());
    }

    /**
     * @param allowRandomSecret 密钥为空时是否允许随机生成（只用于本机开发与测试）
     */
    public UserJwtService(SecurityProperties properties, boolean allowRandomSecret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secretBytes(properties.getJwtUserSecret(), allowRandomSecret));
        this.ttl = properties.getAccessTokenTtl();
        this.clock = clock;
    }

    private static byte[] secretBytes(String secret, boolean allowRandomSecret) {
        if (!StringUtils.hasText(secret)) {
            if (!allowRandomSecret) {
                throw new IllegalStateException("未配置 zhimu.security.jwt-user-secret（环境变量 JWT_USER_SECRET）");
            }
            log.warn("未配置 JWT_USER_SECRET，已随机生成访问令牌密钥，重启后已签发的访问令牌全部失效（只用于本机开发）");
            return SecureTokens.randomBytes(MIN_SECRET_BYTES);
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("zhimu.security.jwt-user-secret 至少 32 字节");
        }
        return bytes;
    }

    public AccessToken issue(Long userId, ClientType clientType, int tokenVersion) {
        Instant now = clock.instant();
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, TYPE_USER)
                .claim(CLAIM_CLIENT, clientType.name())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new AccessToken(token, jti, ttl.toSeconds());
    }

    /** 校验并解析访问令牌；任何失败抛 40101 */
    public UserClaims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_USER.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED);
            }
            Integer tv = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            if (tv == null || claims.getSubject() == null) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED);
            }
            return new UserClaims(Long.valueOf(claims.getSubject()), tv,
                    parseClient(claims.get(CLAIM_CLIENT, String.class)), claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }
    }

    private static ClientType parseClient(String value) {
        try {
            return value == null ? null : ClientType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param token     访问令牌
     * @param jti       令牌编号
     * @param expiresIn 有效秒数
     */
    public record AccessToken(String token, String jti, long expiresIn) {
    }

    /** 访问令牌中的身份信息 */
    public record UserClaims(Long userId, int tokenVersion, ClientType clientType, String jti) {
    }
}
