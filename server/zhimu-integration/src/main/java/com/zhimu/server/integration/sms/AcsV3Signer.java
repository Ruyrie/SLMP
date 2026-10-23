package com.zhimu.server.integration.sms;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 阿里云 OpenAPI V3 签名（ACS3-HMAC-SHA256），移植自 App 的 AcsV3Signer：用于 RPC 风格、参数全部放在查询串、请求体为空的接口。
 * <pre>
 * CanonicalRequest = 方法 \n / \n 规范化查询串 \n 规范化请求头 \n 已签名请求头 \n 请求体 SHA-256
 * StringToSign     = ACS3-HMAC-SHA256 \n hex(SHA-256(CanonicalRequest))
 * Signature        = hex(HMAC-SHA256(AccessKeySecret, StringToSign))
 * Authorization    = ACS3-HMAC-SHA256 Credential=AccessKeyId,SignedHeaders=...,Signature=...
 * </pre>
 */
public final class AcsV3Signer {

    public static final String ALGORITHM = "ACS3-HMAC-SHA256";
    /** 空请求体的 SHA-256 */
    public static final String EMPTY_BODY_SHA256 = sha256Hex(new byte[0]);

    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private AcsV3Signer() {
    }

    /**
     * 签名结果：要加到请求上的请求头（小写名称，含 host 与 authorization）与中间结果（不含密钥）。
     * host 由 HTTP 客户端按地址自动设置，发送时跳过。
     */
    public record Signed(String canonicalQuery, String canonicalRequest, String stringToSign, String signature,
                         String authorization, Map<String, String> headers) {
    }

    public static Signed sign(String accessKeyId, String accessKeySecret, String method, String host, String action,
                              String version, Map<String, String> query, String date, String nonce) {
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
            if (!names.isEmpty()) {
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
    public static String canonicalQuery(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(query).entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(percentEncode(e.getKey())).append('=')
                    .append(percentEncode(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    /** 按 UTF-8 编码：A–Z、a–z、0–9、-、_、.、~ 不编码，其余（含空格、*）编码为 %XX（大写） */
    public static String percentEncode(String value) {
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
    public static String formatDate(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    /** x-acs-signature-nonce：32 位十六进制随机串 */
    public static String newNonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
