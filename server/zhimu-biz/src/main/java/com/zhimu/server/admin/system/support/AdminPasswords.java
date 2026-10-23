package com.zhimu.server.admin.system.support;

import java.security.SecureRandom;

/**
 * 管理员初始 / 临时密码：10 位字母数字，至少各含 1 个字母和数字，去掉易混字符 0、O、1、I、L。
 * 明文只用于短信（本批不发送）与 dev 联调返回，不写日志。
 */
public final class AdminPasswords {

    public static final int LENGTH = 10;
    public static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AdminPasswords() {
    }

    public static String tempPassword() {
        char[] chars = new char[LENGTH];
        chars[0] = LETTERS.charAt(RANDOM.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        // Fisher-Yates 打乱，避免字母和数字固定在前两位
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
