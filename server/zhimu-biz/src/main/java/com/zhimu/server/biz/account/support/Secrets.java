package com.zhimu.server.biz.account.support;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;

import java.security.SecureRandom;
import java.util.Base64;

/** 恢复码与临时密码生成。恢复码 16 位，去掉易混字符 0、O、1、I；明文只返回给调用方，不写日志。 */
public final class Secrets {

    /** 去掉 0、O、1、I 的 32 个字符 */
    public static final String RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 临时密码去掉易混字符后的字符集 */
    public static final String TEMP_PASSWORD_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    public static String recoveryCode() {
        return random(RECOVERY_ALPHABET, 16);
    }

    /** 10 位字母数字临时密码 */
    public static String tempPassword() {
        return random(TEMP_PASSWORD_ALPHABET, 10);
    }

    public static String random(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** SHA-256 摘要（十六进制），用于手机号审计摘要等 */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.SERVER_ERROR);
        }
    }

    /** 刷新令牌只用于测试/调试展示（真实令牌由框架生成） */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
