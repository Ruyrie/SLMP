package com.zhimu.server.framework.security.token;

import com.zhimu.server.common.enums.ClientType;

import java.time.LocalDateTime;

/**
 * Redis auth:refresh:{token} 的内容。轮换后保留旧记录（rotatedAt、next），用于宽限期返回同一组新令牌和盗用检测。
 *
 * @param userId       用户 ID
 * @param client       客户端（APP_ANDROID / WEB）
 * @param deviceId     设备编号
 * @param tokenVersion 签发时的 token_version
 * @param issuedAt     签发时间（北京时间）
 * @param rotatedAt    轮换时刻（毫秒时间戳），未轮换为 null
 * @param next         轮换后换发的新令牌，未轮换为 null
 */
public record RefreshTokenRecord(Long userId, ClientType client, String deviceId, Integer tokenVersion,
                                 LocalDateTime issuedAt, Long rotatedAt, TokenPair next) {

    public boolean rotated() {
        return rotatedAt != null;
    }

    public RefreshTokenRecord rotate(long nowMillis, TokenPair nextPair) {
        return new RefreshTokenRecord(userId, client, deviceId, tokenVersion, issuedAt, nowMillis, nextPair);
    }
}
