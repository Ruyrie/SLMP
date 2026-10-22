package com.zhimu.slmp.common.sms;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 阿里云 OpenAPI V3 签名（ACS3-HMAC-SHA256），用于 RPC 风格、参数全部放在查询串、请求体为空的接口
 * （号码认证服务 Dypnsapi 的 SendSmsVerifyCode、CheckSmsVerifyCode 即是）。
 * <pre>
 * CanonicalRequest = 方法 \n / \n 规范化查询串 \n 规范化请求头 \n 已签名请求头 \n 请求体 SHA-256
 * StringToSign     = ACS3-HMAC-SHA256 \n hex(SHA-256(CanonicalRequest))
 * Signature        = hex(HMAC-SHA256(AccessKeySecret, StringToSign))
 * Authorization    = ACS3-HMAC-SHA256 Credential=AccessKeyId,SignedHeaders=...,Signature=...
 * </pre>
 * 纯 Java 实现、结果确定（日期与随机数由调用方传入），单元测试用阿里云文档的示例校验。
 */
public final class AcsV3Signer {

    public static final String ALGORITHM = "ACS3-HMAC-SHA256";

    // 必须在 EMPTY_BODY_SHA256 之前初始化
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    /** 空请求体的 SHA-256 */
    public static final String EMPTY_BODY_SHA256 = sha256Hex(new byte[0]);

    private AcsV3Signer() {
    }

    /** 签名结果：要加到请求上的全部请求头（含 Authorization）与中间结果（测试与排查用，不含密钥） */
    public static final class Signed {
        @NonNull
        public final String canonicalQuery;
        @NonNull
        public final String canonicalRequest;
        @NonNull
        public final String stringToSign;
        @NonNull
        public final String signature;
        @NonNull
        public final String authorization;
        /** 小写名称 → 值，含 host 与 authorization；host 由 OkHttp 按地址自动设置，发送时跳过 */
        @NonNull
        public final Map<String, String> headers;

        Signed(@NonNull String canonicalQuery, @NonNull String canonicalRequest, @NonNull String stringToSign,
               @NonNull String signature, @NonNull String authorization, @NonNull Map<String, String> headers) {
            this.canonicalQuery = canonicalQuery;
            this.canonicalRequest = canonicalRequest;
            this.stringToSign = stringToSign;
            this.signature = signature;
            this.authorization = authorization;
            this.headers = headers;
        }
    }

    /**
     * 签名一个请求体为空的 RPC 请求。
     *
     * @param method  HTTP 方法（POST / GET）
     * @param host    服务地址，例如 dypnsapi.aliyuncs.com
     * @param query   查询参数（未编码）
     * @param date    {@link #formatDate(long)} 的结果，UTC
     * @param nonce   每次请求唯一的随机串，{@link #newNonce()}
     */
    @NonNull
    public static Signed sign(@NonNull String accessKeyId, @NonNull String accessKeySecret, @NonNull String method,
                              @NonNull String host, @NonNull String action, @NonNull String version,
                              @NonNull Map<String, String> query, @NonNull String date, @NonNull String nonce) {
        String canonicalQuery = canonicalQuery(query);

        TreeMap<String, String> signedHeaders = new TreeMap<>();
        signedHeaders.put("host", host.trim());
        signedHeaders.put("x-acs-action", action.trim());
        signedHeaders.put("x-acs-content-sha256", EMPTY_BODY_SHA256);
        signedHeaders.put("x-acs-date", date.trim());
        signedHeaders.put("x-acs-signature-nonce", nonce.trim());
        signedHeaders.put("x-acs-version", version.trim());

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (Map.Entry<String, String> e : signedHeaders.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            if (names.length() > 0) {
                names.append(';');
            }
            names.append(e.getKey());
        }
        String signedHeaderNames = names.toString();

        String canonicalRequest = method.toUpperCase(Locale.ROOT) + '\n'
                + "/" + '\n'
                + canonicalQuery + '\n'
                + canonicalHeaders + '\n'
                + signedHeaderNames + '\n'
                + EMPTY_BODY_SHA256;
        String stringToSign = ALGORITHM + '\n' + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = hmacSha256Hex(accessKeySecret, stringToSign);
        String authorization = ALGORITHM + " Credential=" + accessKeyId + ",SignedHeaders=" + signedHeaderNames
                + ",Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>(signedHeaders);
        headers.put("authorization", authorization);
        return new Signed(canonicalQuery, canonicalRequest, stringToSign, signature, authorization,
                Collections.unmodifiableMap(headers));
    }

    /** 规范化查询串：按参数名升序，名称与值分别编码，name=value 以 & 连接（同时用作实际请求的查询串） */
    @NonNull
    public static String canonicalQuery(@NonNull Map<String, String> query) {
        TreeMap<String, String> sorted = new TreeMap<>(query);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(percentEncode(e.getKey())).append('=')
                    .append(percentEncode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /** 按 UTF-8 编码：A–Z、a–z、0–9、-、_、.、~ 不编码，其余（含空格、*）编码为 %XX（大写） */
    @NonNull
    public static String percentEncode(@NonNull String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX_UPPER[c >> 4]).append(HEX_UPPER[c & 0x0F]);
            }
        }
        return sb.toString();
    }

    /** x-acs-date：UTC，yyyy-MM-ddTHH:mm:ssZ */
    @NonNull
    public static String formatDate(long epochMillis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochMillis));
    }

    /** x-acs-signature-nonce：32 位十六进制随机串 */
    @NonNull
    public static String newNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @NonNull
    public static String sha256Hex(@NonNull byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @NonNull
    public static String hmacSha256Hex(@NonNull String key, @NonNull String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    @NonNull
    private static String hex(@NonNull byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
