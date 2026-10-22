package com.zhimu.slmp.today.model.vo;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 推送附加字段（服务端 2.8.4 extras）：route、farm_id、biz_type、biz_id、message_id、push_id、todo_id、channel。
 * 极光通道从 NotificationMessage.notificationExtras（JSON 字符串）解析，厂商通道与本应用中转从 Intent extras 读取。
 */
public class PushExtras {

    public static final String KEY_ROUTE = "route";
    public static final String KEY_FARM_ID = "farm_id";
    public static final String KEY_BIZ_TYPE = "biz_type";
    public static final String KEY_BIZ_ID = "biz_id";
    public static final String KEY_MESSAGE_ID = "message_id";
    public static final String KEY_PUSH_ID = "push_id";
    public static final String KEY_TODO_ID = "todo_id";
    public static final String KEY_CHANNEL = "channel";

    @Nullable
    private String route;
    @Nullable
    private Long farmId;
    @Nullable
    private String bizType;
    @Nullable
    private Long bizId;
    @Nullable
    private Long messageId;
    @Nullable
    private Long pushId;
    @Nullable
    private Long todoId;
    @Nullable
    private String channel;

    /** 解析极光 extras JSON；格式错误时返回空对象 */
    @NonNull
    public static PushExtras fromJson(@Nullable String json) {
        PushExtras e = new PushExtras();
        if (json == null || json.isEmpty()) {
            return e;
        }
        try {
            JSONObject o = new JSONObject(json);
            e.route = o.optString(KEY_ROUTE, null);
            e.farmId = optLong(o, KEY_FARM_ID);
            e.bizType = o.optString(KEY_BIZ_TYPE, null);
            e.bizId = optLong(o, KEY_BIZ_ID);
            e.messageId = optLong(o, KEY_MESSAGE_ID);
            e.pushId = optLong(o, KEY_PUSH_ID);
            e.todoId = optLong(o, KEY_TODO_ID);
            e.channel = o.optString(KEY_CHANNEL, null);
        } catch (JSONException ignored) {
            // 按没有附加字段处理（打开今日首页）
        }
        return e;
    }

    @Nullable
    private static Long optLong(@NonNull JSONObject o, @NonNull String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        String s = o.optString(key, "");
        return parseLong(s);
    }

    /** 从 Intent extras 读取（数值可能是 long 或字符串） */
    @NonNull
    public static PushExtras fromBundle(@Nullable Bundle b) {
        PushExtras e = new PushExtras();
        if (b == null) {
            return e;
        }
        e.route = stringOf(b.get(KEY_ROUTE));
        e.farmId = longOf(b.get(KEY_FARM_ID));
        e.bizType = stringOf(b.get(KEY_BIZ_TYPE));
        e.bizId = longOf(b.get(KEY_BIZ_ID));
        e.messageId = longOf(b.get(KEY_MESSAGE_ID));
        e.pushId = longOf(b.get(KEY_PUSH_ID));
        e.todoId = longOf(b.get(KEY_TODO_ID));
        e.channel = stringOf(b.get(KEY_CHANNEL));
        return e;
    }

    @NonNull
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(KEY_ROUTE, route);
        putLong(b, KEY_FARM_ID, farmId);
        b.putString(KEY_BIZ_TYPE, bizType);
        putLong(b, KEY_BIZ_ID, bizId);
        putLong(b, KEY_MESSAGE_ID, messageId);
        putLong(b, KEY_PUSH_ID, pushId);
        putLong(b, KEY_TODO_ID, todoId);
        b.putString(KEY_CHANNEL, channel);
        return b;
    }

    private static void putLong(@NonNull Bundle b, @NonNull String key, @Nullable Long v) {
        if (v != null) {
            b.putLong(key, v);
        }
    }

    @Nullable
    private static String stringOf(@Nullable Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @Nullable
    private static Long longOf(@Nullable Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return o == null ? null : parseLong(String.valueOf(o));
    }

    @Nullable
    private static Long parseLong(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 是否带有可打开的消息 */
    public boolean hasMessage() {
        return messageId != null && messageId > 0;
    }

    @Nullable
    public String getRoute() {
        return route;
    }

    @Nullable
    public Long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getBizType() {
        return bizType;
    }

    @Nullable
    public Long getBizId() {
        return bizId;
    }

    @Nullable
    public Long getMessageId() {
        return messageId;
    }

    @Nullable
    public Long getPushId() {
        return pushId;
    }

    @Nullable
    public Long getTodoId() {
        return todoId;
    }

    @Nullable
    public String getChannel() {
        return channel;
    }
}
