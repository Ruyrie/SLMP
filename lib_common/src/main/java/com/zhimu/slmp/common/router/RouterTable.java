package com.zhimu.slmp.common.router;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 页面编号 → action：推送附加字段、消息中心、扫码结果中的 route（例如 "I06"）按此打开页面。
 */
public final class RouterTable {

    private static final Map<String, String> TABLE;

    static {
        Map<String, String> m = new HashMap<>();
        for (String code : RouterPath.PAGE_CODES) {
            m.put(code, RouterPath.PREFIX + code);
        }
        TABLE = Collections.unmodifiableMap(m);
    }

    private RouterTable() {
    }

    /** 页面编号对应的 action，未知编号返回 null */
    @Nullable
    public static String actionOf(@Nullable String pageCode) {
        if (pageCode == null) {
            return null;
        }
        return TABLE.get(pageCode.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** action 对应的页面编号（去掉前缀），不是页面 action 时返回 null */
    @Nullable
    public static String codeOf(@Nullable String action) {
        if (action == null || !action.startsWith(RouterPath.PREFIX)) {
            return null;
        }
        String code = action.substring(RouterPath.PREFIX.length());
        return TABLE.containsKey(code) ? code : null;
    }

    @NonNull
    public static Map<String, String> all() {
        return TABLE;
    }
}
