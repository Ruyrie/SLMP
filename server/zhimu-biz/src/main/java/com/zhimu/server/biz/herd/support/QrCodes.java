package com.zhimu.server.biz.herd.support;

import java.security.SecureRandom;

/** 批次二维码：短链接前缀与令牌解析（文档 2.7“二维码”）。 */
public final class QrCodes {

    /** 旧版已印发标签的前缀；新标签由 BatchQrLinks 使用部署地址生成。 */
    public static final String DEFAULT_PREFIX = "https://web.zhimutong.example.com/s/b/";

    /** 去掉易混淆字符（0/O、1/I/L 等）后的字母数字表 */
    private static final char[] ALPHABET =
            "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private QrCodes() {
    }

    /** 10 位安全随机令牌 */
    public static String nextToken() {
        char[] buffer = new char[10];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }

    public static String content(String token) {
        return DEFAULT_PREFIX + token;
    }

    /** 从扫码原文解析 10 位令牌；不是批次二维码返回 null */
    public static String parseToken(String qrContent) {
        return parseToken(qrContent, DEFAULT_PREFIX);
    }

    /** 只接受明确配置的站点前缀，不从任意网址中提取批次令牌。 */
    static String parseToken(String qrContent, String prefix) {
        if (qrContent == null) {
            return null;
        }
        String text = qrContent.trim();
        if (!text.startsWith(prefix)) {
            return null;
        }
        String token = text.substring(prefix.length());
        return token.length() == 10 && token.chars().allMatch(QrCodes::allowed) ? token : null;
    }

    private static boolean allowed(int ch) {
        // 已印发的旧标签可能含 0、1、l 等字符；生成新码仍使用上面的防混淆字母表。
        return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9';
    }
}
