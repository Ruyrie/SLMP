package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.ui.status.StatusActivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 导航目标：页面编号 route、参数 params（与 JSON 字段同名）、跨场切换后的提示 switchTip（可空）。
 * ViewModel 经 navEvent 发出，页面用 {@link TodayNavigator} 打开。
 */
public final class NavTarget {

    /** 不是页面编号：只关闭当前页面并回到上一页 */
    public static final String ROUTE_BACK = "BACK";

    @NonNull
    private final String route;
    @NonNull
    private final Map<String, String> params;
    @Nullable
    private final String switchTip;

    public NavTarget(@NonNull String route, @Nullable Map<String, String> params, @Nullable String switchTip) {
        this.route = route;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(params));
        this.switchTip = switchTip;
    }

    @NonNull
    public static NavTarget of(@NonNull String route) {
        return new NavTarget(route, null, null);
    }

    @NonNull
    public static NavTarget of(@NonNull String route, @NonNull String key, @Nullable Object value) {
        Map<String, String> p = new HashMap<>();
        if (value != null) {
            p.put(key, String.valueOf(value));
        }
        return new NavTarget(route, p, null);
    }

    /** T04 待办详情 */
    @NonNull
    public static NavTarget todoDetail(long todoId) {
        return of("T04", RouterPath.EXTRA_TODO_ID, todoId);
    }

    /** T08 关联记录已失效 */
    @NonNull
    public static NavTarget recordInvalid(@Nullable String pageTitle, @Nullable String title, @Nullable String desc,
                                          @Nullable Long messageId) {
        Map<String, String> p = new HashMap<>();
        putIfNotNull(p, RouterPath.EXTRA_PAGE_TITLE, pageTitle);
        putIfNotNull(p, RecordInvalidKeys.DELETED_TITLE, title);
        putIfNotNull(p, RecordInvalidKeys.DELETED_DESC, desc);
        if (messageId != null) {
            p.put(RouterPath.EXTRA_MESSAGE_ID, String.valueOf(messageId));
        }
        return new NavTarget("T08", p, null);
    }

    /** U03 无权查看 */
    @NonNull
    public static NavTarget noPermission(@Nullable String title, @Nullable String message, @Nullable String actionText) {
        Map<String, String> p = new HashMap<>();
        p.put(RouterPath.EXTRA_STATUS_TYPE, StatusActivity.TYPE_NO_PERMISSION);
        putIfNotNull(p, RouterPath.EXTRA_TITLE, title);
        putIfNotNull(p, RouterPath.EXTRA_MESSAGE, message);
        putIfNotNull(p, RouterPath.EXTRA_ACTION_TEXT, actionText);
        return new NavTarget("U03", p, null);
    }

    private static void putIfNotNull(@NonNull Map<String, String> p, @NonNull String key, @Nullable String value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    @NonNull
    public String getRoute() {
        return route;
    }

    @NonNull
    public Map<String, String> getParams() {
        return params;
    }

    @Nullable
    public String getSwitchTip() {
        return switchTip;
    }

    @Nullable
    public String param(@NonNull String key) {
        return params.get(key);
    }

    @NonNull
    public NavTarget withSwitchTip(@Nullable String tip) {
        return new NavTarget(route, params, tip);
    }

    @NonNull
    @Override
    public String toString() {
        return "NavTarget{" + route + " " + params + (switchTip == null ? "" : " tip=" + switchTip) + "}";
    }

    /** T08 启动参数键（OpenResultVO 的字段名） */
    public static final class RecordInvalidKeys {
        public static final String DELETED_TITLE = RouterPath.EXTRA_DELETED_TITLE;
        public static final String DELETED_DESC = RouterPath.EXTRA_DELETED_DESC;

        private RecordInvalidKeys() {
        }
    }
}
