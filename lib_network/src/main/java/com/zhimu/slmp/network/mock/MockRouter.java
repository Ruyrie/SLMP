package com.zhimu.slmp.network.mock;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mock 规则表：读取 mock 下各子目录的 routes.json，按目录名字母顺序、文件内从上到下排列，匹配第一条。
 * routes.json 为规则数组（也接受 {"routes": [...]}）。
 */
public final class MockRouter {

    private static final String TAG = "ZmtMock";
    public static final String ROUTES_FILE = "routes.json";

    /** 匹配结果 */
    public static final class Match {
        @NonNull
        public final MockRule rule;
        @NonNull
        public final Map<String, String> pathParams;

        Match(@NonNull MockRule rule, @NonNull Map<String, String> pathParams) {
            this.rule = rule;
            this.pathParams = pathParams;
        }
    }

    private final MockSource source;
    @Nullable
    private volatile List<MockRule> rules;

    public MockRouter(@NonNull MockSource source) {
        this.source = source;
    }

    @NonNull
    public MockSource getSource() {
        return source;
    }

    /** 全部规则（首次调用时加载） */
    @NonNull
    public List<MockRule> rules() {
        List<MockRule> r = rules;
        if (r == null) {
            synchronized (this) {
                r = rules;
                if (r == null) {
                    r = load();
                    rules = r;
                }
            }
        }
        return r;
    }

    /**
     * @param method  HTTP 方法
     * @param segments 已解码的路径段（不含域名），例如 [api, v1, farms, 1001]
     * @param query   查询参数
     * @param headers 请求头（名称不区分大小写）
     * @param body    请求体文本，没有时为空字符串
     */
    @Nullable
    public Match match(@NonNull String method, @NonNull List<String> segments, @NonNull Map<String, String> query,
                       @NonNull Map<String, String> headers, @NonNull String body) {
        String m = method.toUpperCase(Locale.ROOT);
        Map<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        h.putAll(headers);
        for (MockRule rule : rules()) {
            if (!rule.method.equals(m)) {
                continue;
            }
            Map<String, String> params = matchPath(rule.segments, segments);
            if (params == null) {
                continue;
            }
            if (containsAll(params, rule.whenPath) && containsAll(query, rule.whenQuery)
                    && containsAll(h, rule.whenHeader) && bodyMatches(body, rule.whenBody)) {
                return new Match(rule, params);
            }
        }
        return null;
    }

    @Nullable
    static Map<String, String> matchPath(@NonNull String[] pattern, @NonNull List<String> actual) {
        if (pattern.length != actual.size()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            String a = actual.get(i);
            if (p.length() > 2 && p.startsWith("{") && p.endsWith("}")) {
                if (a.isEmpty()) {
                    return null;
                }
                params.put(p.substring(1, p.length() - 1), a);
            } else if (!p.equals(a)) {
                return null;
            }
        }
        return params;
    }

    private static boolean containsAll(@NonNull Map<String, String> actual, @NonNull Map<String, String> expected) {
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String v = actual.get(e.getKey());
            if (v == null || !v.equals(e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean bodyMatches(@NonNull String body, @NonNull List<String> parts) {
        for (String p : parts) {
            if (!body.contains(p)) {
                return false;
            }
        }
        return true;
    }

    // ===== 加载 =====

    @NonNull
    private List<MockRule> load() {
        List<String> dirs = source.listDirs();
        Collections.sort(dirs);
        List<MockRule> all = new ArrayList<>();
        for (String dir : dirs) {
            String json = source.read(dir, ROUTES_FILE);
            if (json == null) {
                continue;
            }
            try {
                all.addAll(parse(dir, json));
            } catch (RuntimeException e) {
                Log.w(TAG, "routes.json 格式错误：mock/" + dir, e);
            }
        }
        Log.d(TAG, "已加载 Mock 规则 " + all.size() + " 条，目录 " + dirs);
        return Collections.unmodifiableList(all);
    }

    @NonNull
    static List<MockRule> parse(@NonNull String dir, @NonNull String json) {
        JsonElement root = JsonParser.parseString(json);
        JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("routes");
        List<MockRule> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            JsonObject when = o.has("when") && o.get("when").isJsonObject() ? o.getAsJsonObject("when") : null;
            long delay = o.has("delay") ? o.get("delay").getAsLong() : MockRule.DEFAULT_DELAY_MS;
            list.add(new MockRule(dir,
                    o.get("method").getAsString(),
                    o.get("path").getAsString(),
                    when == null ? null : stringMap(when.get("path")),
                    when == null ? null : stringMap(when.get("query")),
                    when == null ? null : stringMap(when.get("header")),
                    when == null ? null : stringList(when.get("body")),
                    o.get("file").getAsString(),
                    delay));
        }
        return list;
    }

    @Nullable
    private static Map<String, String> stringMap(@Nullable JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
            map.put(en.getKey(), en.getValue().getAsString());
        }
        return map;
    }

    @Nullable
    private static List<String> stringList(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                list.add(x.getAsString());
            }
        } else {
            list.add(e.getAsString());
        }
        return list;
    }
}
