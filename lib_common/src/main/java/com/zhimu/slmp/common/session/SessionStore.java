package com.zhimu.slmp.common.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 登录态存储 zmt_session（03 号文档 7.1 节）：access_token、refresh_token 用 Keystore AES-GCM 加密；
 * user_json 为 AccountUserVO；farm_context_json 由 FarmContext 读写。
 */
@Singleton
public class SessionStore {

    public static final String FILE = "zmt_session";
    static final String KEY_ACCESS = "access_token";
    static final String KEY_REFRESH = "refresh_token";
    static final String KEY_USER = "user_json";
    public static final String KEY_FARM_CONTEXT = "farm_context_json";

    private final SharedPreferences prefs;

    @Inject
    public SessionStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    @NonNull
    public SharedPreferences prefs() {
        return prefs;
    }

    @Nullable
    public String getAccessToken() {
        return TokenCipher.decrypt(prefs.getString(KEY_ACCESS, null));
    }

    @Nullable
    public String getRefreshToken() {
        return TokenCipher.decrypt(prefs.getString(KEY_REFRESH, null));
    }

    /** refreshToken 为 null 时保留原值（刷新接口可能只返回访问令牌） */
    public void saveTokens(@Nullable String accessToken, @Nullable String refreshToken) {
        SharedPreferences.Editor e = prefs.edit().putString(KEY_ACCESS, TokenCipher.encrypt(accessToken));
        if (refreshToken != null) {
            e.putString(KEY_REFRESH, TokenCipher.encrypt(refreshToken));
        }
        e.apply();
    }

    @Nullable
    public String getUserJson() {
        return prefs.getString(KEY_USER, null);
    }

    public void saveUserJson(@Nullable String json) {
        prefs.edit().putString(KEY_USER, json).apply();
    }

    /** 清除令牌与用户（farm_context_json 由 FarmContext.clear 清除） */
    public void clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_USER).commit();
    }
}
