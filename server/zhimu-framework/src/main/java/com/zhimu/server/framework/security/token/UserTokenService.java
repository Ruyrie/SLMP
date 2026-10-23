package com.zhimu.server.framework.security.token;

import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import com.zhimu.server.framework.security.SecureTokens;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.spi.UserAuthState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 用户端令牌签发与刷新。
 * <ul>
 * <li>刷新令牌：32 字节随机串，存 auth:refresh:{token}（30 天）；索引集合 auth:user:{user_id}。</li>
 * <li>同一用户同一类客户端只保留最近一次登录的刷新令牌。</li>
 * <li>每次刷新都轮换；轮换后 30 秒内再用旧令牌返回同一组新令牌；超过 30 秒视为盗用，作废该客户端全部刷新令牌并返回 40104。</li>
 * </ul>
 */
@Slf4j
@Component
public class UserTokenService {

    public static final String REFRESH_PREFIX = "auth:refresh:";
    public static final String USER_INDEX_PREFIX = "auth:user:";
    public static final String TOKEN_TYPE = "Bearer";

    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REASON_ACCOUNT_CANCELLED = "ACCOUNT_CANCELLED";

    static final String STATUS_DISABLED = "DISABLED";
    static final String STATUS_CANCELLED = "CANCELLED";

    /** 旧值未变时才写入轮换标记（保留剩余有效期），防止并发刷新各自换发 */
    private static final RedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL') return 1 end return 0", Long.class);

    private final StringRedisTemplate redis;
    private final UserJwtService jwtService;
    private final AuthStateCache authStateCache;
    private final SecurityProperties properties;
    private final Clock clock;

    @Autowired
    public UserTokenService(StringRedisTemplate redis, UserJwtService jwtService, AuthStateCache authStateCache,
                            SecurityProperties properties) {
        this(redis, jwtService, authStateCache, properties, Clock.system(BeijingTime.ZONE));
    }

    public UserTokenService(StringRedisTemplate redis, UserJwtService jwtService, AuthStateCache authStateCache,
                            SecurityProperties properties, Clock clock) {
        this.redis = redis;
        this.jwtService = jwtService;
        this.authStateCache = authStateCache;
        this.properties = properties;
        this.clock = clock;
    }

    /** 登录、注册成功后签发令牌；同时作废该用户同一类客户端的旧刷新令牌 */
    public TokenPair issue(Long userId, ClientType clientType, String deviceId, int tokenVersion) {
        ClientType client = normalize(clientType);
        revokeClient(userId, client);
        return createPair(userId, client, deviceId, tokenVersion);
    }

