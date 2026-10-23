package com.zhimu.server.framework.security.token;

/**
 * 一组用户端令牌，输出为 access_token、token_type、expires_in、refresh_token、refresh_expires_in。
 *
 * @param accessToken      访问令牌（JWT）
 * @param tokenType        固定 Bearer
 * @param expiresIn        访问令牌有效秒数（7200）
 * @param refreshToken     刷新令牌（Web 端由接口改用 Cookie 下发，不放进响应体）
 * @param refreshExpiresIn 刷新令牌有效秒数（2592000）
 */
public record TokenPair(String accessToken, String tokenType, long expiresIn, String refreshToken,
                        long refreshExpiresIn) {
}
