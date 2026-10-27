package com.zhimu.server.biz.herd.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/** New batch labels use the deployed user Web site; existing printed labels keep working in App scans. */
@Component
public class BatchQrLinks {
    private final String prefix;

    public BatchQrLinks(@Value("${zhimu.herd.qr.web-base-url:http://127.0.0.1:5173}") String webBaseUrl) {
        String value = webBaseUrl == null ? "" : webBaseUrl.trim();
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("USER_WEB_BASE_URL must be an absolute HTTP(S) user Web address without credentials, query or fragment");
        }
        prefix = value.replaceAll("/+$", "") + "/s/b/";
    }

    public String content(String token) {
        return prefix + token;
    }

    public String parseToken(String content) {
        String token = QrCodes.parseToken(content, prefix);
        return token != null ? token : QrCodes.parseToken(content);
    }
}
