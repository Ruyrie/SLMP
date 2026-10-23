package com.zhimu.server.framework.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证与安全配置（zhimu.security）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zhimu.security")
public class SecurityProperties {

    /** 用户端访问令牌 HS256 密钥，至少 32 字节；dev 环境为空时随机生成 */
    private String jwtUserSecret;

    private Duration accessTokenTtl = Duration.ofHours(2);

    private Duration refreshTokenTtl = Duration.ofDays(30);

    /** 刷新令牌轮换后的宽限期：期间再用旧令牌返回同一组新令牌 */
    private Duration refreshGrace = Duration.ofSeconds(30);

    private String webRefreshCookieName = "ZMT_REFRESH";

    private String adminSessionCookieName = "ZMT_ADMIN_SESSION";

    private Duration adminSessionTtl = Duration.ofMinutes(30);

    /** 预留（管理端使用 Redis 会话，不签发 JWT） */
    private String jwtAdminSecret;

    private boolean cookieSecure = true;

    /** 凭 Cookie 认证的请求允许的 Origin；为空时不校验 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 用户鉴权状态缓存 auth:state:{user_id} 的有效期 */
    private Duration authStateTtl = Duration.ofMinutes(10);

    /** 成员缓存 farm:member:{farm_id}:{user_id} 的有效期 */
    private Duration farmMemberTtl = Duration.ofMinutes(10);
}
