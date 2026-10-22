package com.zhimu.slmp.network.interceptor;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.logging.HttpLoggingInterceptor;

/**
 * 调试日志（只在 debug 版加入）：脱敏令牌、手机号、密码、验证码、恢复码（含字符串类型的 code 字段）后输出到 Logcat，标签 ZmtHttp。
 */
public final class SafeLogger implements HttpLoggingInterceptor.Logger {

    public static final String TAG = "ZmtHttp";

    /** JSON 中需要整体隐藏的字段 */
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "\"(access_token|refresh_token|password|new_password|current_password|confirm_password"
                    + "|sms_code|new_phone_sms_code|recovery_code|code|temp_password)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern BEARER = Pattern.compile("(Bearer\\s+)[A-Za-z0-9._\\-]+");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");

    /** 创建 debug 用的日志拦截器 */
    @NonNull
    public static HttpLoggingInterceptor create(@NonNull HttpLoggingInterceptor.Level level) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new SafeLogger());
        interceptor.setLevel(level);
        interceptor.redactHeader("Authorization");
        interceptor.redactHeader("Cookie");
        return interceptor;
    }

    @Override
    public void log(@NonNull String message) {
        Log.d(TAG, mask(message));
    }

    /** 脱敏 */
    @NonNull
    public static String mask(@NonNull String message) {
        String s = message;
        Matcher m = SECRET_FIELD.matcher(s);
        s = m.replaceAll("\"$1\":\"***\"");
        s = BEARER.matcher(s).replaceAll("$1***");
        s = PHONE.matcher(s).replaceAll("$1****$3");
        return s;
    }
}