    /**
     * 刷新令牌（API-A06）。失败一律抛 40104：data.reason 为 EXPIRED、ACCOUNT_DISABLED、ACCOUNT_CANCELLED
     * 或 token_version 变化的原因。
     */
    public TokenPair refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw sessionInvalid(REASON_EXPIRED);
        }
        String key = REFRESH_PREFIX + refreshToken;
        String json = redis.opsForValue().get(key);
        RefreshTokenRecord record = CacheJson.read(json, RefreshTokenRecord.class);
        if (record == null) {
            throw sessionInvalid(REASON_EXPIRED);
        }
        if (record.rotated()) {
            return reuseRotated(record);
        }
        int tokenVersion = checkState(record, refreshToken);
        TokenPair pair = createPair(record.userId(), record.client(), record.deviceId(), tokenVersion);
        String rotated = CacheJson.write(record.rotate(clock.millis(), pair));
        Long updated = redis.execute(COMPARE_AND_SET, List.of(key), json, rotated);
        if (updated == null || updated != 1L) {
            // 并发刷新：另一个请求已轮换，撤回本次换发，按宽限期规则返回对方的结果
            removeTokens(record.userId(), List.of(pair.refreshToken()));
            RefreshTokenRecord latest = CacheJson.read(redis.opsForValue().get(key), RefreshTokenRecord.class);
            if (latest != null && latest.rotated()) {
                return reuseRotated(latest);
            }
            throw sessionInvalid(REASON_EXPIRED);
        }
        return pair;
    }

    /** 作废该用户全部刷新令牌（改密、找回、注销、停用、退出全部设备） */
    public void revokeAll(Long userId) {
        String indexKey = USER_INDEX_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(t -> REFRESH_PREFIX + t).toList());
        }
        redis.delete(indexKey);
    }

    /** 作废该用户某类客户端的全部刷新令牌（含轮换后保留的旧令牌），顺带清理索引中已过期的令牌 */
    public void revokeClient(Long userId, ClientType clientType) {
        ClientType client = normalize(clientType);
        String indexKey = USER_INDEX_PREFIX + userId;
        Set<String> members = redis.opsForSet().members(indexKey);
        if (members == null || members.isEmpty()) {
            return;
        }
        List<String> tokens = new ArrayList<>(members);
        List<String> values = redis.opsForValue().multiGet(tokens.stream().map(t -> REFRESH_PREFIX + t).toList());
        List<String> remove = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String value = values == null ? null : values.get(i);
            if (value == null) {
                remove.add(tokens.get(i));
                continue;
            }
            RefreshTokenRecord record = CacheJson.read(value, RefreshTokenRecord.class);
            if (record == null || record.client() == null || record.client() == client) {
                remove.add(tokens.get(i));
            }
        }
        removeTokens(userId, remove);
    }

    private TokenPair reuseRotated(RefreshTokenRecord record) {
        long elapsed = clock.millis() - record.rotatedAt();
        if (record.next() != null && elapsed <= properties.getRefreshGrace().toMillis()) {
            return record.next();
        }
        log.warn("刷新令牌在轮换后被再次使用，视为盗用，作废该客户端全部刷新令牌 user_id={} client={}",
                record.userId(), record.client());
        revokeClient(record.userId(), record.client());
        throw sessionInvalid(REASON_EXPIRED);
    }

    /** 校验账号状态，返回新访问令牌使用的 token_version；没有 Loader 实现时沿用签发时的值 */
    private int checkState(RefreshTokenRecord record, String refreshToken) {
        int issuedVersion = record.tokenVersion() == null ? 0 : record.tokenVersion();
        if (!authStateCache.hasLoader()) {
            return issuedVersion;
        }
        UserAuthState state = authStateCache.get(record.userId());
        String reason = null;
        if (state == null) {
            reason = REASON_EXPIRED;
        } else if (STATUS_DISABLED.equals(state.status())) {
            reason = REASON_ACCOUNT_DISABLED;
        } else if (STATUS_CANCELLED.equals(state.status())) {
            reason = REASON_ACCOUNT_CANCELLED;
        } else if (record.tokenVersion() != null && state.tokenVersion() != record.tokenVersion()) {
            reason = StringUtils.hasText(state.invalidReason()) ? state.invalidReason() : REASON_EXPIRED;
        }
        if (reason != null) {
            removeTokens(record.userId(), List.of(refreshToken));
            throw sessionInvalid(reason);
        }
        return state.tokenVersion();
    }

    private TokenPair createPair(Long userId, ClientType client, String deviceId, int tokenVersion) {
        UserJwtService.AccessToken access = jwtService.issue(userId, client, tokenVersion);
        String refreshToken = SecureTokens.random32();
        Duration ttl = properties.getRefreshTokenTtl();
        RefreshTokenRecord record = new RefreshTokenRecord(userId, client, deviceId, tokenVersion,
                LocalDateTime.now(clock.withZone(BeijingTime.ZONE)), null, null);
        redis.opsForValue().set(REFRESH_PREFIX + refreshToken, CacheJson.write(record), ttl);
        String indexKey = USER_INDEX_PREFIX + userId;
        redis.opsForSet().add(indexKey, refreshToken);
        redis.expire(indexKey, ttl);
        return new TokenPair(access.token(), TOKEN_TYPE, access.expiresIn(), refreshToken, ttl.toSeconds());
    }

    private void removeTokens(Long userId, List<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        redis.delete(tokens.stream().map(t -> REFRESH_PREFIX + t).toList());
        redis.opsForSet().remove(USER_INDEX_PREFIX + userId, tokens.toArray());
    }

    /** 用户端令牌只区分 App 与 Web：WEB 以外一律按 APP_ANDROID */
    static ClientType normalize(ClientType clientType) {
        return clientType == ClientType.WEB ? ClientType.WEB : ClientType.APP_ANDROID;
    }

    private static BizException sessionInvalid(String reason) {
        return new BizException(ErrorCode.SESSION_INVALID).with("reason", reason);
    }
}
