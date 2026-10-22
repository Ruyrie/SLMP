package com.zhimu.slmp.network.mock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 一条 Mock 规则（routes.json 中的一项），格式见《App开发约定》4.3 节：
 * method、path（路径参数写 {farmId}）、when.path / when.query / when.header / when.body、file、delay。
 */
public final class MockRule {

    public static final long DEFAULT_DELAY_MS = 300;

    @NonNull
    final String dir;
    @NonNull
    final String method;
    @NonNull
    final String path;
    @NonNull
    final String[] segments;
    @NonNull
    final Map<String, String> whenPath;
    @NonNull
    final Map<String, String> whenQuery;
    @NonNull
    final Map<String, String> whenHeader;
    @NonNull
    final List<String> whenBody;
    @NonNull
    final String file;
    final long delayMs;

    MockRule(@NonNull String dir, @NonNull String method, @NonNull String path,
             @Nullable Map<String, String> whenPath, @Nullable Map<String, String> whenQuery,
             @Nullable Map<String, String> whenHeader, @Nullable List<String> whenBody,
             @NonNull String file, long delayMs) {
        this.dir = dir;
        this.method = method.toUpperCase(java.util.Locale.ROOT);
        this.path = path;
        this.segments = split(path);
        this.whenPath = whenPath == null ? Collections.emptyMap() : whenPath;
        this.whenQuery = whenQuery == null ? Collections.emptyMap() : whenQuery;
        this.whenHeader = whenHeader == null ? Collections.emptyMap() : whenHeader;
        this.whenBody = whenBody == null ? Collections.emptyList() : whenBody;
        this.file = file;
        this.delayMs = delayMs;
    }

    @NonNull
    public String getDir() {
        return dir;
    }

    @NonNull
    public String getFile() {
        return file;
    }

    public long getDelayMs() {
        return delayMs;
    }

    @NonNull
    static String[] split(@NonNull String path) {
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.isEmpty() ? new String[0] : p.split("/");
    }

    @NonNull
    @Override
    public String toString() {
        return dir + ": " + method + " " + path + " -> " + file;
    }
}
