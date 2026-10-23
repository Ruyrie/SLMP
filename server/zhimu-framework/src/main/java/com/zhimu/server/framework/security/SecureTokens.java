package com.zhimu.server.framework.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 随机令牌：32 字节安全随机数，base64url（无填充，43 个字符）。用于刷新令牌与管理端会话令牌。
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    public static String random32() {
        return ENCODER.encodeToString(randomBytes(32));
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
